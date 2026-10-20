package cn.yumis.skyisland.server;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.effect.ModEffects;
import cn.yumis.skyisland.effect.Vulnerability;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;

/** One persistent nightly roll; temporary buffs are re-applied to loaded entities. */
public final class BloodMoonService {
    private final SkyServer runtime;
    public BloodMoonService(SkyServer runtime){this.runtime=runtime;}
    public boolean active(){return runtime.state().bloodMoonActive;}
    public void forceTonight(net.minecraft.server.level.ServerPlayer actor,Runnable after) {
        long target=cn.yumis.skyisland.core.MarketClock.tonight(runtime.server.overworld().getOverworldClockTime());
        long night=Math.floorDiv(target,24000);
        if(!runtime.tryChange(actor, SyncImpact.none(),next->{next.lastBloodMoonNight=night;next.bloodMoonActive=true;next.failedBloodMoonNights=0;},()->{
            AdminTimeService.setClock(runtime,target);
            for(var player:runtime.server.getPlayerList().getPlayers())if(player.level()==runtime.server.overworld())SkyServer.tell(player,"血月升起，怪物变得更危险了！");
            for(Entity entity:runtime.server.overworld().getAllEntities())if(entity instanceof LivingEntity living)apply(living);
            after.run();
        },error->{}))throw new IllegalStateException("数据正在保存，请稍后开启血月");
    }
    public void tick(){
        long clock=runtime.server.overworld().getOverworldClockTime();
        long night=Math.floorDiv(clock,24000);long dayTick=Math.floorMod(clock,24000);
        IslandState state=runtime.state();
        if(dayTick>=13000 && state.lastBloodMoonNight<night && runtime.store.available()){
            var settings=runtime.config.object("gameplay/blood_moon.toml");
            long chance=Math.min(10000L,(long)settings.get("initialChanceBps").getAsInt()+(long)state.failedBloodMoonNights*settings.get("missIncrementBps").getAsInt());
            boolean active=runtime.server.overworld().getRandom().nextInt(10000)<chance;
            runtime.change(null, SyncImpact.none(),next->{next.lastBloodMoonNight=night;next.bloodMoonActive=active;next.failedBloodMoonNights=active?0:Math.min(100,next.failedBloodMoonNights+1);},()->{
                if(active)for(var player:runtime.server.getPlayerList().getPlayers())if(player.level()==runtime.server.overworld())SkyServer.tell(player,"血月升起，怪物变得更危险了！");
            });
        }
        if(dayTick<13000 && state.bloodMoonActive && runtime.store.available())runtime.change(null, SyncImpact.none(),next->next.bloodMoonActive=false,()->{});
        if(runtime.tick%20==0){
            for(Entity entity:runtime.server.overworld().getAllEntities())if(entity instanceof LivingEntity living)apply(living);
            for(ServerPlayer player:runtime.server.getPlayerList().getPlayers())if(player.level()!=runtime.server.overworld())apply(player);
        }
    }
    public void apply(LivingEntity entity){
        strip(entity);
        boolean here=active() && entity.level() instanceof ServerLevel level && level==runtime.server.overworld();
        if(!here || entity instanceof cn.yumis.skyisland.npc.ResidentEntity){clear(entity);return;}
        if(entity instanceof ServerPlayer){
            entity.removeEffect(ModEffects.BLOOD_MOON_FRENZY);
            refresh(entity, ModEffects.BLOOD_MOON_RALLY);
            return;
        }
        if(entity instanceof Enemy){
            entity.removeEffect(ModEffects.BLOOD_MOON_RALLY);
            refresh(entity, ModEffects.BLOOD_MOON_FRENZY);
            Vulnerability.apply(entity, Vulnerability.BLOOD_MOON_LEVEL, 80);
            return;
        }
        clear(entity);
    }
    private static void refresh(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect){
        MobEffectInstance current=entity.getEffect(effect);
        if(current==null || current.getAmplifier()!=0 || current.getDuration()<40)
            entity.addEffect(new MobEffectInstance(effect, 80, 0, false, true, true));
    }
    private static void clear(LivingEntity entity){
        entity.removeEffect(ModEffects.BLOOD_MOON_RALLY);
        entity.removeEffect(ModEffects.BLOOD_MOON_FRENZY);
        MobEffectInstance vulnerability=entity.getEffect(ModEffects.VULNERABILITY);
        if(vulnerability!=null && vulnerability.getAmplifier()>=Vulnerability.BLOOD_MOON_LEVEL-1)entity.removeEffect(ModEffects.VULNERABILITY);
    }
    private static void strip(LivingEntity entity){
        remove(entity, Attributes.ATTACK_DAMAGE, "blood_moon_attack");
        remove(entity, Attributes.MOVEMENT_SPEED, "blood_moon_speed");
        remove(entity, Attributes.ATTACK_SPEED, "blood_moon_attack_speed");
        remove(entity, Attributes.MAX_HEALTH, "blood_moon_health");
    }
    private static void remove(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, String name){
        var instance=entity.getAttribute(attribute);
        Identifier id=YumisSkyIsland.id(name);
        if(instance!=null && instance.hasModifier(id))instance.removeModifier(id);
    }
}
