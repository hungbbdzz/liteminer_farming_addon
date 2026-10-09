package com.velorise.liteminerfarming.client;

import com.velorise.liteminerfarming.LiteMinerFarmingMod;
import com.velorise.liteminerfarming.network.FarmingKeyPayload;
import com.velorise.liteminerfarming.network.ToggleSmartPlantPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Handles client-side keybind detection, toggle events, and network syncing.
 */
@EventBusSubscriber(modid = LiteMinerFarmingMod.MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    private static boolean lastKeyActive = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            lastKeyActive = false;
            return;
        }

        // 1. Check toggle smart plant key
        while (ModKeyMappings.KEY_TOGGLE_SMART_PLANT.consumeClick()) {
            boolean newState = !ModKeyMappings.isSmartPlantEnabled();
            ModKeyMappings.setSmartPlantEnabled(newState);
            PacketDistributor.sendToServer(new ToggleSmartPlantPayload(newState));

            Component msg = newState
                    ? Component.translatable("message.liteminer_farming_addon.smart_plant.enabled").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("message.liteminer_farming_addon.smart_plant.disabled").withStyle(ChatFormatting.RED);
            mc.player.displayClientMessage(msg, true);
        }

        // 2. Check activation key state changes to sync with server
        boolean currentActive = ModKeyMappings.isKeyActive();
        if (currentActive != lastKeyActive) {
            lastKeyActive = currentActive;
            PacketDistributor.sendToServer(new FarmingKeyPayload(currentActive));
        }
    }

    @EventBusSubscriber(modid = LiteMinerFarmingMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(ModKeyMappings.KEY_ACTIVATE);
            event.register(ModKeyMappings.KEY_TOGGLE_SMART_PLANT);
        }
    }
}

