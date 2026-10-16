package cn.yumis.skyisland.server;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.attribute.ModAttributes;
import cn.yumis.skyisland.item.ModItems;
import cn.yumis.skyisland.item.BiomeTreasureBox;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;

/** Regional treasure is inserted before the vanilla catch is awarded. */
public final class FishingService {
    public record Drop(String id,Item item,int weight,boolean common) {}
    /** Probability is conditional on already rolling a treasure catch. */
    public record TreasureOutcome(Item item,double probability) {}
    private static final List<Drop> BASE=List.of(
        new Drop("sky_carp",ModItems.FISH.get("sky_carp"),40,true),
        new Drop("glowing_scales",ModItems.FISHING_MATERIALS.get("glowing_scales"),10,false),
        new Drop("nautilus_shell",Items.NAUTILUS_SHELL,6,false),
        new Drop("name_tag",Items.NAME_TAG,5,false),
        new Drop("saddle",Items.SADDLE,3,false));
    private static final Identifier LEVEL_BONUS=YumisSkyIsland.id("fisherman_level");
    private FishingService() {}
    /** Profession progress is derived from the durable personal state; command-set base values remain intact. */
    public static void applyLevel(SkyServer runtime,ServerPlayer player){
        int level=runtime.professions.level(runtime.state().players.get(player.getUUID()),"fisherman");
        var attribute=Objects.requireNonNull(player.getAttribute(ModAttributes.FISHING_POWER),"Player fishing power attribute");
        var modifier=attribute.getModifier(LEVEL_BONUS);
        if(modifier==null||modifier.amount()!=level||modifier.operation()!=AttributeModifier.Operation.ADD_VALUE){
            attribute.removeModifier(LEVEL_BONUS);
            attribute.addTransientModifier(new AttributeModifier(LEVEL_BONUS,level,AttributeModifier.Operation.ADD_VALUE));
        }
    }
    public static double power(SkyServer runtime,ServerPlayer player){
        applyLevel(runtime,player);
        return player.getAttributeValue(ModAttributes.FISHING_POWER);
    }
    /** The UI receives the same effective value and modifiers used by the actual fishing roll. */
    public static JsonObject snapshot(SkyServer runtime,ServerPlayer player){
        double total=power(runtime,player);
        var attribute=Objects.requireNonNull(player.getAttribute(ModAttributes.FISHING_POWER));
        JsonObject result=new JsonObject();
        result.addProperty("total",total);
        result.addProperty("base",attribute.getBaseValue());
        result.addProperty("maximum",ModAttributes.MAX_FISHING_POWER);
        JsonArray sources=new JsonArray();
        attribute.getModifiers().stream().sorted(Comparator.comparingInt((AttributeModifier modifier)->sourceOrder(modifier.id()))
                .thenComparing(modifier->modifier.id().toString())).forEach(modifier->{
            JsonObject source=new JsonObject();
            source.addProperty("id",modifier.id().toString());
            source.addProperty("name",sourceName(modifier.id()));
            source.addProperty("amount",modifier.amount());
            source.addProperty("operation",modifier.operation().name());
            sources.add(source);
        });
        result.add("sources",sources);
        return result;
    }
    private static int sourceOrder(Identifier id){
        if(id.equals(LEVEL_BONUS))return 0;
        if(id.equals(YumisSkyIsland.id("accessory_bobber")))return 1;
        if(id.equals(YumisSkyIsland.id("accessory_fisher_charm")))return 2;
        return 3;
    }
    private static String sourceName(Identifier id){
        if(id.equals(LEVEL_BONUS))return "渔夫等级";
        for(var entry:AccessoryService.CATALOG.values())
            if(id.equals(YumisSkyIsland.id("accessory_"+entry.id())))return entry.name();
        return "额外修正（"+id+"）";
    }
    public static double waitFactor(double power){return power<=100?1-power*0.0025:0.75-(power-100)*(0.5/900.0);}
    public static double treasureChance(SkyServer runtime,ServerPlayer player){
        double power=power(runtime,player);
        return Math.min(1,0.05*(1+power/1000.0)*(AccessoryService.active(runtime,player,"treasure_compass")?1.1:1));
    }
    public static void caught(ServerPlayer player,FishingHook hook,Collection<ItemStack> loot){
        if(loot.isEmpty()||hook.getHookedIn()!=null||hook.tickCount<1||!(hook.level() instanceof ServerLevel level))return;
        SkyServer runtime=SkyServer.get(level.getServer());if(runtime==null)return;
        String biome=level.getBiome(hook.blockPosition()).unwrapKey().map(key->key.identifier().getPath()).orElse("plains");
        String medium=hook instanceof FishingMediumAccess access?access.yumis$medium():"water";
        // Half of successful water catches use the regional pool; the other half keep vanilla loot.
        if(medium.equals("water")&&!player.getRandom().nextBoolean())return;
        ItemStack selected;
        if(player.getRandom().nextDouble()<treasureChance(runtime,player))
            selected=BiomeTreasureBox.create(biome,medium,runtime.bloodMoon.active() && level.dimension().equals(Level.OVERWORLD));
        else selected=ordinary(player,biome,medium);
        if(selected==null||selected.isEmpty())return;
        loot.clear();loot.add(selected);
    }
    private static ItemStack ordinary(ServerPlayer player,String biome,String medium){
        Item fish=medium.equals("lava")?ModItems.FISH.get("lava_eel"):medium.equals("void")?ModItems.FISH.get("void_fish"):
            biome.contains("desert")?ModItems.FISH.get("desert_lungfish"):
            biome.contains("frozen")||biome.contains("snowy")||biome.contains("ice")?ModItems.FISH.get("frost_trout"):
            biome.equals("mushroom_fields")?ModItems.FISH.get("mushroom_fish"):ModItems.FISH.get("sky_carp");
        return new ItemStack(fish);
    }
    /** The box rolls at opening, using current server settings and the opener's current power. */
    public static ItemStack openBox(SkyServer runtime,ServerPlayer player,BiomeTreasureBox.Kind kind){
        boolean end=Set.of("the_end","end_highlands","end_midlands","small_end_islands","end_barrens").contains(kind.biome());
        int eggBps=runtime.config.object("gameplay/blood_moon.toml").get("treasureEggBps").getAsInt();
        List<TreasureOutcome> outcomes=treasureOutcomes(kind.biome(),kind.medium(),power(runtime,player),kind.bloodMoon(),end,eggBps);
        double target=player.getRandom().nextDouble();
        for(TreasureOutcome outcome:outcomes){target-=outcome.probability();if(target<0)return new ItemStack(outcome.item());}
        return new ItemStack(outcomes.getLast().item());
    }
    public static List<TreasureOutcome> treasureOutcomes(String biome,String medium,double power,boolean bloodMoon,boolean end,int eggBps){
        List<TreasureOutcome> result=new ArrayList<>();
        List<Item> eggs=bloodMoon?bloodEggs(biome):List.of();
        double eggChance=eggs.isEmpty()?0:Math.clamp(eggBps/10000.0,0,1);
        if(eggChance>0){
            for(Item egg:eggs)result.add(new TreasureOutcome(egg,eggChance/eggs.size()));
        }
        double elytraChance=end?0.0001:0;
        if(elytraChance>0)result.add(new TreasureOutcome(Items.ELYTRA,(1-eggChance)*elytraChance));
        List<Drop> pool=treasurePool(biome,medium);
        double total=0,multiplier=1+9*power/1000.0;
        for(Drop drop:pool)total+=drop.weight()*(drop.common()?1:multiplier);
        for(Drop drop:pool)result.add(new TreasureOutcome(drop.item(),(1-eggChance)*(1-elytraChance)*drop.weight()*(drop.common()?1:multiplier)/total));
        return result;
    }
    private static List<Drop> treasurePool(String biome,String medium){
        List<Drop> pool=new ArrayList<>(BASE);
        if(medium.equals("lava")){pool.add(new Drop("lava_eel",ModItems.FISH.get("lava_eel"),32,false));pool.add(new Drop("ash_pearl",ModItems.FISHING_MATERIALS.get("ash_pearl"),17,false));pool.add(drop(Items.MAGMA_CREAM,15));}
        if(medium.equals("void")){pool.add(new Drop("void_fish",ModItems.FISH.get("void_fish"),32,false));pool.add(new Drop("void_fragment",ModItems.FISHING_MATERIALS.get("void_fragment"),16,false));pool.add(drop(Items.CHORUS_FLOWER,12));}
        switch(biome){
            case "desert" -> {pool.add(drop(Items.CACTUS,30));pool.add(drop(Items.DEAD_BUSH,8));}
            case "forest", "flower_forest", "plains", "sunflower_plains", "swamp" -> pool.add(drop(Items.OAK_SAPLING,24));
            case "birch_forest", "old_growth_birch_forest" -> pool.add(drop(Items.BIRCH_SAPLING,28));
            case "dark_forest" -> pool.add(drop(Items.DARK_OAK_SAPLING,28));
            case "pale_garden" -> pool.add(drop(Items.PALE_OAK_SAPLING,28));
            case "cherry_grove" -> pool.add(drop(Items.CHERRY_SAPLING,28));
            case "jungle", "sparse_jungle", "bamboo_jungle" -> {pool.add(drop(Items.JUNGLE_SAPLING,25));pool.add(drop(Items.BAMBOO,8));}
            case "savanna", "savanna_plateau", "windswept_savanna" -> pool.add(drop(Items.ACACIA_SAPLING,28));
            case "taiga", "snowy_taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "grove" -> pool.add(drop(Items.SPRUCE_SAPLING,28));
            case "mangrove_swamp" -> pool.add(drop(Items.MANGROVE_PROPAGULE,28));
            case "meadow", "wooded_badlands" -> pool.add(drop(Items.OAK_SAPLING,20));
            case "windswept_forest" -> {pool.add(drop(Items.OAK_SAPLING,14));pool.add(drop(Items.SPRUCE_SAPLING,14));}
            case "lush_caves" -> {pool.add(drop(Items.AZALEA,18));pool.add(drop(Items.FLOWERING_AZALEA,8));}
            case "snowy_plains", "ice_spikes", "frozen_ocean", "deep_frozen_ocean", "frozen_river", "frozen_peaks", "jagged_peaks" -> {pool.add(drop(Items.BLUE_ICE,9));pool.add(drop(Items.PACKED_ICE,20));pool.add(drop(Items.SPRUCE_SAPLING,12));}
            case "mushroom_fields" -> {pool.add(drop(Items.RED_MUSHROOM,16));pool.add(drop(Items.BROWN_MUSHROOM,16));pool.add(new Drop("mushroom_fish",ModItems.FISH.get("mushroom_fish"),15,false));}
            case "warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean" -> {pool.add(drop(Items.TUBE_CORAL,10));pool.add(new Drop("coral_fragment",ModItems.FISHING_MATERIALS.get("coral_fragment"),18,false));}
            case "warped_forest" -> pool.add(drop(Items.WARPED_FUNGUS,26));
            case "crimson_forest" -> pool.add(drop(Items.CRIMSON_FUNGUS,26));
            case "basalt_deltas", "nether_wastes" -> pool.add(new Drop("ash_pearl",ModItems.FISHING_MATERIALS.get("ash_pearl"),17,false));
            case "the_end", "end_highlands", "end_midlands", "small_end_islands", "end_barrens" -> {pool.add(drop(Items.CHORUS_FLOWER,20));pool.add(new Drop("void_fragment",ModItems.FISHING_MATERIALS.get("void_fragment"),10,false));}
            default -> {
                if(biome.contains("ocean"))pool.add(drop(Items.PRISMARINE_SHARD,18));
                else if(biome.contains("badlands"))pool.add(drop(Items.RED_SAND,25));
            }
        }
        return pool;
    }
    private static Drop drop(Item item,int weight){return new Drop(BuiltInRegistries.ITEM.getKey(item).toString(),item,weight,false);}
    public static boolean validLava(ServerLevel level,BlockPos pos){
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            BlockPos at=pos.offset(dx,0,dz);
            var fluid=level.getFluidState(at);
            if(!fluid.is(FluidTags.LAVA)||!fluid.isSource()||!level.getBlockState(at.above()).isAir())return false;
        }
        return true;
    }
    public static boolean validVoid(ServerLevel level,BlockPos pos){
        if(pos.getY()<=level.getMinY()+2)return false;
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)
            for(int y=pos.getY();y>=level.getMinY();y--)
                if(!level.getBlockState(new BlockPos(pos.getX()+dx,y,pos.getZ()+dz)).isAir())return false;
        return true;
    }
    private static Item bloodEgg(ServerPlayer player,String biome){
        List<Item> eggs=bloodEggs(biome);
        return eggs.get(player.getRandom().nextInt(eggs.size()));
    }
    private static List<Item> bloodEggs(String biome){
        if(biome.equals("mushroom_fields"))return List.of();
        return biome.equals("desert")?List.of(Items.HUSK_SPAWN_EGG,Items.SPIDER_SPAWN_EGG):
            biome.equals("deep_dark")?List.of(Items.WARDEN_SPAWN_EGG):
            biome.equals("pale_garden")?List.of(Items.CREAKING_SPAWN_EGG,Items.SPIDER_SPAWN_EGG):
            biome.contains("snow")||biome.contains("frozen")||biome.contains("ice")?List.of(Items.STRAY_SPAWN_EGG,Items.SPIDER_SPAWN_EGG):
            biome.contains("swamp")?List.of(Items.BOGGED_SPAWN_EGG,Items.SLIME_SPAWN_EGG):
            biome.contains("ocean")||biome.equals("river")?List.of(Items.DROWNED_SPAWN_EGG,Items.SPIDER_SPAWN_EGG):
            List.of(Items.ZOMBIE_SPAWN_EGG,Items.SKELETON_SPAWN_EGG,Items.SPIDER_SPAWN_EGG);
    }
}
