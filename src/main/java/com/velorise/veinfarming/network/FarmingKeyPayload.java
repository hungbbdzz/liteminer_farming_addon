package com.velorise.veinfarming.network;

import com.velorise.veinfarming.VeinFarmingMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload sent from client to server when the dedicated activation key is pressed or released.
 */
public record FarmingKeyPayload(boolean active) implements CustomPacketPayload {

    public static final Type<FarmingKeyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VeinFarmingMod.MODID, "key_state")
    );

    public static final StreamCodec<FriendlyByteBuf, FarmingKeyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, FarmingKeyPayload::active,
            FarmingKeyPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

