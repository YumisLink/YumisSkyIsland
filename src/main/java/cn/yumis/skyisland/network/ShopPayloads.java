package cn.yumis.skyisland.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public final class ShopPayloads {
    private ShopPayloads() {}
    public record Request(String action, String product, int quantity) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("yumis_sky_island", "request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of((b, p) -> { b.writeUtf(p.action, 32); b.writeUtf(p.product, 256); b.writeVarInt(p.quantity); }, b -> new Request(b.readUtf(32), b.readUtf(256), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath("yumis_sky_island", "snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of((b, p) -> b.writeUtf(p.json, 262144), b -> new Snapshot(b.readUtf(262144)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Purchase(String product, int quantity, String shop, long marketEpoch,
                           java.util.UUID party, long islandEpoch) implements CustomPacketPayload {
        public static final Type<Purchase> TYPE = new Type<>(Identifier.fromNamespaceAndPath("yumis_sky_island", "purchase"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Purchase> CODEC = StreamCodec.of(
            (b,p) -> { b.writeUtf(p.product,256); b.writeVarInt(p.quantity); b.writeUtf(p.shop,16); b.writeVarLong(p.marketEpoch); b.writeUUID(p.party); b.writeVarLong(p.islandEpoch); },
            b -> new Purchase(b.readUtf(256),b.readVarInt(),b.readUtf(16),b.readVarLong(),b.readUUID(),b.readVarLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
