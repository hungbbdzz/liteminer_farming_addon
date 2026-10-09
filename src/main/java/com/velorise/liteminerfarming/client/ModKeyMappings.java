package com.velorise.liteminerfarming.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.velorise.liteminerfarming.FarmingConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class ModKeyMappings {

    public static final KeyMapping KEY_ACTIVATE = new KeyMapping(
            "key.liteminer_farming_addon.activate",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            "key.categories.liteminer_farming_addon"
    );

    public static final KeyMapping KEY_TOGGLE_SMART_PLANT = new KeyMapping(
            "key.liteminer_farming_addon.toggle_smart_plant",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories.liteminer_farming_addon"
    );

    private static boolean smartPlantEnabled = true;

    public static boolean isSmartPlantEnabled() {
        return smartPlantEnabled;
    }

    public static void setSmartPlantEnabled(boolean enabled) {
        smartPlantEnabled = enabled;
    }

    public static boolean isKeyActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        if (KEY_ACTIVATE.isDown()) {
            return true;
        }
        if (mc.options.keyShift.isDown()) {
            return true;
        }
        if (!FarmingConfig.REQUIRE_SNEAK_FALLBACK.get()) {
            return true;
        }
        return false;
    }

    private ModKeyMappings() {
    }
}

