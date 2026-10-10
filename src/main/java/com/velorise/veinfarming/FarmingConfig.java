package com.velorise.veinfarming;

import net.neoforged.neoforge.common.ModConfigSpec;

public class FarmingConfig {
    public static final ModConfigSpec SPEC;

    // --- Primary Controls & Sliders (Top of list) ---
    public static final ModConfigSpec.IntValue SAPLING_MIN_SPACING;
    public static final ModConfigSpec.IntValue FLOWER_SPARSITY;
    public static final ModConfigSpec.IntValue MAX_BLOCKS;
    public static final ModConfigSpec.IntValue FARMING_RADIUS;
    public static final ModConfigSpec.BooleanValue HARVEST_TO_INVENTORY;
    public static final ModConfigSpec.BooleanValue STANDALONE_PREVIEW;
    public static final ModConfigSpec.BooleanValue SMART_MELON_PUMPKIN_PLANTING;
    public static final ModConfigSpec.BooleanValue BEDROCK_FLOWER_BONEMEAL;
    public static final ModConfigSpec.BooleanValue SMART_INTERCROPPING;
    public static final ModConfigSpec.BooleanValue REPLANT_CROPS;
    public static final ModConfigSpec.BooleanValue PREVENT_TOOL_BREAKING;
    public static final ModConfigSpec.BooleanValue CLEAR_FOLIAGE;
    public static final ModConfigSpec.BooleanValue SMART_WATER_BUCKET_IRRIGATION;
    public static final ModConfigSpec.IntValue WATER_HOLE_SPACING;
    public static final ModConfigSpec.BooleanValue BATCH_COMPOSTER;

    // --- Secondary & Advanced Settings ---
    public static final ModConfigSpec.BooleanValue SMART_BONEMEAL;
    public static final ModConfigSpec.BooleanValue PREVENT_FARMLAND_TRAMPLE;
    public static final ModConfigSpec.BooleanValue DAMAGE_HOE_ON_HARVEST;
    public static final ModConfigSpec.BooleanValue GHOST_PLANT_PREVIEW;
    public static final ModConfigSpec.BooleanValue GHOST_FARMLAND_PREVIEW;
    public static final ModConfigSpec.BooleanValue SMART_IRRIGATION_PREVIEW;
    public static final ModConfigSpec.BooleanValue GROWTH_PENALTY_WARNING;
    public static final ModConfigSpec.BooleanValue SATISFYING_AUDIO_CASCADE;
    public static final ModConfigSpec.DoubleValue EXHAUSTION_PER_BLOCK;
    public static final ModConfigSpec.BooleanValue COLLECT_DROPS_AT_TARGET;
    public static final ModConfigSpec.BooleanValue HARVEST_SUGAR_CANE;
    public static final ModConfigSpec.BooleanValue ENABLE_MASS_HARVEST;
    public static final ModConfigSpec.BooleanValue PULL_FROM_INVENTORY;
    public static final ModConfigSpec.BooleanValue SMART_SAPLING_2X2;

    // Legacy fields kept for backward compatibility
    public static final ModConfigSpec.BooleanValue SMART_SAPLING_PLANTING;
    public static final ModConfigSpec.BooleanValue SMART_FLOWER_PLANTING;

    public static boolean isSmartSaplingEnabled() {
        return SAPLING_MIN_SPACING.get() > 0;
    }

    public static boolean isSmartFlowerEnabled() {
        return FLOWER_SPARSITY.get() > 0;
    }

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");

        // 1. Sliders & Most frequently adjusted features at the TOP
        SAPLING_MIN_SPACING = builder
                .comment("Minimum spacing between planted saplings in blocks. Set to 0 to turn OFF (allows carpet planting). Default: 4.")
                .defineInRange("sapling_min_spacing", 4, 0, 8);

        FLOWER_SPARSITY = builder
                .comment("Organic meadow sparsity level for flowers, mushrooms, ferns, and dead bushes (0 = OFF/Dense Carpet, 1 = Dense, 3 = Natural Sparse, 5 = Minimalist). Set to 0 to turn OFF. Default: 3.")
                .defineInRange("flower_sparsity", 3, 0, 5);

        MAX_BLOCKS = builder
                .comment("Maximum number of blocks to hoe, plant, fertilize, or harvest in a single action.")
                .defineInRange("max_blocks", 64, 1, 512);

        FARMING_RADIUS = builder
                .comment("Maximum horizontal radius from the clicked block.")
                .defineInRange("farming_radius", 8, 1, 32);

        HARVEST_TO_INVENTORY = builder
                .comment("Send harvested items directly into the player's inventory instead of dropping them on the ground (overflow drops on ground).")
                .define("harvest_to_inventory", false);

        STANDALONE_PREVIEW = builder
                .comment("Enable client-side highlight wireframe preview when LiteMiner is not installed.")
                .define("standalone_preview", true);

        SMART_MELON_PUMPKIN_PLANTING = builder
                .comment("When planting Melon or Pumpkin seeds, automatically plant stems in an optimal 40/40 checkerboard pattern, preserving adjacent spaces for fruits to grow.")
                .define("smart_melon_pumpkin_planting", true);

        BEDROCK_FLOWER_BONEMEAL = builder
                .comment("Allow using Bone Meal on small 1-block flowers (Poppy, Dandelion, etc.) to spread duplicates onto surrounding blocks like Minecraft Bedrock Edition.")
                .define("bedrock_flower_bonemeal", true);

        SMART_INTERCROPPING = builder
                .comment("When holding different crop seeds in both hands, automatically plant them in alternating parallel rows for optimal vanilla growth speed.")
                .define("smart_intercropping", true);

        REPLANT_CROPS = builder
                .comment("Automatically replant harvested crops at age 0 using dropped or inventory seeds.")
                .define("replant_crops", true);

        PREVENT_TOOL_BREAKING = builder
                .comment("Prevent tools from breaking during mass farming actions.")
                .define("prevent_tool_breaking", true);

        CLEAR_FOLIAGE = builder
                .comment("Automatically clear replaceable plants/grass above dirt when mass hoeing.")
                .define("clear_foliage", true);

        SMART_WATER_BUCKET_IRRIGATION = builder
                .comment("When holding a Hoe in main hand and a Water Bucket (or fluid container with water) in off hand, automatically creates water irrigation holes in unhydrated areas.")
                .define("smart_water_bucket_irrigation", true);

        WATER_HOLE_SPACING = builder
                .comment("Minimum spacing between water irrigation holes (in blocks). Vanilla water hydrates up to 4 blocks in all horizontal directions (9x9 square). Minimum 8 blocks prevents overlapping hydration zones and saves farmland.")
                .defineInRange("water_hole_spacing", 8, 8, 16);

        BATCH_COMPOSTER = builder
                .comment("Holding Sneak and right-clicking a Composter with seeds or compostables instantly processes the entire stack into Bone Meal in one click.")
                .define("batch_composter", true);

        // 2. Secondary & Advanced Settings
        SMART_BONEMEAL = builder
                .comment("When applying bone meal, continue fertilizing growing crops in the selected area until they reach maturity.")
                .define("smart_bonemeal", true);

        PREVENT_FARMLAND_TRAMPLE = builder
                .comment("Prevent farmland from being trampled into dirt when players or mobs jump or land on it.")
                .define("prevent_farmland_trample", true);

        DAMAGE_HOE_ON_HARVEST = builder
                .comment("If holding a hoe when mass harvesting, consume durability per crop.")
                .define("damage_hoe_on_harvest", true);

        GHOST_PLANT_PREVIEW = builder
                .comment("Display a faded translucent ghost preview of the sapling or plant at each valid planting location.")
                .define("ghost_plant_preview", true);

        GHOST_FARMLAND_PREVIEW = builder
                .comment("Display a faded translucent 3D ghost preview of Farmland when holding a hoe, with visual moisture feedback.")
                .define("ghost_farmland_preview", true);

        SMART_IRRIGATION_PREVIEW = builder
                .comment("In hoe preview, distinguish unhydrated farmland that lacks water with an amber warning, and show optimal 9x9 water well locations.")
                .define("smart_irrigation_preview", true);

        GROWTH_PENALTY_WARNING = builder
                .comment("Highlight planting preview locations that will suffer vanilla 50% growth penalties in warning coral/red.")
                .define("growth_penalty_warning", true);

        SATISFYING_AUDIO_CASCADE = builder
                .comment("Play a musical ascending audio pitch cascade and sparkle particles during mass harvest and planting.")
                .define("satisfying_audio_cascade", true);

        EXHAUSTION_PER_BLOCK = builder
                .comment("Food exhaustion added per block hoed, planted, or harvested.")
                .defineInRange("exhaustion_per_block", 0.02, 0.0, 1.0);

        COLLECT_DROPS_AT_TARGET = builder
                .comment("Gather all harvested item drops at the targeted block position instead of dropping them at each crop position.")
                .define("collect_drops_at_target", true);

        HARVEST_SUGAR_CANE = builder
                .comment("Enable mass harvesting of sugar cane and column crops, preserving the bottom root block.")
                .define("harvest_sugar_cane", true);

        ENABLE_MASS_HARVEST = builder
                .comment("Enable AOE mass harvesting of mature crops.")
                .define("enable_mass_harvest", true);

        PULL_FROM_INVENTORY = builder
                .comment("When mass planting, pull additional matching seeds/crops from the inventory if the hand stack runs out.")
                .define("pull_from_inventory", true);

        SMART_SAPLING_2X2 = builder
                .comment("Automatically plant in 2x2 clusters for trees that support or require 2x2 grids (Dark Oak, Spruce, Jungle).")
                .define("smart_sapling_2x2", true);

        SMART_SAPLING_PLANTING = builder
                .comment("Legacy flag: kept for backward compatibility.")
                .define("smart_sapling_planting", true);

        SMART_FLOWER_PLANTING = builder
                .comment("Legacy flag: kept for backward compatibility.")
                .define("smart_flower_planting", true);

        builder.pop();
        SPEC = builder.build();
    }
}
