package com.velorise.veinfarming.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.velorise.veinfarming.FarmingConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class ModKeyMappings {

    public static final KeyMapping KEY_ACTIVATE = new KeyMapping(
            "key.vein_farming.activate",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            "key.categories.vein_farming"
    );

    public static final KeyMapping KEY_TOGGLE_SMART_PLANT = new KeyMapping(
            "key.vein_farming.toggle_smart_plant",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories.vein_farming"
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
        return KEY_ACTIVATE.isDown() || mc.options.keyShift.isDown();
    }

    private ModKeyMappings() {
    }
}

