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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class FarmingEventHandler {

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

        // Check if activation condition is met
        boolean active = false;
        if (LiteMinerCompat.isLiteMinerLoaded()) {
            active = LiteMinerCompat.isLiteMinerActive(serverPlayer);
        }

        if (!active && FarmingConfig.REQUIRE_SNEAK_FALLBACK.get()) {
            active = player.isShiftKeyDown();
        } else if (!active && !LiteMinerCompat.isLiteMinerLoaded() && !FarmingConfig.REQUIRE_SNEAK_FALLBACK.get()) {
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
        // Works with empty hand, hoe, or any held item when targeting a mature crop (or farmland with a mature crop)
        boolean isDirectCrop = FarmingManager.isMatureCrop(clickedState);
        boolean isFarmlandWithCrop = FarmingManager.isFarmland(clickedState) && FarmingManager.isMatureCrop(level.getBlockState(clickedPos.above()));

        if (isDirectCrop || isFarmlandWithCrop) {
            BlockPos targetCrop = isDirectCrop ? clickedPos : clickedPos.above();
            boolean handled = FarmingManager.handleMassHarvest(serverPlayer, hand, heldItem, targetCrop);
            if (handled) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }
        }

        // If hand is empty and harvest didn't trigger, nothing more to do
        if (heldItem.isEmpty()) {
            return;
        }

        // 1. Hoe Interaction (Mass Tilling)
        if (heldItem.canPerformAction(ItemAbilities.HOE_TILL)) {
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

        // 3. Bone Meal Interaction (AOE Fertilizing)
        if (heldItem.is(Items.BONE_MEAL)) {
            boolean handled = FarmingManager.handleMassBoneMeal(serverPlayer, hand, heldItem, clickedPos);
            if (handled) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
            return;
        }
    }
}
