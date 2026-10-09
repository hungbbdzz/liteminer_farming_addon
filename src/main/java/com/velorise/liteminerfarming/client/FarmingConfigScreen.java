package com.velorise.liteminerfarming.client;

import com.velorise.liteminerfarming.FarmingConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
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
    private boolean standalonePreview;
    private boolean smartMelonPumpkin;
    private boolean bedrockFlowerBonemeal;
    private boolean smartIntercropping;
    private boolean replantCrops;
    private boolean harvestToInventory;
    private boolean preventToolBreaking;
    private boolean clearFoliage;
    private boolean batchComposter;
    private boolean smartBonemeal;
    private boolean preventTrample;
    private boolean damageHoeOnHarvest;
    private boolean ghostFarmlandPreview;
    private boolean ghostPlantPreview;
    private boolean smartIrrigationPreview;
    private boolean growthPenaltyWarning;
    private boolean audioCascade;
    private boolean knifeCompat;
    private double exhaustionPerBlock;
    private boolean requireSneakFallback;
    private boolean useLiteminerLimit;

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
        this.standalonePreview = FarmingConfig.STANDALONE_PREVIEW.get();
        this.smartMelonPumpkin = FarmingConfig.SMART_MELON_PUMPKIN_PLANTING.get();
        this.bedrockFlowerBonemeal = FarmingConfig.BEDROCK_FLOWER_BONEMEAL.get();
        this.smartIntercropping = FarmingConfig.SMART_INTERCROPPING.get();
        this.replantCrops = FarmingConfig.REPLANT_CROPS.get();
        this.harvestToInventory = FarmingConfig.HARVEST_TO_INVENTORY.get();
        this.preventToolBreaking = FarmingConfig.PREVENT_TOOL_BREAKING.get();
        this.clearFoliage = FarmingConfig.CLEAR_FOLIAGE.get();
        this.batchComposter = FarmingConfig.BATCH_COMPOSTER.get();
        this.smartBonemeal = FarmingConfig.SMART_BONEMEAL.get();
        this.preventTrample = FarmingConfig.PREVENT_FARMLAND_TRAMPLE.get();
        this.damageHoeOnHarvest = FarmingConfig.DAMAGE_HOE_ON_HARVEST.get();
        this.ghostFarmlandPreview = FarmingConfig.GHOST_FARMLAND_PREVIEW.get();
        this.ghostPlantPreview = FarmingConfig.GHOST_PLANT_PREVIEW.get();
        this.smartIrrigationPreview = FarmingConfig.SMART_IRRIGATION_PREVIEW.get();
        this.growthPenaltyWarning = FarmingConfig.GROWTH_PENALTY_WARNING.get();
        this.audioCascade = FarmingConfig.SATISFYING_AUDIO_CASCADE.get();
        this.knifeCompat = FarmingConfig.FARMERS_DELIGHT_KNIFE_COMPAT.get();
        this.exhaustionPerBlock = FarmingConfig.EXHAUSTION_PER_BLOCK.get();
        this.requireSneakFallback = FarmingConfig.REQUIRE_SNEAK_FALLBACK.get();
        this.useLiteminerLimit = FarmingConfig.USE_LITEMINER_LIMIT.get();
    }

    private void saveValues() {
        FarmingConfig.SAPLING_MIN_SPACING.set(this.saplingSpacing);
        FarmingConfig.FLOWER_SPARSITY.set(this.flowerSparsity);
        FarmingConfig.MAX_BLOCKS.set(this.maxBlocks);
        FarmingConfig.FARMING_RADIUS.set(this.farmingRadius);
        FarmingConfig.STANDALONE_PREVIEW.set(this.standalonePreview);
        FarmingConfig.SMART_MELON_PUMPKIN_PLANTING.set(this.smartMelonPumpkin);
        FarmingConfig.BEDROCK_FLOWER_BONEMEAL.set(this.bedrockFlowerBonemeal);
        FarmingConfig.SMART_INTERCROPPING.set(this.smartIntercropping);
        FarmingConfig.REPLANT_CROPS.set(this.replantCrops);
        FarmingConfig.HARVEST_TO_INVENTORY.set(this.harvestToInventory);
        FarmingConfig.PREVENT_TOOL_BREAKING.set(this.preventToolBreaking);
        FarmingConfig.CLEAR_FOLIAGE.set(this.clearFoliage);
        FarmingConfig.BATCH_COMPOSTER.set(this.batchComposter);
        FarmingConfig.SMART_BONEMEAL.set(this.smartBonemeal);
        FarmingConfig.PREVENT_FARMLAND_TRAMPLE.set(this.preventTrample);
        FarmingConfig.DAMAGE_HOE_ON_HARVEST.set(this.damageHoeOnHarvest);
        FarmingConfig.GHOST_FARMLAND_PREVIEW.set(this.ghostFarmlandPreview);
        FarmingConfig.GHOST_PLANT_PREVIEW.set(this.ghostPlantPreview);
        FarmingConfig.SMART_IRRIGATION_PREVIEW.set(this.smartIrrigationPreview);
        FarmingConfig.GROWTH_PENALTY_WARNING.set(this.growthPenaltyWarning);
        FarmingConfig.SATISFYING_AUDIO_CASCADE.set(this.audioCascade);
        FarmingConfig.FARMERS_DELIGHT_KNIFE_COMPAT.set(this.knifeCompat);
        FarmingConfig.EXHAUSTION_PER_BLOCK.set(this.exhaustionPerBlock);
        FarmingConfig.REQUIRE_SNEAK_FALLBACK.set(this.requireSneakFallback);
        FarmingConfig.USE_LITEMINER_LIMIT.set(this.useLiteminerLimit);

        FarmingConfig.SPEC.save();
    }

    private void resetDefaults() {
        this.saplingSpacing = 4;
        this.flowerSparsity = 3;
        this.maxBlocks = 64;
        this.farmingRadius = 8;
        this.standalonePreview = true;
        this.smartMelonPumpkin = true;
        this.bedrockFlowerBonemeal = true;
        this.smartIntercropping = true;
        this.replantCrops = true;
        this.harvestToInventory = false;
        this.preventToolBreaking = true;
        this.clearFoliage = true;
        this.batchComposter = true;
        this.smartBonemeal = true;
        this.preventTrample = true;
        this.damageHoeOnHarvest = true;
        this.ghostFarmlandPreview = true;
        this.ghostPlantPreview = true;
        this.smartIrrigationPreview = true;
        this.growthPenaltyWarning = true;
        this.audioCascade = true;
        this.knifeCompat = true;
        this.exhaustionPerBlock = 0.02;
        this.requireSneakFallback = true;
        this.useLiteminerLimit = true;

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

        // Sapling Spacing Slider (0 = OFF, 1..8)
        this.list.addConfigEntry(new IntSlider(0, 0, widgetWidth, widgetHeight,
                "Sapling Spacing", "blocks", 0, 8, this.saplingSpacing, "OFF (Carpet)",
                val -> this.saplingSpacing = val));

        // Flower & Flora Sparsity Slider (0 = OFF, 1..5)
        this.list.addConfigEntry(new FlowerSparsitySlider(0, 0, widgetWidth, widgetHeight,
                this.flowerSparsity, val -> this.flowerSparsity = val));

        // Max Blocks (16..512)
        this.list.addConfigEntry(new IntSlider(0, 0, widgetWidth, widgetHeight,
                "Max Farming Blocks", "blocks", 16, 512, this.maxBlocks, null,
                val -> this.maxBlocks = val));

        // Farming Radius (2..32)
        this.list.addConfigEntry(new IntSlider(0, 0, widgetWidth, widgetHeight,
                "Farming Radius", "blocks", 2, 32, this.farmingRadius, null,
                val -> this.farmingRadius = val));

        // In-World Highlight Preview (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "In-World Highlight Preview", this.standalonePreview, val -> this.standalonePreview = val));

        // Smart Melon & Pumpkin (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Smart Melon & Pumpkin", this.smartMelonPumpkin, val -> this.smartMelonPumpkin = val));

        // Bedrock Flower Bone Meal (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Bedrock Flower Bone Meal", this.bedrockFlowerBonemeal, val -> this.bedrockFlowerBonemeal = val));

        // Smart Intercropping (Xen Canh) (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Smart Intercropping (Xen Canh)", this.smartIntercropping, val -> this.smartIntercropping = val));

        // Auto-Replant Crops (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Auto-Replant Crops", this.replantCrops, val -> this.replantCrops = val));

        // Harvest Direct to Inventory (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Harvest Direct to Inventory", this.harvestToInventory, val -> this.harvestToInventory = val));

        // Prevent Tool Breaking (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Prevent Tool Breaking", this.preventToolBreaking, val -> this.preventToolBreaking = val));

        // Clear Foliage when Hoeing (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Clear Foliage when Hoeing", this.clearFoliage, val -> this.clearFoliage = val));

        // Batch Composter (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Batch Composter (1-Click)", this.batchComposter, val -> this.batchComposter = val));

        // --- 2. Secondary & Advanced Settings (Below) ---

        // Smart Bone Meal Multi-Pass (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Smart Bone Meal (Multi-Pass)", this.smartBonemeal, val -> this.smartBonemeal = val));

        // Prevent Farmland Trample (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Prevent Farmland Trample", this.preventTrample, val -> this.preventTrample = val));

        // Damage Hoe on Harvest (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Damage Hoe on Harvest", this.damageHoeOnHarvest, val -> this.damageHoeOnHarvest = val));

        // Ghost Farmland 3D Preview (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Ghost Farmland 3D Preview", this.ghostFarmlandPreview, val -> this.ghostFarmlandPreview = val));

        // Ghost Plant 3D Preview (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Ghost Plant 3D Preview", this.ghostPlantPreview, val -> this.ghostPlantPreview = val));

        // Smart Irrigation Preview (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Smart Irrigation Preview", this.smartIrrigationPreview, val -> this.smartIrrigationPreview = val));

        // Growth Penalty Warning (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Growth Penalty Warning", this.growthPenaltyWarning, val -> this.growthPenaltyWarning = val));

        // Satisfying Audio Cascade (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Satisfying Audio Cascade", this.audioCascade, val -> this.audioCascade = val));

        // Farmer's Delight Knife Compat (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Farmer's Delight Knife Compat", this.knifeCompat, val -> this.knifeCompat = val));

        // Exhaustion Per Block Slider (0.00 .. 0.10)
        this.list.addConfigEntry(new ExhaustionSlider(0, 0, widgetWidth, widgetHeight,
                this.exhaustionPerBlock, val -> this.exhaustionPerBlock = val));

        // Require Sneak Fallback (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Require Sneak Fallback", this.requireSneakFallback, val -> this.requireSneakFallback = val));

        // Use LiteMiner Limit (ON/OFF)
        this.list.addConfigEntry(createBooleanButton(widgetWidth, widgetHeight,
                "Use LiteMiner Limit", this.useLiteminerLimit, val -> this.useLiteminerLimit = val));
    }

    private static Button createBooleanButton(int width, int height, String label, boolean initial, Consumer<Boolean> onChange) {
        boolean[] state = new boolean[]{initial};
        return Button.builder(formatBooleanComponent(label, state[0]), btn -> {
            state[0] = !state[0];
            btn.setMessage(formatBooleanComponent(label, state[0]));
            onChange.accept(state[0]);
        }).bounds(0, 0, width, height).build();
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
                         int min, int max, int initial, String offLabel, Consumer<Integer> onChange) {
            super(x, y, width, height, Component.empty(), (double) (initial - min) / (max - min));
            this.prefix = prefix;
            this.suffix = suffix;
            this.min = min;
            this.max = max;
            this.currentVal = initial;
            this.offLabel = offLabel;
            this.onChange = onChange;
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

        public FlowerSparsitySlider(int x, int y, int width, int height, int initial, Consumer<Integer> onChange) {
            super(x, y, width, height, Component.empty(), (double) initial / 5.0);
            this.currentVal = initial;
            this.onChange = onChange;
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

        public ExhaustionSlider(int x, int y, int width, int height, double initial, Consumer<Double> onChange) {
            super(x, y, width, height, Component.empty(), initial / 0.10);
            this.currentVal = initial;
            this.onChange = onChange;
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
