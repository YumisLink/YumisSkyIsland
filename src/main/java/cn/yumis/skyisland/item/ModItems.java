package cn.yumis.skyisland.item;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.server.Protection;
import cn.yumis.skyisland.server.AccessoryService;
import cn.yumis.skyisland.server.SkyServer;
import cn.yumis.skyisland.world.VoidWorlds;
import com.google.gson.JsonArray;
import java.util.*;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Server-authoritative special items; inventory and persistent state commit together. */
public final class ModItems {
    public static Item LOTTERY_TICKET;
    public static Item FLIGHT_TICKET;
    public static Item BIOME_TREASURE_BOX;
    public static Item WARDEN_TREASURE_BAG;
    public static final Map<String, Item> BIOME_BALLS = new LinkedHashMap<>();
    public static final Map<String, Item> FRUITS = new LinkedHashMap<>();
    public static final Map<String, Item> ACCESSORY_ICONS = new LinkedHashMap<>();
    public static final Map<String, Item> FISH = new LinkedHashMap<>();
    public static final Map<String, Item> FISHING_MATERIALS = new LinkedHashMap<>();
    public static final Map<String, Item> CROP_MUTATIONS = new LinkedHashMap<>();
    public static final List<String> PROFESSIONS = List.of("blacksmith", "chef", "farmer", "hunter", "miner", "battle_master");
    private static final Set<UUID> FLIGHT_GRANTED = new HashSet<>();
    public static final Set<String> LEGENDARY_BIOMES = Set.of("mushroom_fields", "deep_dark", "pale_garden", "the_end");
    public static final Set<String> EPIC_BIOMES = Set.of("ice_spikes", "mangrove_swamp", "old_growth_birch_forest", "old_growth_pine_taiga", "old_growth_spruce_taiga", "bamboo_jungle", "badlands", "eroded_badlands", "wooded_badlands", "cherry_grove", "frozen_peaks", "jagged_peaks", "deep_frozen_ocean", "sulfur_caves", "warped_forest", "crimson_forest", "end_highlands");

    private ModItems() {}
    public static void register() {
        LOTTERY_TICKET=register("lottery_ticket",new Item(properties("lottery_ticket")){
            @Override public net.minecraft.network.chat.Component getName(ItemStack stack){return net.minecraft.network.chat.Component.literal(cn.yumis.skyisland.core.LotteryNames.ticket());}
        });
        FLIGHT_TICKET = register("flight_ticket", new FlightTicket(properties("flight_ticket").stacksTo(4)));
        for (String profession : PROFESSIONS) FRUITS.put(profession, register("fruit_" + profession, new Fruit(profession, properties("fruit_" + profession).stacksTo(64))));
        // Preserve legacy IDs in old inventories, but never offer or consume their fruit.
        for(String legacy:List.of("stonemason","artisan"))register("fruit_"+legacy,new Item(properties("fruit_"+legacy).stacksTo(64)));
        BIOME_TREASURE_BOX=register("biome_treasure_box",new BiomeTreasureBox(properties("biome_treasure_box").stacksTo(16)));
        WARDEN_TREASURE_BAG=register("warden_treasure_bag",new WardenTreasureBag(properties("warden_treasure_bag").stacksTo(16)));
        for (String accessory : AccessoryService.CATALOG.keySet()) ACCESSORY_ICONS.put(accessory,register("accessory_"+accessory,new AccessoryItem(accessory,properties("accessory_"+accessory).stacksTo(1))));
        for(String fish:List.of("sky_carp","lava_eel","void_fish","frost_trout","desert_lungfish","mushroom_fish"))
            FISH.put(fish,register(fish,new Item(properties(fish).food(new FoodProperties(3,0.3f,false)))));
        for(String material:List.of("glowing_scales","ash_pearl","void_fragment","coral_fragment"))
            FISHING_MATERIALS.put(material,register(material,new Item(properties(material))));
        for(String crop:List.of("barley","white_radish","purple_potato","golden_beet","crimson_wart","sky_fruit"))
            CROP_MUTATIONS.put(crop,register(crop,new Item(properties(crop))));
        List<ResourceKey<Biome>> keys = new ArrayList<>();
        for (var field : Biomes.class.getFields()) {
            if (field.getType() != ResourceKey.class) continue;
            try {
                @SuppressWarnings("unchecked") ResourceKey<Biome> key = (ResourceKey<Biome>) field.get(null);
                if (!key.equals(Biomes.THE_VOID)) keys.add(key);
            } catch (IllegalAccessException e) { throw new IllegalStateException("无法读取原版群系", e); }
        }
        keys.sort(Comparator.comparing(k -> k.identifier().toString()));
        if (keys.size() != 65) throw new IllegalStateException("26.2原版群系数量变化：" + keys.size());
        for (ResourceKey<Biome> key : keys) {
            String path = key.identifier().getPath();
            BIOME_BALLS.put(path, register("biome_ball_" + path, new BiomeBall(key, properties("biome_ball_" + path).stacksTo(16))));
        }
    }
    private static Item.Properties properties(String path) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, YumisSkyIsland.id(path)));
    }
    private static Item register(String path, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, YumisSkyIsland.id(path), item);
    }
    public static String rarity(String biome) {
        return LEGENDARY_BIOMES.contains(biome) ? "legendary" : EPIC_BIOMES.contains(biome) ? "epic" : "rare";
    }
    private static final class AccessoryItem extends Item {
        private final String accessory;
        AccessoryItem(String accessory,Properties properties){super(properties);this.accessory=accessory;}
        @Override public InteractionResult use(Level level,net.minecraft.world.entity.player.Player user,InteractionHand hand){
            if(level.isClientSide())return InteractionResult.SUCCESS;
            if(!(user instanceof ServerPlayer player)||!(level instanceof ServerLevel world))return InteractionResult.PASS;
            var runtime=SkyServer.get(world.getServer());if(runtime==null)return InteractionResult.FAIL;
            try {runtime.accessories.unlockFromItem(player,hand,accessory);return InteractionResult.SUCCESS;}
            catch(IllegalArgumentException|IllegalStateException|ArithmeticException error){SkyServer.tell(player,error.getMessage());return InteractionResult.FAIL;}
        }
        @Override public InteractionResult useOn(UseOnContext context){
            return context.getPlayer()==null?InteractionResult.PASS:use(context.getLevel(),context.getPlayer(),context.getHand());
        }
    }
    private static void consume(ServerPlayer player, SkyServer runtime, Item item, InteractionHand handUsed, java.util.function.Consumer<IslandState> edit, Runnable after) {
        if (runtime.inventoryLocks.contains(player.getUUID())) return;
        try {
            runtime.trades.requireInventoryReady(player);
            int slot = handUsed == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : player.getInventory().getSelectedSlot();
            ItemStack hand = player.getInventory().getItem(slot);
            if (!hand.is(item) || hand.isEmpty()) return;
            JsonArray inventory = runtime.economy.capture(player.getInventory());
            ItemStack remaining = hand.copy(); remaining.shrink(1);
            inventory.set(slot, runtime.economy.encode(remaining));
            runtime.trades.commitInventory(player, inventory, edit, after);
        } catch(IllegalArgumentException|IllegalStateException|ArithmeticException error) {SkyServer.tell(player,error.getMessage());}
    }
    private static final class FlightTicket extends Item {
        FlightTicket(Properties properties) { super(properties); }
        @Override public InteractionResult use(Level level, net.minecraft.world.entity.player.Player user, InteractionHand hand) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!(user instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
            SkyServer runtime = SkyServer.get(serverLevel.getServer());
            if (runtime == null || !VoidWorlds.isSky(serverLevel) || runtime.parties.active(player.getUUID()) == null) return InteractionResult.FAIL;
            long now = System.currentTimeMillis();
            consume(player, runtime, this, hand, state -> {
                IslandState.Person person = state.players.get(player.getUUID());
                person.flightExpiryEpochMs = Math.addExact(Math.max(now, person.flightExpiryEpochMs), 600_000L);
            }, () -> {
                refreshFlight(player, runtime.state().players.get(player.getUUID()));
                SkyServer.tell(player, "飞行时间增加10分钟");
            });
            return InteractionResult.SUCCESS;
        }
    }
    private static final class BiomeBall extends Item {
        private final ResourceKey<Biome> target;
        BiomeBall(ResourceKey<Biome> target, Properties properties) { super(properties); this.target = target; }
        @Override public InteractionResult use(Level level, net.minecraft.world.entity.player.Player user, InteractionHand hand) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!(user instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
            SkyServer runtime = SkyServer.get(serverLevel.getServer());
            if (runtime == null || !VoidWorlds.isSky(serverLevel) || runtime.parties.active(player.getUUID()) == null || !Protection.canBuild(serverLevel, player, player.blockPosition())) return InteractionResult.FAIL;
            IslandState.Party party=runtime.parties.active(player.getUUID());
            Holder<Biome> biome = serverLevel.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(target);
            LevelChunk chunk = serverLevel.getChunkAt(player.blockPosition());
            String chunkKey=chunkKey(serverLevel,chunk);
            consume(player, runtime, this, hand, state -> state.parties.get(party.id).biomeOverrides.put(chunkKey,target.identifier().toString()), () -> {
                applyBiome(serverLevel,chunk,biome);
                SkyServer.tell(player, "当前16×16区块已改为" + getName(new ItemStack(this)).getString());
            });
            return InteractionResult.SUCCESS;
        }
    }
    private static String chunkKey(ServerLevel level, LevelChunk chunk) { return level.dimension().identifier() + "|" + chunk.getPos().pack(); }
    private static void applyBiome(ServerLevel level, LevelChunk chunk, Holder<Biome> biome) {
        chunk.fillBiomesFromNoise((x,y,z,sampler)->biome,level.getChunkSource().randomState().sampler());
        chunk.markUnsaved();
        var packet=ClientboundChunksBiomesPacket.forChunks(List.of(chunk));
        for(ServerPlayer observer:level.players())observer.connection.send(packet);
    }
    public static void applyBiomeOverride(SkyServer runtime, ServerLevel level, LevelChunk chunk) {
        if (!VoidWorlds.isSky(level)) return;
        String key=chunkKey(level,chunk);
        for(IslandState.Party party:runtime.state().parties.values()){
            String id=party.biomeOverrides.get(key);
            if(id==null)continue;
            var biome=level.registryAccess().lookupOrThrow(Registries.BIOME).get(ResourceKey.create(Registries.BIOME,Identifier.parse(id)));
            biome.ifPresent(value->applyBiome(level,chunk,value));
            return;
        }
    }
    private static final class Fruit extends Item {
        private final String profession;
        Fruit(String profession, Properties properties) { super(properties); this.profession = profession; }
        @Override public InteractionResult use(Level level, net.minecraft.world.entity.player.Player user, InteractionHand hand) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!(user instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
            SkyServer runtime = SkyServer.get(serverLevel.getServer());
            if (runtime == null || runtime.parties.active(player.getUUID()) == null) return InteractionResult.FAIL;
            IslandState.Person person = runtime.state().players.get(player.getUUID());
            int cap=Math.clamp(runtime.config.object("products/profession_shop.toml").get("fruitUseCap").getAsInt(),1,100);
            if (person == null || person.consumedFruits.getOrDefault(profession, 0) >= cap) {
                SkyServer.tell(player, "此职业果实已达到"+cap+"个上限"); return InteractionResult.FAIL;
            }
            consume(player, runtime, this, hand, state -> state.players.get(player.getUUID()).consumedFruits.merge(profession, 1, Integer::sum), () -> {
                applyFruitBonuses(player, runtime.state().players.get(player.getUUID()));
                SkyServer.tell(player, "已食用" + cn.yumis.skyisland.server.ProfessionService.name(profession) + "果实");
            });
            return InteractionResult.SUCCESS;
        }
    }
    public static void applyFruitBonuses(ServerPlayer player, IslandState.Person person) {
        if (person == null) return;
        var counts = person.consumedFruits;
        apply(player, Attributes.ARMOR, "fruit_blacksmith", counts.getOrDefault("blacksmith",0) * 0.05);
        apply(player, Attributes.ARMOR_TOUGHNESS, "fruit_stonemason", 0);
        apply(player, Attributes.ATTACK_DAMAGE, "fruit_artisan", 0);
        apply(player, Attributes.MAX_HEALTH, "fruit_farmer", counts.getOrDefault("farmer",0) * 0.1);
        apply(player, Attributes.ARMOR_TOUGHNESS, "fruit_battle_master", counts.getOrDefault("battle_master",0) * 0.01);
        applyMultiplied(player, Attributes.BLOCK_BREAK_SPEED, "fruit_miner", counts.getOrDefault("miner",0) * 0.02);
    }
    private static void apply(ServerPlayer player, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, String name, double amount) {
        var instance = player.getAttribute(attribute);
        if (instance == null) return;
        Identifier id = YumisSkyIsland.id(name);
        AttributeModifier previous = instance.getModifier(id);
        if (amount <= 0) { if (previous != null) instance.removeModifier(id); return; }
        if (previous == null || previous.amount() != amount)
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }
    private static void applyMultiplied(ServerPlayer player, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, String name, double amount) {
        var instance = player.getAttribute(attribute);
        if (instance == null) return;
        Identifier id = YumisSkyIsland.id(name);
        AttributeModifier previous = instance.getModifier(id);
        if (amount <= 0) { if (previous != null) instance.removeModifier(id); return; }
        if (previous == null || previous.amount() != amount)
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }
    public static void refreshFlight(ServerPlayer player, IslandState.Person person) {
        UUID id = player.getUUID();
        if (player.isCreative() || player.isSpectator()) { FLIGHT_GRANTED.remove(id); return; }
        boolean active = person != null && person.flightExpiryEpochMs > System.currentTimeMillis();
        boolean granted = FLIGHT_GRANTED.contains(id);
        if (active && !granted) { player.getAbilities().mayfly = true; player.onUpdateAbilities(); FLIGHT_GRANTED.add(id); }
        if (!active && granted) {
            player.getAbilities().mayfly = false; player.getAbilities().flying = false;
            player.onUpdateAbilities(); FLIGHT_GRANTED.remove(id);
        }
    }
    public static void disconnected(UUID player) { FLIGHT_GRANTED.remove(player); }
}
