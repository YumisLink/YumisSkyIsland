package cn.yumis.skyisland.npc;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.core.ResidentRules;
import cn.yumis.skyisland.server.SkyServer;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.*;

/** A real mob with a player model, never a ServerPlayer or an authenticated account. */
public final class ResidentEntity extends PathfinderMob {
    private static final EntityDataAccessor<ResolvableProfile> PROFILE=SynchedEntityData.defineId(ResidentEntity.class,EntityDataSerializers.RESOLVABLE_PROFILE);
    public UUID partyId,residentId;
    public long islandEpoch;
    private int movementClock;
    public ResidentEntity(EntityType<? extends ResidentEntity> type,Level level){super(type,level);setPersistenceRequired();setCanPickUpLoot(false);if(getNavigation() instanceof GroundPathNavigation ground)ground.setCanOpenDoors(true);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(PROFILE,Mannequin.DEFAULT_PROFILE);}
    public ResolvableProfile profile(){return entityData.get(PROFILE);}
    public void profile(ResolvableProfile value){entityData.set(PROFILE,value);}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(1,new OpenDoorGoal(this,true));goalSelector.addGoal(5,new LookAtPlayerGoal(this,Player.class,8));goalSelector.addGoal(6,new RandomLookAroundGoal(this));}
    public void bind(UUID party,cn.yumis.skyisland.data.IslandState.Resident data){partyId=party;residentId=data.id;islandEpoch=data.islandEpoch;((AttachmentTarget)this).setAttached(YumisSkyIsland.ENTITY_PARTY,party.toString());setCustomNameVisible(true);setCustomName(Component.literal("NPC · "+ResidentRules.name(data.job)+" · "+(data.account.isBlank()?"未绑定":data.account)));}
    @Override protected void addAdditionalSaveData(ValueOutput out){super.addAdditionalSaveData(out);out.putString("ResidentParty",partyId==null?"":partyId.toString());out.putString("ResidentId",residentId==null?"":residentId.toString());out.putLong("ResidentEpoch",islandEpoch);}
    @Override protected void readAdditionalSaveData(ValueInput in){super.readAdditionalSaveData(in);try{partyId=UUID.fromString(in.getStringOr("ResidentParty",""));residentId=UUID.fromString(in.getStringOr("ResidentId",""));}catch(IllegalArgumentException ignored){partyId=null;residentId=null;}islandEpoch=in.getLongOr("ResidentEpoch",0);}
    @Override public boolean canBeLeashed(){return false;}
    @Override public boolean startRiding(Entity vehicle,boolean force,boolean teleport){return false;}
    @Override public boolean canTeleport(Level from,Level to){return false;}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean shouldDropExperience(){return false;}
    @Override protected boolean shouldDropLoot(ServerLevel level){return false;}
    @Override protected void dropAllDeathLoot(ServerLevel level,DamageSource source){}
    @Override public void die(DamageSource source){if(level() instanceof ServerLevel world){var rt=SkyServer.get(world.getServer());if(rt!=null)rt.residents.died(this);}super.die(source);}
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand){if(player instanceof net.minecraft.server.level.ServerPlayer online){var rt=SkyServer.get(online.level().getServer());if(rt!=null)try{rt.residents.open(online,residentId);}catch(IllegalArgumentException error){SkyServer.tell(online,error.getMessage());}}return InteractionResult.SUCCESS;}
    @Override protected void customServerAiStep(ServerLevel world){
        super.customServerAiStep(world);var rt=SkyServer.get(world.getServer());if(rt==null||!isAlive())return;
        if(getY()<-100){kill(world);return;}
        var data=rt.residents.record(this);if(data==null){discard();return;}
        if(++movementClock%40!=0)return;
        data.x=getBlockX();data.y=getBlockY();data.z=getBlockZ();
        var party=rt.state().parties.get(partyId);var house=party.settlement.houses.get(data.house);if(house==null){getNavigation().stop();if(isSleeping())stopSleeping();return;}
        BlockPos home=BlockPos.of(house.seed),bed=BlockPos.of(house.bed);setHomeTo(home,rt.residents.rules.radius);
        long time=Math.floorMod(world.getOverworldClockTime(),24000);boolean night=time>=13000&&time<23000;
        if(isSleeping()){if(!night||!world.getBlockState(bed).is(net.minecraft.tags.BlockTags.BEDS))stopSleeping();return;}
        if(night){if(distanceToSqr(bed.getX()+0.5,bed.getY()+0.5,bed.getZ()+0.5)<4){startSleeping(bed);return;}walk(rt,bed);return;}
        if(!getNavigation().isDone())return;
        BlockPos target=home.offset(random.nextInt(17)-8,random.nextInt(5)-2,random.nextInt(17)-8);
        if(getLastHurtByMob()!=null&&getLastHurtByMob().distanceToSqr(this)<100){double dx=getX()-getLastHurtByMob().getX(),dz=getZ()-getLastHurtByMob().getZ();target=blockPosition().offset((int)Math.signum(dx)*6,0,(int)Math.signum(dz)*6);}
        if(!rt.residents.allowed(this,blockPosition()))target=home;
        walk(rt,target);
    }
    private void walk(SkyServer rt,BlockPos target){
        if(!rt.residents.allowed(this,target)||!level().hasChunkAt(target))return;
        // Path creation builds a region around the mob; check it before vanilla may read its chunks.
        int margin=(int)Math.ceil(getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE))+16;
        for(int x=Math.floorDiv(getBlockX()-margin,16);x<=Math.floorDiv(getBlockX()+margin,16);x++)for(int z=Math.floorDiv(getBlockZ()-margin,16);z<=Math.floorDiv(getBlockZ()+margin,16);z++)if(!rt.server.overworld().hasChunk(x,z))return;
        Path path=getNavigation().createPath(target,1);if(path==null)return;
        for(int i=0;i<path.getNodeCount();i++){BlockPos pos=path.getNode(i).asBlockPos();if(!rt.residents.allowed(this,pos)||!level().hasChunkAt(pos))return;}
        getNavigation().moveTo(path,0.75);
    }
}
