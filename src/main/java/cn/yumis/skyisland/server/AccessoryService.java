package cn.yumis.skyisland.server;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.attribute.ModAttributes;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
        if(CATALOG.size()!=25)throw new IllegalStateException("饰品目录必须为25件");
    }
    private static void special(String id,String name,String effect,String icon,boolean passive){CATALOG.put(id,new Entry(id,name,effect,icon,passive,null,0,null));}
    private static void bonus(String id,String name,String effect,String icon,Holder<Attribute> attr,double amount,AttributeModifier.Operation op){CATALOG.put(id,new Entry(id,name,effect,icon,false,attr,amount,op));}
    private final SkyServer runtime;
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
            if(!entry.passive()&&current.activeAccessories.size()<Math.clamp(current.accessorySlots,3,CATALOG.size()))current.activeAccessories.add(id);
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
        List<String> candidates=new ArrayList<>(CATALOG.keySet());candidates.removeAll(person.unlockedAccessories);
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
        if(!person.activeAccessories.contains(id)&&person.activeAccessories.size()>=Math.clamp(person.accessorySlots,3,CATALOG.size()))throw new IllegalArgumentException("已达到当前饰品栏位上限");
        runtime.change(player,state->{var active=state.players.get(player.getUUID()).activeAccessories;if(!active.remove(id))active.add(id);},()->apply(player,runtime.state().players.get(player.getUUID())));
    }
    public static boolean active(SkyServer runtime,ServerPlayer player,String id){
        IslandState.Person person=runtime.state().players.get(player.getUUID());
        return person!=null&&person.activeAccessories.contains(id)&&person.unlockedAccessories.contains(id);
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
            boolean enabled=person!=null&&person.activeAccessories.contains(entry.id())&&person.unlockedAccessories.contains(entry.id());
            if(enabled){
                if(!instance.hasModifier(id))instance.addOrUpdateTransientModifier(new AttributeModifier(id,entry.amount(),entry.operation()));
            }else if(instance.hasModifier(id))instance.removeModifier(id);
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
