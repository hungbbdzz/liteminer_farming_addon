package com.velorise.liteminerfarming;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

public class FTBUltimineCompat {
    public static final String FTB_ULTIMINE_MODID = "ftbultimine";

    public static boolean isFTBUltimineLoaded() {
        return ModList.get().isLoaded(FTB_ULTIMINE_MODID);
    }

    /**
     * Checks if the player is currently holding the FTB Ultimine activation key.
     * Uses reflection for clean soft-dependency without crash risks.
     */
    public static boolean isUltimineActive(Player player) {
        if (!isFTBUltimineLoaded() || player == null) {
            return false;
        }
        try {
            Class<?> ftbClass = Class.forName("dev.ftb.mods.ftbultimine.FTBUltimine");
            Method getInstanceMethod = ftbClass.getMethod("getInstance");
            Object instance = getInstanceMethod.invoke(null);
            if (instance != null) {
                Method getPlayerDataMethod = ftbClass.getMethod("getOrCreatePlayerData", Player.class);
                Object data = getPlayerDataMethod.invoke(instance, player);
                if (data != null) {
                    Method isPressed = data.getClass().getMethod("isPressed");
                    return (boolean) isPressed.invoke(data);
                }
            }
        } catch (Throwable t) {
            LiteMinerFarmingMod.LOGGER.debug("FTB Ultimine reflection check failed", t);
        }
        return false;
    }

    /**
     * Checks if the client player is pressing the FTB Ultimine keybind.
     */
    public static boolean isUltimineClientActive() {
        if (!isFTBUltimineLoaded()) {
            return false;
        }
        try {
            Class<?> clientClass = Class.forName("dev.ftb.mods.ftbultimine.client.FTBUltimineClient");
            java.lang.reflect.Field keyBindField = clientClass.getField("keyBindUltimine");
            net.minecraft.client.KeyMapping keyMapping = (net.minecraft.client.KeyMapping) keyBindField.get(null);
            if (keyMapping != null) {
                return keyMapping.isDown();
            }
        } catch (Throwable t) {
            LiteMinerFarmingMod.LOGGER.debug("FTB Ultimine client reflection check failed", t);
        }
        return false;
    }
}
