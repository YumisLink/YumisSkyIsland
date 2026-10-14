package cn.yumis.skyisland.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Separate bounded task protocol; editor definitions cannot be stuffed into product IDs. */
public final class TaskPayloads {
    private TaskPayloads() {}
    public record Request(String action, String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("yumis_sky_island","task_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC = StreamCodec.of(
            (b,p) -> {b.writeUtf(p.action,24);b.writeUtf(p.json,65536);},
            b -> new Request(b.readUtf(24),b.readUtf(65536)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath("yumis_sky_island","task_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC = StreamCodec.of(
            (b,p) -> b.writeUtf(p.json,262144), b -> new Snapshot(b.readUtf(262144)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
}
