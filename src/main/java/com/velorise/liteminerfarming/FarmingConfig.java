package com.velorise.liteminerfarming;

import net.neoforged.neoforge.common.ModConfigSpec;

public class FarmingConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_BLOCKS;
    public static final ModConfigSpec.IntValue FARMING_RADIUS;
    public static final ModConfigSpec.BooleanValue PREVENT_TOOL_BREAKING;
    public static final ModConfigSpec.BooleanValue PULL_FROM_INVENTORY;
    public static final ModConfigSpec.BooleanValue CLEAR_FOLIAGE;
    public static final ModConfigSpec.DoubleValue EXHAUSTION_PER_BLOCK;
    public static final ModConfigSpec.BooleanValue REQUIRE_SNEAK_FALLBACK;
    public static final ModConfigSpec.BooleanValue USE_LITEMINER_LIMIT;
    public static final ModConfigSpec.BooleanValue ENABLE_MASS_HARVEST;
    public static final ModConfigSpec.BooleanValue REPLANT_CROPS;
    public static final ModConfigSpec.BooleanValue DAMAGE_HOE_ON_HARVEST;
    public static final ModConfigSpec.BooleanValue SMART_BONEMEAL;
    public static final ModConfigSpec.BooleanValue PREVENT_FARMLAND_TRAMPLE;
    public static final ModConfigSpec.BooleanValue COLLECT_DROPS_AT_TARGET;
    public static final ModConfigSpec.BooleanValue HARVEST_SUGAR_CANE;
    public static final ModConfigSpec.BooleanValue STANDALONE_PREVIEW;
    public static final ModConfigSpec.BooleanValue SMART_SAPLING_PLANTING;
    public static final ModConfigSpec.IntValue SAPLING_MIN_SPACING;
    public static final ModConfigSpec.BooleanValue SMART_SAPLING_2X2;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");

        MAX_BLOCKS = builder
                .comment("Maximum number of blocks to hoe, plant, fertilize, or harvest in a single action.")
                .defineInRange("max_blocks", 64, 1, 512);

        FARMING_RADIUS = builder
                .comment("Maximum horizontal radius from the clicked block.")
                .defineInRange("farming_radius", 8, 1, 32);

        PREVENT_TOOL_BREAKING = builder
                .comment("Prevent tools from breaking during mass farming actions.")
                .define("prevent_tool_breaking", true);

        PULL_FROM_INVENTORY = builder
                .comment("When mass planting, pull additional matching seeds/crops from the inventory if the hand stack runs out.")
                .define("pull_from_inventory", true);

        CLEAR_FOLIAGE = builder
                .comment("Automatically clear replaceable plants/grass above dirt when mass hoeing.")
                .define("clear_foliage", true);

        EXHAUSTION_PER_BLOCK = builder
                .comment("Food exhaustion added per block hoed, planted, or harvested.")
                .defineInRange("exhaustion_per_block", 0.02, 0.0, 1.0);

        REQUIRE_SNEAK_FALLBACK = builder
                .comment("If LiteMiner is not installed or active, require holding Sneak (Shift) to trigger.")
                .define("require_sneak_fallback", true);

        USE_LITEMINER_LIMIT = builder
                .comment("If LiteMiner is installed, inherit its blockBreakLimit setting.")
                .define("use_liteminer_limit", true);

        ENABLE_MASS_HARVEST = builder
                .comment("Enable AOE mass harvesting of mature crops.")
                .define("enable_mass_harvest", true);

        REPLANT_CROPS = builder
                .comment("Automatically replant harvested crops at age 0 using dropped or inventory seeds.")
                .define("replant_crops", true);

        DAMAGE_HOE_ON_HARVEST = builder
                .comment("If holding a hoe when mass harvesting, consume durability per crop.")
                .define("damage_hoe_on_harvest", true);

        SMART_BONEMEAL = builder
                .comment("When applying bone meal, continue fertilizing growing crops in the selected area until they reach maturity.")
                .define("smart_bonemeal", true);

        PREVENT_FARMLAND_TRAMPLE = builder
                .comment("Prevent farmland from being trampled into dirt when players or mobs jump or land on it.")
                .define("prevent_farmland_trample", true);

        COLLECT_DROPS_AT_TARGET = builder
                .comment("Gather all harvested item drops at the targeted block position instead of dropping them at each crop position.")
                .define("collect_drops_at_target", true);

        HARVEST_SUGAR_CANE = builder
                .comment("Enable mass harvesting of sugar cane and column crops, preserving the bottom root block.")
                .define("harvest_sugar_cane", true);

        STANDALONE_PREVIEW = builder
                .comment("Enable client-side highlight wireframe preview when LiteMiner is not installed.")
                .define("standalone_preview", true);

        SMART_SAPLING_PLANTING = builder
                .comment("Enable smart spacing and natural density when mass planting saplings (applies to saplings only, default off).")
                .define("smart_sapling_planting", false);

        SAPLING_MIN_SPACING = builder
                .comment("Minimum horizontal spacing in blocks between planted saplings in smart planting mode.")
                .defineInRange("sapling_min_spacing", 4, 1, 10);

        SMART_SAPLING_2X2 = builder
                .comment("Automatically plant in 2x2 clusters for trees that support or require 2x2 grids (Dark Oak, Spruce, Jungle).")
                .define("smart_sapling_2x2", true);

        builder.pop();
        SPEC = builder.build();
    }
}

