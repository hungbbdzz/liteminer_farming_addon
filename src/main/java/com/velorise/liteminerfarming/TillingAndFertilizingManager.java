package com.velorise.liteminerfarming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;

import java.util.*;

/**
 * Handles hoe tilling, smart bone meal fertilizing, and batch composting.
 */
public class TillingAndFertilizingManager {

    /**
     * Handles mass tilling using positions defined by LiteMiner, FTB Ultimine, or standalone BFS.
     */
    public static boolean handleMassHoe(ServerPlayer player, InteractionHand hand, ItemStack hoeStack, BlockPos clickedPos) {
        Level level = player.level();

        Collection<BlockPos> selected = FarmingManager.getSelectedPositions(player, clickedPos);
        if (selected == null || selected.isEmpty()) {
            selected = fallbackHoeSearch(player, hand, clickedPos);
        }

        if (selected == null || selected.isEmpty()) {
            return false;
        }

        List<BlockPos> sorted = selected.stream()
                .sorted(Comparator.comparingInt(p -> p.distManhattan(clickedPos)))
                .toList();

        int maxLimit = FarmingManager.getEffectiveBlockLimit(player);
        boolean preventBreaking = FarmingManager.shouldPreventToolBreaking(player);
        boolean clearFoliage = FarmingConfig.CLEAR_FOLIAGE.get();
        float exhaustion = FarmingManager.getFoodExhaustion(player);

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

            if (clearFoliage && !aboveState.isAir() && !isValuableCrop && (aboveState.canBeReplaced() || aboveState.is(BlockTags.REPLACEABLE_BY_TREES) || aboveState.is(BlockTags.FLOWERS) || (aboveState.getBlock() instanceof BushBlock && !isValuableCrop))) {
                level.destroyBlock(above, true, player);
                aboveState = level.getBlockState(above);
            }

            if (aboveState.isAir()) {
                BlockHitResult hitResult = new BlockHitResult(
                        Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false
                );
                UseOnContext context = new UseOnContext(player, hand, hitResult);
                BlockState tilledState = currentState.getToolModifiedState(context, ItemAbilities.HOE_TILL, false);
                boolean tilled = false;

                if (tilledState != null) {
                    level.setBlock(pos, tilledState, 11);
                    level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);

                    if (!player.isCreative()) {
                        hoeStack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                    }
                    tilled = true;
                } else {
                    // Fallback for modded blocks (such as Regions Unexplored RUGrassBlock / RUDirtBlock)
                    // that implement hoe tilling inside useItemOn rather than getToolModifiedState
                    int prevDamage = hoeStack.getDamageValue();
                    int prevCount = hoeStack.getCount();
                    ItemInteractionResult result = currentState.useItemOn(hoeStack, level, player, hand, hitResult);
                    if (result.consumesAction()) {
                        tilled = true;
                        if (player.isCreative()) {
                            if (hoeStack.getDamageValue() != prevDamage) {
                                hoeStack.setDamageValue(prevDamage);
                            }
                            if (hoeStack.getCount() != prevCount) {
                                hoeStack.setCount(prevCount);
                            }
                        }
                    }
                }

                if (tilled) {
                    boolean audioCascade = FarmingConfig.SATISFYING_AUDIO_CASCADE.get();
                    int soundInterval = audioCascade ? 2 : 4;
                    if (tilledCount % soundInterval == 0) {
                        float pitch = audioCascade 
                                ? (0.85F + Math.min(0.40F, (tilledCount / 32.0F) * 0.40F))
                                : 1.0F;
                        level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, pitch);
                    }

                    tilledCount++;
                    if (!player.isCreative() && exhaustion > 0) {
                        player.causeFoodExhaustion(exhaustion);
                    }
                }
            }
        }

        if (tilledCount > 0) {
            level.playSound(null, clickedPos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            player.swing(hand, true);
            if (FTBUltimineCompat.isFTBUltimineLoaded() && FTBUltimineCompat.isUltimineActive(player)) {
                FTBUltimineCompat.applyPostUltimineCosts(player, tilledCount);
            }
            return true;
        }

        return false;
    }

    /**
     * Handles AOE Bone Meal fertilizing using positions strictly defined by LiteMiner's Walker or fallback search.
     */
    public static boolean handleMassBoneMeal(ServerPlayer player, InteractionHand hand, ItemStack boneMealStack, BlockPos clickedPos) {
        Level level = player.level();

        // Determine target crop position
        BlockPos targetCropPos = clickedPos;
        BlockState clickedState = level.getBlockState(clickedPos);
        if (PlantClassifier.isFarmland(clickedState)) {
            targetCropPos = clickedPos.above();
        }

        Collection<BlockPos> selected = FarmingManager.getSelectedPositions(player, targetCropPos);
        if (selected == null || selected.isEmpty()) {
            selected = FarmingManager.getSelectedPositions(player, targetCropPos.below());
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

        int maxLimit = FarmingManager.getEffectiveBlockLimit(player);
        float exhaustion = FarmingManager.getFoodExhaustion(player);
        boolean smartBonemeal = FarmingConfig.SMART_BONEMEAL.get();

        int fertilizedCount = 0;
        int maxPasses = smartBonemeal ? 8 : 1;
        int pass = 0;
        boolean anyFertilizedThisPass = true;

        while (anyFertilizedThisPass && fertilizedCount < maxLimit && pass < maxPasses) {
            pass++;
            anyFertilizedThisPass = false;

            for (BlockPos pos : sorted) {
                if (fertilizedCount >= maxLimit) {
                    break;
                }

                if (!player.isCreative() && player.getItemInHand(hand).isEmpty()) {
                    break;
                }

                BlockPos cropPos = pos;
                BlockState state = level.getBlockState(cropPos);
                if (!PlantClassifier.isBonemealCrop(state)) {
                    if (PlantClassifier.isBonemealCrop(level.getBlockState(pos.above()))) {
                        cropPos = pos.above();
                        state = level.getBlockState(cropPos);
                    } else {
                        continue;
                    }
                }

                if (!(state.getBlock() instanceof BonemealableBlock bonemealable)) {
                    continue;
                }

                // Check if this crop can still accept bone meal (skip crops that are already fully mature)
                if (!bonemealable.isValidBonemealTarget(level, cropPos, state)) {
                    continue;
                }

                ItemStack held = player.getItemInHand(hand);
                if (!held.is(Items.BONE_MEAL)) {
                    break;
                }

                // In Creative mode, use a copy of the stack so player's bone meal is never consumed
                ItemStack stackToUse = player.isCreative() ? held.copy() : held;

                if (BoneMealItem.applyBonemeal(stackToUse, level, cropPos, player)) {
                    level.levelEvent(1505, cropPos, 15);
                    fertilizedCount++;
                    anyFertilizedThisPass = true;

                    // Replenish bone meal from inventory if hand stack empties
                    if (!player.isCreative() && player.getItemInHand(hand).isEmpty() && FarmingConfig.PULL_FROM_INVENTORY.get()) {
                        FarmingManager.replenishHand(player, hand, Items.BONE_MEAL);
                    }

                    if (!player.isCreative() && exhaustion > 0) {
                        player.causeFoodExhaustion(exhaustion);
                    }
                }
            }
        }

        if (fertilizedCount > 0) {
            player.swing(hand, true);
            if (FTBUltimineCompat.isFTBUltimineLoaded() && FTBUltimineCompat.isUltimineActive(player)) {
                FTBUltimineCompat.applyPostUltimineCosts(player, fertilizedCount);
            }
            return true;
        }

        return false;
    }

    public static Collection<BlockPos> fallbackHoeSearch(Player player, InteractionHand hand, BlockPos clickedPos) {
        Level level = player.level();
        int maxLimit = FarmingConfig.MAX_BLOCKS.get();
        int radius = FarmingConfig.FARMING_RADIUS.get();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();

        if (!isTillable(level, player, hand, clickedPos)) {
            return Collections.emptyList();
        }

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

                        // Must be contiguous tillable block! Stops at stone, water, wood, farmland, etc.
                        if (isTillable(level, player, hand, next)) {
                            visited.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return result;
    }

    public static boolean isTillable(Level level, Player player, InteractionHand hand, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        UseOnContext context = new UseOnContext(player, hand, new BlockHitResult(
                Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false
        ));
        if (state.getToolModifiedState(context, ItemAbilities.HOE_TILL, false) != null) {
            return true;
        }
        if (state.is(BlockTags.DIRT)) {
            return true;
        }
        String id = state.getBlock().getDescriptionId().toLowerCase(Locale.ROOT);
        return id.contains("dirt") || id.contains("grass") || id.contains("soil");
    }

    public static Collection<BlockPos> fallbackCropSearch(Level level, BlockPos startPos) {
        int maxLimit = FarmingConfig.MAX_BLOCKS.get();
        int radius = FarmingConfig.FARMING_RADIUS.get();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();

        BlockState startState = level.getBlockState(startPos);
        BlockPos actualTargetPos = startPos;
        if (PlantClassifier.isFarmland(startState)) {
            actualTargetPos = startPos.above();
            startState = level.getBlockState(actualTargetPos);
        }

        // Strictly do not bonemeal wild grass blocks!
        if (startState.is(Blocks.GRASS_BLOCK)) {
            return Collections.emptyList();
        }

        // Determine target crop block for strict type matching
        final Block targetCropBlock = PlantClassifier.isBonemealCrop(startState) ? startState.getBlock() : null;

        queue.add(actualTargetPos);
        visited.add(actualTargetPos);

        while (!queue.isEmpty() && result.size() < maxLimit) {
            BlockPos current = queue.poll();

            BlockState curState = level.getBlockState(current);
            BlockPos candidateCrop = current;
            BlockState candidateState = curState;
            if (!PlantClassifier.isBonemealCrop(candidateState) && PlantClassifier.isFarmland(candidateState)) {
                candidateCrop = current.above();
                candidateState = level.getBlockState(candidateCrop);
            }

            boolean isSameCropType = targetCropBlock == null || candidateState.is(targetCropBlock);
            if (isSameCropType && PlantClassifier.isBonemealCrop(candidateState) && candidateState.getBlock() instanceof BonemealableBlock bonemealable) {
                if (bonemealable.isValidBonemealTarget(level, candidateCrop, candidateState)) {
                    if (!result.contains(candidateCrop)) {
                        result.add(candidateCrop);
                    }
                }
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos next = current.offset(dx, dy, dz);
                        if (visited.contains(next)) continue;
                        if (Math.abs(next.getX() - actualTargetPos.getX()) > radius
                                || Math.abs(next.getZ() - actualTargetPos.getZ()) > radius
                                || Math.abs(next.getY() - actualTargetPos.getY()) > 2) {
                            continue;
                        }

                        BlockState nextState = level.getBlockState(next);

                        // STRICTLY ONLY traverse connected farmland or matching crops!
                        // NEVER traverse Grass Blocks, Dirt, or wild foliage!
                        boolean canTraverse = false;
                        if (PlantClassifier.isFarmland(nextState)) {
                            canTraverse = true;
                        } else if (PlantClassifier.isBonemealCrop(nextState)) {
                            if (targetCropBlock != null) {
                                canTraverse = nextState.is(targetCropBlock);
                            } else {
                                canTraverse = true;
                            }
                        }

                        if (canTraverse) {
                            visited.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Batch processes an entire stack (and inventory) of compostables in a Composter in one click.
     */
    public static boolean handleBatchCompost(ServerPlayer player, InteractionHand hand, ItemStack heldItem, BlockPos composterPos) {
        if (!FarmingConfig.BATCH_COMPOSTER.get()) {
            return false;
        }
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        BlockState state = level.getBlockState(composterPos);
        if (!state.is(Blocks.COMPOSTER)) {
            return false;
        }

        Item compostItem = heldItem.getItem();
        if (!ComposterBlock.COMPOSTABLES.containsKey(compostItem)) {
            return false;
        }
        float chance = ComposterBlock.COMPOSTABLES.getFloat(compostItem);
        if (chance <= 0.0F) {
            return false;
        }

        int currentLevel = state.getValue(ComposterBlock.LEVEL);
        int producedBoneMeal = 0;

        // If composter is currently ready (level 8), first harvest that bone meal
        if (currentLevel >= 8) {
            producedBoneMeal++;
            currentLevel = 0;
        }

        int availableCount = heldItem.getCount();
        List<ItemStack> invStacksToConsume = new ArrayList<>();
        if (FarmingConfig.PULL_FROM_INVENTORY.get()) {
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                ItemStack s = player.getInventory().items.get(i);
                if (!s.isEmpty() && s != heldItem && s.is(compostItem)) {
                    invStacksToConsume.add(s);
                    availableCount += s.getCount();
                }
            }
        }

        if (availableCount <= 0) {
            return false;
        }

        int batchLimit = Math.min(availableCount, 128);
        int consumed = 0;

        for (int i = 0; i < batchLimit; i++) {
            consumed++;
            if (serverLevel.random.nextFloat() < chance) {
                currentLevel++;
                if (currentLevel >= 7) {
                    producedBoneMeal++;
                    currentLevel = 0;
                }
            }
        }

        if (!player.isCreative()) {
            int toDeduct = consumed;
            int handDeduct = Math.min(toDeduct, heldItem.getCount());
            heldItem.shrink(handDeduct);
            toDeduct -= handDeduct;

            for (ItemStack invStack : invStacksToConsume) {
                if (toDeduct <= 0) break;
                int deduct = Math.min(toDeduct, invStack.getCount());
                invStack.shrink(deduct);
                toDeduct -= deduct;
            }
        }

        BlockState newState = state.setValue(ComposterBlock.LEVEL, currentLevel);
        serverLevel.setBlock(composterPos, newState, 3);
        serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, composterPos);

        if (producedBoneMeal > 0) {
            ItemStack boneMealStack = new ItemStack(Items.BONE_MEAL, producedBoneMeal);
            boolean harvestToInv = FarmingConfig.HARVEST_TO_INVENTORY.get();
            if (harvestToInv) {
                boolean added = player.getInventory().add(boneMealStack);
                if (!added || !boneMealStack.isEmpty()) {
                    Block.popResource(serverLevel, composterPos.above(), boneMealStack);
                }
                player.containerMenu.broadcastChanges();
            } else {
                Block.popResource(serverLevel, composterPos.above(), boneMealStack);
            }
            serverLevel.playSound(null, composterPos, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            serverLevel.playSound(null, composterPos, SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        serverLevel.sendParticles(ParticleTypes.COMPOSTER, composterPos.getX() + 0.5, composterPos.getY() + 0.8, composterPos.getZ() + 0.5, 12, 0.25, 0.2, 0.25, 0.05);

        player.swing(hand, true);
        return true;
    }
}

