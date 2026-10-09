package com.velorise.liteminerfarming;

import com.velorise.liteminerfarming.client.StandaloneHighlightRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(LiteMinerFarmingMod.MODID)
public class LiteMinerFarmingMod {
    public static final String MODID = "liteminer_farming_addon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public LiteMinerFarmingMod(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, FarmingConfig.SPEC);
        NeoForge.EVENT_BUS.register(new FarmingEventHandler());

        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.register(new StandaloneHighlightRenderer());
        }

        LOGGER.info("Vein Farming: Universal Crop Harvester initialized successfully!");
    }
}

