package com.velorise.liteminerfarming.network;

import com.velorise.liteminerfarming.LiteMinerFarmingMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload sent from client to server when smart planting mode is toggled on/off.
 */
public record ToggleSmartPlantPayload(boolean enabled) implements CustomPacketPayload {

    public static final Type<ToggleSmartPlantPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LiteMinerFarmingMod.MODID, "toggle_smart_plant")
    );

    public static final StreamCodec<FriendlyByteBuf, ToggleSmartPlantPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ToggleSmartPlantPayload::enabled,
            ToggleSmartPlantPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

