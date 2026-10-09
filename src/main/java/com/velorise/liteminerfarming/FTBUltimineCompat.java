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
    public static boolean isUltimineActive(ServerPlayer player) {
        if (!isFTBUltimineLoaded()) {
            return false;
        }
        try {
            Class<?> dataClass = Class.forName("dev.ftb.mods.ftbultimine.FTBUltiminePlayerData");
            Method getMethod = dataClass.getMethod("get", Player.class);
            Object data = getMethod.invoke(null, player);
            if (data != null) {
                try {
                    Method isPressed = data.getClass().getMethod("isPressed");
                    return (boolean) isPressed.invoke(data);
                } catch (NoSuchMethodException e) {
                    try {
                        Method isActive = data.getClass().getMethod("isActive");
                        return (boolean) isActive.invoke(data);
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            LiteMinerFarmingMod.LOGGER.debug("FTB Ultimine reflection check failed", t);
        }
        return false;
    }
}
