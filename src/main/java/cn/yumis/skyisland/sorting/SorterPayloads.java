package cn.yumis.skyisland.sorting;

import cn.yumis.skyisland.YumisSkyIsland;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class SorterPayloads {
    private SorterPayloads() {}
    public record Operation(String device,long revision,String action) implements CustomPacketPayload {
        public static final Type<Operation> TYPE=new Type<>(YumisSkyIsland.id("sorter_operation"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Operation> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.device,36);b.writeLong(p.revision);b.writeUtf(p.action,24);},b->new Operation(b.readUtf(36),b.readLong(),b.readUtf(24)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(YumisSkyIsland.id("sorter_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,16384),b->new Snapshot(b.readUtf(16384)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
}
