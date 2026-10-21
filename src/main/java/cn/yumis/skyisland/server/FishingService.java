package cn.yumis.skyisland.server;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.attribute.ModAttributes;
import cn.yumis.skyisland.core.FishingRules;
import cn.yumis.skyisland.item.ModItems;
import cn.yumis.skyisland.item.BiomeTreasureBox;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
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
        new Drop("saddle",Items.SADDLE,3,false),
        new Drop("enchanted_book",Items.ENCHANTED_BOOK,8,false),
        new Drop("fishing_rod",Items.FISHING_ROD,6,false),
        new Drop("bow",Items.BOW,6,false));
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
        if(id.equals(YumisSkyIsland.id("accessory_fishing_line")))return 3;
        if(id.equals(YumisSkyIsland.id("accessory_angler_bag")))return 4;
        return 5;
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
        return Math.min(1,0.05*(1+power/1000.0)*FishingRules.treasureMultiplier(AccessoryService.treasureBonus(runtime,player)));
    }
    public static void caught(ServerPlayer player,FishingHook hook,Collection<ItemStack> loot){
        if(loot.isEmpty()||hook.getHookedIn()!=null||hook.tickCount<1||!(hook.level() instanceof ServerLevel level))return;
        SkyServer runtime=SkyServer.get(level.getServer());if(runtime==null)return;
        String biome=level.getBiome(hook.blockPosition()).unwrapKey().map(key->key.identifier().getPath()).orElse("plains");
        String medium=hook instanceof FishingMediumAccess access?access.yumis$medium():"water";
        if(medium.equals("water")&&biome.equals("deep_dark")&&player.getRandom().nextDouble()<0.001){loot.clear();summonWarden(level,hook,player);return;}
        // Half of successful water catches use the regional pool. Tackle rolls the vanilla half once more.
        if(!FishingRules.regionalCatch(medium,AccessoryService.tackle(runtime,player),player.getRandom()::nextBoolean))return;
        ItemStack selected;
        if(player.getRandom().nextDouble()<treasureChance(runtime,player))
            selected=BiomeTreasureBox.create(stamp(level,biome,medium),medium,medium.equals("water")&&runtime.bloodMoon.active()&&level.dimension().equals(Level.OVERWORLD));
        else selected=ordinary(player,biome,medium);
        if(selected==null||selected.isEmpty())return;
        loot.clear();loot.add(selected);
    }
    private static ItemStack ordinary(ServerPlayer player,String biome,String medium){
        if(medium.equals("lava"))return new ItemStack(ModItems.FISH.get("lava_eel"));
        if(medium.equals("void"))return new ItemStack(ModItems.FISH.get("void_fish"));
        if((biome.equals("jungle")||biome.equals("sparse_jungle")||biome.equals("bamboo_jungle"))){
            if(player.getRandom().nextDouble()<0.08)return new ItemStack(Items.BAMBOO);
            if(player.getRandom().nextDouble()<0.05)return new ItemStack(Items.COCOA_BEANS);
        }
        Item fish=biome.contains("desert")?ModItems.FISH.get("desert_lungfish"):
            biome.contains("frozen")||biome.contains("snowy")||biome.contains("ice")?ModItems.FISH.get("frost_trout"):
            biome.equals("mushroom_fields")?ModItems.FISH.get("mushroom_fish"):ModItems.FISH.get("sky_carp");
        return new ItemStack(fish);
    }
    private static void summonWarden(ServerLevel level,FishingHook hook,ServerPlayer player){
        var warden=net.minecraft.world.entity.EntityTypes.WARDEN.create(level,net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if(warden==null)return;
        warden.snapTo(hook.getX(),hook.getY(),hook.getZ(),player.getYRot(),0);
        warden.finalizeSpawn(level,level.getCurrentDifficultyAt(warden.blockPosition()),net.minecraft.world.entity.EntitySpawnReason.TRIGGERED,null);
        ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget)warden).setAttached(YumisSkyIsland.FISHED_WARDEN,true);
        warden.increaseAngerAt(player,150,true);
        level.addFreshEntity(warden);
    }
    /** The box rolls at opening, using current server settings and the opener's current power. */
    public static ItemStack openBox(SkyServer runtime,ServerPlayer player,BiomeTreasureBox.Kind kind){
        boolean end=BiomeTreasureBox.END_BIOMES.contains(kind.biome());
        int eggBps=runtime.config.object("gameplay/blood_moon.toml").get("treasureEggBps").getAsInt();
        List<TreasureOutcome> outcomes=treasureOutcomes(kind.biome(),kind.medium(),power(runtime,player),kind.bloodMoon(),end,eggBps,BloodAccessories.chance(runtime,"accessoryBoxBps",2000));
        double target=player.getRandom().nextDouble();
        Item item=outcomes.getLast().item();
        for(TreasureOutcome outcome:outcomes){target-=outcome.probability();if(target<0){item=outcome.item();break;}}
        return enchant(player,item);
    }
    private static final List<Identifier> ROD_ENCHANTS=List.of(id("lure"),id("luck_of_the_sea"),id("unbreaking"),id("mending"));
    private static final List<Identifier> BOW_ENCHANTS=List.of(id("power"),id("punch"),id("flame"),id("unbreaking"),id("mending"),id("infinity"));
    private static Identifier id(String path){return Identifier.fromNamespaceAndPath("minecraft",path);}
    /** One enchantment, level 1 through its max. Books take any non-curse; rods and bows use a fixed set. */
    private static ItemStack enchant(ServerPlayer player,Item item){
        ItemStack stack=new ItemStack(item);
        boolean book=stack.is(Items.ENCHANTED_BOOK);
        List<Identifier> restricted=stack.is(Items.FISHING_ROD)?ROD_ENCHANTS:stack.is(Items.BOW)?BOW_ENCHANTS:null;
        if(!book&&restricted==null)return stack;
        Registry<Enchantment> registry=player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<Holder<Enchantment>> choices=new ArrayList<>();
        if(book)registry.listElements().forEach(holder->{if(!holder.is(EnchantmentTags.CURSE))choices.add(holder);});
        else for(Identifier enchantment:restricted)registry.get(ResourceKey.create(Registries.ENCHANTMENT,enchantment)).ifPresent(choices::add);
        if(choices.isEmpty())return stack;
        Holder<Enchantment> chosen=choices.get(player.getRandom().nextInt(choices.size()));
        int level=1+player.getRandom().nextInt(Math.max(1,chosen.value().getMaxLevel()));
        ItemEnchantments.Mutable stored=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(chosen,level);
        stack.set(book?DataComponents.STORED_ENCHANTMENTS:DataComponents.ENCHANTMENTS,stored.toImmutable());
        return stack;
    }
    public static List<TreasureOutcome> treasureOutcomes(String biome,String medium,double power,boolean bloodMoon,boolean end,int eggBps){
        return treasureOutcomes(biome,medium,power,bloodMoon,end,eggBps,2000);
    }
    public static List<TreasureOutcome> treasureOutcomes(String biome,String medium,double power,boolean bloodMoon,boolean end,int eggBps,int accessoryBps){
        List<TreasureOutcome> result=new ArrayList<>();
        double accessoryChance=bloodMoon?Math.clamp(accessoryBps/10000.0,0,1):0;
        if(accessoryChance>0){
            result.add(new TreasureOutcome(ModItems.ACCESSORY_ICONS.get(BloodAccessories.ANKLET),accessoryChance/2));
            result.add(new TreasureOutcome(ModItems.ACCESSORY_ICONS.get(BloodAccessories.SHARK),accessoryChance/2));
        }
        double original=1-accessoryChance;
        List<Item> eggs=bloodMoon?bloodEggs(biome):List.of();
        double eggChance=eggs.isEmpty()?0:Math.clamp(eggBps/10000.0,0,1);
        if(eggChance>0){
            for(Item egg:eggs)result.add(new TreasureOutcome(egg,original*eggChance/eggs.size()));
        }
        double elytraChance=end?0.0001:0;
        if(elytraChance>0)result.add(new TreasureOutcome(Items.ELYTRA,original*(1-eggChance)*elytraChance));
        List<Drop> pool=treasurePool(biome,medium);
        double total=0,multiplier=1+9*power/1000.0;
        for(Drop drop:pool)total+=drop.weight()*(drop.common()?1:multiplier);
        for(Drop drop:pool)result.add(new TreasureOutcome(drop.item(),original*(1-eggChance)*(1-elytraChance)*drop.weight()*(drop.common()?1:multiplier)/total));
        return result;
    }
    private static String stamp(ServerLevel level,String biome,String medium){
        if(medium.equals("water"))return ModItems.BIOME_BALLS.containsKey(biome)?biome:"plains";
        if(level.dimension().equals(Level.NETHER))return BiomeTreasureBox.NETHER_BIOMES.contains(biome)?biome:"nether_wastes";
        if(level.dimension().equals(Level.END))return "the_end";
        return "overworld";
    }
    private static String family(String biome,String medium){
        if(medium.equals("water"))return biome;
        if(BiomeTreasureBox.NETHER_BIOMES.contains(biome))return biome;
        if(BiomeTreasureBox.END_BIOMES.contains(biome))return "the_end";
        return "overworld";
    }
    private static List<Drop> treasurePool(String biome,String medium){
        List<Drop> pool=new ArrayList<>(BASE);
        if(medium.equals("lava")){pool.add(new Drop("lava_eel",ModItems.FISH.get("lava_eel"),32,false));pool.add(new Drop("ash_pearl",ModItems.FISHING_MATERIALS.get("ash_pearl"),17,false));pool.add(drop(Items.MAGMA_CREAM,15));}
        if(medium.equals("void")){pool.add(new Drop("void_fish",ModItems.FISH.get("void_fish"),32,false));pool.add(new Drop("void_fragment",ModItems.FISHING_MATERIALS.get("void_fragment"),16,false));pool.add(drop(Items.CHORUS_FLOWER,12));}
        if(!medium.equals("water")){
            switch(family(biome,medium)){
                case "overworld" -> { if(medium.equals("lava")){pool.add(drop(Items.COAL,18));pool.add(drop(Items.COBBLESTONE,16));pool.add(drop(Items.MAGMA_BLOCK,14));pool.add(drop(Items.OBSIDIAN,8));} else {pool.add(drop(Items.FEATHER,18));pool.add(drop(Items.STRING,14));pool.add(drop(Items.PHANTOM_MEMBRANE,12));pool.add(drop(Items.LEATHER,10));} }
                case "the_end" -> { if(medium.equals("lava")){pool.add(drop(Items.END_STONE,20));pool.add(drop(Items.CHORUS_FRUIT,16));pool.add(drop(Items.MAGMA_BLOCK,12));pool.add(drop(Items.OBSIDIAN,10));} else {pool.add(drop(Items.END_STONE,20));pool.add(drop(Items.CHORUS_FRUIT,18));pool.add(drop(Items.POPPED_CHORUS_FRUIT,12));pool.add(drop(Items.SHULKER_SHELL,4));} }
                case "nether_wastes" -> {pool.add(drop(Items.NETHERRACK,22));pool.add(drop(Items.QUARTZ,16));pool.add(drop(Items.GOLD_NUGGET,14));pool.add(drop(Items.GLOWSTONE_DUST,10));}
                case "soul_sand_valley" -> addSoul(pool);
                case "crimson_forest" -> {pool.add(drop(Items.CRIMSON_FUNGUS,26));pool.add(drop(Items.CRIMSON_NYLIUM,18));pool.add(drop(Items.WEEPING_VINES,14));pool.add(drop(Items.NETHER_WART,12));}
                case "warped_forest" -> {pool.add(drop(Items.WARPED_FUNGUS,26));pool.add(drop(Items.WARPED_NYLIUM,18));pool.add(drop(Items.TWISTING_VINES,14));pool.add(drop(Items.SHROOMLIGHT,8));}
                case "basalt_deltas" -> {pool.add(drop(Items.BASALT,22));pool.add(drop(Items.BLACKSTONE,18));pool.add(drop(Items.SMOOTH_BASALT,14));pool.add(drop(Items.MAGMA_BLOCK,12));}
                default -> {}
            }
            return pool;
        }
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
            case "frozen_ocean", "deep_frozen_ocean" -> {pool.add(drop(Items.BLUE_ICE,9));pool.add(drop(Items.PACKED_ICE,20));pool.add(drop(Items.SPRUCE_SAPLING,12));oceanRares(pool);}
            case "snowy_plains", "ice_spikes", "frozen_river", "frozen_peaks", "jagged_peaks" -> {pool.add(drop(Items.BLUE_ICE,9));pool.add(drop(Items.PACKED_ICE,20));pool.add(drop(Items.SPRUCE_SAPLING,12));}
            case "mushroom_fields" -> {pool.add(drop(Items.RED_MUSHROOM,16));pool.add(drop(Items.BROWN_MUSHROOM,16));pool.add(new Drop("mushroom_fish",ModItems.FISH.get("mushroom_fish"),15,false));}
            case "warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean" -> {pool.add(drop(Items.TUBE_CORAL,10));pool.add(new Drop("coral_fragment",ModItems.FISHING_MATERIALS.get("coral_fragment"),18,false));oceanRares(pool);}
            case "warped_forest" -> pool.add(drop(Items.WARPED_FUNGUS,26));
            case "crimson_forest" -> pool.add(drop(Items.CRIMSON_FUNGUS,26));
            case "basalt_deltas", "nether_wastes" -> pool.add(new Drop("ash_pearl",ModItems.FISHING_MATERIALS.get("ash_pearl"),17,false));
            case "soul_sand_valley" -> addSoul(pool);
            case "the_end", "end_highlands", "end_midlands", "small_end_islands", "end_barrens" -> {pool.add(drop(Items.CHORUS_FLOWER,20));pool.add(new Drop("void_fragment",ModItems.FISHING_MATERIALS.get("void_fragment"),10,false));}
            case "beach" -> {pool.add(drop(Items.SAND,20));pool.add(drop(Items.KELP,16));pool.add(drop(Items.SEA_PICKLE,12));pool.add(drop(Items.TURTLE_SCUTE,8));}
            case "deep_dark" -> {pool.add(drop(Items.SCULK,22));pool.add(drop(Items.COBBLED_DEEPSLATE,18));pool.add(drop(Items.SCULK_SENSOR,10));pool.add(drop(Items.ECHO_SHARD,6));}
            case "dripstone_caves" -> {pool.add(drop(Items.POINTED_DRIPSTONE,22));pool.add(drop(Items.DRIPSTONE_BLOCK,16));pool.add(drop(Items.RAW_COPPER,14));pool.add(drop(Items.COPPER_INGOT,8));}
            case "river" -> {pool.add(drop(Items.CLAY_BALL,20));pool.add(drop(Items.MUD,16));pool.add(drop(Items.LILY_PAD,14));pool.add(drop(Items.SUGAR_CANE,12));}
            case "snowy_beach" -> {pool.add(drop(Items.SNOW_BLOCK,18));pool.add(drop(Items.SAND,16));pool.add(drop(Items.KELP,14));pool.add(drop(Items.PACKED_ICE,12));}
            case "snowy_slopes" -> {pool.add(drop(Items.SNOW_BLOCK,20));pool.add(drop(Items.PACKED_ICE,16));pool.add(drop(Items.SPRUCE_SAPLING,12));pool.add(drop(Items.POWDER_SNOW_BUCKET,8));}
            case "stony_peaks" -> {pool.add(drop(Items.CALCITE,22));pool.add(drop(Items.STONE,16));pool.add(drop(Items.RAW_IRON,12));pool.add(drop(Items.EMERALD,6));}
            case "stony_shore" -> {pool.add(drop(Items.COBBLESTONE,20));pool.add(drop(Items.GRAVEL,16));pool.add(drop(Items.FLINT,14));pool.add(drop(Items.COAL,12));}
            case "sulfur_caves" -> {pool.add(drop(Items.SULFUR,22));pool.add(drop(Items.SULFUR_SPIKE,16));pool.add(drop(Items.SULFUR_BRICKS,12));pool.add(drop(Items.POTENT_SULFUR,6));}
            case "windswept_gravelly_hills" -> {pool.add(drop(Items.GRAVEL,22));pool.add(drop(Items.FLINT,16));pool.add(drop(Items.SPRUCE_SAPLING,12));pool.add(drop(Items.EMERALD,6));}
            case "windswept_hills" -> {pool.add(drop(Items.STONE,20));pool.add(drop(Items.GRAVEL,14));pool.add(drop(Items.RAW_IRON,12));pool.add(drop(Items.EMERALD,6));}
            default -> {
                if(biome.contains("ocean")){pool.add(drop(Items.PRISMARINE_SHARD,18));oceanRares(pool);}
                else if(biome.contains("badlands"))pool.add(drop(Items.RED_SAND,25));
            }
        }
        return pool;
    }
    private static void oceanRares(List<Drop> pool){pool.add(drop(Items.HEART_OF_THE_SEA,1));pool.add(drop(Items.DOLPHIN_SPAWN_EGG,1));}
    private static void addSoul(List<Drop> pool){pool.add(drop(Items.SOUL_SAND,24));pool.add(drop(Items.SOUL_SOIL,20));pool.add(drop(Items.BONE,14));pool.add(drop(Items.GHAST_TEAR,6));}
    private static Drop drop(Item item,int weight){return new Drop(BuiltInRegistries.ITEM.getKey(item).toString(),item,weight,false);}
    public static boolean validLava(ServerLevel level,BlockPos pos){
        var fluid=level.getFluidState(pos);
        if(!fluid.is(FluidTags.LAVA)||!fluid.isSource()||!level.getBlockState(pos.above()).isAir())return false;
        // Any 5x5 source surface containing the hook qualifies, including its edges and corners.
        for(int dx=-4;dx<=0;dx++)for(int dz=-4;dz<=0;dz++)
            if(validLavaArea(level,pos.offset(dx,0,dz)))return true;
        return false;
    }
    private static boolean validLavaArea(ServerLevel level,BlockPos corner){
        for(int dx=0;dx<5;dx++)for(int dz=0;dz<5;dz++){
            BlockPos at=corner.offset(dx,0,dz);
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
