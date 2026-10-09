package com.velorise.veinfarming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Main Facade for Vein Farming.
 * Coordinates modular domain managers:
 * - {@link PlantClassifier}: Plant, crop, seed, and soil classification
 * - {@link PlantingAlgorithms}: Spacing, grove pairing, MIS cactus, fruit layouts, and intercropping
 * - {@link PlantingManager}: Mass planting execution and BFS searches
 * - {@link HarvestManager}: Mass harvesting, replanting, mass destruction, and harvest searches
 * - {@link TillingAndFertilizingManager}: Mass hoeing, bone meal fertilizing, and composting
 *
 * Preserves 100% backward-compatible public static API for existing callers and external mods.
 */
public class FarmingManager {

    // ==========================================
    // Tags forwarded from PlantClassifier
    // ==========================================
    public static final TagKey<Item> C_SAPLINGS = PlantClassifier.C_SAPLINGS;
    public static final TagKey<Block> C_BLOCK_SAPLINGS = PlantClassifier.C_BLOCK_SAPLINGS;
    public static final TagKey<Item> FORGE_SAPLINGS = PlantClassifier.FORGE_SAPLINGS;
    public static final TagKey<Block> FORGE_BLOCK_SAPLINGS = PlantClassifier.FORGE_BLOCK_SAPLINGS;

    public static final TagKey<Item> C_MUSHROOMS = PlantClassifier.C_MUSHROOMS;
    public static final TagKey<Block> C_BLOCK_MUSHROOMS = PlantClassifier.C_BLOCK_MUSHROOMS;

    public static final TagKey<Item> C_KNIVES = PlantClassifier.C_KNIVES;
    public static final TagKey<Item> FD_KNIVES = PlantClassifier.FD_KNIVES;

    // ==========================================
    // Shared Utilities
    // ==========================================

    /**
     * Retrieves selected positions from LiteMiner or FTB Ultimine if active, or empty list.
     */
    public static Collection<BlockPos> getSelectedPositions(ServerPlayer player, BlockPos pos) {
        if (LiteMinerCompat.isLiteMinerLoaded() && LiteMinerCompat.isLiteMinerActive(player)) {
            Collection<BlockPos> lm = LiteMinerCompat.getSelectedBlocks(player, pos);
            if (lm != null && !lm.isEmpty()) {
                return lm;
            }
        }
        if (FTBUltimineCompat.isFTBUltimineLoaded() && FTBUltimineCompat.isUltimineActive(player)) {
            Collection<BlockPos> ftb = FTBUltimineCompat.getSelectedBlocks(player, pos);
            if (ftb != null && !ftb.isEmpty()) {
                return ftb;
            }
        }
        return Collections.emptyList();
    }

    public static int getEffectiveBlockLimit(ServerPlayer player) {
        if (LiteMinerCompat.isLiteMinerLoaded() && LiteMinerCompat.isLiteMinerActive(player)) {
            return LiteMinerCompat.getEffectiveBlockLimit();
        }
        if (FTBUltimineCompat.isFTBUltimineLoaded() && FTBUltimineCompat.isUltimineActive(player)) {
            return FTBUltimineCompat.getEffectiveBlockLimit(player);
        }
        return FarmingConfig.MAX_BLOCKS.get();
    }

    public static boolean shouldPreventToolBreaking(ServerPlayer player) {
        if (LiteMinerCompat.isLiteMinerLoaded() && LiteMinerCompat.isLiteMinerActive(player)) {
            return LiteMinerCompat.shouldPreventToolBreaking();
        }
        if (FTBUltimineCompat.isFTBUltimineLoaded() && FTBUltimineCompat.isUltimineActive(player)) {
            return FTBUltimineCompat.shouldPreventToolBreaking();
        }
        return FarmingConfig.PREVENT_TOOL_BREAKING.get();
    }

    public static float getFoodExhaustion(ServerPlayer player) {
        if (LiteMinerCompat.isLiteMinerLoaded() && LiteMinerCompat.isLiteMinerActive(player)) {
            return LiteMinerCompat.getFoodExhaustion();
        }
        if (FTBUltimineCompat.isFTBUltimineLoaded() && FTBUltimineCompat.isUltimineActive(player)) {
            return FTBUltimineCompat.getFoodExhaustion(player);
        }
        return FarmingConfig.EXHAUSTION_PER_BLOCK.get().floatValue();
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

    /**
     * Merges individual ItemStacks into compact stacks up to their max stack size.
     */
    public static List<ItemStack> mergeItemStacks(List<ItemStack> rawStacks) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack raw : rawStacks) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            ItemStack toAdd = raw.copy();
            for (ItemStack existing : merged) {
                if (ItemStack.isSameItemSameComponents(existing, toAdd)) {
                    int space = existing.getMaxStackSize() - existing.getCount();
                    if (space > 0) {
                        int addCount = Math.min(space, toAdd.getCount());
                        existing.grow(addCount);
                        toAdd.shrink(addCount);
                        if (toAdd.isEmpty()) {
                            break;
                        }
                    }
                }
            }
            if (!toAdd.isEmpty()) {
                merged.add(toAdd);
            }
        }
        return merged;
    }

    // ==========================================
    // PlantClassifier Delegates
    // ==========================================
    public static boolean isPlantableSeed(ItemStack stack) { return PlantClassifier.isPlantableSeed(stack); }
    public static boolean isPlantableCrop(ItemStack stack) { return PlantClassifier.isPlantableCrop(stack); }
    public static boolean isMushroom(ItemStack stack) { return PlantClassifier.isMushroom(stack); }
    public static boolean isMushroomBlock(BlockState state) { return PlantClassifier.isMushroomBlock(state); }
    public static boolean isFlower(ItemStack stack) { return PlantClassifier.isFlower(stack); }
    public static boolean isFlowerBlock(BlockState state) { return PlantClassifier.isFlowerBlock(state); }
    public static boolean isSmallFlower(BlockState state) { return PlantClassifier.isSmallFlower(state); }
    public static boolean isChorusFlower(ItemStack stack) { return PlantClassifier.isChorusFlower(stack); }
    public static boolean isChorus(BlockState state) { return PlantClassifier.isChorus(state); }
    public static boolean isCocoaBean(ItemStack stack) { return PlantClassifier.isCocoaBean(stack); }
    public static boolean isJungleLog(BlockState state) { return PlantClassifier.isJungleLog(state); }
    public static boolean isKnife(ItemStack stack) { return PlantClassifier.isKnife(stack); }
    public static boolean isCompostable(ItemStack stack) { return PlantClassifier.isCompostable(stack); }
    public static boolean isNearWater(LevelReader level, BlockPos pos) { return PlantClassifier.isNearWater(level, pos); }
    public static boolean isSapling(ItemStack stack) { return PlantClassifier.isSapling(stack); }
    public static boolean isSaplingBlock(Block block) { return PlantClassifier.isSaplingBlock(block); }
    public static boolean isStrictly2x2Sapling(Block block) { return PlantClassifier.isStrictly2x2Sapling(block); }
    public static boolean isSupported2x2Sapling(Block block) { return PlantClassifier.isSupported2x2Sapling(block); }
    public static boolean isFruitSeed(ItemStack stack) { return PlantClassifier.isFruitSeed(stack); }
    public static Block getFruitForSeed(ItemStack stack) { return PlantClassifier.getFruitForSeed(stack); }
    public static BlockPos findNearbyWaterBlock(Level level, BlockPos originPos, int radius) { return PlantClassifier.findNearbyWaterBlock(level, originPos, radius); }
    public static boolean isFarmland(BlockState state) { return PlantClassifier.isFarmland(state); }
    public static boolean isSoulSand(BlockState state) { return PlantClassifier.isSoulSand(state); }
    public static boolean isCactus(ItemStack stack) { return PlantClassifier.isCactus(stack); }
    public static boolean isValidSoilForSeed(ItemStack seedStack, BlockState soilState, Level level, BlockPos soilPos) {
        return PlantClassifier.isValidSoilForSeed(seedStack, soilState, level, soilPos);
    }
    public static boolean isBonemealCrop(BlockState state) { return PlantClassifier.isBonemealCrop(state); }
    public static boolean isMatureCrop(BlockState state) { return PlantClassifier.isMatureCrop(state); }
    public static boolean isCrop(BlockState state) { return PlantClassifier.isCrop(state); }
    public static boolean isRiceCrop(BlockState state) { return PlantClassifier.isRiceCrop(state); }
    public static boolean isDestructionTool(ItemStack stack) { return PlantClassifier.isDestructionTool(stack); }
    public static boolean isColumnCrop(ItemStack stack) { return PlantClassifier.isColumnCrop(stack); }
    public static boolean isIntercroppableCrop(ItemStack stack) { return PlantClassifier.isIntercroppableCrop(stack); }
    public static boolean isFarmlandCrop(BlockState state) { return PlantClassifier.isFarmlandCrop(state); }
    public static boolean isHarvestablePlant(BlockState state) { return PlantClassifier.isHarvestablePlant(state); }
    public static boolean isColumnCrop(BlockState state) { return PlantClassifier.isColumnCrop(state); }
    public static boolean isSugarCane(BlockState state) { return PlantClassifier.isSugarCane(state); }
    public static boolean isSameColumnType(BlockState a, BlockState b) { return PlantClassifier.isSameColumnType(a, b); }
    public static BlockPos getColumnCropRoot(Level level, BlockPos pos) { return PlantClassifier.getColumnCropRoot(level, pos); }
    public static BlockPos getSugarCaneRoot(Level level, BlockPos pos) { return PlantClassifier.getSugarCaneRoot(level, pos); }
    public static boolean isFruitCrop(BlockState state) { return PlantClassifier.isFruitCrop(state); }
    public static boolean isStem(BlockState state) { return PlantClassifier.isStem(state); }
    public static Block getCropBlock(ItemStack stack) { return PlantClassifier.getCropBlock(stack); }
    public static boolean isMatchingCrop(BlockState state, Block cropBlock) { return PlantClassifier.isMatchingCrop(state, cropBlock); }
    public static BlockState getResetCropState(BlockState state) { return PlantClassifier.getResetCropState(state); }

    // ==========================================
    // PlantingAlgorithms Delegates
    // ==========================================
    public static boolean isNearExistingTreeOrSapling(Level level, BlockPos plantPos, int minSpacing) {
        return PlantingAlgorithms.isNearExistingTreeOrSapling(level, plantPos, minSpacing);
    }
    public static int hashPos(int x, int z, long seed) {
        return PlantingAlgorithms.hashPos(x, z, seed);
    }
    public static List<BlockPos> filterSmartSaplingPositions(Level level, List<BlockPos> candidateSoilList, ItemStack seedStack, BlockPos originSoilPos) {
        return PlantingAlgorithms.filterSmartSaplingPositions(level, candidateSoilList, seedStack, originSoilPos);
    }
    public static List<BlockPos> filterAntiOvercrowdedFloraPositions(Level level, List<BlockPos> candidateSoils, BlockPos originSoilPos, ItemStack seedStack) {
        return PlantingAlgorithms.filterAntiOvercrowdedFloraPositions(level, candidateSoils, originSoilPos, seedStack);
    }
    public static List<BlockPos> filterOptimalCactusPositions(Level level, List<BlockPos> candidateSoils, BlockPos originSoilPos) {
        return PlantingAlgorithms.filterOptimalCactusPositions(level, candidateSoils, originSoilPos);
    }
    public static List<BlockPos> filterOptimalFruitStemPositions(Level level, List<BlockPos> candidateSoils, BlockPos originSoilPos) {
        return PlantingAlgorithms.filterOptimalFruitStemPositions(level, candidateSoils, originSoilPos);
    }
    public static boolean hasGrowthPenalty(Level level, BlockPos plantPos, Block candidateCrop, Map<BlockPos, Block> plannedCrops) {
        return PlantingAlgorithms.hasGrowthPenalty(level, plantPos, candidateCrop, plannedCrops);
    }
    public static boolean determineIntercropPhase(Level level, BlockPos originPos, Direction facing, Block mainCropBlock, Block offCropBlock) {
        return PlantingAlgorithms.determineIntercropPhase(level, originPos, facing, mainCropBlock, offCropBlock);
    }
    public static boolean isMainCropRow(Level level, BlockPos soilPos, BlockPos originPos, Direction facing, Block mainCropBlock, Block offCropBlock) {
        return PlantingAlgorithms.isMainCropRow(level, soilPos, originPos, facing, mainCropBlock, offCropBlock);
    }
    public static boolean isMainCropRow(BlockPos pos, BlockPos originPos, Direction facing) {
        return PlantingAlgorithms.isMainCropRow(pos, originPos, facing);
    }

    // ==========================================
    // PlantingManager Delegates
    // ==========================================
    public static boolean handleMassPlanting(ServerPlayer player, InteractionHand hand, ItemStack seedStack, BlockPos clickedPos) {
        return PlantingManager.handleMassPlanting(player, hand, seedStack, clickedPos);
    }
    public static boolean handleMassPlantingCocoa(ServerPlayer player, InteractionHand hand, ItemStack seedStack, BlockPos clickedPos) {
        return PlantingManager.handleMassPlantingCocoa(player, hand, seedStack, clickedPos);
    }
    public static Collection<BlockPos> fallbackCocoaPlantingSearch(Level level, BlockPos startPos) {
        return PlantingManager.fallbackCocoaPlantingSearch(level, startPos);
    }
    public static Collection<BlockPos> fallbackPlantingSearch(Level level, BlockPos startSoilPos, ItemStack seedStack, boolean applySmartPlant) {
        return PlantingManager.fallbackPlantingSearch(level, startSoilPos, seedStack, applySmartPlant);
    }
    public static Collection<BlockPos> fallbackPlantingSearch(Level level, BlockPos startSoilPos, ItemStack seedStack) {
        return PlantingManager.fallbackPlantingSearch(level, startSoilPos, seedStack);
    }
    public static Collection<BlockPos> fallbackPlantingSearch(Level level, BlockPos startFarmPos) {
        return PlantingManager.fallbackPlantingSearch(level, startFarmPos);
    }

    // ==========================================
    // HarvestManager Delegates
    // ==========================================
    public static boolean handleMassHarvest(ServerPlayer player, InteractionHand hand, ItemStack heldItem, BlockPos clickedCropPos) {
        return HarvestManager.handleMassHarvest(player, hand, heldItem, clickedCropPos);
    }
    public static boolean handleMassDestroy(ServerPlayer player, InteractionHand hand, ItemStack heldItem, BlockPos clickedPos, BlockState clickedState) {
        return HarvestManager.handleMassDestroy(player, hand, heldItem, clickedPos, clickedState);
    }
    public static Collection<BlockPos> findDestroyTargets(Level level, BlockPos clickedPos, BlockState clickedState) {
        return HarvestManager.findDestroyTargets(level, clickedPos, clickedState);
    }
    public static Collection<BlockPos> fallbackHarvestSearch(Level level, BlockPos startPos) {
        return HarvestManager.fallbackHarvestSearch(level, startPos);
    }

    // ==========================================
    // TillingAndFertilizingManager Delegates
    // ==========================================
    public static boolean handleMassHoe(ServerPlayer player, InteractionHand hand, ItemStack hoeStack, BlockPos clickedPos) {
        return TillingAndFertilizingManager.handleMassHoe(player, hand, hoeStack, clickedPos);
    }
    public static boolean handleMassBoneMeal(ServerPlayer player, InteractionHand hand, ItemStack boneMealStack, BlockPos clickedPos) {
        return TillingAndFertilizingManager.handleMassBoneMeal(player, hand, boneMealStack, clickedPos);
    }
    public static boolean handleBatchCompost(ServerPlayer player, InteractionHand hand, ItemStack heldItem, BlockPos composterPos) {
        return TillingAndFertilizingManager.handleBatchCompost(player, hand, heldItem, composterPos);
    }
    public static Collection<BlockPos> fallbackHoeSearch(Player player, InteractionHand hand, BlockPos clickedPos) {
        return TillingAndFertilizingManager.fallbackHoeSearch(player, hand, clickedPos);
    }
    public static Collection<BlockPos> fallbackCropSearch(Level level, BlockPos startPos) {
        return TillingAndFertilizingManager.fallbackCropSearch(level, startPos);
    }
    public static boolean isTillable(Level level, Player player, InteractionHand hand, BlockPos pos) {
        return TillingAndFertilizingManager.isTillable(level, player, hand, pos);
    }
    public static boolean applyFlowerBoneMeal(Level level, BlockPos originPos, BlockState originState, Player player) {
        return TillingAndFertilizingManager.applyFlowerBoneMeal(level, originPos, originState, player);
    }
}
