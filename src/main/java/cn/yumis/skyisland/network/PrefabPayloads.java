package cn.yumis.skyisland.network;

import cn.yumis.skyisland.YumisSkyIsland;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class PrefabPayloads {
    private PrefabPayloads() {}
    public record Request(String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(YumisSkyIsland.id("prefab_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,8192),b->new Request(b.readUtf(8192)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(YumisSkyIsland.id("prefab_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,65536),b->new Snapshot(b.readUtf(65536)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
}
