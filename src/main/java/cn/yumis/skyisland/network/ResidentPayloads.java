package cn.yumis.skyisland.network;
import cn.yumis.skyisland.YumisSkyIsland;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public final class ResidentPayloads {
    private ResidentPayloads() {}
    public record Request(String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(YumisSkyIsland.id("resident_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((buffer,value)->buffer.writeUtf(value.json,8192),buffer->new Request(buffer.readUtf(8192)));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
}
