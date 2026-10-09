package com.velorise.liteminerfarming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
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
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.KelpPlantBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FarmingManager {

    /**
     * Universal check if an item stack is a plantable crop/seed.
     */
    public static boolean isPlantableSeed(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.NETHER_WART) || stack.is(Items.BAMBOO) || stack.is(Items.SUGAR_CANE)
                || stack.is(Items.CACTUS) || stack.is(Items.KELP)) {
            return true;
        }
        Item item = stack.getItem();
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block instanceof BushBlock || block instanceof CropBlock
                    || block instanceof BambooSaplingBlock || block instanceof BambooStalkBlock
                    || block instanceof SugarCaneBlock || block instanceof CactusBlock
                    || block instanceof KelpBlock || block instanceof KelpPlantBlock) {
                return true;
            }
            String id = block.getDescriptionId().toLowerCase(Locale.ROOT);
            return id.contains("bamboo") || id.contains("sapling") || id.contains("crop") || id.contains("seed");
        }
        return false;
    }

    public static boolean isPlantableCrop(ItemStack stack) {
        return isPlantableSeed(stack);
    }

    public static final TagKey<Item> C_SAPLINGS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "saplings"));
    public static final TagKey<Block> C_BLOCK_SAPLINGS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "saplings"));
    public static final TagKey<Item> FORGE_SAPLINGS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", "saplings"));
    public static final TagKey<Block> FORGE_BLOCK_SAPLINGS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("forge", "saplings"));

    private static final Map<Block, Boolean> SUPPORTED_2X2_CACHE = new ConcurrentHashMap<>();
    private static final Map<Block, Boolean> STRICT_2X2_CACHE = new ConcurrentHashMap<>();

    /**
     * Checks if the given ItemStack represents a sapling (vanilla or modded).
     */
    public static boolean isSapling(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(ItemTags.SAPLINGS) || stack.is(C_SAPLINGS) || stack.is(FORGE_SAPLINGS)) {
            return true;
        }
        Item item = stack.getItem();
        if (item instanceof BlockItem blockItem) {
            return isSaplingBlock(blockItem.getBlock());
        }
        return false;
    }

    /**
     * Checks if a Block is a sapling or propagule (vanilla or modded).
     */
    public static boolean isSaplingBlock(Block block) {
        if (block == null) {
            return false;
        }
        BlockState state = block.defaultBlockState();
        if (state.is(BlockTags.SAPLINGS) || state.is(C_BLOCK_SAPLINGS) || state.is(FORGE_BLOCK_SAPLINGS)) {
            return true;
        }
        if (block instanceof SaplingBlock) {
            return true;
        }
        String id = block.getDescriptionId().toLowerCase(Locale.ROOT);
        return id.contains("sapling") || id.contains("propagule");
    }

    private static void inspectTreeGrower(Block block) {
        if (block == null) {
            return;
        }

        // Vanilla hardcoded knowns
        if (block == Blocks.DARK_OAK_SAPLING) {
            SUPPORTED_2X2_CACHE.put(block, true);
            STRICT_2X2_CACHE.put(block, true);
            return;
        }
        if (block == Blocks.SPRUCE_SAPLING || block == Blocks.JUNGLE_SAPLING) {
            SUPPORTED_2X2_CACHE.put(block, true);
            STRICT_2X2_CACHE.put(block, false);
            return;
        }

        if (block instanceof SaplingBlock) {
            try {
                // Find TreeGrower field in SaplingBlock or its subclasses
                Object grower = null;
                Class<?> clazz = block.getClass();
                while (clazz != null && clazz != Object.class) {
                    for (Field f : clazz.getDeclaredFields()) {
                        if (TreeGrower.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            grower = f.get(block);
                            break;
                        }
                    }
                    if (grower != null) {
                        break;
                    }
                    clazz = clazz.getSuperclass();
                }

                if (grower instanceof TreeGrower tg) {
                    boolean hasMega = false;
                    boolean hasRegular = false;

                    for (Field gf : TreeGrower.class.getDeclaredFields()) {
                        gf.setAccessible(true);
                        Object val = gf.get(tg);
                        if (val instanceof Optional<?> opt) {
                            String fname = gf.getName().toLowerCase(Locale.ROOT);
                            if (fname.contains("mega")) {
                                if (opt.isPresent()) {
                                    hasMega = true;
                                }
                            } else if (fname.contains("tree") && !fname.contains("grower")) {
                                if (opt.isPresent()) {
                                    hasRegular = true;
                                }
                            }
                        }
                    }

                    if (hasMega) {
                        SUPPORTED_2X2_CACHE.put(block, true);
                        STRICT_2X2_CACHE.put(block, !hasRegular);
                        return;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // Name-based fallback for modded trees (Regions Unexplored, Biomes O' Plenty, Twilight Forest, etc.)
        String id = block.getDescriptionId().toLowerCase(Locale.ROOT);
        boolean isStrict = id.contains("dark_oak") || id.contains("pale_oak");
        boolean isSupported = isStrict || id.contains("spruce") || id.contains("jungle")
                || id.contains("redwood") || id.contains("sequoia") || id.contains("baobab")
                || id.contains("cypress") || id.contains("fir") || id.contains("giant_");

        SUPPORTED_2X2_CACHE.put(block, isSupported);
        STRICT_2X2_CACHE.put(block, isStrict);
    }

    /**
     * Trees that STRICTLY require a 2x2 grid to grow (e.g. Dark Oak, or modded trees without 1x1 feature).
     * Single 1x1 saplings will never grow in vanilla Minecraft.
     */
    public static boolean isStrictly2x2Sapling(Block block) {
        if (block == null) {
            return false;
        }
        Boolean cached = STRICT_2X2_CACHE.get(block);
        if (cached == null) {
            inspectTreeGrower(block);
            cached = STRICT_2X2_CACHE.getOrDefault(block, false);
        }
        return cached;
    }

    /**
     * Trees that support 2x2 mega structures (Dark Oak, Spruce, Jungle, Redwood, Sequoia, Baobab, etc.).
     */
    public static boolean isSupported2x2Sapling(Block block) {
        if (block == null) {
            return false;
        }
        Boolean cached = SUPPORTED_2X2_CACHE.get(block);
        if (cached == null) {
            inspectTreeGrower(block);
            cached = SUPPORTED_2X2_CACHE.getOrDefault(block, false);
        }
        return cached;
    }

    private static final int[] DY_ORDER = {0, 1, -1, 2, 3, 4, 5};

    /**
     * Checks if plantPos is within minSpacing of any already existing sapling, tree trunk (logs),
     * or foliage (leaves) in the world. Prevents overcrowding and repeat-planting saturation.
     */
    public static boolean isNearExistingTreeOrSapling(Level level, BlockPos plantPos, int minSpacing) {
        int checkRadius = Math.max(1, minSpacing - 1);
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        int px = plantPos.getX();
        int py = plantPos.getY();
        int pz = plantPos.getZ();

        for (int dy : DY_ORDER) {
            for (int dx = -checkRadius; dx <= checkRadius; dx++) {
                for (int dz = -checkRadius; dz <= checkRadius; dz++) {
                    if (dx == 0 && dz == 0 && dy == 0) {
                        continue;
                    }
                    mPos.set(px + dx, py + dy, pz + dz);
                    BlockState state = level.getBlockState(mPos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.is(BlockTags.SAPLINGS) || state.is(C_BLOCK_SAPLINGS) || state.is(FORGE_BLOCK_SAPLINGS)
                            || state.getBlock() instanceof SaplingBlock) {
                        return true;
                    }
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Deterministic, uniform spatial 32-bit hash based on SplitMix64.
     */
    public static int hashPos(int x, int z, long seed) {
        long h = (long) x * 3129871L ^ (long) z * 116129781L ^ seed;
        h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
        h = (h ^ (h >>> 27)) * 0x94d049bb133111ebL;
        h = h ^ (h >>> 31);
        return (int) (h ^ (h >>> 32));
    }

    private static boolean containsOrigin(BlockPos clusterOrigin, BlockPos originSoilPos) {
        if (originSoilPos == null) {
            return false;
        }
        int dx = originSoilPos.getX() - clusterOrigin.getX();
        int dy = originSoilPos.getY() - clusterOrigin.getY();
        int dz = originSoilPos.getZ() - clusterOrigin.getZ();
        return dy == 0 && dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1;
    }

    private static void add1x1SaplingsWithSpacing(Level level, List<BlockPos> soils, BlockPos originSoilPos,
                                                  int minSpacing, long seed, Set<BlockPos> chosenSaplingPositions,
                                                  List<BlockPos> result, Map<BlockPos, Boolean> nearCache) {
        List<BlockPos> candidates = new ArrayList<>(soils);
        candidates.sort(Comparator.comparingInt(p -> hashPos(p.getX(), p.getZ(), seed)));

        for (BlockPos soil : candidates) {
            BlockPos above = soil.above();
            BlockState aboveState = level.getBlockState(above);
            if (!aboveState.isAir() && !aboveState.canBeReplaced()) {
                continue;
            }
            boolean near = nearCache.computeIfAbsent(above, p -> isNearExistingTreeOrSapling(level, p, minSpacing));
            if (near) {
                continue;
            }

            boolean spacingOk = true;
            for (BlockPos chosen : chosenSaplingPositions) {
                if (Math.max(Math.abs(above.getX() - chosen.getX()), Math.abs(above.getZ() - chosen.getZ())) < minSpacing) {
                    spacingOk = false;
                    break;
                }
            }

            if (spacingOk) {
                result.add(soil);
                chosenSaplingPositions.add(above);
            }
        }
    }

    /**
     * Filters candidate soil positions for smart sapling planting.
     * Enforces minSpacing between trees, anti-overcrowding against existing trees/saplings,
     * and 2x2 cluster alignment for Dark Oak, Spruce, and Jungle.
     */
    public static List<BlockPos> filterSmartSaplingPositions(Level level, List<BlockPos> candidateSoilList,
                                                             ItemStack seedStack, BlockPos originSoilPos) {
        if (!FarmingConfig.SMART_SAPLING_PLANTING.get() || !isSapling(seedStack) || candidateSoilList.isEmpty()) {
            return candidateSoilList;
        }

        Item seedItem = seedStack.getItem();
        if (!(seedItem instanceof BlockItem blockItem)) {
            return candidateSoilList;
        }
        Block saplingBlock = blockItem.getBlock();

        int minSpacing = FarmingConfig.SAPLING_MIN_SPACING.get();
        boolean enable2x2 = FarmingConfig.SMART_SAPLING_2X2.get();
        boolean isStrict2x2 = isStrictly2x2Sapling(saplingBlock);
        boolean isSupported2x2 = isSupported2x2Sapling(saplingBlock);

        long seed = (long) level.dimension().location().hashCode();

        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> chosenSaplingPositions = new HashSet<>();
        Set<BlockPos> candidateSoilSet = new HashSet<>(candidateSoilList);
        Map<BlockPos, Boolean> nearCache = new HashMap<>();

        if ((isStrict2x2 || isSupported2x2) && enable2x2) {
            // Find 2x2 clusters at same Y level
            List<BlockPos> potentialClusterOrigins = new ArrayList<>();
            for (BlockPos soil : candidateSoilList) {
                BlockPos p0 = soil;
                BlockPos p1 = soil.offset(1, 0, 0);
                BlockPos p2 = soil.offset(0, 0, 1);
                BlockPos p3 = soil.offset(1, 0, 1);

                if (candidateSoilSet.contains(p1) && candidateSoilSet.contains(p2) && candidateSoilSet.contains(p3)) {
                    boolean valid = true;
                    for (BlockPos p : List.of(p0, p1, p2, p3)) {
                        BlockPos above = p.above();
                        BlockState aboveState = level.getBlockState(above);
                        if (!aboveState.isAir() && !aboveState.canBeReplaced()) {
                            valid = false;
                            break;
                        }
                        boolean near = nearCache.computeIfAbsent(above, pos -> isNearExistingTreeOrSapling(level, pos, minSpacing));
                        if (near) {
                            valid = false;
                            break;
                        }
                    }
                    if (valid) {
                        potentialClusterOrigins.add(soil);
                    }
                }
            }

            final long finalSeed = seed;
            potentialClusterOrigins.sort((a, b) -> {
                boolean aContainsOrigin = containsOrigin(a, originSoilPos);
                boolean bContainsOrigin = containsOrigin(b, originSoilPos);
                if (aContainsOrigin && !bContainsOrigin) return -1;
                if (!aContainsOrigin && bContainsOrigin) return 1;
                return Integer.compare(hashPos(a.getX(), a.getZ(), finalSeed), hashPos(b.getX(), b.getZ(), finalSeed));
            });

            Set<BlockPos> usedSoilPositions = new HashSet<>();
            for (BlockPos origin : potentialClusterOrigins) {
                BlockPos p0 = origin;
                BlockPos p1 = origin.offset(1, 0, 0);
                BlockPos p2 = origin.offset(0, 0, 1);
                BlockPos p3 = origin.offset(1, 0, 1);
                List<BlockPos> clusterSoils = List.of(p0, p1, p2, p3);

                if (clusterSoils.stream().anyMatch(usedSoilPositions::contains)) {
                    continue;
                }

                boolean spacingOk = true;
                for (BlockPos p : clusterSoils) {
                    BlockPos above = p.above();
                    for (BlockPos chosen : chosenSaplingPositions) {
                        if (Math.max(Math.abs(above.getX() - chosen.getX()), Math.abs(above.getZ() - chosen.getZ())) < minSpacing) {
                            spacingOk = false;
                            break;
                        }
                    }
                    if (!spacingOk) break;
                }

                if (spacingOk) {
                    usedSoilPositions.addAll(clusterSoils);
                    result.addAll(clusterSoils);
                    for (BlockPos p : clusterSoils) {
                        chosenSaplingPositions.add(p.above());
                    }
                }
            }

            // Strictly 2x2 saplings (Dark Oak) cannot grow on 1x1, so only 2x2 clusters are planted
            if (isStrict2x2) {
                if (result.size() < 4) {
                    return Collections.emptyList();
                }
                return result;
            }

            // Supported 2x2 (Spruce, Jungle): leftover candidate soils can be planted as spaced 1x1 saplings
            List<BlockPos> remainingSoils = new ArrayList<>();
            for (BlockPos soil : candidateSoilList) {
                if (!usedSoilPositions.contains(soil)) {
                    remainingSoils.add(soil);
                }
            }
            add1x1SaplingsWithSpacing(level, remainingSoils, originSoilPos, minSpacing, finalSeed, chosenSaplingPositions, result, nearCache);
            if (result.size() < 2) {
                return Collections.emptyList();
            }
            return result;
        }

        // Standard 1x1 saplings (Oak, Birch, Acacia, Cherry, Mangrove, etc.)
        add1x1SaplingsWithSpacing(level, candidateSoilList, originSoilPos, minSpacing, seed, chosenSaplingPositions, result, nearCache);
        if (result.size() < 2) {
            return Collections.emptyList();
        }
        return result;
    }

    /**
     * Universal farmland check supporting vanilla and modded farmlands.
     */
    public static boolean isFarmland(BlockState state) {
        if (state == null) {
            return false;
        }
        if (state.is(Blocks.FARMLAND) || state.getBlock() instanceof FarmBlock) {
            return true;
        }
        String descriptionId = state.getBlock().getDescriptionId().toLowerCase(Locale.ROOT);
        return descriptionId.contains("farmland");
    }

    /**
     * Universal Soul Sand check for Nether Wart planting.
     */
    public static boolean isSoulSand(BlockState state) {
        if (state == null) {
            return false;
        }
        return state.is(Blocks.SOUL_SAND) || state.is(BlockTags.SOUL_SPEED_BLOCKS);
    }

    /**
     * Universal soil check for any seed.
     */
    public static boolean isValidSoilForSeed(ItemStack seedStack, BlockState soilState, Level level, BlockPos soilPos) {
        if (seedStack.isEmpty() || soilState == null) {
            return false;
        }
        if (seedStack.is(Items.NETHER_WART)) {
            return isSoulSand(soilState);
        }
        if (seedStack.is(Items.BAMBOO)) {
            return soilState.is(BlockTags.BAMBOO_PLANTABLE_ON) || soilState.is(BlockTags.DIRT) || soilState.is(BlockTags.SAND);
        }
        if (isFarmland(soilState)) {
            return true;
        }
        Item item = seedStack.getItem();
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            BlockPos plantPos = soilPos.above();
            return block.defaultBlockState().canSurvive(level, plantPos);
        }
        return false;
    }

    /**
     * Filters Bonemealable targets to actual crops/plants (strictly EXCLUDES wild grass blocks).
     */
    public static boolean isBonemealCrop(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN) || state.is(Blocks.SHORT_GRASS)) {
            return false;
        }
        return state.getBlock() instanceof BonemealableBlock;
    }

    /**
     * Universal check for mature crops supporting vanilla and modded crops.
     * Stems (melon/pumpkin stems) are NEVER considered mature crops to harvest!
     */
    public static boolean isMatureCrop(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }

        // Fruit stems are NEVER harvested!
        if (isStem(state)) {
            return false;
        }

        Block block = state.getBlock();

        // 1. Standard CropBlock (Wheat, Carrots, Potatoes, Beetroots, Torchflower, modded CropBlocks)
        if (block instanceof CropBlock cropBlock) {
            return cropBlock.isMaxAge(state);
        }

        // 2. Pitcher Crop (2 blocks tall, max age 4)
        if (state.is(Blocks.PITCHER_CROP)) {
            for (Property<?> prop : state.getProperties()) {
                if (prop instanceof IntegerProperty intProp && intProp.getName().equalsIgnoreCase("age")) {
                    return state.getValue(intProp) >= 4;
                }
            }
        }

        // 3. Nether Wart
        if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= 3;
        }

        // 4. Cocoa
        if (block instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= 2;
        }

        // 5. Sweet Berry Bush
        if (block instanceof SweetBerryBushBlock) {
            return state.getValue(SweetBerryBushBlock.AGE) >= 2;
        }

        // 6. Cave Vines (Glow Berries)
        if (block instanceof CaveVines || state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT)) {
            return CaveVines.hasGlowBerries(state);
        }

        // 7. Generic check for any BushBlock with an "age" integer property (Farmer's Delight tomato/rice, etc.)
        // But NOT StemBlock or AttachedStemBlock!
        if (block instanceof BushBlock && !(block instanceof StemBlock) && !(block instanceof AttachedStemBlock)) {
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
     * Universal check for any crop (mature or immature).
     * Excludes fruit stems so stems are never treated as harvest targets.
     */
    public static boolean isCrop(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (isMatureCrop(state) || isColumnCrop(state) || isFruitCrop(state)) {
            return true;
        }
        if (isStem(state)) {
            return false;
        }
        Block block = state.getBlock();
        if (block instanceof CropBlock
                || state.is(Blocks.PITCHER_CROP)
                || block instanceof NetherWartBlock
                || block instanceof CocoaBlock
                || block instanceof SweetBerryBushBlock
                || block instanceof CaveVines
                || state.is(Blocks.CAVE_VINES)
                || state.is(Blocks.CAVE_VINES_PLANT)) {
            return true;
        }
        if (state.is(BlockTags.CROPS)) {
            return true;
        }
        if (block instanceof BushBlock && !(block instanceof StemBlock) && !(block instanceof AttachedStemBlock)) {
            for (Property<?> prop : state.getProperties()) {
                if (prop instanceof IntegerProperty intProp && intProp.getName().equalsIgnoreCase("age")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Universal check for vertical column crops (Sugar Cane, Cactus, Bamboo, Kelp).
     */
    public static boolean isColumnCrop(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        Block block = state.getBlock();
        if (block instanceof SugarCaneBlock || state.is(Blocks.SUGAR_CANE)
                || block instanceof CactusBlock || state.is(Blocks.CACTUS)
                || block instanceof BambooStalkBlock || state.is(Blocks.BAMBOO)
                || block instanceof KelpBlock || block instanceof KelpPlantBlock
                || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)) {
            return true;
        }
        String id = block.getDescriptionId().toLowerCase(Locale.ROOT);
        return id.contains("sugar_cane") || id.contains("sugarcane")
                || id.contains("cactus") || id.contains("bamboo") || id.contains("kelp");
    }

    public static boolean isSugarCane(BlockState state) {
        return isColumnCrop(state);
    }

    public static boolean isSameColumnType(BlockState a, BlockState b) {
        if (a == null || b == null || a.isAir() || b.isAir()) {
            return false;
        }
        if (a.is(b.getBlock())) {
            return true;
        }
        boolean aIsKelp = a.getBlock() instanceof KelpBlock || a.getBlock() instanceof KelpPlantBlock;
        boolean bIsKelp = b.getBlock() instanceof KelpBlock || b.getBlock() instanceof KelpPlantBlock;
        return aIsKelp && bIsKelp;
    }

    /**
     * Finds the bottom-most anchor block (root) of a vertical column crop.
     */
    public static BlockPos getColumnCropRoot(Level level, BlockPos pos) {
        BlockPos curr = pos;
        BlockState currState = level.getBlockState(curr);
        int safety = 0;
        while (safety < 64 && isSameColumnType(currState, level.getBlockState(curr.below()))) {
            curr = curr.below();
            currState = level.getBlockState(curr);
            safety++;
        }
        return curr;
    }

    public static BlockPos getSugarCaneRoot(Level level, BlockPos pos) {
        return getColumnCropRoot(level, pos);
    }

    /**
     * Universal check for fruit crops with stems (Melon, Pumpkin).
     */
    public static boolean isFruitCrop(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN) || state.is(Blocks.CARVED_PUMPKIN);
    }

    /**
     * Universal check for fruit stems that must be protected.
     */
    public static boolean isStem(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof StemBlock || block instanceof AttachedStemBlock;
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

    /**
     * Handles mass tilling using positions defined by LiteMiner, FTB Ultimine, or standalone BFS.
     */
    public static boolean handleMassHoe(ServerPlayer player, InteractionHand hand, ItemStack hoeStack, BlockPos clickedPos) {
        Level level = player.level();

        Collection<BlockPos> selected = getSelectedPositions(player, clickedPos);
        if (selected == null || selected.isEmpty()) {
            selected = fallbackHoeSearch(player, hand, clickedPos);
        }

        if (selected == null || selected.isEmpty()) {
            return false;
        }

        List<BlockPos> sorted = selected.stream()
                .sorted(Comparator.comparingInt(p -> p.distManhattan(clickedPos)))
                .toList();

        int maxLimit = getEffectiveBlockLimit(player);
        boolean preventBreaking = shouldPreventToolBreaking(player);
        boolean clearFoliage = FarmingConfig.CLEAR_FOLIAGE.get();
        float exhaustion = getFoodExhaustion(player);

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
                    if (tilledCount % 4 == 0) {
                        level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
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
     * Handles mass planting using positions strictly defined by LiteMiner's Walker.
     */
    public static boolean handleMassPlanting(ServerPlayer player, InteractionHand hand, ItemStack seedStack, BlockPos clickedPos) {
        Level level = player.level();
        Item seedItem = seedStack.getItem();
        if (!(seedItem instanceof BlockItem blockItem)) {
            return false;
        }

        Block cropBlock = blockItem.getBlock();

        // Determine starting soil pos
        BlockPos startSoilPos = clickedPos;
        BlockState clickedState = level.getBlockState(clickedPos);
        if (!isValidSoilForSeed(seedStack, clickedState, level, clickedPos)) {
            if (isValidSoilForSeed(seedStack, level.getBlockState(clickedPos.below()), level, clickedPos.below())) {
                startSoilPos = clickedPos.below();
                clickedState = level.getBlockState(startSoilPos);
            } else {
                return false;
            }
        }

        Collection<BlockPos> selected = getSelectedPositions(player, startSoilPos);
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

        if (isSapling(seedStack) && FarmingConfig.SMART_SAPLING_PLANTING.get()) {
            sorted = filterSmartSaplingPositions(level, sorted, seedStack, originSoilPos);
            if (sorted == null || sorted.isEmpty()) {
                return false;
            }
        }

        int maxLimit = getEffectiveBlockLimit(player);
        float exhaustion = getFoodExhaustion(player);

        int plantedCount = 0;
        SoundType cropSound = null;

        for (BlockPos soilPos : sorted) {
            if (plantedCount >= maxLimit) {
                break;
            }

            BlockState soilState = level.getBlockState(soilPos);
            if (!isValidSoilForSeed(seedStack, soilState, level, soilPos)) {
                continue;
            }

            BlockPos above = soilPos.above();
            BlockState aboveState = level.getBlockState(above);

            if (aboveState.isAir() || aboveState.canBeReplaced()) {
                BlockPlaceContext placeContext = new BlockPlaceContext(new UseOnContext(
                        player, hand, new BlockHitResult(Vec3.atCenterOf(above), Direction.UP, soilPos, false)
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
                        if (!player.isCreative() && exhaustion > 0) {
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

        Collection<BlockPos> selected = getSelectedPositions(player, targetCropPos);
        if (selected == null || selected.isEmpty()) {
            selected = getSelectedPositions(player, targetCropPos.below());
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

        int maxLimit = getEffectiveBlockLimit(player);
        float exhaustion = getFoodExhaustion(player);
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
                if (!isBonemealCrop(state)) {
                    if (isBonemealCrop(level.getBlockState(pos.above()))) {
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
                        replenishHand(player, hand, Items.BONE_MEAL);
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

        Collection<BlockPos> selected = getSelectedPositions(player, clickedCropPos);
        if (selected == null || selected.isEmpty()) {
            selected = getSelectedPositions(player, clickedCropPos.below());
        }
        if (selected == null || selected.isEmpty()) {
            selected = getSelectedPositions(player, clickedCropPos.above());
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

        int maxLimit = getEffectiveBlockLimit(player);
        boolean preventBreaking = shouldPreventToolBreaking(player);
        boolean replant = FarmingConfig.REPLANT_CROPS.get();
        boolean damageHoe = FarmingConfig.DAMAGE_HOE_ON_HARVEST.get();
        boolean collectAtTarget = FarmingConfig.COLLECT_DROPS_AT_TARGET.get();
        boolean allowColumnCrops = FarmingConfig.HARVEST_SUGAR_CANE.get();
        float exhaustion = getFoodExhaustion(player);

        int harvestedCount = 0;
        SoundType lastSoundType = null;
        BlockState originState = serverLevel.getBlockState(clickedCropPos);

        List<ItemStack> allDrops = new ArrayList<>();
        Set<BlockPos> processedColumnRoots = new HashSet<>();

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

            // 1. Column Crops handling (Sugar Cane, Cactus, Bamboo, Kelp - preserves bottom root)
            if (allowColumnCrops) {
                BlockPos colPos = null;
                if (isColumnCrop(serverLevel.getBlockState(pos))) {
                    colPos = pos;
                } else if (isColumnCrop(serverLevel.getBlockState(pos.above()))) {
                    colPos = pos.above();
                }

                if (colPos != null) {
                    BlockPos rootPos = getColumnCropRoot(serverLevel, colPos);
                    if (!processedColumnRoots.contains(rootPos)) {
                        processedColumnRoots.add(rootPos);

                        // Harvest stalks strictly ABOVE rootPos
                        BlockPos stalkPos = rootPos.above();
                        BlockState rootState = serverLevel.getBlockState(rootPos);
                        while (isSameColumnType(rootState, serverLevel.getBlockState(stalkPos))) {
                            if (harvestedCount >= maxLimit) {
                                break;
                            }
                            if (!player.isCreative() && !heldItem.isEmpty() && heldItem.isDamageableItem()) {
                                if (preventBreaking && heldItem.getDamageValue() >= heldItem.getMaxDamage() - 1) {
                                    break;
                                }
                            }

                            BlockState stalkState = serverLevel.getBlockState(stalkPos);
                            lastSoundType = stalkState.getSoundType(serverLevel, stalkPos, player);

                            List<ItemStack> stalkDrops = new ArrayList<>(Block.getDrops(stalkState, serverLevel, stalkPos, null, player, heldItem));
                            if (stalkDrops.isEmpty()) {
                                stalkDrops.add(new ItemStack(stalkState.getBlock().asItem()));
                            }

                            if (collectAtTarget) {
                                allDrops.addAll(stalkDrops);
                            } else {
                                for (ItemStack drop : stalkDrops) {
                                    if (!drop.isEmpty()) {
                                        Block.popResource(serverLevel, stalkPos, drop);
                                    }
                                }
                            }

                            serverLevel.destroyBlock(stalkPos, false, player);
                            harvestedCount++;
                            applyHarvestCosts(player, hand, heldItem, damageHoe, exhaustion);

                            stalkPos = stalkPos.above();
                        }
                    }
                    continue;
                }
            }

            // 2. Fruit Crops & Stems handling (Melon, Pumpkin - strictly protects stems!)
            BlockState curState = serverLevel.getBlockState(pos);
            if (isFruitCrop(curState)) {
                lastSoundType = curState.getSoundType(serverLevel, pos, player);
                List<ItemStack> fruitDrops = new ArrayList<>(Block.getDrops(curState, serverLevel, pos, null, player, heldItem));
                if (collectAtTarget) {
                    allDrops.addAll(fruitDrops);
                } else {
                    for (ItemStack drop : fruitDrops) {
                        if (!drop.isEmpty()) {
                            Block.popResource(serverLevel, pos, drop);
                        }
                    }
                }
                serverLevel.destroyBlock(pos, false, player);
                harvestedCount++;
                applyHarvestCosts(player, hand, heldItem, damageHoe, exhaustion);
                continue;
            } else if (isStem(curState)) {
                // 100% Protect stem: never harvest, never break, never reset!
                continue;
            }

            // 3. Pitcher Crop (2 blocks tall)
            if (curState.is(Blocks.PITCHER_CROP) || serverLevel.getBlockState(pos.above()).is(Blocks.PITCHER_CROP)) {
                BlockPos pPos = curState.is(Blocks.PITCHER_CROP) ? pos : pos.above();
                BlockState pState = serverLevel.getBlockState(pPos);
                if (isMatureCrop(pState)) {
                    BlockPos lowerPos = pPos;
                    for (Property<?> prop : pState.getProperties()) {
                        if (prop.getName().equalsIgnoreCase("half") && pState.getValue(prop).toString().equalsIgnoreCase("upper")) {
                            lowerPos = pPos.below();
                            break;
                        }
                    }
                    BlockPos upperPos = lowerPos.above();
                    BlockState lowerState = serverLevel.getBlockState(lowerPos);
                    List<ItemStack> pitcherDrops = new ArrayList<>(Block.getDrops(lowerState, serverLevel, lowerPos, null, player, heldItem));

                    boolean canReplant = player.isCreative();
                    Item seedItem = Items.PITCHER_POD;
                    if (!canReplant && replant) {
                        for (Iterator<ItemStack> it = pitcherDrops.iterator(); it.hasNext(); ) {
                            ItemStack drop = it.next();
                            if (!drop.isEmpty() && drop.is(seedItem)) {
                                drop.shrink(1);
                                if (drop.isEmpty()) it.remove();
                                canReplant = true;
                                break;
                            }
                        }
                        if (!canReplant && FarmingConfig.PULL_FROM_INVENTORY.get()) {
                            canReplant = consumeSeed(player, hand, seedItem);
                        }
                    }

                    if (canReplant && replant) {
                        if (serverLevel.getBlockState(upperPos).is(Blocks.PITCHER_CROP)) {
                            serverLevel.setBlock(upperPos, Blocks.AIR.defaultBlockState(), 3);
                        }
                        serverLevel.setBlock(lowerPos, Blocks.PITCHER_CROP.defaultBlockState(), 3);
                        serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, lowerPos);
                    } else {
                        serverLevel.destroyBlock(lowerPos, false, player);
                        if (serverLevel.getBlockState(upperPos).is(Blocks.PITCHER_CROP)) {
                            serverLevel.destroyBlock(upperPos, false, player);
                        }
                    }

                    if (collectAtTarget) {
                        allDrops.addAll(pitcherDrops);
                    } else {
                        for (ItemStack drop : pitcherDrops) {
                            if (!drop.isEmpty()) {
                                Block.popResource(serverLevel, lowerPos, drop);
                            }
                        }
                    }
                    serverLevel.playSound(null, lowerPos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
                    harvestedCount++;
                    applyHarvestCosts(player, hand, heldItem, damageHoe, exhaustion);
                    continue;
                }
            }

            // 4. Standard crop / Sweet Berry Bush / Cave Vines handling
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

            // Cave Vines (Glow Berries)
            if (cropBlock instanceof CaveVines || cropState.is(Blocks.CAVE_VINES) || cropState.is(Blocks.CAVE_VINES_PLANT)) {
                if (CaveVines.hasGlowBerries(cropState)) {
                    BlockState resetVines = cropState.setValue(CaveVines.BERRIES, false);
                    serverLevel.setBlock(cropPos, resetVines, 2);
                    serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, cropPos);
                    ItemStack berryDrop = new ItemStack(Items.GLOW_BERRIES, 1);
                    if (collectAtTarget) {
                        allDrops.add(berryDrop);
                    } else {
                        Block.popResource(serverLevel, cropPos, berryDrop);
                    }
                    serverLevel.playSound(null, cropPos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 1.0F);
                    harvestedCount++;
                    applyHarvestCosts(player, hand, heldItem, damageHoe, exhaustion);
                }
                continue;
            }

            // Sweet Berry Bush: harvest berries without breaking or consuming seeds
            if (cropBlock instanceof SweetBerryBushBlock) {
                int age = cropState.getValue(SweetBerryBushBlock.AGE);
                int berryCount = 1 + serverLevel.random.nextInt(2) + (age >= 3 ? 1 : 0);
                BlockState resetBush = cropState.setValue(SweetBerryBushBlock.AGE, 1);
                serverLevel.setBlock(cropPos, resetBush, 2);
                serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, cropPos);

                ItemStack berryDrop = new ItemStack(Items.SWEET_BERRIES, berryCount);
                if (collectAtTarget) {
                    allDrops.add(berryDrop);
                } else {
                    Block.popResource(serverLevel, cropPos, berryDrop);
                }
                serverLevel.playSound(null, cropPos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 1.0F);

                harvestedCount++;
                applyHarvestCosts(player, hand, heldItem, damageHoe, exhaustion);
                continue;
            }

            // Standard crops: calculate drops
            List<ItemStack> drops = new ArrayList<>(Block.getDrops(cropState, serverLevel, cropPos, null, player, heldItem));

            if (replant) {
                Item seedItem = cropBlock.asItem();
                if (seedItem == Items.AIR) {
                    ItemStack pickStack = cropBlock.getCloneItemStack(serverLevel, cropPos, cropState);
                    if (!pickStack.isEmpty()) {
                        seedItem = pickStack.getItem();
                    }
                }
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

            // Drop items
            if (collectAtTarget) {
                allDrops.addAll(drops);
            } else {
                for (ItemStack drop : drops) {
                    if (!drop.isEmpty()) {
                        Block.popResource(serverLevel, cropPos, drop);
                    }
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
            if (collectAtTarget && !allDrops.isEmpty()) {
                List<ItemStack> mergedDrops = mergeItemStacks(allDrops);
                for (ItemStack drop : mergedDrops) {
                    if (!drop.isEmpty()) {
                        Block.popResource(serverLevel, clickedCropPos, drop);
                    }
                }
            }

            if (lastSoundType != null) {
                serverLevel.playSound(null, clickedCropPos, lastSoundType.getBreakSound(), SoundSource.BLOCKS,
                        (lastSoundType.getVolume() + 1.0F) / 2.0F, lastSoundType.getPitch() * 0.8F);
            } else if (originState != null) {
                SoundType st = originState.getSoundType(serverLevel, clickedCropPos, player);
                serverLevel.playSound(null, clickedCropPos, st.getBreakSound(), SoundSource.BLOCKS,
                        (st.getVolume() + 1.0F) / 2.0F, st.getPitch() * 0.8F);
            }
            player.swing(hand, true);
            if (FTBUltimineCompat.isFTBUltimineLoaded() && FTBUltimineCompat.isUltimineActive(player)) {
                FTBUltimineCompat.applyPostUltimineCosts(player, harvestedCount);
            }
            return true;
        }

        return false;
    }

    private static void applyHarvestCosts(ServerPlayer player, InteractionHand hand, ItemStack heldItem, boolean damageHoe, float exhaustion) {
        if (player.isCreative()) {
            return;
        }
        if (damageHoe && !heldItem.isEmpty() && FarmingEventHandler.isHoe(heldItem)) {
            heldItem.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        if (exhaustion > 0) {
            player.causeFoodExhaustion(exhaustion);
        }
    }

    public static Collection<BlockPos> fallbackHarvestSearch(Level level, BlockPos startPos) {
        int maxLimit = FarmingConfig.MAX_BLOCKS.get();
        int radius = FarmingConfig.FARMING_RADIUS.get();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();

        BlockState startState = level.getBlockState(startPos);
        BlockPos actualCropPos = startPos;
        if (isFarmland(startState) || isSoulSand(startState)) {
            actualCropPos = startPos.above();
            startState = level.getBlockState(actualCropPos);
        }

        boolean targetColumn = isColumnCrop(startState);
        boolean targetFruit = isFruitCrop(startState);
        boolean targetBerry = startState.getBlock() instanceof SweetBerryBushBlock;
        boolean targetVines = startState.getBlock() instanceof CaveVines || startState.is(Blocks.CAVE_VINES) || startState.is(Blocks.CAVE_VINES_PLANT);
        boolean targetCocoa = startState.getBlock() instanceof CocoaBlock;
        boolean targetNetherWart = startState.getBlock() instanceof NetherWartBlock;

        // If column crop, normalize root and traverse horizontally across adjacent column roots
        if (targetColumn) {
            BlockPos startRoot = getColumnCropRoot(level, actualCropPos);
            queue.add(startRoot);
            visited.add(startRoot);

            while (!queue.isEmpty() && result.size() < maxLimit) {
                BlockPos currentRoot = queue.poll();

                // Add stalks strictly ABOVE currentRoot to result (protecting root)
                BlockPos stalk = currentRoot.above();
                BlockState rootState = level.getBlockState(currentRoot);
                while (isSameColumnType(rootState, level.getBlockState(stalk))) {
                    if (result.size() >= maxLimit) break;
                    result.add(stalk);
                    stalk = stalk.above();
                }

                // Check 8 horizontal adjacent columns (including diagonals)
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos neighborPos = currentRoot.offset(dx, dy, dz);
                            if (visited.contains(neighborPos)) continue;
                            if (Math.abs(neighborPos.getX() - startRoot.getX()) > radius
                                    || Math.abs(neighborPos.getZ() - startRoot.getZ()) > radius
                                    || Math.abs(neighborPos.getY() - startRoot.getY()) > 3) {
                                continue;
                            }

                            BlockState neighborState = level.getBlockState(neighborPos);
                            if (isSameColumnType(startState, neighborState)) {
                                BlockPos neighborRoot = getColumnCropRoot(level, neighborPos);
                                if (!visited.contains(neighborRoot)) {
                                    visited.add(neighborRoot);
                                    queue.add(neighborRoot);
                                }
                            }
                        }
                    }
                }
            }
            return result;
        }

        // For all other crops: BFS directly on contiguous harvestable targets
        queue.add(actualCropPos);
        visited.add(actualCropPos);

        while (!queue.isEmpty() && result.size() < maxLimit) {
            BlockPos current = queue.poll();

            BlockState curState = level.getBlockState(current);
            if (targetFruit) {
                if (isFruitCrop(curState)) {
                    result.add(current);
                }
            } else if (targetBerry) {
                if (curState.getBlock() instanceof SweetBerryBushBlock && isMatureCrop(curState)) {
                    result.add(current);
                }
            } else if (targetVines) {
                if ((curState.getBlock() instanceof CaveVines || curState.is(Blocks.CAVE_VINES) || curState.is(Blocks.CAVE_VINES_PLANT)) && CaveVines.hasGlowBerries(curState)) {
                    result.add(current);
                }
            } else if (targetCocoa) {
                if (curState.getBlock() instanceof CocoaBlock && isMatureCrop(curState)) {
                    result.add(current);
                }
            } else if (targetNetherWart) {
                if (curState.getBlock() instanceof NetherWartBlock && isMatureCrop(curState)) {
                    result.add(current);
                }
                // Pitcher Crop or standard Farmland crops (STRICTLY same crop type!)
                if (curState.is(Blocks.PITCHER_CROP) && isMatureCrop(curState)) {
                    result.add(current);
                } else if (curState.is(startState.getBlock()) && isMatureCrop(curState) && !isStem(curState)) {
                    result.add(current);
                }
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos next = current.offset(dx, dy, dz);
                        if (visited.contains(next)) continue;
                        if (Math.abs(next.getX() - actualCropPos.getX()) > radius
                                || Math.abs(next.getZ() - actualCropPos.getZ()) > radius
                                || Math.abs(next.getY() - actualCropPos.getY()) > 2) {
                            continue;
                        }

                        BlockState nextState = level.getBlockState(next);
                        boolean canTraverse = false;

                        if (targetFruit) {
                            // Only traverse connected fruits of the same type! Never traverse stems!
                            if (nextState.is(startState.getBlock())) {
                                canTraverse = true;
                            }
                        } else if (targetBerry) {
                            if (nextState.getBlock() instanceof SweetBerryBushBlock) {
                                canTraverse = true;
                            }
                        } else if (targetVines) {
                            if (nextState.getBlock() instanceof CaveVines || nextState.is(Blocks.CAVE_VINES) || nextState.is(Blocks.CAVE_VINES_PLANT)) {
                                canTraverse = true;
                            }
                        } else if (targetCocoa) {
                            if (nextState.getBlock() instanceof CocoaBlock) {
                                canTraverse = true;
                            }
                        } else if (targetNetherWart) {
                            if (nextState.getBlock() instanceof NetherWartBlock || isSoulSand(nextState)) {
                                canTraverse = true;
                            }
                        } else {
                            // Farmland crops: traverse connected Farmland or the SAME crop type!
                            if (isFarmland(nextState)) {
                                BlockState aboveFarmland = level.getBlockState(next.above());
                                if (aboveFarmland.is(startState.getBlock()) || aboveFarmland.isAir()) {
                                    canTraverse = true;
                                }
                            } else if (nextState.is(startState.getBlock())) {
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

    public static Collection<BlockPos> fallbackCropSearch(Level level, BlockPos startPos) {
        int maxLimit = FarmingConfig.MAX_BLOCKS.get();
        int radius = FarmingConfig.FARMING_RADIUS.get();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();

        BlockState startState = level.getBlockState(startPos);
        BlockPos actualTargetPos = startPos;
        if (isFarmland(startState)) {
            actualTargetPos = startPos.above();
            startState = level.getBlockState(actualTargetPos);
        }

        // Strictly do not bonemeal wild grass blocks!
        if (startState.is(Blocks.GRASS_BLOCK)) {
            return Collections.emptyList();
        }

        // Determine target crop block for strict type matching
        final net.minecraft.world.level.block.Block targetCropBlock = isBonemealCrop(startState) ? startState.getBlock() : null;

        queue.add(actualTargetPos);
        visited.add(actualTargetPos);

        while (!queue.isEmpty() && result.size() < maxLimit) {
            BlockPos current = queue.poll();

            BlockState curState = level.getBlockState(current);
            BlockPos candidateCrop = current;
            BlockState candidateState = curState;
            if (!isBonemealCrop(candidateState) && isFarmland(candidateState)) {
                candidateCrop = current.above();
                candidateState = level.getBlockState(candidateCrop);
            }

            boolean isSameCropType = targetCropBlock == null || candidateState.is(targetCropBlock);
            if (isSameCropType && isBonemealCrop(candidateState) && candidateState.getBlock() instanceof BonemealableBlock bonemealable) {
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
                        if (isFarmland(nextState)) {
                            canTraverse = true;
                        } else if (isBonemealCrop(nextState)) {
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

    public static Collection<BlockPos> fallbackPlantingSearch(Level level, BlockPos startSoilPos, ItemStack seedStack) {
        int maxLimit = FarmingConfig.MAX_BLOCKS.get();
        int radius = FarmingConfig.FARMING_RADIUS.get();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();

        BlockState startState = level.getBlockState(startSoilPos);
        BlockPos actualSoil = startSoilPos;
        if (!isValidSoilForSeed(seedStack, startState, level, startSoilPos)) {
            if (isValidSoilForSeed(seedStack, level.getBlockState(startSoilPos.below()), level, startSoilPos.below())) {
                actualSoil = startSoilPos.below();
                startState = level.getBlockState(actualSoil);
            } else {
                return Collections.emptyList();
            }
        }

        boolean isSoulSandTarget = isSoulSand(startState);
        boolean isFarmlandTarget = isFarmland(startState);
        boolean isSoilTarget = isValidSoilForSeed(seedStack, startState, level, actualSoil);

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
                        if (isSoulSandTarget && isSoulSand(nextState)) {
                            matches = true;
                        } else if (isFarmlandTarget && isFarmland(nextState)) {
                            matches = true;
                        } else if (isSoilTarget && isValidSoilForSeed(seedStack, nextState, level, next)) {
                            if (nextState.is(startState.getBlock())
                                    || (startState.is(BlockTags.DIRT) && nextState.is(BlockTags.DIRT))
                                    || (startState.is(BlockTags.SAND) && nextState.is(BlockTags.SAND))
                                    || (seedStack.is(Items.BAMBOO) && nextState.is(BlockTags.BAMBOO_PLANTABLE_ON))) {
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

        if (isSapling(seedStack) && FarmingConfig.SMART_SAPLING_PLANTING.get()) {
            result = filterSmartSaplingPositions(level, result, seedStack, actualSoil);
        }

        return result;
    }

    public static Collection<BlockPos> fallbackPlantingSearch(Level level, BlockPos startFarmPos) {
        return fallbackPlantingSearch(level, startFarmPos, ItemStack.EMPTY);
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
