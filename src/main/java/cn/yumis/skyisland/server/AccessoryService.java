package cn.yumis.skyisland.server;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.attribute.ModAttributes;
import cn.yumis.skyisland.core.AccessoryCraft;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.item.ModItems;
import cn.yumis.skyisland.shop.TradeService;
import com.google.gson.JsonArray;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.component.ResolvableProfile;

/** Personal unlocks and three active slots. Baits are owned passive unlocks. */
public final class AccessoryService {
    public record Entry(String id,String name,String effect,String icon,boolean passive,Holder<Attribute> attribute,double amount,AttributeModifier.Operation operation) {}
    public static final Map<String,Entry> CATALOG=new LinkedHashMap<>();
    static {
        special("lava_core","熔岩核心","造成伤害点燃目标5秒","magma_cream",false);
        bonus("speed_candy","极速糖","移动速度提高10%","sugar",Attributes.MOVEMENT_SPEED,0.10,AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        special("beheader","斩头者","玩家必掉头；有头生物1%掉头","skeleton_skull",false);
        special("treasure_compass","寻宝罗盘","宝藏出现概率提高10%","compass",false);
        bonus("bobber","幸运浮标","渔力增加25","fishing_rod",ModAttributes.FISHING_POWER,25,AttributeModifier.Operation.ADD_VALUE);
        special("chain_hammer","连锁锤","连锁采集上限增加8","iron_pickaxe",false);
        special("lava_bait","熔岩鱼饵","拥有后可以岩浆钓鱼","blaze_powder",true);
        special("void_bait","虚空鱼饵","拥有后可以虚空钓鱼","ender_pearl",true);
        bonus("iron_heart","铁心","护甲增加0.5","iron_ingot",Attributes.ARMOR,0.5,AttributeModifier.Operation.ADD_VALUE);
        bonus("cherry_charm","樱花护符","最大生命增加1","cherry_sapling",Attributes.MAX_HEALTH,1,AttributeModifier.Operation.ADD_VALUE);
        bonus("quick_clock","迅捷时钟","攻击速度提高5%","clock",Attributes.ATTACK_SPEED,0.05,AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        bonus("sharp_eye","锐眼","攻击力增加0.5","spyglass",Attributes.ATTACK_DAMAGE,0.5,AttributeModifier.Operation.ADD_VALUE);
        bonus("soft_feather","软羽","安全落地高度增加1","feather",Attributes.SAFE_FALL_DISTANCE,1,AttributeModifier.Operation.ADD_VALUE);
        bonus("sea_shell","海贝","氧气加成增加0.05","nautilus_shell",Attributes.OXYGEN_BONUS,0.05,AttributeModifier.Operation.ADD_VALUE);
        bonus("spring_sole","弹簧鞋垫","跨越高度增加0.1","slime_ball",Attributes.STEP_HEIGHT,0.1,AttributeModifier.Operation.ADD_VALUE);
        bonus("miner_glove","矿工手套","破坏方块速度提高5%","iron_pickaxe",Attributes.BLOCK_BREAK_SPEED,0.05,AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        bonus("swift_fins","迅游鳍","水中移动效率提高10%","prismarine_shard",Attributes.WATER_MOVEMENT_EFFICIENCY,0.1,AttributeModifier.Operation.ADD_VALUE);
        bonus("steady_boots","稳固靴","击退抗性增加0.05","leather_boots",Attributes.KNOCKBACK_RESISTANCE,0.05,AttributeModifier.Operation.ADD_VALUE);
        bonus("lucky_clover","幸运草","幸运值增加0.2","fern",Attributes.LUCK,0.2,AttributeModifier.Operation.ADD_VALUE);
        bonus("sturdy_belt","结实腰带","护甲韧性增加0.1","leather",Attributes.ARMOR_TOUGHNESS,0.1,AttributeModifier.Operation.ADD_VALUE);
        bonus("gold_fang","金牙","攻击击退增加0.1","gold_nugget",Attributes.ATTACK_KNOCKBACK,0.1,AttributeModifier.Operation.ADD_VALUE);
        bonus("frost_charm","霜晶","燃烧时长降低10%","blue_ice",Attributes.BURNING_TIME,-0.1,AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        bonus("wind_chime","风铃","移动速度提高3%","breeze_rod",Attributes.MOVEMENT_SPEED,0.03,AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        bonus("shadow_cape","影披风","潜行速度增加5%","black_wool",Attributes.SNEAKING_SPEED,0.05,AttributeModifier.Operation.ADD_VALUE);
        bonus("fisher_charm","渔夫护符","渔力增加10","cod",ModAttributes.FISHING_POWER,10,AttributeModifier.Operation.ADD_VALUE);
        special("night_pupil","夜明瞳","启用时获得夜视","ender_eye",false);
        special("scrap_magnet","拾荒磁铁","扩大掉落物拾取范围","iron_ingot",false);
        special("regen_leaf","回春叶","未满血时每8秒回复0.5心","pink_petals",false);
        special("study_pendant","学识坠","捡起经验球时额外获得20%经验","experience_bottle",false);
        bonus("jump_charm","跃起护符","跳跃力度提高10%","rabbit_foot",Attributes.JUMP_STRENGTH,0.10,AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        bonus("fall_stone","缓落石","摔落伤害降低10%","smooth_stone",Attributes.FALL_DAMAGE_MULTIPLIER,-0.10,AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        bonus(AccessoryCraft.FISHING_LINE,"鱼线","渔力 +35，咬钩后可收杆时间加倍","fishing_rod",ModAttributes.FISHING_POWER,35,AttributeModifier.Operation.ADD_VALUE);
        special(AccessoryCraft.TACKLE_BOX,"钓具箱","宝藏概率提高10%，水中地区渔获再判一次","chest",false);
        bonus(AccessoryCraft.ANGLER_BAG,"渔具袋","渔力 +35，收杆时间加倍，宝藏与地区渔获提升","bundle",ModAttributes.FISHING_POWER,35,AttributeModifier.Operation.ADD_VALUE);
        if(AccessoryCraft.drawable(CATALOG.keySet()).size()!=AccessoryCraft.DRAWABLE_COUNT||CATALOG.size()!=AccessoryCraft.DRAWABLE_COUNT+3)
            throw new IllegalStateException("饰品目录必须为31件可抽取和3件合成");
    }
    private static void special(String id,String name,String effect,String icon,boolean passive){CATALOG.put(id,new Entry(id,name,effect,icon,passive,null,0,null));}
    private static void bonus(String id,String name,String effect,String icon,Holder<Attribute> attr,double amount,AttributeModifier.Operation op){CATALOG.put(id,new Entry(id,name,effect,icon,false,attr,amount,op));}
    private final SkyServer runtime;
    private static final Map<UUID,Integer> studyRemainder=new HashMap<>();
    public AccessoryService(SkyServer runtime){this.runtime=runtime;}
    /** Consume the real held token together with the permanent personal unlock. */
    public void unlockFromItem(ServerPlayer player,InteractionHand hand,String id) {
        var party=runtime.parties.requireMember(player);
        runtime.trades.requireInventoryReady(player);
        if(party.resetInProgress)throw new IllegalStateException("岛屿重开期间暂停饰品解锁");
        Entry entry=CATALOG.get(id);Item expected=ModItems.ACCESSORY_ICONS.get(id);
        if(entry==null||expected==null)throw new IllegalArgumentException("饰品不存在");
        var person=runtime.state().players.get(player.getUUID());
        if(person==null)throw new IllegalArgumentException("个人空岛资料尚未就绪");
        if(person.unlockedAccessories.contains(id)){SkyServer.tell(player,"已经拥有"+entry.name()+"，物品已保留");return;}
        player.stopUsingItem();
        if(player.containerMenu!=player.inventoryMenu||!player.inventoryMenu.getCarried().isEmpty())player.closeContainer();
        int slot=hand==InteractionHand.OFF_HAND?Inventory.SLOT_OFFHAND:player.getInventory().getSelectedSlot();
        ItemStack held=player.getInventory().getItem(slot);
        if(held.isEmpty()||!held.is(expected))throw new IllegalArgumentException("手持饰品已变化，请重试");
        JsonArray inventory=runtime.economy.capture(player.getInventory());ItemStack remaining=held.copy();remaining.shrink(1);
        inventory.set(slot,runtime.economy.encode(remaining));UUID owner=player.getUUID();
        if(!runtime.trades.commitInventory(player,new TradeService.InventoryChange(inventory,TradeService.ExperienceChange.none(),null),state->{
            var current=state.players.get(owner);
            if(current==null||!current.unlockedAccessories.add(id))throw new IllegalArgumentException("该饰品已经解锁，物品未扣除");
            if(!entry.passive()&&AccessoryCraft.fits(current.activeAccessories,id,slots(current)))AccessoryCraft.enable(current.activeAccessories,id);
        },()->{},current->{
            var saved=runtime.state().players.get(owner);apply(current,saved);
            SkyServer.tell(current,"已永久解锁"+entry.name()+(entry.passive()?"，拥有后自动生效":saved.activeAccessories.contains(id)?"，已自动启用":"，激活栏位已满，可在饰品面板启用"));
        }))throw new IllegalStateException("数据正在保存，请稍后重试");
    }
    public void grantDraws(ServerPlayer target,int amount,ServerPlayer actor,Runnable after) {
        if(amount<1)throw new IllegalArgumentException("增加次数必须为正整数");
        var person=runtime.state().players.get(target.getUUID());
        if(person==null)throw new IllegalArgumentException("玩家尚无个人空岛资料");
        Math.addExact(person.pendingAccessoryDraws,amount);UUID owner=target.getUUID();
        if(!runtime.tryChange(actor,state->{var current=state.players.get(owner);current.pendingAccessoryDraws=Math.addExact(current.pendingAccessoryDraws,amount);},after,
            error->YumisSkyIsland.LOGGER.error("无法保存玩家 {} 的饰品抽奖次数",owner,error)))throw new IllegalStateException("数据正在保存，请稍后重试");
    }
    public static void awardMilestones(IslandState state,IslandState.Party party,int before,int after){
        if(after<=before)return;
        for(int level=((before/5)+1)*5;level<=after;level+=5){
            for(UUID member:party.members){
                IslandState.Person person=state.players.get(member);
                if(person!=null && person.rewardedAccessoryLevels.add(level))person.pendingAccessoryDraws++;
            }
        }
    }
    public void claim(ServerPlayer player){
        runtime.parties.requireMember(player);
        IslandState.Person person=runtime.state().players.get(player.getUUID());
        if(person==null||person.pendingAccessoryDraws<=0)throw new IllegalArgumentException("没有待领取的饰品机会");
        List<String> candidates=AccessoryCraft.drawable(CATALOG.keySet());candidates.removeAll(person.unlockedAccessories);
        if(candidates.isEmpty())throw new IllegalArgumentException("所有饰品已解锁，抽取次数保留");
        String chosen=candidates.get(player.getRandom().nextInt(candidates.size()));
        runtime.change(player,state->{var p=state.players.get(player.getUUID());p.pendingAccessoryDraws--;p.unlockedAccessories.add(chosen);},()->SkyServer.tell(player,"获得饰品："+CATALOG.get(chosen).name()));
    }
    public void toggle(ServerPlayer player,String id){
        runtime.parties.requireMember(player);
        Entry entry=CATALOG.get(id);
        IslandState.Person person=runtime.state().players.get(player.getUUID());
        if(entry==null||person==null||!person.unlockedAccessories.contains(id))throw new IllegalArgumentException("饰品尚未解锁");
        if(entry.passive()){SkyServer.tell(player,"鱼饵拥有后自动生效，无需占用激活栏位");return;}
        boolean disabling=person.activeAccessories.contains(id);
        if(!disabling&&!AccessoryCraft.fits(person.activeAccessories,id,slots(person)))throw new IllegalArgumentException("已达到当前饰品栏位上限");
        runtime.change(player,state->{
            var active=state.players.get(player.getUUID()).activeAccessories;
            if(!active.remove(id))AccessoryCraft.enable(active,id);
        },()->apply(player,runtime.state().players.get(player.getUUID())));
    }
    /** Unlock a crafted accessory without spending coins or removing ingredient unlocks. */
    public void craft(ServerPlayer player,String id){
        runtime.parties.requireMember(player);
        if(!AccessoryCraft.crafted(id))throw new IllegalArgumentException("该饰品不能合成");
        Entry entry=CATALOG.get(id);
        IslandState.Person person=runtime.state().players.get(player.getUUID());
        if(entry==null||person==null)throw new IllegalArgumentException("个人空岛资料尚未就绪");
        if(person.unlockedAccessories.contains(id))throw new IllegalArgumentException("已经拥有"+entry.name());
        List<String> missing=AccessoryCraft.missing(person.unlockedAccessories,id);
        if(!missing.isEmpty())throw new IllegalArgumentException("还缺少"+CATALOG.get(missing.getFirst()).name());
        boolean[] enabled={false};
        runtime.change(player,state->{
            var current=state.players.get(player.getUUID());
            if(current==null||current.unlockedAccessories.contains(id))throw new IllegalArgumentException("已经拥有"+entry.name());
            List<String> stillMissing=AccessoryCraft.missing(current.unlockedAccessories,id);
            if(!stillMissing.isEmpty())throw new IllegalArgumentException("还缺少"+CATALOG.get(stillMissing.getFirst()).name());
            current.unlockedAccessories.add(id);
            if(AccessoryCraft.fits(current.activeAccessories,id,slots(current))){AccessoryCraft.enable(current.activeAccessories,id);enabled[0]=true;}
        },()->{
            apply(player,runtime.state().players.get(player.getUUID()));
            SkyServer.tell(player,"已合成"+entry.name()+(enabled[0]?"，已自动启用":"，激活栏位已满，可在饰品面板启用"));
        });
    }
    public static boolean extendsBite(SkyServer runtime,ServerPlayer player){
        return active(runtime,player,AccessoryCraft.FISHING_LINE)||active(runtime,player,AccessoryCraft.ANGLER_BAG);
    }
    public static boolean tackle(SkyServer runtime,ServerPlayer player){
        return active(runtime,player,AccessoryCraft.TACKLE_BOX)||active(runtime,player,AccessoryCraft.ANGLER_BAG);
    }
    public static boolean treasureBonus(SkyServer runtime,ServerPlayer player){
        return active(runtime,player,"treasure_compass")||tackle(runtime,player);
    }
    private static int slots(IslandState.Person person){return Math.clamp(person.accessorySlots,3,CATALOG.size());}
    public static boolean active(SkyServer runtime,ServerPlayer player,String id){
        return equipped(runtime.state().players.get(player.getUUID()),id);
    }
    /** A finished accessory pauses its ingredients even if an older save still lists both as enabled. */
    private static boolean equipped(IslandState.Person person,String id){
        if(person==null||!person.unlockedAccessories.contains(id)||!person.activeAccessories.contains(id))return false;
        for(String other:person.activeAccessories)if(!other.equals(id)&&AccessoryCraft.closure(other).contains(id))return false;
        return true;
    }
    public static boolean owns(SkyServer runtime,ServerPlayer player,String id){
        IslandState.Person person=runtime.state().players.get(player.getUUID());
        return person!=null&&person.unlockedAccessories.contains(id);
    }
    public static void apply(ServerPlayer player,IslandState.Person person){
        for(Entry entry:CATALOG.values()){
            if(entry.attribute()==null)continue;
            var instance=player.getAttribute(entry.attribute());if(instance==null)continue;
            Identifier id=YumisSkyIsland.id("accessory_"+entry.id());
            boolean enabled=equipped(person,entry.id());
            if(enabled){
                if(!instance.hasModifier(id))instance.addOrUpdateTransientModifier(new AttributeModifier(id,entry.amount(),entry.operation()));
            }else if(instance.hasModifier(id))instance.removeModifier(id);
        }
    }
    /** Night vision, slow healing and item pull. Attribute accessories stay on the existing apply path. */
    public static void tick(ServerPlayer player){
        SkyServer runtime=SkyServer.get(player.level().getServer());
        if(runtime==null||!player.isAlive())return;
        boolean night=active(runtime,player,"night_pupil");
        var effect=player.getEffect(MobEffects.NIGHT_VISION);
        if(night){
            if(effect==null||(effect.isAmbient()&&effect.getAmplifier()==0&&effect.getDuration()<220))
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,320,0,true,false,true));
        }else if(effect!=null&&effect.isAmbient()&&effect.getAmplifier()==0&&effect.getDuration()<=320)player.removeEffect(MobEffects.NIGHT_VISION);
        if(active(runtime,player,"regen_leaf")&&!player.isCreative()&&!player.isSpectator()&&player.getHealth()>0&&player.getHealth()<player.getMaxHealth()&&player.tickCount%160==0)
            player.heal(1f);
        if(active(runtime,player,"scrap_magnet"))pullItems(runtime,player);
    }
    /** Twenty percent of experience actually granted by an orb. The leftover hundredths wait for the next orb or checkpoint. */
    public static int studyBonus(ServerPlayer player,int granted){
        if(granted<=0)return 0;
        SkyServer runtime=SkyServer.get(player.level().getServer());
        if(runtime==null||!active(runtime,player,"study_pendant"))return 0;
        UUID id=player.getUUID();
        int stored=studyRemainder.computeIfAbsent(id,key->{var person=runtime.state().players.get(key);return person==null?0:Math.max(0,person.studyXpRemainder);});
        long total=(long)granted*20L+stored;
        studyRemainder.put(id,(int)(total%100));
        long bonus=total/100;
        return bonus>Integer.MAX_VALUE?Integer.MAX_VALUE:(int)bonus;
    }
    public static void flushStudyRemainder(IslandState state){
        studyRemainder.forEach((id,value)->{var person=state.players.get(id);if(person!=null)person.studyXpRemainder=value;});
    }
    private static void pullItems(SkyServer runtime,ServerPlayer player){
        if(runtime.inventoryLocks.contains(player.getUUID())||!(player.level() instanceof ServerLevel level))return;
        var reach=player.getBoundingBox().inflate(2);
        for(ItemEntity item:level.getEntitiesOfClass(ItemEntity.class,reach,entity->entity.isAlive()&&!entity.getItem().isEmpty())){
            if(player.getBoundingBox().intersects(item.getBoundingBox()))continue;
            if(!Protection.canBuild(level,player,item.blockPosition()))continue;
            double dx=player.getX()-item.getX(),dy=player.getY()+0.6-item.getY(),dz=player.getZ()-item.getZ();
            double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(distance<0.05||distance>3.5)continue;
            item.setDeltaMovement(dx/distance*0.18,dy/distance*0.18,dz/distance*0.18);
        }
    }
    public static void damaged(LivingEntity victim,DamageSource source){
        if(!(source.getEntity() instanceof ServerPlayer player)||!(victim.level() instanceof ServerLevel level))return;
        SkyServer runtime=SkyServer.get(level.getServer());
        if(runtime!=null&&active(runtime,player,"lava_core"))victim.igniteForSeconds(5);
    }
    public static void defeated(LivingEntity victim,DamageSource source){
        if(!(source.getEntity() instanceof ServerPlayer killer)||!(victim.level() instanceof ServerLevel level))return;
        SkyServer runtime=SkyServer.get(level.getServer());
        if(runtime==null||!active(runtime,killer,"beheader"))return;
        ItemStack head;
        if(victim instanceof ServerPlayer dead){head=new ItemStack(Items.PLAYER_HEAD);head.set(DataComponents.PROFILE,ResolvableProfile.createResolved(dead.getGameProfile()));}
        else {
            if(killer.getRandom().nextInt(100)!=0)return;
            String type=BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).getPath();
            Item item=switch(type){case "skeleton" -> Items.SKELETON_SKULL;case "wither_skeleton" -> Items.WITHER_SKELETON_SKULL;case "zombie" -> Items.ZOMBIE_HEAD;case "creeper" -> Items.CREEPER_HEAD;case "piglin" -> Items.PIGLIN_HEAD;case "ender_dragon" -> Items.DRAGON_HEAD;default -> null;};
            if(item==null)return;head=new ItemStack(item);
        }
        victim.spawnAtLocation(level,head);
    }
}
