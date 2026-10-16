package cn.yumis.skyisland.network;

import cn.yumis.skyisland.YumisSkyIsland;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Requests contain intent only; targets, materials, limits and permissions are server-owned. */
public final class WandPayloads {
    private WandPayloads() {}
    public record Request(int action,int direction,int matching,boolean copyFacing,boolean random,
                          String dimension,long contextRevision) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(YumisSkyIsland.id("wand_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of(
                (b,p)->{b.writeVarInt(p.action);b.writeVarInt(p.direction);b.writeVarInt(p.matching);b.writeBoolean(p.copyFacing);b.writeBoolean(p.random);b.writeUtf(p.dimension,256);b.writeVarLong(p.contextRevision);},
                b->new Request(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readUtf(256),b.readVarLong()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Snapshot(String dimension,long contextRevision,long revision,String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(YumisSkyIsland.id("wand_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of(
                (b,p)->{b.writeUtf(p.dimension,256);b.writeVarLong(p.contextRevision);b.writeVarLong(p.revision);b.writeUtf(p.json,65536);},
                b->new Snapshot(b.readUtf(256),b.readVarLong(),b.readVarLong(),b.readUtf(65536)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
}
