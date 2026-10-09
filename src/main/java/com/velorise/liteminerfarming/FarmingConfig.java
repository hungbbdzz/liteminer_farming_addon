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

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");

        MAX_BLOCKS = builder
                .comment("Maximum number of blocks to hoe or plant in a single action.")
                .defineInRange("max_blocks", 64, 1, 512);

        FARMING_RADIUS = builder
                .comment("Maximum horizontal radius from the clicked block.")
                .defineInRange("farming_radius", 8, 1, 32);

        PREVENT_TOOL_BREAKING = builder
                .comment("Prevent tools from breaking during mass hoeing.")
                .define("prevent_tool_breaking", true);

        PULL_FROM_INVENTORY = builder
                .comment("When mass planting, pull additional matching seeds/crops from the inventory if the hand stack runs out.")
                .define("pull_from_inventory", true);

        CLEAR_FOLIAGE = builder
                .comment("Automatically clear replaceable plants/grass above dirt when mass hoeing.")
                .define("clear_foliage", true);

        EXHAUSTION_PER_BLOCK = builder
                .comment("Food exhaustion added per block hoed or planted.")
                .defineInRange("exhaustion_per_block", 0.02, 0.0, 1.0);

        REQUIRE_SNEAK_FALLBACK = builder
                .comment("If LiteMiner is not installed or active, require holding Sneak (Shift) to trigger.")
                .define("require_sneak_fallback", true);

        USE_LITEMINER_LIMIT = builder
                .comment("If LiteMiner is installed, inherit its blockBreakLimit setting.")
                .define("use_liteminer_limit", true);

        builder.pop();
        SPEC = builder.build();
    }
}

