package cn.yumis.skyisland;
import cn.yumis.skyisland.config.GameConfig;
import cn.yumis.skyisland.shop.ItemEconomy;
import cn.yumis.skyisland.world.VoidMarker;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class RegistryCatalogTest {
    static GameConfig config; static HolderLookup.Provider lookup;
    @BeforeAll static void setup() throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        if(YumisSkyIsland.CONFIG==null)new YumisSkyIsland().onInitialize();
        config=YumisSkyIsland.CONFIG;
        lookup=VanillaRegistries.createLookup();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
    }
    @Test void everyEnabledProductResolvesAndPreservesItsVariant() throws Exception {
        ItemEconomy economy=new ItemEconomy(config,lookup,123);
        var missing=new java.util.ArrayList<>(config.products.keySet());missing.removeAll(economy.entries.keySet());
        var example=ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,lookup),JsonParser.parseString("{\"id\":\"minecraft:dirt\",\"count\":1,\"components\":{}}"));
        assertTrue(missing.isEmpty(),"Unresolved "+missing.size()+" products, first: "+missing.stream().limit(10).toList()+"; dirt codec: "+example.error());
        for(var e:economy.entries.values()){
            var roundTrip=economy.decode(economy.encode(e.template()));assertTrue(ItemEconomy.matchesCanonical(roundTrip,e.template(),e.product().canonical().getAsJsonObject("componentPatch")),e.product().id());
            assertNotNull(economy.match(roundTrip),e.product().id());
        }
    }
    @Test void customPresetMarksAllThreeDimensionsAndHasEmptyBaseHeight() throws Exception {
        try(var in=getClass().getResourceAsStream("/data/yumis_sky_island/worldgen/world_preset/sky_island.json")){
            assertNotNull(in);var json=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8));
            var preset=WorldPreset.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,lookup),json).getOrThrow();
            var dimensions=preset.createWorldDimensions().dimensions();assertEquals(3,dimensions.size());
            for(var stem:dimensions.values()){var generator=stem.generator();assertTrue(generator instanceof VoidMarker marker&&marker.yumis$isVoid());assertEquals(-64,generator.getBaseHeight(0,0,Heightmap.Types.WORLD_SURFACE_WG,LevelHeightAccessor.create(-64,384),null));}
        }
    }
    @Test void minimumRetailAndBuybackUseTheSameDailyUnitPrice() {
        var stack=new ItemStack(net.minecraft.world.item.Items.COBBLESTONE);
        var value=ItemEconomy.fullValue(100,9000,stack,config.object("products/quality.json"),config.object("shop/enchantments.json"),config.minimumBaseCoins);
        assertEquals(cn.yumis.skyisland.core.Rational.of(100),value);
        assertEquals(1,value.divide(cn.yumis.skyisland.core.Rational.of(config.saleDivisor)).floorLongExact());
        var iron=ItemEconomy.fullValue(900,9333,new ItemStack(net.minecraft.world.item.Items.IRON_INGOT),config.object("products/quality.json"),config.object("shop/enchantments.json"),config.minimumBaseCoins);
        assertEquals(839,iron.floorLongExact());
        assertEquals(839,iron.divide(cn.yumis.skyisland.core.Rational.of(100)).multiply(cn.yumis.skyisland.core.Rational.of(100)).floorLongExact());
    }
    @Test void purchasePremiumNeverRaisesBuybackAndRemovedItemsAreUnpriced() {
        ItemEconomy economy=new ItemEconomy(config,lookup,123);
        var seed=economy.entries.get("minecraft:wheat_seeds");
        assertEquals(1000000,seed.product().buyMultiplierBps());
        var quote=economy.quote(seed.template());
        assertEquals(economy.full(seed,seed.template()).divide(cn.yumis.skyisland.core.Rational.of(100)),quote.normalSale());
        assertEquals(10000,cn.yumis.skyisland.core.Rational.of(economy.buyPrice(seed)).divide(quote.normalSale()).floorLongExact());
        assertNotNull(economy.quote(new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS)));
        assertTrue(economy.entries.get("minecraft:oak_planks").product().canBuy());
        assertTrue(economy.entries.get("minecraft:oak_log").product().canBuy());
        assertTrue(economy.entries.get("minecraft:cobblestone").product().canBuy());
        for(String id:java.util.List.of("diamond_pickaxe","iron_block","white_wool","cut_copper_stairs","stick","oak_fence_gate","oak_door","oak_button","stripped_oak_log","mossy_cobblestone_stairs","quartz_block","quartz_stairs","iron_bars","iron_door","stone_bricks","sandstone","smooth_stone")){
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("minecraft:"+id));
            assertNotEquals(net.minecraft.world.item.Items.AIR,item,id);
            assertNull(economy.match(new ItemStack(item)),id);
            assertNull(economy.quote(new ItemStack(item)),id);
        }
        for(String id:java.util.List.of("ancient_debris","end_portal_frame","budding_amethyst","netherite_upgrade_smithing_template"))
            assertTrue(economy.entries.get("minecraft:"+id).product().canBuy(),id);
        assertFalse(economy.entries.get("minecraft:netherite_scrap").product().canBuy());
        assertFalse(economy.entries.get("minecraft:netherite_ingot").product().canBuy());
        var string=economy.entries.get("minecraft:string").product();
        assertTrue(string.canBuy());assertEquals("rare",string.rarity());assertEquals(1000000,string.buyMultiplierBps());
        assertTrue(economy.entries.containsKey("minecraft:piglin_spawn_egg"));
        for(String id:java.util.List.of("zombie_spawn_egg","enderman_spawn_egg","spider_spawn_egg","blaze_spawn_egg"))assertFalse(economy.entries.containsKey("minecraft:"+id),id);
        var party=new cn.yumis.skyisland.data.IslandState.Party();
        assertTrue(economy.starter(seed));assertEquals(economy.buyPrice(seed),economy.buyPrice(seed,party));
        party.acquiredStarterIds.add(seed.product().id());assertEquals(economy.full(seed,seed.template()).floorLongExact(),economy.buyPrice(seed,party));
        var monster=economy.entries.get("minecraft:string");party.acquiredStarterIds.add(monster.product().id());
        assertFalse(economy.starter(monster));assertEquals(economy.buyPrice(monster),economy.buyPrice(monster,party));
        for(String id:java.util.List.of("stone","granite","diorite","andesite","deepslate","cobbled_deepslate","blackstone","basalt","dripstone_block"))assertTrue(economy.entries.get("minecraft:"+id).product().canBuy(),id);
    }
    @Test void questRewardSupportsRequestedSpecialEnchantmentsWithoutEnablingGoldToolsForTrade() {
        var economy=new ItemEconomy(config,lookup,123);
        var reward=economy.decode(config.object("tasks/quests.json").getAsJsonArray("quests").get(0).getAsJsonObject().getAsJsonArray("rewardItems").get(0));
        assertTrue(reward.is(net.minecraft.world.item.Items.GOLDEN_PICKAXE));assertEquals(0,reward.getDamageValue());
        var enchants=reward.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);assertNotNull(enchants);
        var values=new java.util.HashMap<String,Integer>();for(var e:enchants.entrySet())values.put(e.getKey().unwrapKey().orElseThrow().identifier().toString(),e.getIntValue());
        assertEquals(4,values.get("minecraft:unbreaking"));assertEquals(5,values.get("minecraft:efficiency"));
        assertNull(economy.quote(reward));
    }
    @Test void scoringGroupsAndProfessionGoodsReferenceRealRegistries() {
        var missing=config.blocks.keySet().stream().filter(id->!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.Identifier.parse(id))).toList();
        assertTrue(missing.isEmpty(),"Unregistered scoring blocks: "+missing);
        for(var row:config.blocks.entrySet()) {
            assertNotNull(config.blocks.get(row.getValue().group()),row.getValue().group());
            assertEquals(row.getValue().points(),config.blocks.get(row.getValue().group()).points(),row.getKey());
        }
        for(var id:config.object("products/goods.json").getAsJsonObject("items").keySet())
            assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.Identifier.parse(id)),id);
    }
}
