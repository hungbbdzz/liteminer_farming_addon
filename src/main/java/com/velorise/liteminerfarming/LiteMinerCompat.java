package com.velorise.liteminerfarming;

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
            LiteMinerFarmingMod.LOGGER.error("Failed to get blocks from LiteMiner Walker", t);
        }
        return Collections.emptyList();
    }

    public static int getEffectiveBlockLimit() {
        if (FarmingConfig.USE_LITEMINER_LIMIT.get() && isLiteMinerLoaded()) {
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
