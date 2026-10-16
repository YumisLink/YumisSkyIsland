package cn.yumis.skyisland.server;
import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.block.ShippingChestEntity;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.mixin.CompoundContainerAccessor;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

public final class Protection {
    private record Location(ServerLevel level,BlockPos pos,Player actor) { Location(ServerLevel level,BlockPos pos){this(level,pos,null);} }
    private static final Map<Container, Location> locations = new WeakHashMap<>();
    private static final ThreadLocal<Deque<Location>> sources = ThreadLocal.withInitial(ArrayDeque::new);
    private record Placement(Level level,Player player,Map<BlockPos,String> expected) {}
    private static final ThreadLocal<Deque<Placement>> placements=ThreadLocal.withInitial(ArrayDeque::new);
    public static void beginPlacement(Level level,Player player){if(level instanceof ServerLevel)placements.get().push(new Placement(level,player,new HashMap<>()));}
    public static void expectPlacement(Level level,BlockPos pos,net.minecraft.world.level.block.state.BlockState state){
        var stack=placements.get();if(stack.isEmpty()||stack.peek().level!=level)return;
        String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        stack.peek().expected.put(pos.immutable(),id);
        if(state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock||state.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock)stack.peek().expected.put(pos.above(),id);
        if(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock)stack.peek().expected.put(pos.relative(state.getValue(net.minecraft.world.level.block.BedBlock.FACING)),id);
    }
    public static void endPlacement(Level level){if(level instanceof ServerLevel&&!placements.get().isEmpty())placements.get().pop();}
    public static boolean playerPlacement(Level level,BlockPos pos,String id){var stack=placements.get();return !stack.isEmpty()&&stack.peek().level==level&&stack.peek().player instanceof ServerPlayer&&id.equals(stack.peek().expected.get(pos));}
    public static boolean admin(Player player) { return player instanceof ServerPlayer p && p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER); }
    public static boolean locked(Level level,BlockPos pos){if(cn.yumis.skyisland.prefab.PrefabService.locked(level,pos))return true;if(level instanceof ServerLevel world){var rt=SkyServer.get(world.getServer());return rt!=null&&rt.residents!=null&&rt.residents.locked(level,pos);}return false;}
    public static boolean locked(Container container){if(cn.yumis.skyisland.prefab.PrefabService.locked(container))return true;for(var location:positions(container)){var rt=SkyServer.get(location.level.getServer());if(rt!=null&&rt.residents!=null&&rt.residents.locked(container))return true;}return false;}
    public static boolean canBuild(ServerLevel level, Player player, BlockPos pos) {
        if(locked(level,pos))return false;
        SkyServer rt = SkyServer.get(level.getServer()); if (rt == null || admin(player)) return true;
        if (!(player instanceof ServerPlayer p) || rt.inventoryLocks.contains(p.getUUID())) return false;
        if(!rt.residents.boxAllowed(p,pos))return false;
        IslandState.Party claim = rt.claim(pos); if (claim == null || claim.resetInProgress) return false;
        if(!claim.members.contains(p.getUUID())&&!claim.trusted.contains(p.getUUID()))return false;
        if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof cn.yumis.skyisland.prefab.BlueprintCannonEntity cannon&&!rt.prefabs.canOperate(p,cannon))return false;
        return !level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof ShippingChestEntity box && box.locked);
    }
    public static boolean sameClaim(Level level, BlockPos source,BlockPos target) {
        if(locked(level,source)||locked(level,target))return false;
        if (!(level instanceof ServerLevel world)) return true; SkyServer rt = SkyServer.get(world.getServer()); if (rt == null) return true;
        IslandState.Party a = rt.claim(source), b = rt.claim(target);
        return a != null && a == b && !a.resetInProgress && !(world.getBlockEntity(target) instanceof ShippingChestEntity box && box.locked);
    }
    public static void push(Level level,BlockPos pos) { if (level instanceof ServerLevel world) sources.get().push(new Location(world,pos.immutable())); }
    public static void pushPlayer(Level level,BlockPos pos,Player player){if(level instanceof ServerLevel world)sources.get().push(new Location(world,pos.immutable(),player));}
    public static void pop(Level level) { if (level instanceof ServerLevel && !sources.get().isEmpty()) sources.get().pop(); }
    public static void clear() { sources.get().clear();placements.get().clear(); }
    public static boolean contextAllowed(Level level,BlockPos pos) { var stack = sources.get(); return stack.isEmpty() || stack.peek().level != level || (stack.peek().actor!=null?canBuild(stack.peek().level,stack.peek().actor,pos):sameClaim(level,stack.peek().pos,pos)); }
    public static UUID currentOrigin(SkyServer rt){var stack=sources.get();if(stack.isEmpty()||stack.peek().level.getServer()!=rt.server)return null;var party=rt.claim(stack.peek().pos);return party==null?null:party.id;}
    public static void locate(Container container, Level level, BlockPos pos) { if (container != null && level instanceof ServerLevel world) locations.put(container,new Location(world,pos.immutable())); }
    private static List<Location> positions(Container container) {
        if (container instanceof BlockEntity block && block.getLevel() instanceof ServerLevel level) return List.of(new Location(level,block.getBlockPos()));
        if (container instanceof Entity entity && entity.level() instanceof ServerLevel level) return List.of(new Location(level,entity.blockPosition()));
        if (container instanceof CompoundContainer compound) { var c = (CompoundContainerAccessor)compound; var p = new ArrayList<>(positions(c.yumis$first())); p.addAll(positions(c.yumis$second())); return p; }
        Location location = locations.get(container); return location == null ? List.of() : List.of(location);
    }
    public static boolean transfer(Container from,Container to) {
        if(locked(from)||locked(to))return false;
        if (from instanceof ShippingChestEntity first && first.locked || to instanceof ShippingChestEntity second && second.locked) return false;
        List<Location> a = positions(from), b = positions(to);
        if (a.isEmpty() || b.isEmpty()) return from == null; // Item pickup has a separate origin check.
        for (Location x : a) for (Location y : b) if (x.level != y.level || !sameClaim(x.level,x.pos,y.pos)) return false;
        return true;
    }
    public static boolean absorb(Container to,Entity item) {
        if(locked(to))return false;
        if (!(item.level() instanceof ServerLevel world) || SkyServer.get(world.getServer()) == null) return true;
        if (to instanceof ShippingChestEntity box && box.locked) return false;
        for (Location target : positions(to)) if (world != target.level || !sameClaim(world,item.blockPosition(),target.pos)) return false;
        return !positions(to).isEmpty();
    }
    public static boolean menuAllowed(AbstractContainerMenu menu,ServerPlayer player) {
        for (var slot : menu.slots) {
            if (slot.container instanceof Inventory) continue;
            for (Location location : positions(slot.container)) if (!canBuild(location.level,player,location.pos)) return false;
        }
        return true;
    }
    public static UUID entityParty(SkyServer rt,Entity entity) {
        if (entity instanceof ServerPlayer p) { var party = rt.parties.active(p.getUUID()); return party == null ? null : party.id; }
        if (entity == null) return null;
        String attached = ((AttachmentTarget)entity).getAttachedOrElse(YumisSkyIsland.ENTITY_PARTY, "");
        try { return attached.isEmpty() ? null : UUID.fromString(attached); } catch (IllegalArgumentException e) { return null; }
    }
    public static boolean explosion(ServerExplosion explosion,BlockPos pos) {
        if(locked(explosion.level(),pos))return false;
        SkyServer rt = SkyServer.get(explosion.level().getServer()); if (rt == null) return true;
        Entity indirect = explosion.getIndirectSourceEntity(), direct = explosion.getDirectSourceEntity();
        if (indirect instanceof Player p && admin(p)) return true;
        UUID origin = entityParty(rt,direct); if (origin == null) origin = entityParty(rt,indirect);
        var claim = rt.claim(pos); return claim != null && !claim.resetInProgress && claim.id.equals(origin) && !(explosion.level().getBlockEntity(pos) instanceof ShippingChestEntity box && box.locked);
    }
    public static boolean damage(ServerLevel level,Entity victim,net.minecraft.world.damagesource.DamageSource source){
        if(victim instanceof cn.yumis.skyisland.npc.ResidentEntity resident){var rt=SkyServer.get(level.getServer());if(rt==null)return true;Entity attacker=source.getEntity();if(attacker==null&&source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile)attacker=projectile.getOwner();if(attacker instanceof ServerPlayer player){var party=rt.parties.active(player.getUUID());return party!=null&&!party.resetInProgress&&party.id.equals(resident.partyId)&&party.members.contains(player.getUUID());}return true;}
        SkyServer rt=SkyServer.get(level.getServer());if(rt==null||source.getEntity()==null&&source.getDirectEntity()==null)return true;
        if(source.getEntity() instanceof Player p)return canBuild(level,p,victim.blockPosition());
        UUID owner=entityParty(rt,source.getDirectEntity());if(owner==null)owner=entityParty(rt,source.getEntity());var target=rt.claim(victim.blockPosition());
        return target!=null&&!target.resetInProgress&&target.id.equals(owner);
    }
}
