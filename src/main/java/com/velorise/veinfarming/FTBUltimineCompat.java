package com.velorise.veinfarming;

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
            VeinFarmingMod.LOGGER.debug("FTB Ultimine reflection check failed", t);
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
            VeinFarmingMod.LOGGER.debug("FTB Ultimine client reflection check failed", t);
        }
        return false;
    }

    /**
     * Gets the target blocks strictly determined by FTB Ultimine's active shape and preview.
     */
    public static java.util.Collection<net.minecraft.core.BlockPos> getSelectedBlocks(ServerPlayer player, net.minecraft.core.BlockPos targetPos) {
        return getSelectedBlocks(player, targetPos, net.minecraft.core.Direction.UP);
    }

    /**
     * Gets the target blocks strictly determined by FTB Ultimine's active shape and preview.
     */
    public static java.util.Collection<net.minecraft.core.BlockPos> getSelectedBlocks(ServerPlayer player, net.minecraft.core.BlockPos targetPos, net.minecraft.core.Direction face) {
        if (!isFTBUltimineLoaded() || player == null || targetPos == null) {
            return java.util.Collections.emptyList();
        }
        try {
            Class<?> ftbClass = Class.forName("dev.ftb.mods.ftbultimine.FTBUltimine");
            Method getInstanceMethod = ftbClass.getMethod("getInstance");
            Object instance = getInstanceMethod.invoke(null);
            if (instance == null) {
                return java.util.Collections.emptyList();
            }

            Method getPlayerDataMethod = ftbClass.getMethod("getOrCreatePlayerData", Player.class);
            Object data = getPlayerDataMethod.invoke(instance, player);
            if (data == null) {
                return java.util.Collections.emptyList();
            }

            Method isPressedMethod = data.getClass().getMethod("isPressed");
            if (!((boolean) isPressedMethod.invoke(data))) {
                return java.util.Collections.emptyList();
            }

            int maxBlocks = getEffectiveBlockLimit(player);

            Method hasCachedMethod = data.getClass().getMethod("hasCachedPositions");
            Method cachedPositionsMethod = data.getClass().getMethod("cachedPositions");

            // 1. If FTB Ultimine already cached positions for the player's view, check if they match targetPos
            if ((boolean) hasCachedMethod.invoke(data)) {
                Object coll = cachedPositionsMethod.invoke(data);
                if (coll instanceof java.util.Collection<?> collection && !collection.isEmpty()) {
                    boolean containsTarget = false;
                    for (Object obj : collection) {
                        if (obj instanceof net.minecraft.core.BlockPos bp) {
                            if (bp.equals(targetPos) || bp.equals(targetPos.above()) || bp.equals(targetPos.below())) {
                                containsTarget = true;
                                break;
                            }
                        }
                    }
                    if (containsTarget) {
                        java.util.List<net.minecraft.core.BlockPos> result = new java.util.ArrayList<>();
                        for (Object obj : collection) {
                            if (obj instanceof net.minecraft.core.BlockPos bp) {
                                result.add(bp);
                            }
                        }
                        return result;
                    }
                }
            }

            // 2. If not matched, update FTB Ultimine blocks for the target position
            net.minecraft.core.Direction useFace = face != null ? face : net.minecraft.core.Direction.UP;
            Method updateBlocksMethod = data.getClass().getMethod("updateBlocks", ServerPlayer.class, net.minecraft.core.BlockPos.class, net.minecraft.core.Direction.class, boolean.class, int.class);
            updateBlocksMethod.invoke(data, player, targetPos, useFace, false, maxBlocks);

            Object coll = cachedPositionsMethod.invoke(data);
            if (coll instanceof java.util.Collection<?> collection && !collection.isEmpty()) {
                java.util.List<net.minecraft.core.BlockPos> result = new java.util.ArrayList<>();
                for (Object obj : collection) {
                    if (obj instanceof net.minecraft.core.BlockPos bp) {
                        result.add(bp);
                    }
                }
                return result;
            }
        } catch (Throwable t) {
            VeinFarmingMod.LOGGER.debug("FTB Ultimine getSelectedBlocks reflection failed", t);
        }
        return java.util.Collections.emptyList();
    }

    public static int getEffectiveBlockLimit(ServerPlayer player) {
        if (isFTBUltimineLoaded() && player != null) {
            try {
                Class<?> configClass = Class.forName("dev.ftb.mods.ftbultimine.config.FTBUltimineServerConfig");
                Method getMaxBlocks = configClass.getMethod("getMaxBlocks", ServerPlayer.class);
                return (int) getMaxBlocks.invoke(null, player);
            } catch (Throwable ignored) {
            }
        }
        return FarmingConfig.MAX_BLOCKS.get();
    }

    public static boolean shouldPreventToolBreaking() {
        if (isFTBUltimineLoaded()) {
            try {
                Class<?> configClass = Class.forName("dev.ftb.mods.ftbultimine.config.FTBUltimineServerConfig");
                java.lang.reflect.Field field = configClass.getField("PREVENT_TOOL_BREAK");
                Object intVal = field.get(null);
                if (intVal != null) {
                    Method getMethod = intVal.getClass().getMethod("get");
                    int threshold = (int) getMethod.invoke(intVal);
                    return threshold > 0;
                }
            } catch (Throwable ignored) {
            }
        }
        return FarmingConfig.PREVENT_TOOL_BREAKING.get();
    }

    public static float getFoodExhaustion(ServerPlayer player) {
        if (isFTBUltimineLoaded() && player != null) {
            try {
                Class<?> configClass = Class.forName("dev.ftb.mods.ftbultimine.config.FTBUltimineServerConfig");
                Method getExhaustion = configClass.getMethod("getExhaustionPerBlock", ServerPlayer.class);
                double exhaustion = (double) getExhaustion.invoke(null, player);
                return (float) exhaustion;
            } catch (Throwable ignored) {
            }
        }
        return FarmingConfig.EXHAUSTION_PER_BLOCK.get().floatValue();
    }

    /**
     * Applies cooldown and XP costs according to FTB Ultimine's configuration.
     */
    public static void applyPostUltimineCosts(ServerPlayer player, int count) {
        if (!isFTBUltimineLoaded() || player == null || count <= 0) {
            return;
        }
        try {
            // 1. Set Cooldown
            Class<?> trackerClass = Class.forName("dev.ftb.mods.ftbultimine.CooldownTracker");
            Method setCooldown = trackerClass.getMethod("setLastUltimineTime", Player.class, long.class);
            setCooldown.invoke(null, player, System.currentTimeMillis());

            // 2. XP Cost
            if (!player.isCreative()) {
                Class<?> ftbClass = Class.forName("dev.ftb.mods.ftbultimine.FTBUltimine");
                Method getInstanceMethod = ftbClass.getMethod("getInstance");
                Object instance = getInstanceMethod.invoke(null);
                if (instance != null) {
                    Method getPlayerDataMethod = ftbClass.getMethod("getOrCreatePlayerData", Player.class);
                    Object data = getPlayerDataMethod.invoke(instance, player);
                    if (data != null) {
                        int extraBlocks = Math.max(0, count - 1);
                        Method addPendingXP = data.getClass().getMethod("addPendingXPCost", ServerPlayer.class, int.class);
                        addPendingXP.invoke(data, player, extraBlocks);
                        Method takePendingXP = data.getClass().getMethod("takePendingXP", ServerPlayer.class);
                        takePendingXP.invoke(data, player);
                    }
                }
            }
        } catch (Throwable t) {
            VeinFarmingMod.LOGGER.debug("Failed to apply post FTB Ultimine costs", t);
        }
    }
}
