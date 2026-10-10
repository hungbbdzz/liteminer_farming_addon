package com.velorise.veinfarming;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

public class LiteMinerCompat {
    public static final String LITEMINER_MODID = "liteminer";

    public static boolean isLiteMinerLoaded() {
        return ModList.get().isLoaded(LITEMINER_MODID);
    }

    public static boolean isLiteMinerActive(ServerPlayer player) {
        if (!isLiteMinerLoaded()) {
            return false;
        }
        try {
            var state = com.iamkaf.liteminer.Liteminer.instance.getPlayerState(player);
            return state != null && state.getKeymappingState();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isLiteMinerClientActive() {
        if (!isLiteMinerLoaded()) {
            return false;
        }
        try {
            return ClientHandler.isClientActive();
        } catch (Throwable t) {
            return false;
        }
    }

    public static Collection<BlockPos> getClientSelectedBlocks(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player, BlockPos targetPos) {
        if (!isLiteMinerLoaded()) {
            return Collections.emptyList();
        }
        try {
            return ClientHandler.getClientSelectedBlocks(level, player, targetPos);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    private static class ClientHandler {
        private static boolean isClientActive() {
            try {
                return com.iamkaf.liteminer.LiteminerClient.isVeinMining()
                        || (com.iamkaf.liteminer.LiteminerClient.KEY_MAPPING != null
                            && com.iamkaf.liteminer.LiteminerClient.KEY_MAPPING.isDown());
            } catch (Throwable t) {
                return false;
            }
        }

        private static Collection<BlockPos> getClientSelectedBlocks(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player, BlockPos targetPos) {
            try {
                var set = com.iamkaf.liteminer.LiteminerClient.selectedBlocks;
                if (set != null && !set.isEmpty() && targetPos != null && (set.contains(targetPos) || set.contains(targetPos.above()) || set.contains(targetPos.below()))) {
                    return new java.util.ArrayList<>(set);
                }
                if (com.iamkaf.liteminer.LiteminerClient.shapes != null) {
                    var walker = com.iamkaf.liteminer.LiteminerClient.shapes.getCurrentItem();
                    if (walker != null && level != null && player != null && targetPos != null) {
                        var walked = walker.walk(level, player, targetPos);
                        if (walked != null && !walked.isEmpty()) {
                            return walked;
                        }
                    }
                }
                if (set != null && !set.isEmpty()) {
                    return new java.util.ArrayList<>(set);
                }
            } catch (Throwable ignored) {
            }
            return Collections.emptyList();
        }
    }

    /**
     * Gets the target blocks strictly determined by LiteMiner's active Walker/Shape.
     */
    public static Collection<BlockPos> getSelectedBlocks(ServerPlayer player, BlockPos targetPos) {
        if (!isLiteMinerLoaded()) {
            return Collections.emptyList();
        }
        try {
            var state = com.iamkaf.liteminer.Liteminer.instance.getPlayerState(player);
            if (state != null) {
                int shapeIndex = state.getShape();
                if (shapeIndex >= 0 && shapeIndex < com.iamkaf.liteminer.Liteminer.WALKERS.size()) {
                    var walker = com.iamkaf.liteminer.Liteminer.WALKERS.get(shapeIndex);
                    HashSet<BlockPos> walked = walker.walk(player.level(), player, targetPos);
                    if (walked != null && !walked.isEmpty()) {
                        return walked;
                    }
                }
            }
        } catch (Throwable t) {
            VeinFarmingMod.LOGGER.error("Failed to get blocks from LiteMiner Walker", t);
        }
        return Collections.emptyList();
    }

    public static int getEffectiveBlockLimit() {
        if (isLiteMinerLoaded()) {
            try {
                return com.iamkaf.liteminer.Liteminer.CONFIG.blockBreakLimit.get();
            } catch (Throwable ignored) {
            }
        }
        return FarmingConfig.MAX_BLOCKS.get();
    }

    public static boolean shouldPreventToolBreaking() {
        if (isLiteMinerLoaded()) {
            try {
                return com.iamkaf.liteminer.Liteminer.CONFIG.preventToolBreaking.get();
            } catch (Throwable ignored) {
            }
        }
        return FarmingConfig.PREVENT_TOOL_BREAKING.get();
    }

    public static float getFoodExhaustion() {
        if (isLiteMinerLoaded()) {
            try {
                if (com.iamkaf.liteminer.Liteminer.CONFIG.foodExhaustionEnabled.get()) {
                    return com.iamkaf.liteminer.Liteminer.CONFIG.foodExhaustion.get().floatValue();
                }
                return 0.0f;
            } catch (Throwable ignored) {
            }
        }
        return FarmingConfig.EXHAUSTION_PER_BLOCK.get().floatValue();
    }
}
