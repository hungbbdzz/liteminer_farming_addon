package com.velorise.liteminerfarming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Handles mass planting operations and planting candidate searches:
 * - Mass planting for seeds and crops with layout algorithms and intercropping
 * - Horizontal mass planting of Cocoa Beans on Jungle wood logs
 * - Breadth-First-Search (BFS) fallback planting candidate searches
 */
public class PlantingManager {

    /**
     * Handles mass planting using positions strictly defined by LiteMiner's Walker or fallback search.
     */
    public static boolean handleMassPlanting(ServerPlayer player, InteractionHand hand, ItemStack seedStack, BlockPos clickedPos) {
        if (PlantClassifier.isCocoaBean(seedStack)) {
            return handleMassPlantingCocoa(player, hand, seedStack, clickedPos);
        }

        Level level = player.level();
        Item seedItem = seedStack.getItem();
        if (!(seedItem instanceof BlockItem blockItem)) {
            return false;
        }

        Block cropBlock = blockItem.getBlock();

        // Determine starting soil pos
        BlockPos startSoilPos = clickedPos;
        BlockState clickedState = level.getBlockState(clickedPos);
        if (!PlantClassifier.isValidSoilForSeed(seedStack, clickedState, level, clickedPos)) {
            if (PlantClassifier.isValidSoilForSeed(seedStack, level.getBlockState(clickedPos.below()), level, clickedPos.below())) {
                startSoilPos = clickedPos.below();
                clickedState = level.getBlockState(startSoilPos);
            } else {
                return false;
            }
        }

        Collection<BlockPos> selected = FarmingManager.getSelectedPositions(player, startSoilPos);
        if (selected == null || selected.isEmpty()) {
            selected = fallbackPlantingSearch(level, startSoilPos, seedStack);
        }

        if (selected == null || selected.isEmpty()) {
            return false;
        }

        final BlockPos originSoilPos = startSoilPos;
        List<BlockPos> sorted = selected.stream()
                .sorted(Comparator.comparingInt(p -> p.distManhattan(originSoilPos)))
                .toList();

        if (PlantClassifier.isCactus(seedStack)) {
            sorted = PlantingAlgorithms.filterOptimalCactusPositions(level, sorted, originSoilPos);
            if (sorted == null || sorted.isEmpty()) {
                return false;
            }
        } else if (PlantClassifier.isFruitSeed(seedStack) && FarmingConfig.SMART_MELON_PUMPKIN_PLANTING.get()) {
            sorted = PlantingAlgorithms.filterOptimalFruitStemPositions(level, sorted, originSoilPos);
            if (sorted == null || sorted.isEmpty()) {
                return false;
            }
        } else if (PlantClassifier.isSapling(seedStack) && FarmingConfig.SMART_SAPLING_PLANTING.get()) {
            sorted = PlantingAlgorithms.filterSmartSaplingPositions(level, sorted, seedStack, originSoilPos);
            if (sorted == null || sorted.isEmpty()) {
                return false;
            }
        } else if ((PlantClassifier.isFlower(seedStack) || PlantClassifier.isMushroom(seedStack) || PlantClassifier.isChorusFlower(seedStack)) && FarmingConfig.SMART_FLOWER_PLANTING.get()) {
            sorted = PlantingAlgorithms.filterAntiOvercrowdedFloraPositions(level, sorted, originSoilPos, seedStack);
            if (sorted == null || sorted.isEmpty()) {
                return false;
            }
        }

        int maxLimit = FarmingManager.getEffectiveBlockLimit(player);
        float exhaustion = FarmingManager.getFoodExhaustion(player);

        ItemStack offHandStack = player.getItemInHand(InteractionHand.OFF_HAND);
        boolean isIntercropping = FarmingConfig.SMART_INTERCROPPING.get()
                && PlantClassifier.isIntercroppableCrop(seedStack)
                && PlantClassifier.isIntercroppableCrop(offHandStack)
                && !seedStack.is(offHandStack.getItem());

        Direction facing = player.getDirection();
        Item mainSeedItem = seedItem;
        Block mainCropBlock = cropBlock;

        Item offSeedItem = isIntercropping ? offHandStack.getItem() : null;
        Block offCropBlock = isIntercropping ? PlantClassifier.getCropBlock(offHandStack) : null;

        boolean evenIsMain = true;
        if (isIntercropping && offCropBlock != null) {
            evenIsMain = PlantingAlgorithms.determineIntercropPhase(level, originSoilPos, facing, mainCropBlock, offCropBlock);
        }

        boolean alternateOnX = (facing.getAxis() == Direction.Axis.Z);

        int plantedCount = 0;
        SoundType cropSound = null;

        for (BlockPos soilPos : sorted) {
            if (plantedCount >= maxLimit) {
                break;
            }

            boolean useMainCrop = true;
            if (isIntercropping && offCropBlock != null) {
                int rowCoord = alternateOnX ? (soilPos.getX() - originSoilPos.getX()) : (soilPos.getZ() - originSoilPos.getZ());
                useMainCrop = (Math.floorMod(rowCoord, 2) == 0) ? evenIsMain : !evenIsMain;
            }

            Item currentSeedItem = useMainCrop ? mainSeedItem : offSeedItem;
            Block currentCropBlock = useMainCrop ? mainCropBlock : offCropBlock;
            InteractionHand currentHand = useMainCrop ? hand : InteractionHand.OFF_HAND;
            ItemStack currentSeedStack = useMainCrop ? seedStack : offHandStack;

            BlockState soilState = level.getBlockState(soilPos);
            if (!PlantClassifier.isValidSoilForSeed(currentSeedStack, soilState, level, soilPos)) {
                continue;
            }

            BlockPos above = soilPos.above();
            BlockState aboveState = level.getBlockState(above);

            if (aboveState.isAir() || aboveState.canBeReplaced()) {
                BlockPlaceContext placeContext = new BlockPlaceContext(new UseOnContext(
                        player, currentHand, new BlockHitResult(Vec3.atCenterOf(above), Direction.UP, soilPos, false)
                ));
                BlockState placeState = currentCropBlock.getStateForPlacement(placeContext);
                if (placeState == null) {
                    placeState = currentCropBlock.defaultBlockState();
                }

                if (placeState.canSurvive(level, above)) {
                    if (FarmingManager.consumeSeed(player, currentHand, currentSeedItem)) {
                        if (currentCropBlock instanceof DoublePlantBlock) {
                            DoublePlantBlock.placeAt(level, placeState, above, 3);
                        } else {
                            level.setBlock(above, placeState, 3);
                        }
                        level.gameEvent(player, GameEvent.BLOCK_PLACE, above);

                        cropSound = placeState.getSoundType(level, above, player);
                        boolean audioCascade = FarmingConfig.SATISFYING_AUDIO_CASCADE.get();
                        int soundInterval = audioCascade ? 2 : 4;
                        if (plantedCount % soundInterval == 0) {
                            float pitch = audioCascade 
                                    ? (0.8F + Math.min(0.45F, (plantedCount / 32.0F) * 0.45F))
                                    : (cropSound.getPitch() * 0.8F);
                            level.playSound(null, above, cropSound.getPlaceSound(), SoundSource.BLOCKS,
                                    (cropSound.getVolume() + 1.0F) / 2.0F, pitch);
                        }

                        if (audioCascade && level instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, above.getX() + 0.5, above.getY() + 0.2, above.getZ() + 0.5, 1, 0.2, 0.1, 0.2, 0.02);
                        }

                        plantedCount++;
                        if (!player.isCreative() && exhaustion > 0) {
                            player.causeFoodExhaustion(exhaustion);
                        }
                    } else if (!isIntercropping) {
                        // Out of seeds! Stop planting in single crop mode
                        break;
                    }
                }
            }
        }

        if (plantedCount > 0) {
            if (cropSound != null) {
                level.playSound(null, startSoilPos.above(), cropSound.getPlaceSound(), SoundSource.BLOCKS,
                        (cropSound.getVolume() + 1.0F) / 2.0F, cropSound.getPitch() * 0.8F);
            }
            player.swing(hand, true);
            if (FTBUltimineCompat.isFTBUltimineLoaded() && FTBUltimineCompat.isUltimineActive(player)) {
                FTBUltimineCompat.applyPostUltimineCosts(player, plantedCount);
            }
            return true;
        }

        return false;
    }

    /**
     * Handles mass planting of Cocoa Beans on horizontal faces of Jungle Logs.
     */
    public static boolean handleMassPlantingCocoa(ServerPlayer player, InteractionHand hand, ItemStack seedStack, BlockPos clickedPos) {
        Level level = player.level();
        Collection<BlockPos> selected = FarmingManager.getSelectedPositions(player, clickedPos);
        List<BlockPos> targetPodPositions = new ArrayList<>();

        if (selected != null && !selected.isEmpty()) {
            for (BlockPos pos : selected) {
                BlockState s = level.getBlockState(pos);
                if (PlantClassifier.isJungleLog(s)) {
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        BlockPos podPos = pos.relative(dir);
                        BlockState podState = level.getBlockState(podPos);
                        if (podState.isAir() || podState.canBeReplaced()) {
                            Direction facingToLog = dir.getOpposite();
                            BlockState placeState = Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, facingToLog);
                            if (placeState.canSurvive(level, podPos) && !targetPodPositions.contains(podPos)) {
                                targetPodPositions.add(podPos);
                            }
                        }
                    }
                }
            }
        }

        if (targetPodPositions.isEmpty()) {
            targetPodPositions.addAll(fallbackCocoaPlantingSearch(level, clickedPos));
        }

        if (targetPodPositions.isEmpty()) {
            return false;
        }

        final BlockPos origin = clickedPos;
        targetPodPositions.sort(Comparator.comparingInt(p -> p.distManhattan(origin)));

        int maxLimit = FarmingManager.getEffectiveBlockLimit(player);
        float exhaustion = FarmingManager.getFoodExhaustion(player);
        int plantedCount = 0;

        for (BlockPos podPos : targetPodPositions) {
            if (plantedCount >= maxLimit) break;

            BlockState currentAtPod = level.getBlockState(podPos);
            if (!currentAtPod.isAir() && !currentAtPod.canBeReplaced()) {
                continue;
            }

            Direction facingToLog = null;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (PlantClassifier.isJungleLog(level.getBlockState(podPos.relative(d)))) {
                    facingToLog = d;
                    break;
                }
            }
            if (facingToLog == null) {
                continue;
            }

            BlockState placeState = Blocks.COCOA.defaultBlockState()
                    .setValue(CocoaBlock.FACING, facingToLog)
                    .setValue(CocoaBlock.AGE, 0);

            if (!placeState.canSurvive(level, podPos)) {
                continue;
            }

            if (!FarmingManager.consumeSeed(player, hand, Items.COCOA_BEANS)) {
                break;
            }

            level.setBlock(podPos, placeState, 3);
            level.gameEvent(player, GameEvent.BLOCK_PLACE, podPos);

            SoundType cropSound = placeState.getSoundType(level, podPos, player);
            boolean audioCascade = FarmingConfig.SATISFYING_AUDIO_CASCADE.get();
            int soundInterval = audioCascade ? 2 : 4;
            if (plantedCount % soundInterval == 0) {
                float pitch = audioCascade 
                        ? (0.8F + Math.min(0.45F, (plantedCount / 32.0F) * 0.45F))
                        : (cropSound.getPitch() * 0.8F);
                level.playSound(null, podPos, cropSound.getPlaceSound(), SoundSource.BLOCKS,
                        (cropSound.getVolume() + 1.0F) / 2.0F, pitch);
            }

            if (audioCascade && level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, podPos.getX() + 0.5, podPos.getY() + 0.5, podPos.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.02);
            }

            plantedCount++;
            if (!player.isCreative() && exhaustion > 0) {
                player.causeFoodExhaustion(exhaustion);
            }
        }

        if (plantedCount > 0) {
            player.swing(hand, true);
            if (FTBUltimineCompat.isFTBUltimineLoaded() && FTBUltimineCompat.isUltimineActive(player)) {
                FTBUltimineCompat.applyPostUltimineCosts(player, plantedCount);
            }
            return true;
        }
        return false;
    }

    public static Collection<BlockPos> fallbackCocoaPlantingSearch(Level level, BlockPos startPos) {
        int maxLimit = FarmingConfig.MAX_BLOCKS.get();
        int radius = FarmingConfig.FARMING_RADIUS.get();

        BlockPos startLog = startPos;
        BlockState st = level.getBlockState(startPos);
        if (st.getBlock() instanceof CocoaBlock) {
            startLog = startPos.relative(st.getValue(CocoaBlock.FACING));
        } else if (!PlantClassifier.isJungleLog(st)) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (PlantClassifier.isJungleLog(level.getBlockState(startPos.relative(d)))) {
                    startLog = startPos.relative(d);
                    break;
                }
            }
            if (!PlantClassifier.isJungleLog(level.getBlockState(startLog))) {
                return Collections.emptyList();
            }
        }

        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visitedLogs = new HashSet<>();
        List<BlockPos> validPodSpots = new ArrayList<>();

        queue.add(startLog);
        visitedLogs.add(startLog);

        while (!queue.isEmpty() && validPodSpots.size() < maxLimit) {
            BlockPos logPos = queue.poll();

            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos podPos = logPos.relative(dir);
                BlockState podState = level.getBlockState(podPos);
                if (podState.isAir() || podState.canBeReplaced()) {
                    Direction facingToLog = dir.getOpposite();
                    BlockState placeState = Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, facingToLog);
                    if (placeState.canSurvive(level, podPos) && !validPodSpots.contains(podPos)) {
                        validPodSpots.add(podPos);
                        if (validPodSpots.size() >= maxLimit) {
                            break;
                        }
                    }
                }
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos nextLog = logPos.offset(dx, dy, dz);
                        if (visitedLogs.contains(nextLog)) continue;
                        if (Math.abs(nextLog.getX() - startLog.getX()) > radius
                                || Math.abs(nextLog.getZ() - startLog.getZ()) > radius
                                || Math.abs(nextLog.getY() - startLog.getY()) > 16) {
                            continue;
                        }
                        if (PlantClassifier.isJungleLog(level.getBlockState(nextLog))) {
                            visitedLogs.add(nextLog);
                            queue.add(nextLog);
                        }
                    }
                }
            }
        }

        final BlockPos origin = startPos;
        validPodSpots.sort(Comparator.comparingInt(p -> p.distManhattan(origin)));
        return validPodSpots;
    }

    public static Collection<BlockPos> fallbackPlantingSearch(Level level, BlockPos startSoilPos, ItemStack seedStack) {
        if (PlantClassifier.isCocoaBean(seedStack)) {
            return fallbackCocoaPlantingSearch(level, startSoilPos);
        }

        int maxLimit = FarmingConfig.MAX_BLOCKS.get();
        int radius = FarmingConfig.FARMING_RADIUS.get();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();

        BlockState startState = level.getBlockState(startSoilPos);
        BlockPos actualSoil = startSoilPos;
        if (!PlantClassifier.isValidSoilForSeed(seedStack, startState, level, startSoilPos)) {
            if (PlantClassifier.isValidSoilForSeed(seedStack, level.getBlockState(startSoilPos.below()), level, startSoilPos.below())) {
                actualSoil = startSoilPos.below();
                startState = level.getBlockState(actualSoil);
            } else {
                return Collections.emptyList();
            }
        }

        boolean isSoulSandTarget = PlantClassifier.isSoulSand(startState);
        boolean isFarmlandTarget = PlantClassifier.isFarmland(startState);
        boolean isSoilTarget = PlantClassifier.isValidSoilForSeed(seedStack, startState, level, actualSoil);

        queue.add(actualSoil);
        visited.add(actualSoil);

        while (!queue.isEmpty() && result.size() < maxLimit) {
            BlockPos current = queue.poll();

            // If space above is air or replaceable, add to result
            BlockPos above = current.above();
            BlockState aboveState = level.getBlockState(above);
            if (aboveState.isAir() || aboveState.canBeReplaced()) {
                result.add(current);
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos next = current.offset(dx, dy, dz);
                        if (visited.contains(next)) continue;
                        if (Math.abs(next.getX() - actualSoil.getX()) > radius
                                || Math.abs(next.getZ() - actualSoil.getZ()) > radius
                                || Math.abs(next.getY() - actualSoil.getY()) > 2) {
                            continue;
                        }

                        BlockState nextState = level.getBlockState(next);
                        boolean matches = false;
                        if (isSoulSandTarget && PlantClassifier.isSoulSand(nextState)) {
                            matches = true;
                        } else if (isFarmlandTarget && PlantClassifier.isFarmland(nextState)) {
                            matches = true;
                        } else if (isSoilTarget && PlantClassifier.isValidSoilForSeed(seedStack, nextState, level, next)) {
                            if (nextState.is(startState.getBlock())
                                    || (startState.is(BlockTags.DIRT) && nextState.is(BlockTags.DIRT))
                                    || (startState.is(BlockTags.SAND) && nextState.is(BlockTags.SAND))
                                    || (seedStack.is(Items.BAMBOO) && nextState.is(BlockTags.BAMBOO_PLANTABLE_ON))
                                    || (PlantClassifier.isChorusFlower(seedStack) && nextState.is(Blocks.END_STONE))
                                    || (PlantClassifier.isMushroom(seedStack) && (nextState.is(BlockTags.DIRT) || nextState.is(BlockTags.NYLIUM) || nextState.is(Blocks.MYCELIUM) || nextState.is(Blocks.PODZOL)))) {
                                matches = true;
                            }
                        }

                        if (matches) {
                            visited.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }

        if (PlantClassifier.isCactus(seedStack)) {
            result = PlantingAlgorithms.filterOptimalCactusPositions(level, result, actualSoil);
        } else if (PlantClassifier.isFruitSeed(seedStack) && FarmingConfig.SMART_MELON_PUMPKIN_PLANTING.get()) {
            result = PlantingAlgorithms.filterOptimalFruitStemPositions(level, result, actualSoil);
        } else if (PlantClassifier.isSapling(seedStack) && FarmingConfig.SMART_SAPLING_PLANTING.get()) {
            result = PlantingAlgorithms.filterSmartSaplingPositions(level, result, seedStack, actualSoil);
        } else if ((PlantClassifier.isFlower(seedStack) || PlantClassifier.isMushroom(seedStack) || PlantClassifier.isChorusFlower(seedStack)) && FarmingConfig.SMART_FLOWER_PLANTING.get()) {
            result = PlantingAlgorithms.filterAntiOvercrowdedFloraPositions(level, result, actualSoil, seedStack);
        }

        return result;
    }

    public static Collection<BlockPos> fallbackPlantingSearch(Level level, BlockPos startFarmPos) {
        return fallbackPlantingSearch(level, startFarmPos, ItemStack.EMPTY);
    }
}
