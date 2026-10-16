package cn.yumis.skyisland.server;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.data.IslandState;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
        if(!runtime.tryChange(actor,next->{next.lastBloodMoonNight=night;next.bloodMoonActive=true;next.failedBloodMoonNights=0;},()->{
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
            runtime.change(null,next->{next.lastBloodMoonNight=night;next.bloodMoonActive=active;next.failedBloodMoonNights=active?0:Math.min(100,next.failedBloodMoonNights+1);},()->{
                if(active)for(var player:runtime.server.getPlayerList().getPlayers())if(player.level()==runtime.server.overworld())SkyServer.tell(player,"血月升起，怪物变得更危险了！");
            });
        }
        if(dayTick<13000 && state.bloodMoonActive && runtime.store.available())runtime.change(null,next->next.bloodMoonActive=false,()->{});
        if(runtime.tick%20==0)for(Entity entity:runtime.server.overworld().getAllEntities())if(entity instanceof LivingEntity living)apply(living);
    }
    public void apply(LivingEntity entity){
        if(entity instanceof cn.yumis.skyisland.npc.ResidentEntity)return;
        boolean active=active() && entity.level() instanceof ServerLevel level && level==runtime.server.overworld();
        var cfg=runtime.config.object("gameplay/blood_moon.toml");
        modifier(entity,Attributes.ATTACK_DAMAGE,"blood_moon_attack",active,cfg.get("attackBonusBps").getAsDouble()/10000);
        modifier(entity,Attributes.MOVEMENT_SPEED,"blood_moon_speed",active,cfg.get("movementBonusBps").getAsDouble()/10000);
        modifier(entity,Attributes.ATTACK_SPEED,"blood_moon_attack_speed",active,cfg.get("attackSpeedBonusBps").getAsDouble()/10000);
        var health=entity.getAttribute(Attributes.MAX_HEALTH);
        if(health!=null){
            boolean had=health.hasModifier(YumisSkyIsland.id("blood_moon_health"));
            boolean wanted=active&&entity instanceof Enemy;
            if(had!=wanted){
                float previousMaximum=entity.getMaxHealth();
                float previousHealth=entity.getHealth();
                modifier(entity,Attributes.MAX_HEALTH,"blood_moon_health",wanted,cfg.get("hostileHealthBonusBps").getAsDouble()/10000);
                if(previousMaximum>0 && previousHealth>0)
                    entity.setHealth(Math.min(entity.getMaxHealth(),previousHealth*entity.getMaxHealth()/previousMaximum));
            }
        }
    }
    private static void modifier(LivingEntity entity,Holder<Attribute> attr,String name,boolean enabled,double amount){
        var instance=entity.getAttribute(attr);if(instance==null)return;
        Identifier id=YumisSkyIsland.id(name);
        if(enabled){if(!instance.hasModifier(id))instance.addOrUpdateTransientModifier(new AttributeModifier(id,amount,AttributeModifier.Operation.ADD_MULTIPLIED_BASE));}
        else if(instance.hasModifier(id))instance.removeModifier(id);
    }
}
