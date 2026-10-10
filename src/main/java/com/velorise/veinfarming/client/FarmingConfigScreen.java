package com.velorise.veinfarming.client;

import com.velorise.veinfarming.FarmingConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Clean, native vanilla-style in-game Configuration GUI for Vein Farming.
 * Accessible directly from the NeoForge Mods menu without requiring third-party config mods.
 */
public class FarmingConfigScreen extends Screen {

    private final Screen parent;
    private ConfigList list;

    // Temporary state holding
    private int saplingSpacing;
    private int flowerSparsity;
    private int maxBlocks;
    private int farmingRadius;
    private boolean harvestToInventory;
    private boolean standalonePreview;
    private boolean smartMelonPumpkin;
    private boolean bedrockFlowerBonemeal;
    private boolean smartIntercropping;
    private boolean replantCrops;
    private boolean preventToolBreaking;
    private boolean clearFoliage;
    private boolean smartWaterBucketIrrigation;
    private int waterHoleSpacing;
    private boolean batchComposter;
    private boolean smartBonemeal;
    private boolean preventTrample;
    private boolean damageHoeOnHarvest;
    private boolean ghostFarmlandPreview;
    private boolean ghostPlantPreview;
    private boolean smartIrrigationPreview;
    private boolean growthPenaltyWarning;
    private boolean audioCascade;
    private double exhaustionPerBlock;

    public FarmingConfigScreen(Screen parent) {
        super(Component.literal("Vein Farming Configuration"));
        this.parent = parent;
        this.loadCurrentValues();
    }

    private void loadCurrentValues() {
        this.saplingSpacing = FarmingConfig.SAPLING_MIN_SPACING.get();
        this.flowerSparsity = FarmingConfig.FLOWER_SPARSITY.get();
        this.maxBlocks = FarmingConfig.MAX_BLOCKS.get();
        this.farmingRadius = FarmingConfig.FARMING_RADIUS.get();
        this.harvestToInventory = FarmingConfig.HARVEST_TO_INVENTORY.get();
        this.standalonePreview = FarmingConfig.STANDALONE_PREVIEW.get();
        this.smartMelonPumpkin = FarmingConfig.SMART_MELON_PUMPKIN_PLANTING.get();
        this.bedrockFlowerBonemeal = FarmingConfig.BEDROCK_FLOWER_BONEMEAL.get();
        this.smartIntercropping = FarmingConfig.SMART_INTERCROPPING.get();
        this.replantCrops = FarmingConfig.REPLANT_CROPS.get();
        this.preventToolBreaking = FarmingConfig.PREVENT_TOOL_BREAKING.get();
        this.clearFoliage = FarmingConfig.CLEAR_FOLIAGE.get();
        this.smartWaterBucketIrrigation = FarmingConfig.SMART_WATER_BUCKET_IRRIGATION.get();
        this.waterHoleSpacing = FarmingConfig.WATER_HOLE_SPACING.get();
        this.batchComposter = FarmingConfig.BATCH_COMPOSTER.get();
        this.smartBonemeal = FarmingConfig.SMART_BONEMEAL.get();
        this.preventTrample = FarmingConfig.PREVENT_FARMLAND_TRAMPLE.get();
        this.damageHoeOnHarvest = FarmingConfig.DAMAGE_HOE_ON_HARVEST.get();
        this.ghostFarmlandPreview = FarmingConfig.GHOST_FARMLAND_PREVIEW.get();
        this.ghostPlantPreview = FarmingConfig.GHOST_PLANT_PREVIEW.get();
        this.smartIrrigationPreview = FarmingConfig.SMART_IRRIGATION_PREVIEW.get();
        this.growthPenaltyWarning = FarmingConfig.GROWTH_PENALTY_WARNING.get();
        this.audioCascade = FarmingConfig.SATISFYING_AUDIO_CASCADE.get();
        this.exhaustionPerBlock = FarmingConfig.EXHAUSTION_PER_BLOCK.get();
    }

    private void saveValues() {
        FarmingConfig.SAPLING_MIN_SPACING.set(this.saplingSpacing);
        FarmingConfig.FLOWER_SPARSITY.set(this.flowerSparsity);
        FarmingConfig.MAX_BLOCKS.set(this.maxBlocks);
        FarmingConfig.FARMING_RADIUS.set(this.farmingRadius);
        FarmingConfig.HARVEST_TO_INVENTORY.set(this.harvestToInventory);
        FarmingConfig.STANDALONE_PREVIEW.set(this.standalonePreview);
        FarmingConfig.SMART_MELON_PUMPKIN_PLANTING.set(this.smartMelonPumpkin);
        FarmingConfig.BEDROCK_FLOWER_BONEMEAL.set(this.bedrockFlowerBonemeal);
        FarmingConfig.SMART_INTERCROPPING.set(this.smartIntercropping);
        FarmingConfig.REPLANT_CROPS.set(this.replantCrops);
        FarmingConfig.PREVENT_TOOL_BREAKING.set(this.preventToolBreaking);
        FarmingConfig.CLEAR_FOLIAGE.set(this.clearFoliage);
        FarmingConfig.SMART_WATER_BUCKET_IRRIGATION.set(this.smartWaterBucketIrrigation);
        FarmingConfig.WATER_HOLE_SPACING.set(this.waterHoleSpacing);
        FarmingConfig.BATCH_COMPOSTER.set(this.batchComposter);
        FarmingConfig.SMART_BONEMEAL.set(this.smartBonemeal);
        FarmingConfig.PREVENT_FARMLAND_TRAMPLE.set(this.preventTrample);
        FarmingConfig.DAMAGE_HOE_ON_HARVEST.set(this.damageHoeOnHarvest);
        FarmingConfig.GHOST_FARMLAND_PREVIEW.set(this.ghostFarmlandPreview);
        FarmingConfig.GHOST_PLANT_PREVIEW.set(this.ghostPlantPreview);
        FarmingConfig.SMART_IRRIGATION_PREVIEW.set(this.smartIrrigationPreview);
        FarmingConfig.GROWTH_PENALTY_WARNING.set(this.growthPenaltyWarning);
        FarmingConfig.SATISFYING_AUDIO_CASCADE.set(this.audioCascade);
        FarmingConfig.EXHAUSTION_PER_BLOCK.set(this.exhaustionPerBlock);

        FarmingConfig.SPEC.save();
    }

    private void resetDefaults() {
        this.saplingSpacing = 4;
        this.flowerSparsity = 3;
        this.maxBlocks = 91;
        this.farmingRadius = 8;
        this.harvestToInventory = false;
        this.standalonePreview = true;
        this.smartMelonPumpkin = true;
        this.bedrockFlowerBonemeal = true;
        this.smartIntercropping = true;
        this.replantCrops = true;
        this.preventToolBreaking = true;
        this.clearFoliage = true;
        this.smartWaterBucketIrrigation = true;
        this.waterHoleSpacing = 8;
        this.batchComposter = true;
        this.smartBonemeal = true;
        this.preventTrample = true;
        this.damageHoeOnHarvest = true;
        this.ghostFarmlandPreview = true;
        this.ghostPlantPreview = true;
        this.smartIrrigationPreview = true;
        this.growthPenaltyWarning = true;
        this.audioCascade = true;
        this.exhaustionPerBlock = 0.02;

        this.rebuildList();
    }

    @Override
    protected void init() {
        int listHeight = this.height - 64;
        this.list = new ConfigList(this.minecraft, this.width, listHeight, 32, 26);
        this.rebuildList();
        this.addRenderableWidget(this.list);

        int buttonY = this.height - 26;
        int buttonWidth = 100;

        // Reset to Defaults Button
        this.addRenderableWidget(Button.builder(Component.literal("Reset Defaults"), b -> this.resetDefaults())
                .bounds(this.width / 2 - 155, buttonY, buttonWidth, 20)
                .build());

        // Cancel Button
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parent);
            }
        }).bounds(this.width / 2 - 50, buttonY, buttonWidth, 20).build());

        // Done / Save Button
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
            this.saveValues();
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parent);
            }
        }).bounds(this.width / 2 + 55, buttonY, buttonWidth, 20).build());
    }

    private void rebuildList() {
        if (this.list == null) return;
        this.list.clearAllEntries();

        int widgetWidth = 310;
        int widgetHeight = 20;

        // --- 1. Primary Gameplay & Planting Controls (Top of list) ---

        // 1. Sapling Spacing Slider (0 = OFF, 1..8)
        this.list.addConfigEntry(new IntSlider(0, 0, widgetWidth, widgetHeight,
                "Sapling Spacing", "blocks", 0, 8, this.saplingSpacing, "OFF (Carpet)",
                "Minimum block distance between planted saplings. Set to 0 to disable.",
                val -> this.saplingSpacing = val));

        // 2. Flower & Flora Sparsity Slider (0 = OFF, 1..5)
        this.list.addConfigEntry(new FlowerSparsitySlider(0, 0, widgetWidth, widgetHeight,
                this.flowerSparsity,
                "Scattering sparsity for flowers, ferns, and mushrooms. Set to 0 for dense carpet.",
                val -> this.flowerSparsity = val));

        // 3. Max Farming Blocks (16..512)
        this.list.addConfigEntry(new IntSlider(0, 0, widgetWidth, widgetHeight,
                "Max Farming Blocks", "blocks", 16, 512, this.maxBlocks, null,
                "Maximum number of blocks affected in a single mass farming action.",
                val -> this.maxBlocks = val));

        // 4. Farming Radius (2..32)
        this.list.addConfigEntry(new IntSlider(0, 0, widgetWidth, widgetHeight,
                "Farming Radius", "blocks", 2, 32, this.farmingRadius, null,
                "Maximum horizontal block distance from the targeted block.",
                val -> this.farmingRadius = val));

        // 5. Harvest Direct to Inventory (ON/OFF) - 5th slot
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Harvest Direct to Inventory", this.harvestToInventory,
                "Places harvested crops directly into your inventory instead of dropping them.",
                val -> this.harvestToInventory = val));

        // 6. In-World Highlight Preview (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "In-World Highlight Preview", this.standalonePreview,
                "Renders a 3D wireframe box around affected blocks when mass action key is held.",
                val -> this.standalonePreview = val));

        // 7. Smart Melon & Pumpkin (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Smart Melon & Pumpkin", this.smartMelonPumpkin,
                "Plants melon and pumpkin stems in a 40/40 pattern so fruits have space to grow.",
                val -> this.smartMelonPumpkin = val));

        // 8. Bedrock Flower Bone Meal (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Bedrock Flower Bone Meal", this.bedrockFlowerBonemeal,
                "Applying bone meal to small flowers spawns duplicate blooms nearby.",
                val -> this.bedrockFlowerBonemeal = val));

        // 9. Smart Intercropping (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Smart Intercropping", this.smartIntercropping,
                "Plants alternating crop rows when holding different seeds in each hand for faster growth.",
                val -> this.smartIntercropping = val));

        // 10. Auto-Replant Crops (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Auto-Replant Crops", this.replantCrops,
                "Automatically replants crops at age 0 using dropped or inventory seeds.",
                val -> this.replantCrops = val));

        // 11. Prevent Tool Breaking (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Prevent Tool Breaking", this.preventToolBreaking,
                "Stops mass farming actions when your tool reaches 1 durability to prevent breaking.",
                val -> this.preventToolBreaking = val));

        // 12. Clear Foliage when Hoeing (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Clear Foliage when Hoeing", this.clearFoliage,
                "Automatically clears weeds, grass, and flowers above dirt when tilling.",
                val -> this.clearFoliage = val));

        // 13. Smart Water Bucket Irrigation (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Water Bucket Irrigation", this.smartWaterBucketIrrigation,
                "When holding a Hoe (main) and Water Bucket (off), automatically digs 8-block spaced water holes in dry soil.",
                val -> this.smartWaterBucketIrrigation = val));

        // 14. Water Hole Spacing Slider (8..16 blocks)
        this.list.addConfigEntry(new IntSlider(0, 0, widgetWidth, widgetHeight,
                "Irrigation Spacing", "blocks", 8, 16, this.waterHoleSpacing, null,
                "Minimum spacing between water irrigation holes. 8+ blocks ensures non-overlapping moisture zones.",
                val -> this.waterHoleSpacing = val));

        // 14. Batch Composter (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Batch Composter (1-Click)", this.batchComposter,
                "Right-click a composter while holding Sneak to compost entire stacks instantly.",
                val -> this.batchComposter = val));

        // --- 2. Secondary & Advanced Settings (Below) ---

        // 14. Smart Bone Meal Multi-Pass (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Smart Bone Meal (Multi-Pass)", this.smartBonemeal,
                "Continues applying bone meal to adjacent growing crops until fully mature.",
                val -> this.smartBonemeal = val));

        // 15. Prevent Farmland Trample (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Prevent Farmland Trample", this.preventTrample,
                "Prevents farmland from reverting to dirt when jumped on by players or mobs.",
                val -> this.preventTrample = val));

        // 16. Damage Hoe on Harvest (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Damage Hoe on Harvest", this.damageHoeOnHarvest,
                "Consumes hoe durability when right-click harvesting crops with a hoe.",
                val -> this.damageHoeOnHarvest = val));

        // 17. Ghost Farmland 3D Preview (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Ghost Farmland 3D Preview", this.ghostFarmlandPreview,
                "Displays translucent farmland ghost preview with water hydration indicator.",
                val -> this.ghostFarmlandPreview = val));

        // 18. Ghost Plant 3D Preview (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Ghost Plant 3D Preview", this.ghostPlantPreview,
                "Displays translucent plant preview at each valid planting location.",
                val -> this.ghostPlantPreview = val));

        // 19. Smart Irrigation Preview (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Smart Irrigation Preview", this.smartIrrigationPreview,
                "Highlights unhydrated farmland lacking water and suggests water well locations.",
                val -> this.smartIrrigationPreview = val));

        // 20. Growth Penalty Warning (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Growth Penalty Warning", this.growthPenaltyWarning,
                "Warns when planting crops in patterns that suffer vanilla 50% growth penalties.",
                val -> this.growthPenaltyWarning = val));

        // 21. Satisfying Audio Cascade (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Satisfying Audio Cascade", this.audioCascade,
                "Plays musical ascending pitch chime notes during mass harvest and planting.",
                val -> this.audioCascade = val));

        // 22. Exhaustion Per Block Slider (0.00 .. 0.10)
        this.list.addConfigEntry(new ExhaustionSlider(0, 0, widgetWidth, widgetHeight,
                this.exhaustionPerBlock,
                "Hunger exhaustion added to the player per block affected.",
                val -> this.exhaustionPerBlock = val));
    }

    private static Button createBooleanButton(int width, int height, String label, boolean initial, String tooltip, Consumer<Boolean> onChange) {
        boolean[] state = new boolean[]{initial};
        Button button = Button.builder(formatBooleanComponent(label, state[0]), btn -> {
            state[0] = !state[0];
            btn.setMessage(formatBooleanComponent(label, state[0]));
            onChange.accept(state[0]);
        }).bounds(0, 0, width, height).build();
        if (tooltip != null && !tooltip.isEmpty()) {
            button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        }
        return button;
    }

    private static Component formatBooleanComponent(String label, boolean state) {
        return Component.literal(label + ": ")
                .append(Component.literal(state ? "ON" : "OFF")
                        .withStyle(state ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
    }

    // =========================================================================
    // Inner Scrollable List
    // =========================================================================
    public static class ConfigList extends ContainerObjectSelectionList<ConfigList.ConfigEntry> {

        public ConfigList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        public void addConfigEntry(AbstractWidget widget) {
            this.addEntry(new ConfigEntry(widget));
        }

        public void clearAllEntries() {
            this.clearEntries();
        }

        @Override
        public int getRowWidth() {
            return 310;
        }

        public static class ConfigEntry extends ContainerObjectSelectionList.Entry<ConfigEntry> {
            private final AbstractWidget widget;

            public ConfigEntry(AbstractWidget widget) {
                this.widget = widget;
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return Collections.singletonList(this.widget);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return Collections.singletonList(this.widget);
            }

            @Override
            public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean isHovered, float partialTick) {
                this.widget.setX(left);
                this.widget.setY(top + 2);
                this.widget.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
    }

    // =========================================================================
    // Custom Slider Controls
    // =========================================================================
    private static class IntSlider extends AbstractSliderButton {
        private final String prefix;
        private final String suffix;
        private final int min;
        private final int max;
        private final String offLabel;
        private int currentVal;
        private final Consumer<Integer> onChange;

        public IntSlider(int x, int y, int width, int height, String prefix, String suffix,
                         int min, int max, int initial, String offLabel, String tooltip, Consumer<Integer> onChange) {
            super(x, y, width, height, Component.empty(), (double) (initial - min) / (max - min));
            this.prefix = prefix;
            this.suffix = suffix;
            this.min = min;
            this.max = max;
            this.currentVal = initial;
            this.offLabel = offLabel;
            this.onChange = onChange;
            if (tooltip != null && !tooltip.isEmpty()) {
                this.setTooltip(Tooltip.create(Component.literal(tooltip)));
            }
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            if (this.min == 0 && this.currentVal == 0 && this.offLabel != null) {
                this.setMessage(Component.literal(this.prefix + ": ")
                        .append(Component.literal(this.offLabel).withStyle(ChatFormatting.RED)));
            } else {
                this.setMessage(Component.literal(this.prefix + ": " + this.currentVal + (this.suffix.isEmpty() ? "" : " " + this.suffix)));
            }
        }

        @Override
        protected void applyValue() {
            this.currentVal = (int) Math.round(this.min + this.value * (this.max - this.min));
            this.updateMessage();
            this.onChange.accept(this.currentVal);
        }
    }

    private static class FlowerSparsitySlider extends AbstractSliderButton {
        private int currentVal;
        private final Consumer<Integer> onChange;

        public FlowerSparsitySlider(int x, int y, int width, int height, int initial, String tooltip, Consumer<Integer> onChange) {
            super(x, y, width, height, Component.empty(), (double) initial / 5.0);
            this.currentVal = initial;
            this.onChange = onChange;
            if (tooltip != null && !tooltip.isEmpty()) {
                this.setTooltip(Tooltip.create(Component.literal(tooltip)));
            }
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            Component desc;
            switch (this.currentVal) {
                case 0 -> desc = Component.literal("OFF (Dense Carpet)").withStyle(ChatFormatting.RED);
                case 1 -> desc = Component.literal("1 (Dense Meadow)").withStyle(ChatFormatting.YELLOW);
                case 2 -> desc = Component.literal("2 (Moderate Meadow)").withStyle(ChatFormatting.YELLOW);
                case 3 -> desc = Component.literal("3 (Natural Sparse - Default)").withStyle(ChatFormatting.GREEN);
                case 4 -> desc = Component.literal("4 (Very Sparse)").withStyle(ChatFormatting.AQUA);
                case 5 -> desc = Component.literal("5 (Minimalist Scatter)").withStyle(ChatFormatting.LIGHT_PURPLE);
                default -> desc = Component.literal(String.valueOf(this.currentVal));
            }
            this.setMessage(Component.literal("Flower & Flora Sparsity: ").append(desc));
        }

        @Override
        protected void applyValue() {
            this.currentVal = (int) Math.round(this.value * 5.0);
            this.updateMessage();
            this.onChange.accept(this.currentVal);
        }
    }

    private static class ExhaustionSlider extends AbstractSliderButton {
        private double currentVal;
        private final Consumer<Double> onChange;

        public ExhaustionSlider(int x, int y, int width, int height, double initial, String tooltip, Consumer<Double> onChange) {
            super(x, y, width, height, Component.empty(), initial / 0.10);
            this.currentVal = initial;
            this.onChange = onChange;
            if (tooltip != null && !tooltip.isEmpty()) {
                this.setTooltip(Tooltip.create(Component.literal(tooltip)));
            }
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(String.format("Exhaustion Per Block: %.3f", this.currentVal)));
        }

        @Override
        protected void applyValue() {
            this.currentVal = Math.round(this.value * 0.10 * 1000.0) / 1000.0;
            this.updateMessage();
            this.onChange.accept(this.currentVal);
        }
    }
}
