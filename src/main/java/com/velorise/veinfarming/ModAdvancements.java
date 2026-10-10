package com.velorise.veinfarming;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handles awarding custom in-game advancements for Vein Farming.
 */
public class ModAdvancements {

    public static final String ROOT = "root";
    public static final String BEDROCK_FLOWER = "is_it_bedrock";
    public static final String CIVIL_ENGINEER = "civil_engineer";
    public static final String CROP_ROTATION = "crop_rotation_genius";
    public static final String LORAX = "the_lorax_approves";
    public static final String SPEEDRUN_COMPOST = "speedrun_composting";
    public static final String LIVING_ON_EDGE = "living_on_the_edge";
    public static final String LAZY_FARMER = "lazy_farmer_3000";

    /**
     * Awards an advancement to the specified player.
     * Automatically unlocks the root tab first if awarding any child advancement.
     */
    public static void award(ServerPlayer player, String id) {
        if (player == null || player.server == null) {
            return;
        }
        try {
            if (!ROOT.equals(id)) {
                awardRoot(player);
            }

            ResourceLocation resLoc = ResourceLocation.fromNamespaceAndPath("vein_farming", id);
            AdvancementHolder holder = player.server.getAdvancements().get(resLoc);
            if (holder != null) {
                AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
                if (!progress.isDone()) {
                    for (String criterion : progress.getRemainingCriteria()) {
                        player.getAdvancements().award(holder, criterion);
                    }
                }
            }
        } catch (Exception ignored) {
            // Failsafe: avoid any gameplay disruption
        }
    }

    private static void awardRoot(ServerPlayer player) {
        ResourceLocation rootLoc = ResourceLocation.fromNamespaceAndPath("vein_farming", ROOT);
        AdvancementHolder rootHolder = player.server.getAdvancements().get(rootLoc);
        if (rootHolder != null) {
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(rootHolder);
            if (!progress.isDone()) {
                for (String criterion : progress.getRemainingCriteria()) {
                    player.getAdvancements().award(rootHolder, criterion);
                }
            }
        }
    }
}
