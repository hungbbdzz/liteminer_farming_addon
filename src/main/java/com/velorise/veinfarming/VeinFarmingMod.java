package com.velorise.veinfarming;

import com.velorise.veinfarming.client.StandaloneHighlightRenderer;
import com.velorise.veinfarming.network.FarmingKeyPayload;
import com.velorise.veinfarming.network.ToggleSmartPlantPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(VeinFarmingMod.MODID)
public class VeinFarmingMod {
    public static final String MODID = "vein_farming";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public VeinFarmingMod(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, FarmingConfig.SPEC);
        modBus.addListener(VeinFarmingMod::registerPayloads);

        NeoForge.EVENT_BUS.register(new FarmingEventHandler());

        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.register(new StandaloneHighlightRenderer());
            modContainer.registerExtensionPoint(
                    net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                    (container, screen) -> new com.velorise.veinfarming.client.FarmingConfigScreen(screen)
            );
        }

        LOGGER.info("Vein Farming: Universal Crop Harvester initialized successfully!");
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                FarmingKeyPayload.TYPE,
                FarmingKeyPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        FarmingEventHandler.setPlayerKeyActive(sp.getUUID(), payload.active());
                    }
                })
        );
        registrar.playToServer(
                ToggleSmartPlantPayload.TYPE,
                ToggleSmartPlantPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        FarmingEventHandler.setPlayerSmartPlantEnabled(sp.getUUID(), payload.enabled());
                    }
                })
        );
    }
}
