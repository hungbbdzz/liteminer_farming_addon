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

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (FarmingConfig.PREVENT_FARMLAND_TRAMPLE.get()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        InteractionHand hand = event.getHand();
        ItemStack heldItem = player.getItemInHand(hand);

        // Check if activation condition is met (LiteMiner, FTB Ultimine, or Sneak fallback)
        boolean active = false;
        if (LiteMinerCompat.isLiteMinerLoaded()) {
            active = LiteMinerCompat.isLiteMinerActive(serverPlayer);
        }

        if (!active && FTBUltimineCompat.isFTBUltimineLoaded()) {
            active = FTBUltimineCompat.isUltimineActive(serverPlayer);
        }

        if (!active && FarmingConfig.REQUIRE_SNEAK_FALLBACK.get()) {
            active = player.isShiftKeyDown();
        } else if (!active && !LiteMinerCompat.isLiteMinerLoaded() && !FTBUltimineCompat.isFTBUltimineLoaded() && !FarmingConfig.REQUIRE_SNEAK_FALLBACK.get()) {
            active = true;
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

        // 1. Mass Harvesting (AOE Harvest & Replant)
        // Works with empty hand, hoe, or any held item when targeting a crop, farmland, or sugar cane
        // Note: When holding Bone Meal, never harvest here - prioritize fertilizing growing crops in the area!
        if (!heldItem.is(Items.BONE_MEAL)) {
            boolean isDirectCrop = FarmingManager.isCrop(clickedState) || FarmingManager.isSugarCane(clickedState);
            boolean isFarmland = FarmingManager.isFarmland(clickedState);
            boolean isAboveCrop = FarmingManager.isCrop(level.getBlockState(clickedPos.above())) || FarmingManager.isSugarCane(level.getBlockState(clickedPos.above()));

            // If player clicks empty farmland while holding seeds, prioritize mass planting over harvesting
            boolean plantingOnEmptyFarmland = isFarmland && level.getBlockState(clickedPos.above()).isAir() && FarmingManager.isPlantableCrop(heldItem);

            if (!plantingOnEmptyFarmland && (isDirectCrop || isFarmland || isAboveCrop)) {
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
        if (FarmingManager.isPlantableCrop(heldItem)) {
            boolean handled = FarmingManager.handleMassPlanting(serverPlayer, hand, heldItem, clickedPos);
            if (handled) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
            return;
        }

        // 3. Bone Meal Interaction (AOE Fertilizing, with fallback to harvesting if all crops are mature)
        if (heldItem.is(Items.BONE_MEAL)) {
            boolean handled = FarmingManager.handleMassBoneMeal(serverPlayer, hand, heldItem, clickedPos);
            if (handled) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }

            // If bone meal could not fertilize anything (i.e. all crops in the area are already fully mature),
            // automatically harvest and replant them!
            boolean isDirectCrop = FarmingManager.isCrop(clickedState) || FarmingManager.isSugarCane(clickedState);
            boolean isFarmland = FarmingManager.isFarmland(clickedState);
            boolean isAboveCrop = FarmingManager.isCrop(level.getBlockState(clickedPos.above())) || FarmingManager.isSugarCane(level.getBlockState(clickedPos.above()));
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

    public static boolean isHoe(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.canPerformAction(ItemAbilities.HOE_TILL)
                || stack.getItem() instanceof HoeItem
                || stack.is(ItemTags.HOES);
    }
}
