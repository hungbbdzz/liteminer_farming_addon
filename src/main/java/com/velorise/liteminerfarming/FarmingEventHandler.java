package com.velorise.liteminerfarming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.HoeItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public class FarmingEventHandler {

    private static final ThreadLocal<Boolean> IS_HANDLING_BREAK = ThreadLocal.withInitial(() -> false);

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (FarmingConfig.PREVENT_FARMLAND_TRAMPLE.get()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        // If LiteMiner or FTB Ultimine is loaded, let them handle block breaking vein mining!
        if (LiteMinerCompat.isLiteMinerLoaded() || FTBUltimineCompat.isFTBUltimineLoaded()) {
            return;
        }

        if (!FarmingConfig.ENABLE_MASS_HARVEST.get()) {
            return;
        }

        if (IS_HANDLING_BREAK.get()) {
            return;
        }

        if (!(event.getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        boolean active = serverPlayer.isShiftKeyDown();
        if (!active && !FarmingConfig.REQUIRE_SNEAK_FALLBACK.get()) {
            active = true;
        }
        if (!active) {
            return;
        }

        BlockPos clickedPos = event.getPos();
        BlockState clickedState = event.getState();

        if (FarmingManager.isCrop(clickedState) || FarmingManager.isColumnCrop(clickedState) || FarmingManager.isFruitCrop(clickedState)) {
            try {
                IS_HANDLING_BREAK.set(true);
                InteractionHand hand = InteractionHand.MAIN_HAND;
                ItemStack heldItem = serverPlayer.getItemInHand(hand);
                boolean handled = FarmingManager.handleMassHarvest(serverPlayer, hand, heldItem, clickedPos);
                if (handled) {
                    event.setCanceled(true);
                }
            } finally {
                IS_HANDLING_BREAK.set(false);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        ItemStack heldItem = player.getItemInHand(hand);

        // Check if activation condition is met (LiteMiner, FTB Ultimine, or Sneak fallback)
        boolean active = false;
        if (LiteMinerCompat.isLiteMinerLoaded()) {
            if (player instanceof ServerPlayer sp) {
                active = LiteMinerCompat.isLiteMinerActive(sp);
            }
        }

        if (!active && FTBUltimineCompat.isFTBUltimineLoaded()) {
            if (player instanceof ServerPlayer sp) {
                active = FTBUltimineCompat.isUltimineActive(sp);
            } else {
                active = FTBUltimineCompat.isUltimineClientActive();
            }
        }

        // If neither LiteMiner nor FTB Ultimine is loaded, use Standalone fallback
        if (!LiteMinerCompat.isLiteMinerLoaded() && !FTBUltimineCompat.isFTBUltimineLoaded()) {
            if (FarmingConfig.REQUIRE_SNEAK_FALLBACK.get()) {
                active = player.isShiftKeyDown();
            } else {
                active = true;
            }
        }

        if (!active) {
            return;
        }

        BlockPos clickedPos = event.getPos();
        Direction clickedFace = event.getFace();
        if (clickedFace == Direction.DOWN) {
            return;
        }

        BlockState clickedState = level.getBlockState(clickedPos);

        // On client side: cancel event if it's an active farming target to prevent ghost prediction
        if (level.isClientSide()) {
            if (isFarmingTarget(level, player, hand, clickedPos, clickedState, heldItem)) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                player.swing(hand);
            }
            return;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // 1. Mass Harvesting (AOE Harvest & Replant)
        if (!heldItem.is(Items.BONE_MEAL)) {
            boolean isDirectCrop = FarmingManager.isCrop(clickedState) || FarmingManager.isColumnCrop(clickedState);
            boolean isSoil = FarmingManager.isFarmland(clickedState) || FarmingManager.isSoulSand(clickedState);
            boolean isAboveCrop = FarmingManager.isCrop(level.getBlockState(clickedPos.above())) || FarmingManager.isColumnCrop(level.getBlockState(clickedPos.above()));

            // If player clicks empty soil while holding seeds, prioritize mass planting over harvesting
            boolean plantingOnEmptySoil = isSoil && level.getBlockState(clickedPos.above()).isAir() && FarmingManager.isPlantableSeed(heldItem);

            if (!plantingOnEmptySoil && (isDirectCrop || isSoil || isAboveCrop)) {
                BlockPos targetCrop = isDirectCrop ? clickedPos : clickedPos.above();
                boolean handled = FarmingManager.handleMassHarvest(serverPlayer, hand, heldItem, targetCrop);
                if (handled) {
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // If hand is empty and harvest didn't trigger, nothing more to do
        if (heldItem.isEmpty()) {
            return;
        }

        // 1. Hoe Interaction (Mass Tilling)
        if (isHoe(heldItem)) {
            boolean handled = FarmingManager.handleMassHoe(serverPlayer, hand, heldItem, clickedPos);
            if (handled) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
            return;
        }

        // 2. Planting Interaction (Mass Planting)
        if (FarmingManager.isPlantableSeed(heldItem)) {
            boolean handled = FarmingManager.handleMassPlanting(serverPlayer, hand, heldItem, clickedPos);
            if (handled) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            } else if (FarmingManager.isSapling(heldItem) && FarmingConfig.SMART_SAPLING_PLANTING.get()) {
                // If smart sapling planting could not find enough space to plant a group/grove,
                // cancel the event so vanilla does NOT sneak-place a single lonely sapling!
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
            return;
        }

        // 3. Bone Meal Interaction (AOE Fertilizing, strictly on farm targets)
        if (heldItem.is(Items.BONE_MEAL)) {
            boolean isFarmTarget = FarmingManager.isCrop(clickedState) || FarmingManager.isFarmland(clickedState)
                    || FarmingManager.isBonemealCrop(clickedState)
                    || FarmingManager.isBonemealCrop(level.getBlockState(clickedPos.above()));
            if (!isFarmTarget) {
                return; // Do NOT trigger mass bone meal on wild grass blocks!
            }

            boolean handled = FarmingManager.handleMassBoneMeal(serverPlayer, hand, heldItem, clickedPos);
            if (handled) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }

            // If bone meal could not fertilize anything (all mature), harvest them
            boolean isDirectCrop = FarmingManager.isCrop(clickedState) || FarmingManager.isColumnCrop(clickedState);
            boolean isFarmland = FarmingManager.isFarmland(clickedState);
            boolean isAboveCrop = FarmingManager.isCrop(level.getBlockState(clickedPos.above())) || FarmingManager.isColumnCrop(level.getBlockState(clickedPos.above()));
            if (isDirectCrop || isFarmland || isAboveCrop) {
                BlockPos targetCrop = isDirectCrop ? clickedPos : clickedPos.above();
                boolean harvestHandled = FarmingManager.handleMassHarvest(serverPlayer, hand, heldItem, targetCrop);
                if (harvestHandled) {
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    event.setCanceled(true);
                }
            }
            return;
        }
    }

    public static boolean isFarmingTarget(Level level, Player player, InteractionHand hand, BlockPos clickedPos, BlockState clickedState, ItemStack heldItem) {
        if (isHoe(heldItem) && FarmingManager.isTillable(level, player, hand, clickedPos)) {
            return true;
        }
        if (heldItem.is(Items.BONE_MEAL)) {
            return FarmingManager.isCrop(clickedState) || FarmingManager.isFarmland(clickedState)
                    || FarmingManager.isBonemealCrop(clickedState)
                    || FarmingManager.isBonemealCrop(level.getBlockState(clickedPos.above()));
        }
        if (FarmingManager.isPlantableSeed(heldItem)) {
            if (FarmingManager.isValidSoilForSeed(heldItem, clickedState, level, clickedPos)
                    || FarmingManager.isValidSoilForSeed(heldItem, level.getBlockState(clickedPos.below()), level, clickedPos.below())) {
                return true;
            }
        }
        boolean isDirectCrop = FarmingManager.isCrop(clickedState) || FarmingManager.isColumnCrop(clickedState);
        boolean isSoil = FarmingManager.isFarmland(clickedState) || FarmingManager.isSoulSand(clickedState);
        boolean isAboveCrop = FarmingManager.isCrop(level.getBlockState(clickedPos.above())) || FarmingManager.isColumnCrop(level.getBlockState(clickedPos.above()));
        return isDirectCrop || isSoil || isAboveCrop;
    }

    public static boolean isHoe(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.canPerformAction(ItemAbilities.HOE_TILL)
                || stack.getItem() instanceof HoeItem
                || stack.is(ItemTags.HOES);
    }
}
