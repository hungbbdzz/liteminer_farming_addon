package com.velorise.veinfarming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!FarmingConfig.ENABLE_MASS_HARVEST.get()) {
            return;
        }

        if (IS_HANDLING_BREAK.get()) {
            return;
        }

        if (!(event.getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        BlockPos clickedPos = event.getPos();
        BlockState clickedState = event.getState();

        // Left-click mass destruction strictly ONLY targets harvestable agricultural crops/plants!
        // Regular blocks (Stone, Dirt, Wood, Ores, etc.) are strictly left to other vein miner mods or vanilla!
        if (!FarmingManager.isHarvestablePlant(clickedState)) {
            return;
        }

        if (!isVeinActive(serverPlayer)) {
            return;
        }

        try {
            IS_HANDLING_BREAK.set(true);
            InteractionHand hand = InteractionHand.MAIN_HAND;
            ItemStack heldItem = serverPlayer.getItemInHand(hand);
            boolean handled = FarmingManager.handleMassDestroy(serverPlayer, hand, heldItem, clickedPos, clickedState);
            if (handled) {
                event.setCanceled(true);
            }
        } finally {
            IS_HANDLING_BREAK.set(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        ItemStack heldItem = player.getItemInHand(hand);

        BlockPos clickedPos = event.getPos();
        Direction clickedFace = event.getFace();
        if (clickedFace == Direction.DOWN) {
            return;
        }

        BlockState clickedState = level.getBlockState(clickedPos);

        // Bedrock-style single-click flower bone meal (works even without vein key active)
        if (heldItem.is(Items.BONE_MEAL) && FarmingManager.isSmallFlower(clickedState) && !isVeinActive(player)) {
            if (level.isClientSide()) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                player.swing(hand);
                return;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                boolean sprouted = FarmingManager.applyFlowerBoneMeal(level, clickedPos, clickedState, serverPlayer);
                if (sprouted) {
                    if (!serverPlayer.isCreative()) {
                        heldItem.shrink(1);
                    }
                    level.playSound(null, clickedPos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                    serverPlayer.swing(hand, true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // Check if activation condition is met (LiteMiner, FTB Ultimine, or Sneak fallback)
        if (!isVeinActive(player)) {
            return;
        }

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

        // 0. Batch Composter (1-Click mass composting)
        if (clickedState.is(Blocks.COMPOSTER) && FarmingConfig.BATCH_COMPOSTER.get() && FarmingManager.isCompostable(heldItem)) {
            boolean handled = FarmingManager.handleBatchCompost(serverPlayer, hand, heldItem, clickedPos);
            if (handled) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }
        }

        // 1. Mass Harvesting (AOE Harvest & Replant)
        if (!heldItem.is(Items.BONE_MEAL)) {
            boolean isDirectCrop = FarmingManager.isCrop(clickedState) || FarmingManager.isColumnCrop(clickedState) || FarmingManager.isFruitCrop(clickedState) || FarmingManager.isChorus(clickedState);
            boolean isSoil = FarmingManager.isFarmland(clickedState) || FarmingManager.isSoulSand(clickedState) || clickedState.is(Blocks.END_STONE);
            boolean isAboveCrop = FarmingManager.isCrop(level.getBlockState(clickedPos.above()))
                    || FarmingManager.isColumnCrop(level.getBlockState(clickedPos.above()))
                    || FarmingManager.isFruitCrop(level.getBlockState(clickedPos.above()))
                    || FarmingManager.isChorus(level.getBlockState(clickedPos.above()));

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
            } else if (FarmingManager.isSapling(heldItem) && FarmingConfig.isSmartSaplingEnabled()) {
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
                    || FarmingManager.isBonemealCrop(level.getBlockState(clickedPos.above()))
                    || FarmingManager.isSmallFlower(clickedState);
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
            boolean isDirectCrop = FarmingManager.isCrop(clickedState) || FarmingManager.isColumnCrop(clickedState) || FarmingManager.isFruitCrop(clickedState);
            boolean isFarmland = FarmingManager.isFarmland(clickedState);
            boolean isAboveCrop = FarmingManager.isCrop(level.getBlockState(clickedPos.above()))
                    || FarmingManager.isColumnCrop(level.getBlockState(clickedPos.above()))
                    || FarmingManager.isFruitCrop(level.getBlockState(clickedPos.above()));
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
                    || FarmingManager.isBonemealCrop(level.getBlockState(clickedPos.above()))
                    || FarmingManager.isSmallFlower(clickedState);
        }
        if (FarmingManager.isPlantableSeed(heldItem)) {
            if (FarmingManager.isValidSoilForSeed(heldItem, clickedState, level, clickedPos)
                    || FarmingManager.isValidSoilForSeed(heldItem, level.getBlockState(clickedPos.below()), level, clickedPos.below())) {
                return true;
            }
        }
        if (clickedState.is(Blocks.COMPOSTER) && FarmingConfig.BATCH_COMPOSTER.get() && FarmingManager.isCompostable(heldItem)) {
            return true;
        }
        boolean isDirectCrop = FarmingManager.isCrop(clickedState) || FarmingManager.isColumnCrop(clickedState) || FarmingManager.isFruitCrop(clickedState) || FarmingManager.isChorus(clickedState);
        boolean isSoil = FarmingManager.isFarmland(clickedState) || FarmingManager.isSoulSand(clickedState) || clickedState.is(Blocks.END_STONE);
        boolean isAboveCrop = FarmingManager.isCrop(level.getBlockState(clickedPos.above()))
                || FarmingManager.isColumnCrop(level.getBlockState(clickedPos.above()))
                || FarmingManager.isFruitCrop(level.getBlockState(clickedPos.above()))
                || FarmingManager.isChorus(level.getBlockState(clickedPos.above()));
        return isDirectCrop || isSoil || isAboveCrop;
    }

    private static final java.util.Map<java.util.UUID, Boolean> ACTIVE_KEYS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Boolean> SMART_PLANT_ENABLED = new java.util.concurrent.ConcurrentHashMap<>();

    public static void setPlayerKeyActive(java.util.UUID uuid, boolean active) {
        if (active) {
            ACTIVE_KEYS.put(uuid, true);
        } else {
            ACTIVE_KEYS.remove(uuid);
        }
    }

    public static void setPlayerSmartPlantEnabled(java.util.UUID uuid, boolean enabled) {
        SMART_PLANT_ENABLED.put(uuid, enabled);
    }

    public static boolean isPlayerSmartPlantEnabled(java.util.UUID uuid) {
        return SMART_PLANT_ENABLED.getOrDefault(uuid, true);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE_KEYS.remove(event.getEntity().getUUID());
        SMART_PLANT_ENABLED.remove(event.getEntity().getUUID());
    }

    public static boolean isHoe(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.canPerformAction(ItemAbilities.HOE_TILL)
                || stack.getItem() instanceof HoeItem
                || stack.is(ItemTags.HOES);
    }

    public static boolean isVeinActive(Player player) {
        if (player == null) {
            return false;
        }
        if (LiteMinerCompat.isLiteMinerLoaded()) {
            if (player instanceof ServerPlayer sp) {
                if (LiteMinerCompat.isLiteMinerActive(sp)) {
                    return true;
                }
            }
        }
        if (FTBUltimineCompat.isFTBUltimineLoaded()) {
            if (player instanceof ServerPlayer sp) {
                if (FTBUltimineCompat.isUltimineActive(sp)) {
                    return true;
                }
            } else {
                if (FTBUltimineCompat.isUltimineClientActive()) {
                    return true;
                }
            }
        }
        if (!LiteMinerCompat.isLiteMinerLoaded() && !FTBUltimineCompat.isFTBUltimineLoaded()) {
            if (ACTIVE_KEYS.getOrDefault(player.getUUID(), false)) {
                return true;
            }
            return player.isShiftKeyDown();
        }
        return false;
    }
}
