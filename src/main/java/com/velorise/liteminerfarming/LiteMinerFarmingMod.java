package com.velorise.liteminerfarming;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
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
        LOGGER.info("LiteMiner Farming Addon initialized successfully!");
    }
}

