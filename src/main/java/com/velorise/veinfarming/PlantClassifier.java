package com.velorise.veinfarming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FungusBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.KelpPlantBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plant, Seed, Soil, and Flora classification utility for Vein Farming.
 */
public class PlantClassifier {

    public static final TagKey<Item> C_SAPLINGS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "saplings"));
    public static final TagKey<Block> C_BLOCK_SAPLINGS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "saplings"));
    public static final TagKey<Item> FORGE_SAPLINGS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", "saplings"));
    public static final TagKey<Block> FORGE_BLOCK_SAPLINGS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("forge", "saplings"));

    public static final TagKey<Item> C_MUSHROOMS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "mushrooms"));
    public static final TagKey<Block> C_BLOCK_MUSHROOMS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "mushrooms"));

    public static final TagKey<Item> C_KNIVES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools/knives"));
    public static final TagKey<Item> FD_KNIVES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("farmersdelight", "tools/knives"));

    private static final Map<Block, Boolean> SUPPORTED_2X2_CACHE = new ConcurrentHashMap<>();
    private static final Map<Block, Boolean> STRICT_2X2_CACHE = new ConcurrentHashMap<>();

    /**
     * Universal check if an item stack is a plantable crop/seed.
     */
    public static boolean isPlantableSeed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.NETHER_WART) || stack.is(Items.BAMBOO) || stack.is(Items.SUGAR_CANE)
                || stack.is(Items.CACTUS) || stack.is(Items.KELP) || isCocoaBean(stack)
                || isChorusFlower(stack) || isMushroom(stack)) {
            return true;
        }
        Item item = stack.getItem();
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block instanceof BushBlock || block instanceof CropBlock
                    || block instanceof BambooSaplingBlock || block instanceof BambooStalkBlock
                    || block instanceof SugarCaneBlock || block instanceof CactusBlock
                    || block instanceof KelpBlock || block instanceof KelpPlantBlock
                    || block instanceof CocoaBlock || block == Blocks.CHORUS_FLOWER
                    || block instanceof MushroomBlock || block instanceof FungusBlock
                    || block.defaultBlockState().is(BlockTags.FLOWERS)) {
                return true;
            }
            String id = block.getDescriptionId().toLowerCase(Locale.ROOT);
            return id.contains("bamboo") || id.contains("sapling") || id.contains("crop")
                    || id.contains("seed") || id.contains("flower") || id.contains("mushroom") || id.contains("fungus");
        }
        return false;
    }

    public static boolean isPlantableCrop(ItemStack stack) {
        return isPlantableSeed(stack);
    }

    public static boolean isMushroom(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.RED_MUSHROOM) || stack.is(Items.BROWN_MUSHROOM)
                || stack.is(Items.CRIMSON_FUNGUS) || stack.is(Items.WARPED_FUNGUS)) {
            return true;
        }
        if (stack.is(C_MUSHROOMS)) {
            return true;
        }
        if (stack.getItem() instanceof BlockItem bi) {
            Block b = bi.getBlock();
            return b instanceof MushroomBlock || b instanceof FungusBlock || b.defaultBlockState().is(C_BLOCK_MUSHROOMS);
        }
        return false;
    }

    public static boolean isMushroomBlock(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.CRIMSON_FUNGUS) || state.is(Blocks.WARPED_FUNGUS)
                || state.getBlock() instanceof MushroomBlock
                || state.getBlock() instanceof FungusBlock
                || state.is(C_BLOCK_MUSHROOMS);
    }

    public static boolean isFlower(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(ItemTags.FLOWERS)) {
            return true;
        }
        if (stack.is(Items.FERN) || stack.is(Items.LARGE_FERN)
                || stack.is(Items.DEAD_BUSH) || stack.is(Items.SHORT_GRASS)
                || stack.is(Items.TALL_GRASS)) {
            return true;
        }
        if (stack.getItem() instanceof BlockItem bi) {
            Block b = bi.getBlock();
            BlockState state = b.defaultBlockState();
            if (state.is(BlockTags.FLOWERS) || b instanceof FlowerBlock) {
                return true;
            }
            if (b == Blocks.FERN || b == Blocks.LARGE_FERN || b == Blocks.DEAD_BUSH
                    || b == Blocks.SHORT_GRASS || b == Blocks.TALL_GRASS) {
                return true;
            }
            String id = b.getDescriptionId().toLowerCase(Locale.ROOT);
            return id.contains("flower") || id.contains("fern") || id.contains("dead_bush") || id.contains("deadbush") || id.contains("shrub");
        }
        return false;
    }

    public static boolean isFlowerBlock(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (state.is(BlockTags.FLOWERS) || state.getBlock() instanceof FlowerBlock) {
            return true;
        }
        Block b = state.getBlock();
        if (b == Blocks.FERN || b == Blocks.LARGE_FERN || b == Blocks.DEAD_BUSH
                || b == Blocks.SHORT_GRASS || b == Blocks.TALL_GRASS) {
            return true;
        }
        String id = b.getDescriptionId().toLowerCase(Locale.ROOT);
        return id.contains("flower") || id.contains("fern") || id.contains("dead_bush") || id.contains("deadbush") || id.contains("shrub");
    }

    /**
     * Checks if a block is a 1-block tall flower eligible for Bedrock-style Bone Meal propagation.
     * Excludes Wither Rose and double-tall plants.
     */
    public static boolean isSmallFlower(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (state.is(Blocks.WITHER_ROSE)) {
            return false;
        }
        if (state.getBlock() instanceof DoublePlantBlock) {
            return false;
        }
        return state.is(BlockTags.FLOWERS) || state.getBlock() instanceof FlowerBlock;
    }

    public static boolean isChorusFlower(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.is(Items.CHORUS_FLOWER) || (stack.getItem() instanceof BlockItem bi && bi.getBlock() == Blocks.CHORUS_FLOWER);
    }

    public static boolean isChorus(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return state.is(Blocks.CHORUS_PLANT) || state.is(Blocks.CHORUS_FLOWER);
    }

    public static boolean isCocoaBean(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.is(Items.COCOA_BEANS) || (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CocoaBlock);
    }

    public static boolean isJungleLog(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return state.is(BlockTags.JUNGLE_LOGS)
                || state.is(Blocks.JUNGLE_LOG)
                || state.is(Blocks.STRIPPED_JUNGLE_LOG)
                || state.is(Blocks.JUNGLE_WOOD)
                || state.is(Blocks.STRIPPED_JUNGLE_WOOD);
    }

    public static boolean isKnife(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.is(C_KNIVES) || stack.is(FD_KNIVES);
    }

    public static boolean isCompostable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return ComposterBlock.COMPOSTABLES.containsKey(stack.getItem());
    }

    public static boolean isNearWater(LevelReader level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 1, 4))) {
            if (level.getFluidState(p).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWaterBottle(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
            net.minecraft.world.item.alchemy.PotionContents contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            return contents != null && contents.is(net.minecraft.world.item.alchemy.Potions.WATER);
        }
        return false;
    }

    public static boolean isWaterContainer(ItemStack stack) {
        return isWaterBottle(stack) || (stack != null && stack.is(Items.WATER_BUCKET));
    }

    public static boolean isHoe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.HOE_TILL)
                || stack.getItem() instanceof net.minecraft.world.item.HoeItem;
    }

    /**
     * Checks if the given ItemStack represents a sapling (vanilla or modded).
     */
    public static boolean isSapling(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
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

    public static boolean isFruitSeed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.is(Items.MELON_SEEDS) || stack.is(Items.PUMPKIN_SEEDS);
    }

    public static Block getFruitForSeed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.is(Items.MELON_SEEDS)) return Blocks.MELON;
        if (stack.is(Items.PUMPKIN_SEEDS)) return Blocks.PUMPKIN;
        return null;
    }

    public static BlockPos findNearbyWaterBlock(Level level, BlockPos originPos, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = originPos.offset(dx, dy, dz);
                    if (level.getFluidState(p).is(FluidTags.WATER) || level.getBlockState(p).is(Blocks.WATER)) {
                        return p;
                    }
                }
            }
        }
        return null;
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

    public static boolean isCactus(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.is(Items.CACTUS) || (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CactusBlock);
    }

    /**
     * Universal soil check for any seed.
     */
    public static boolean isValidSoilForSeed(ItemStack seedStack, BlockState soilState, Level level, BlockPos soilPos) {
        if (seedStack == null || seedStack.isEmpty() || soilState == null) {
            return false;
        }
        if (isCocoaBean(seedStack)) {
            return isJungleLog(soilState) || soilState.getBlock() instanceof CocoaBlock;
        }
        if (isChorusFlower(seedStack)) {
            return soilState.is(Blocks.END_STONE);
        }
        if (isCactus(seedStack)) {
            return soilState.is(BlockTags.SAND) || soilState.is(Blocks.SAND) || soilState.is(Blocks.RED_SAND);
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
        if (isSmallFlower(state)) {
            return true;
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
        return isRiceCrop(state);
    }

    /**
     * Checks if a block is Farmer's Delight Rice (submerged rice crop or upper rice panicles).
     */
    public static boolean isRiceCrop(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        Block b = state.getBlock();
        String id = b.getDescriptionId().toLowerCase(Locale.ROOT);
        return id.contains("rice_panicles") || id.contains("rice_crop") || (id.contains("rice") && (b instanceof CropBlock || b instanceof BushBlock));
    }

    /**
     * Checks if an item is a tool used for breaking/destroying crops and blocks.
     */
    public static boolean isDestructionTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.is(ItemTags.AXES)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.HOES)
                || stack.is(Items.SHEARS);
    }

    /**
     * Universal check for column crop item stacks (Sugar Cane, Cactus, Bamboo, Kelp).
     */
    public static boolean isColumnCrop(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.SUGAR_CANE) || stack.is(Items.CACTUS) || stack.is(Items.BAMBOO) || stack.is(Items.KELP)) {
            return true;
        }
        Item item = stack.getItem();
        if (item instanceof BlockItem blockItem) {
            return isColumnCrop(blockItem.getBlock().defaultBlockState());
        }
        return false;
    }

    /**
     * Strictly verifies if a seed/crop is eligible for alternating Intercropping (companion planting).
     * Must be a farmland-only crop. Column crops (Sugar Cane/Bamboo/Cactus), Fruit seeds (Melon/Pumpkin),
     * Saplings, and Rice are strictly excluded so they are never forced into intercropping.
     */
    public static boolean isIntercroppableCrop(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (isColumnCrop(stack) || isFruitSeed(stack) || isSapling(stack)) {
            return false;
        }
        if (stack.is(Items.NETHER_WART) || stack.is(Items.COCOA_BEANS) || stack.is(Items.SWEET_BERRIES) || stack.is(Items.KELP)) {
            return false;
        }
        String itemId = stack.getItem().getDescriptionId().toLowerCase(Locale.ROOT);
        if (itemId.contains("rice")) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block instanceof StemBlock || block instanceof AttachedStemBlock) {
                return false;
            }
            if (block instanceof CropBlock) {
                return true;
            }
        }
        if (stack.is(Items.WHEAT_SEEDS) || stack.is(Items.CARROT) || stack.is(Items.POTATO)
                || stack.is(Items.BEETROOT_SEEDS) || stack.is(Items.TORCHFLOWER_SEEDS) || stack.is(Items.PITCHER_POD)) {
            return true;
        }
        Block cropBlock = getCropBlock(stack);
        return cropBlock != null && isFarmlandCrop(cropBlock.defaultBlockState());
    }

    /**
     * Strict check for crops that grow on Farmland (Wheat, Carrots, Potatoes, Beetroots, Torchflower, Pitcher Crop, etc.).
     * Strictly excludes Column crops (Sugar Cane/Bamboo), Fruit crops (Melon/Pumpkin), Stems, Sweet Berry bushes, Nether Wart, and Cocoa.
     */
    public static boolean isFarmlandCrop(BlockState state) {
        if (state == null || state.isAir() || isStem(state) || isColumnCrop(state) || isFruitCrop(state)) {
            return false;
        }
        Block block = state.getBlock();
        if (block instanceof CropBlock
                || state.is(Blocks.PITCHER_CROP)
                || state.is(Blocks.TORCHFLOWER_CROP)) {
            return true;
        }
        if (state.is(BlockTags.CROPS)) {
            return true;
        }
        if (block instanceof BushBlock
                && !(block instanceof SweetBerryBushBlock)
                && !(block instanceof NetherWartBlock)
                && !(block instanceof CocoaBlock)) {
            for (Property<?> prop : state.getProperties()) {
                if (prop instanceof IntegerProperty intProp && intProp.getName().equalsIgnoreCase("age")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if a block is an agricultural crop or harvestable plant.
     * Strictly excludes regular environmental blocks (Stone, Dirt, Wood, Ores, etc.).
     */
    public static boolean isHarvestablePlant(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return isCrop(state)
                || isColumnCrop(state)
                || isFruitCrop(state)
                || isStem(state)
                || isRiceCrop(state)
                || isChorus(state)
                || isFlowerBlock(state)
                || isMushroomBlock(state)
                || state.getBlock() instanceof NetherWartBlock
                || state.getBlock() instanceof CocoaBlock
                || state.getBlock() instanceof SweetBerryBushBlock
                || state.getBlock() instanceof CaveVines
                || state.is(Blocks.CAVE_VINES)
                || state.is(Blocks.CAVE_VINES_PLANT);
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

    public static Block getCropBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (stack.is(Items.SWEET_BERRIES)) {
            return Blocks.SWEET_BERRY_BUSH;
        }
        if (stack.is(Items.NETHER_WART)) {
            return Blocks.NETHER_WART;
        }
        if (stack.is(Items.BAMBOO)) {
            return Blocks.BAMBOO_SAPLING;
        }
        if (isCocoaBean(stack)) {
            return Blocks.COCOA;
        }
        if (isChorusFlower(stack)) {
            return Blocks.CHORUS_FLOWER;
        }
        if (stack.getItem() instanceof BlockItem bi) {
            return bi.getBlock();
        }
        return null;
    }

    public static boolean isMatchingCrop(BlockState state, Block cropBlock) {
        if (cropBlock == null || state == null || state.isAir()) {
            return false;
        }
        if (state.is(cropBlock)) {
            return true;
        }
        if (cropBlock == Blocks.MELON_STEM && state.is(Blocks.ATTACHED_MELON_STEM)) {
            return true;
        }
        if (cropBlock == Blocks.PUMPKIN_STEM && state.is(Blocks.ATTACHED_PUMPKIN_STEM)) {
            return true;
        }
        return false;
    }

    /**
     * Determines the reset/replanted state of a crop (usually age 0, or age 1 for sweet berry bushes).
     */
    public static BlockState getResetCropState(BlockState state) {
        if (state == null || state.isAir()) {
            return Blocks.AIR.defaultBlockState();
        }
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
}

