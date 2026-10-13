package cn.yumis.skyisland.block;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.server.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import java.util.UUID;
public final class ShippingChestEntity extends ChestBlockEntity {
    public String boxId = UUID.randomUUID().toString(), dimension = "";
    public UUID party;
    public long islandEpoch, saleSequence, activationSequence;
    public boolean powered, pending, locked;
    private boolean initialized;
    private boolean resumePending;
    public ShippingChestEntity(BlockPos pos, BlockState state) { super(ModBlocks.SHIPPING_TYPE,pos,state); }
    @Override protected Component getDefaultName() { return Component.translatable("block.yumis_sky_island.shipping_chest"); }
    public void freshPlacement() { boxId = UUID.randomUUID().toString(); party = null; saleSequence = 0; activationSequence = 0; initialized = false; setChanged(); }
    public boolean bound(SkyServer runtime) {
        IslandState.Party claim = runtime.claim(worldPosition);
        return party != null && claim != null && claim.id.equals(party) && claim.overworldEpoch == (dimension.equals("minecraft:overworld") ? islandEpoch : claim.overworldEpoch);
    }
    public void serverTick() {
        if (initialized || !(level instanceof ServerLevel world)) return;
        SkyServer runtime = SkyServer.get(world.getServer()); if (runtime == null) return;
        if (party == null) { var claim = runtime.claim(worldPosition); if (claim == null) return; party = claim.id; dimension = world.dimension().identifier().toString(); islandEpoch = world.dimension().equals(Level.OVERWORLD) ? claim.overworldEpoch : 0; }
        IslandState.Box saved = runtime.state().boxes.get(boxId);
        if (saved != null && saved.party.equals(party) && saved.dimension.equals(dimension) && saved.islandEpoch == islandEpoch && saved.x == worldPosition.getX() && saved.y == worldPosition.getY() && saved.z == worldPosition.getZ() && saved.saleSequence > saleSequence) {
            runtime.economy.restore(this,saved.contents); saleSequence = saved.saleSequence; activationSequence = Math.max(activationSequence,saved.activationSequence);
        }
        if(saved!=null && saved.activationSequence>=activationSequence)resumePending=false;
        powered = world.hasNeighborSignal(worldPosition); initialized = true; setChanged();
        if(resumePending&&bound(runtime)){resumePending=false;runtime.trades.enqueue(this);}
    }
    public void powerChanged() {
        if (!initialized || !(level instanceof ServerLevel world)) return;
        boolean signal = world.hasNeighborSignal(worldPosition), rising = !powered && signal; powered = signal; setChanged();
        SkyServer runtime = SkyServer.get(world.getServer());
        if (rising && runtime != null && bound(runtime) && !pending && !locked) { activationSequence++; runtime.trades.enqueue(this); }
    }
    @Override public boolean stillValid(Player player) { return !locked && super.stillValid(player) && (!(level instanceof ServerLevel world) || Protection.canBuild(world, player, worldPosition)); }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in); boxId = in.getStringOr("yumis_box_id", boxId); dimension = in.getStringOr("yumis_dimension", "");
        String owner = in.getStringOr("yumis_party", ""); try { party = owner.isEmpty() ? null : UUID.fromString(owner); } catch (IllegalArgumentException e) { party = null; }
        islandEpoch = in.getLongOr("yumis_island_epoch",0); saleSequence = in.getLongOr("yumis_sale_sequence",0); activationSequence = in.getLongOr("yumis_activation",0); powered = in.getBooleanOr("yumis_powered",false);resumePending=in.getBooleanOr("yumis_pending",false);initialized = false;
    }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out); out.putString("yumis_box_id",boxId); out.putString("yumis_dimension",dimension); if (party != null) out.putString("yumis_party",party.toString());
        out.putLong("yumis_island_epoch",islandEpoch); out.putLong("yumis_sale_sequence",saleSequence); out.putLong("yumis_activation",activationSequence); out.putBoolean("yumis_powered",powered);out.putBoolean("yumis_pending",pending||locked||resumePending);
    }
}
