package cn.yumis.skyisland.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public final class ConfigEditPayloads {
    public record Request(String action,String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(Identifier.fromNamespaceAndPath("yumis_sky_island","config_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.action,24);b.writeUtf(p.json,32768);},b->new Request(b.readUtf(24),b.readUtf(32768)));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(Identifier.fromNamespaceAndPath("yumis_sky_island","config_edit_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,65536),b->new Snapshot(b.readUtf(65536)));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private ConfigEditPayloads(){}
}
