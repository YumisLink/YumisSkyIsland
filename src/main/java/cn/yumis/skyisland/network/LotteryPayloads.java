package cn.yumis.skyisland.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class LotteryPayloads {
    private LotteryPayloads() {}
    public record Operation(String action,long expectedSequence,long rulesRevision,String context) implements CustomPacketPayload {
        public static final Type<Operation> TYPE=new Type<>(Identifier.fromNamespaceAndPath("yumis_sky_island","lottery_operation"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Operation> CODEC=StreamCodec.of(
            (b,v)->{b.writeUtf(v.action,24);b.writeVarLong(v.expectedSequence);b.writeVarLong(v.rulesRevision);b.writeUtf(v.context,512);},
            b->new Operation(b.readUtf(24),b.readVarLong(),b.readVarLong(),b.readUtf(512)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
}
