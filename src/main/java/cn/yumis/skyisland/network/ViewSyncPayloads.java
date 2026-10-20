package cn.yumis.skyisland.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Conditional view queries. Business operations continue to use their authoritative protocols. */
public final class ViewSyncPayloads {
    private ViewSyncPayloads() {}
    public record Query(String json) implements CustomPacketPayload {
        public static final Type<Query> TYPE = new Type<>(Identifier.fromNamespaceAndPath("yumis_sky_island", "view_query"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Query> CODEC = StreamCodec.of(
            (b,v)->b.writeUtf(v.json,16384), b->new Query(b.readUtf(16384)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Reply(String json) implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(Identifier.fromNamespaceAndPath("yumis_sky_island", "view_reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC = StreamCodec.of(
            (b,v)->b.writeUtf(v.json,65536), b->new Reply(b.readUtf(65536)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
}
