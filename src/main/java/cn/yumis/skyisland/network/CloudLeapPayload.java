package cn.yumis.skyisland.network;

import cn.yumis.skyisland.YumisSkyIsland;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The client selects the mode; the server controls direction, strength and cooldown. */
public record CloudLeapPayload(boolean vertical) implements CustomPacketPayload {
    public static final Type<CloudLeapPayload> TYPE = new Type<>(YumisSkyIsland.id("cloud_leap"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CloudLeapPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBoolean(payload.vertical()),
            buffer -> new CloudLeapPayload(buffer.readBoolean()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
