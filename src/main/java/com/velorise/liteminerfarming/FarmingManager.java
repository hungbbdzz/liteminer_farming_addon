package com.velorise.liteminerfarming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;

import java.util.*;

public class FarmingManager {

    /**
     * Checks if an item stack represents a plantable crop/seed on farmland.
     */
    public static boolean isPlantableCrop(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            return block instanceof BushBlock;
        }
        return false;
    }

    /**
     * Universal farmland check supporting vanilla and modded farmlands (e.g. Farmer's Delight Rich Soil Farmland).
     */
    public static boolean isFarmland(BlockState state) {
        if (state.is(Blocks.FARMLAND)) {
            return true;
        }
        String descriptionId = state.getBlock().getDescriptionId().toLowerCase(Locale.ROOT);
        return descriptionId.contains("farmland");
    }

    /**
     * Universal check for mature crops supporting vanilla and modded crops.
     */
    public static boolean isMatureCrop(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }

        Block block = state.getBlock();

        // 1. Standard CropBlock (Wheat, Carrots, Potatoes, Beetroots, Torchflower, modded CropBlocks)
        if (block instanceof CropBlock cropBlock) {
            return cropBlock.isMaxAge(state);
        }

        // 2. Nether Wart
        if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= 3;
        }

        // 3. Cocoa
        if (block instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= 2;
        }

        // 4. Sweet Berry Bush
        if (block instanceof SweetBerryBushBlock) {
            return state.getValue(SweetBerryBushBlock.AGE) >= 2;
        }

        // 5. Generic check for any BushBlock with an "age" integer property (Farmer's Delight tomato/rice, etc.)
        if (block instanceof BushBlock) {
            for (Property<?> prop : state.getProperties()) {
                if (prop instanceof IntegerProperty intProp && intProp.getName().equalsIgnoreCase("age")) {
                    int currentAge = state.getValue(intProp);
                    int maxAge = Collections.max(intProp.getPossibleValues());
                    if (maxAge <= 7) {
                        return currentAge >= maxAge;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Determines the reset/replanted state of a crop (usually age 0, or age 1 for sweet berry bushes).
     */
    public static BlockState getResetCropState(BlockState state) {
        Block block = state.getBlock();

        if (block instanceof CropBlock cropBlock) {
            return cropBlock.getStateForAge(0);
        }

        if (block instanceof NetherWartBlock) {
            return state.setValue(NetherWartBlock.AGE, 0);
        }

        if (block instanceof CocoaBlock) {
            return state.setValue(CocoaBlock.AGE, 0);
        }

        if (block instanceof SweetBerryBushBlock) {
            return state.setValue(SweetBerryBushBlock.AGE, 1);
        }

        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof IntegerProperty intProp && intProp.getName().equalsIgnoreCase("age")) {
                int minAge = Collections.min(intProp.getPossibleValues());
                return state.setValue(intProp, minAge);
            }
        }

        return block.defaultBlockState();
    }

    /**
     * Handles mass tilling using positions strictly defined by LiteMiner's Walker.
     */
    public static boolean handleMassHoe(ServerPlayer player, InteractionHand hand, ItemStack hoeStack, BlockPos clickedPos) {
        Level level = player.level();

        Collection<BlockPos> selected = LiteMinerCompat.getSelectedBlocks(player, clickedPos);
        if (selected == null || selected.isEmpty()) {
            selected = fallbackHoeSearch(player, hand, clickedPos);
        }

        if (selected == null || selected.isEmpty()) {
            return false;
        }

        List<BlockPos> sorted = selected.stream()
                .sorted(Comparator.comparingInt(p -> p.distManhattan(clickedPos)))
                .toList();

        int maxLimit = LiteMinerCompat.getEffectiveBlockLimit();
        boolean preventBreaking = LiteMinerCompat.shouldPreventToolBreaking();
        boolean clearFoliage = FarmingConfig.CLEAR_FOLIAGE.get();
        float exhaustion = LiteMinerCompat.getFoodExhaustion();

        int tilledCount = 0;

        for (BlockPos pos : sorted) {
            if (tilledCount >= maxLimit) {
                break;
            }

            // Safety check: tool broken or almost broken
            if (!player.isCreative()) {
                if (hoeStack.isEmpty()) {
                    break;
                }
                if (preventBreaking && hoeStack.getDamageValue() >= hoeStack.getMaxDamage() - 1) {
                    break;
                }
            }

            BlockState currentState = level.getBlockState(pos);
            BlockPos above = pos.above();
            BlockState aboveState = level.getBlockState(above);

            // Safe foliage clearing: never break existing valuable crops (CropBlock or StemBlock)!
            boolean isValuableCrop = aboveState.getBlock() instanceof CropBlock || aboveState.getBlock() instanceof StemBlock;

            if (clearFoliage && !aboveState.isAir() && !isValuableCrop && (aboveState.canBeReplaced() || aboveState.is(BlockTags.REPLACEABLE_BY_TREES) || aboveState.is(BlockTags.FLOWERS))) {
                level.destroyBlock(above, true, player);
                aboveState = level.getBlockState(above);
            }

            if (aboveState.isAir()) {
                UseOnContext context = new UseOnContext(player, hand, new BlockHitResult(
                        Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false
                ));
                BlockState tilledState = currentState.getToolModifiedState(context, ItemAbilities.HOE_TILL, false);
                if (tilledState != null) {
                    level.setBlock(pos, tilledState, 11);
                    level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);

                    if (!player.isCreative()) {
                        hoeStack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                    }

                    if (tilledCount % 4 == 0) {
                        level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }

                    tilledCount++;
                    if (exhaustion > 0) {
                        player.causeFoodExhaustion(exhaustion);
                    }
                }
            }
        }

        if (tilledCount > 0) {
            level.playSound(null, clickedPos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            player.swing(hand, true);
            return true;
        }

        return false;
    }

    /**
     * Handles mass planting using positions strictly defined by LiteMiner's Walker.
     */
    public static boolean handleMassPlanting(ServerPlayer player, InteractionHand hand, ItemStack seedStack, BlockPos clickedPos) {
        Level level = player.level();
        Item seedItem = seedStack.getItem();
        if (!(seedItem instanceof BlockItem blockItem)) {
            return false;
        }

        Block cropBlock = blockItem.getBlock();
        if (!(cropBlock instanceof BushBlock)) {
            return false;
        }

        // Determine starting farmland pos
        BlockPos startFarmPos = clickedPos;
        BlockState clickedState = level.getBlockState(clickedPos);
        if (!isFarmland(clickedState)) {
            if (isFarmland(level.getBlockState(clickedPos.below()))) {
                startFarmPos = clickedPos.below();
            } else {
                return false;
            }
        }

        Collection<BlockPos> selected = LiteMinerCompat.getSelectedBlocks(player, startFarmPos);
        if (selected == null || selected.isEmpty()) {
            selected = fallbackPlantingSearch(level, startFarmPos);
        }

        if (selected == null || selected.isEmpty()) {
            return false;
        }

        final BlockPos originFarmPos = startFarmPos;
        List<BlockPos> sorted = selected.stream()
                .sorted(Comparator.comparingInt(p -> p.distManhattan(originFarmPos)))
                .toList();

        int maxLimit = LiteMinerCompat.getEffectiveBlockLimit();
        float exhaustion = LiteMinerCompat.getFoodExhaustion();

        int plantedCount = 0;
        SoundType cropSound = null;

        for (BlockPos farmPos : sorted) {
            if (plantedCount >= maxLimit) {
                break;
            }

            if (!isFarmland(level.getBlockState(farmPos))) {
                continue;
            }

            BlockPos above = farmPos.above();
            BlockState aboveState = level.getBlockState(above);

            if (aboveState.isAir()) {
                BlockPlaceContext placeContext = new BlockPlaceContext(new UseOnContext(
                        player, hand, new BlockHitResult(Vec3.atCenterOf(above), Direction.UP, farmPos, false)
                ));
                BlockState placeState = cropBlock.getStateForPlacement(placeContext);
                if (placeState == null) {
                    placeState = cropBlock.defaultBlockState();
                }

                if (placeState.canSurvive(level, above)) {
                    if (consumeSeed(player, hand, seedItem)) {
                        level.setBlock(above, placeState, 3);
                        level.gameEvent(player, GameEvent.BLOCK_PLACE, above);

                        cropSound = placeState.getSoundType(level, above, player);
                        if (plantedCount % 4 == 0) {
                            level.playSound(null, above, cropSound.getPlaceSound(), SoundSource.BLOCKS,
                                    (cropSound.getVolume() + 1.0F) / 2.0F, cropSound.getPitch() * 0.8F);
                        }

                        plantedCount++;
                        if (exhaustion > 0) {
                            player.causeFoodExhaustion(exhaustion);
                        }
                    } else {
                        // Out of seeds! Stop planting
                        break;
                    }
                }
            }
        }

        if (plantedCount > 0) {
            if (cropSound != null) {
                level.playSound(null, startFarmPos.above(), cropSound.getPlaceSound(), SoundSource.BLOCKS,
                        (cropSound.getVolume() + 1.0F) / 2.0F, cropSound.getPitch() * 0.8F);
            }
            player.swing(hand, true);
            return true;
        }

        return false;
    }

    /**
     * Handles AOE Bone Meal fertilizing using positions strictly defined by LiteMiner's Walker.
     */
    public static boolean handleMassBoneMeal(ServerPlayer player, InteractionHand hand, ItemStack boneMealStack, BlockPos clickedPos) {
        Level level = player.level();

        // Determine target crop position
        BlockPos targetCropPos = clickedPos;
        BlockState clickedState = level.getBlockState(clickedPos);
        if (isFarmland(clickedState)) {
            targetCropPos = clickedPos.above();
        }

        Collection<BlockPos> selected = LiteMinerCompat.getSelectedBlocks(player, targetCropPos);
        if (selected == null || selected.isEmpty()) {
            selected = LiteMinerCompat.getSelectedBlocks(player, targetCropPos.below());
        }

        if (selected == null || selected.isEmpty()) {
            selected = fallbackCropSearch(level, targetCropPos);
        }

        if (selected == null || selected.isEmpty()) {
            return false;
        }

        final BlockPos originPos = targetCropPos;
        List<BlockPos> sorted = selected.stream()
                .sorted(Comparator.comparingInt(p -> p.distManhattan(originPos)))
                .toList();

        int maxLimit = LiteMinerCompat.getEffectiveBlockLimit();
        float exhaustion = LiteMinerCompat.getFoodExhaustion();

        int fertilizedCount = 0;

        for (BlockPos pos : sorted) {
            if (fertilizedCount >= maxLimit) {
                break;
            }

            if (!player.isCreative() && player.getItemInHand(hand).isEmpty()) {
                break;
            }

            BlockPos cropPos = pos;
            if (!(level.getBlockState(cropPos).getBlock() instanceof BonemealableBlock)) {
                if (level.getBlockState(pos.above()).getBlock() instanceof BonemealableBlock) {
                    cropPos = pos.above();
                } else {
                    continue;
                }
            }

            ItemStack held = player.getItemInHand(hand);
            if (!held.is(Items.BONE_MEAL)) {
                break;
            }

            if (BoneMealItem.applyBonemeal(held, level, cropPos, player)) {
                level.levelEvent(1505, cropPos, 15);
                fertilizedCount++;

                // Replenish bone meal from inventory if hand stack empties
                if (!player.isCreative() && player.getItemInHand(hand).isEmpty() && FarmingConfig.PULL_FROM_INVENTORY.get()) {
                    replenishHand(player, hand, Items.BONE_MEAL);
                }

                if (exhaustion > 0) {
                    player.causeFoodExhaustion(exhaustion);
                }
            }
        }

        if (fertilizedCount > 0) {
            player.swing(hand, true);
            return true;
        }

        return false;
    }

    /**
     * Handles AOE mass harvesting and replanting using positions strictly defined by LiteMiner's Walker.
     */
    public static boolean handleMassHarvest(ServerPlayer player, InteractionHand hand, ItemStack heldItem, BlockPos clickedCropPos) {
        if (!FarmingConfig.ENABLE_MASS_HARVEST.get()) {
            return false;
        }

        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        Collection<BlockPos> selected = LiteMinerCompat.getSelectedBlocks(player, clickedCropPos);
        if (selected == null || selected.isEmpty()) {
            selected = LiteMinerCompat.getSelectedBlocks(player, clickedCropPos.below());
        }

        if (selected == null || selected.isEmpty()) {
            selected = fallbackHarvestSearch(serverLevel, clickedCropPos);
        }

        if (selected == null || selected.isEmpty()) {
            return false;
        }

        final BlockPos originPos = clickedCropPos;
        List<BlockPos> sorted = selected.stream()
                .sorted(Comparator.comparingInt(p -> p.distManhattan(originPos)))
                .toList();

        int maxLimit = LiteMinerCompat.getEffectiveBlockLimit();
        boolean preventBreaking = LiteMinerCompat.shouldPreventToolBreaking();
        boolean replant = FarmingConfig.REPLANT_CROPS.get();
        boolean damageHoe = FarmingConfig.DAMAGE_HOE_ON_HARVEST.get();
        float exhaustion = LiteMinerCompat.getFoodExhaustion();

        int harvestedCount = 0;
        SoundType lastSoundType = null;
        BlockState originState = serverLevel.getBlockState(clickedCropPos);

        for (BlockPos pos : sorted) {
            if (harvestedCount >= maxLimit) {
                break;
            }

            // Check tool breaking safety if using a damageable tool/hoe
            if (!player.isCreative() && !heldItem.isEmpty() && heldItem.isDamageableItem()) {
                if (preventBreaking && heldItem.getDamageValue() >= heldItem.getMaxDamage() - 1) {
                    break;
                }
            }

            BlockPos cropPos = pos;
            BlockState cropState = serverLevel.getBlockState(cropPos);

            // If pos is farmland or not mature, check pos.above()
            if (!isMatureCrop(cropState)) {
                BlockPos above = pos.above();
                if (isMatureCrop(serverLevel.getBlockState(above))) {
                    cropPos = above;
                    cropState = serverLevel.getBlockState(cropPos);
                } else {
                    continue;
                }
            }

            Block cropBlock = cropState.getBlock();
            lastSoundType = cropState.getSoundType(serverLevel, cropPos, player);

            // Special case for SweetBerryBush: harvest berries without breaking or consuming seeds
            if (cropBlock instanceof SweetBerryBushBlock) {
                int age = cropState.getValue(SweetBerryBushBlock.AGE);
                int berryCount = 1 + serverLevel.random.nextInt(2) + (age >= 3 ? 1 : 0);
                BlockState resetBush = cropState.setValue(SweetBerryBushBlock.AGE, 1);
                serverLevel.setBlock(cropPos, resetBush, 2);
                serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, cropPos);
                Block.popResource(serverLevel, cropPos, new ItemStack(Items.SWEET_BERRIES, berryCount));
                serverLevel.playSound(null, cropPos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 1.0F);

                harvestedCount++;
                applyHarvestCosts(player, hand, heldItem, damageHoe, exhaustion);
                continue;
            }

            // Standard crops: calculate drops
            List<ItemStack> drops = new ArrayList<>(Block.getDrops(cropState, serverLevel, cropPos, null, player, heldItem));

            if (replant) {
                Item seedItem = cropBlock.asItem();
                boolean canReplant = player.isCreative();

                if (!canReplant && seedItem != Items.AIR) {
                    // Try to deduct 1 seed from harvested drops
                    for (Iterator<ItemStack> it = drops.iterator(); it.hasNext(); ) {
                        ItemStack drop = it.next();
                        if (!drop.isEmpty() && drop.is(seedItem)) {
                            drop.shrink(1);
                            if (drop.isEmpty()) {
                                it.remove();
                            }
                            canReplant = true;
                            break;
                        }
                    }

                    // If drops didn't provide a seed, try consuming from player inventory
                    if (!canReplant && FarmingConfig.PULL_FROM_INVENTORY.get()) {
                        canReplant = consumeSeed(player, hand, seedItem);
                    }
                }

                if (canReplant) {
                    BlockState resetState = getResetCropState(cropState);
                    serverLevel.setBlock(cropPos, resetState, 3);
                    serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, cropPos);
                } else {
                    serverLevel.destroyBlock(cropPos, false, player);
                }
            } else {
                serverLevel.destroyBlock(cropPos, false, player);
            }

            // Drop all harvested items into the world
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) {
                    Block.popResource(serverLevel, cropPos, drop);
                }
            }

            if (harvestedCount % 4 == 0 && lastSoundType != null) {
                serverLevel.playSound(null, cropPos, lastSoundType.getBreakSound(), SoundSource.BLOCKS,
                        (lastSoundType.getVolume() + 1.0F) / 2.0F, lastSoundType.getPitch() * 0.8F);
            }

            harvestedCount++;
            applyHarvestCosts(player, hand, heldItem, damageHoe, exhaustion);
        }

        if (harvestedCount > 0) {
            if (lastSoundType != null) {
                serverLevel.playSound(null, clickedCropPos, lastSoundType.getBreakSound(), SoundSource.BLOCKS,
                        (lastSoundType.getVolume() + 1.0F) / 2.0F, lastSoundType.getPitch() * 0.8F);
            } else if (originState != null) {
                SoundType st = originState.getSoundType(serverLevel, clickedCropPos, player);
                serverLevel.playSound(null, clickedCropPos, st.getBreakSound(), SoundSource.BLOCKS,
                        (st.getVolume() + 1.0F) / 2.0F, st.getPitch() * 0.8F);
            }
            player.swing(hand, true);
            return true;
        }

        return false;
    }

    private static void applyHarvestCosts(ServerPlayer player, InteractionHand hand, ItemStack heldItem, boolean damageHoe, float exhaustion) {
        if (player.isCreative()) {
            return;
        }
        if (damageHoe && !heldItem.isEmpty() && heldItem.canPerformAction(ItemAbilities.HOE_TILL)) {
            heldItem.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        if (exhaustion > 0) {
            player.causeFoodExhaustion(exhaustion);
        }
    }

    private static Collection<BlockPos> fallbackHarvestSearch(Level level, BlockPos startPos) {
        int maxLimit = FarmingConfig.MAX_BLOCKS.get();
        int radius = FarmingConfig.FARMING_RADIUS.get();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();

        queue.add(startPos);
        visited.add(startPos);

        while (!queue.isEmpty() && result.size() < maxLimit) {
            BlockPos current = queue.poll();
            result.add(current);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos next = current.offset(dx, dy, dz);
                        if (visited.contains(next)) continue;
                        if (Math.abs(next.getX() - startPos.getX()) > radius
                                || Math.abs(next.getZ() - startPos.getZ()) > radius
                                || Math.abs(next.getY() - startPos.getY()) > 2) {
                            continue;
                        }

                        BlockState nextState = level.getBlockState(next);
                        if (isMatureCrop(nextState) || isFarmland(nextState) || isMatureCrop(level.getBlockState(next.above()))) {
                            visited.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static Collection<BlockPos> fallbackCropSearch(Level level, BlockPos startPos) {
        int maxLimit = FarmingConfig.MAX_BLOCKS.get();
        int radius = FarmingConfig.FARMING_RADIUS.get();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();

        queue.add(startPos);
        visited.add(startPos);

        while (!queue.isEmpty() && result.size() < maxLimit) {
            BlockPos current = queue.poll();
            result.add(current);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos next = current.offset(dx, dy, dz);
                        if (visited.contains(next)) continue;
                        if (Math.abs(next.getX() - startPos.getX()) > radius
                                || Math.abs(next.getZ() - startPos.getZ()) > radius
                                || Math.abs(next.getY() - startPos.getY()) > 2) {
                            continue;
                        }

                        BlockState nextState = level.getBlockState(next);
                        if (nextState.getBlock() instanceof BonemealableBlock || isFarmland(nextState)) {
                            visited.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static Collection<BlockPos> fallbackHoeSearch(Player player, InteractionHand hand, BlockPos clickedPos) {
        Level level = player.level();
        int maxLimit = FarmingConfig.MAX_BLOCKS.get();
        int radius = FarmingConfig.FARMING_RADIUS.get();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();

        queue.add(clickedPos);
        visited.add(clickedPos);

        while (!queue.isEmpty() && result.size() < maxLimit) {
            BlockPos current = queue.poll();
            result.add(current);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos next = current.offset(dx, dy, dz);
                        if (visited.contains(next)) continue;
                        if (Math.abs(next.getX() - clickedPos.getX()) > radius
                                || Math.abs(next.getZ() - clickedPos.getZ()) > radius
                                || Math.abs(next.getY() - clickedPos.getY()) > 2) {
                            continue;
                        }

                        BlockState nextState = level.getBlockState(next);
                        if (isFarmland(nextState) || isTillable(level, player, hand, next)) {
                            visited.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static Collection<BlockPos> fallbackPlantingSearch(Level level, BlockPos startFarmPos) {
        int maxLimit = FarmingConfig.MAX_BLOCKS.get();
        int radius = FarmingConfig.FARMING_RADIUS.get();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();

        queue.add(startFarmPos);
        visited.add(startFarmPos);

        while (!queue.isEmpty() && result.size() < maxLimit) {
            BlockPos current = queue.poll();
            result.add(current);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos next = current.offset(dx, dy, dz);
                        if (visited.contains(next)) continue;
                        if (Math.abs(next.getX() - startFarmPos.getX()) > radius
                                || Math.abs(next.getZ() - startFarmPos.getZ()) > radius
                                || Math.abs(next.getY() - startFarmPos.getY()) > 2) {
                            continue;
                        }

                        if (isFarmland(level.getBlockState(next))) {
                            visited.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static boolean isTillable(Level level, Player player, InteractionHand hand, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        UseOnContext context = new UseOnContext(player, hand, new BlockHitResult(
                Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false
        ));
        return state.getToolModifiedState(context, ItemAbilities.HOE_TILL, false) != null;
    }

    public static boolean consumeSeed(Player player, InteractionHand hand, Item seedItem) {
        if (player.isCreative()) {
            return true;
        }

        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty() && held.is(seedItem)) {
            held.shrink(1);
            if (held.isEmpty() && FarmingConfig.PULL_FROM_INVENTORY.get()) {
                replenishHand(player, hand, seedItem);
            }
            return true;
        }

        if (FarmingConfig.PULL_FROM_INVENTORY.get()) {
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                ItemStack invStack = player.getInventory().items.get(i);
                if (!invStack.isEmpty() && invStack.is(seedItem)) {
                    invStack.shrink(1);
                    if (player.getItemInHand(hand).isEmpty()) {
                        player.setItemInHand(hand, invStack.copy());
                        invStack.setCount(0);
                    }
                    return true;
                }
            }
        }

        return false;
    }

    public static void replenishHand(Player player, InteractionHand hand, Item item) {
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack invStack = player.getInventory().items.get(i);
            if (!invStack.isEmpty() && invStack.is(item)) {
                player.setItemInHand(hand, invStack.copy());
                invStack.setCount(0);
                return;
            }
        }
    }
}
