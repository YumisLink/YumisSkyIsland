package cn.yumis.skyisland.shop;

import cn.yumis.skyisland.config.GameConfig;
import cn.yumis.skyisland.core.*;
import cn.yumis.skyisland.prefab.PrefabBlocks;
import cn.yumis.skyisland.prefab.PrefabItems;
import cn.yumis.skyisland.wand.ConstructionWands;
import com.google.gson.*;
import com.mojang.serialization.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.item.enchantment.*;
import java.util.*;

public final class ItemEconomy {
    public record Entry(GameConfig.Product product, ItemStack template) {}
    public record Quote(Entry entry, Rational fullValue, Rational normalSale, GameConfig.QuotaUnit quota) {}
    private final GameConfig config;
    private final DynamicOps<JsonElement> ops;
    public final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<Item, List<Entry>> byItem = new HashMap<>();
    private long epoch;
    private final long seed;
    private java.util.function.ToIntBiFunction<cn.yumis.skyisland.data.IslandState.Party,String> residentSale=(party,item)->10000,residentBuy=(party,item)->10000;
    public void residentModifiers(java.util.function.ToIntBiFunction<cn.yumis.skyisland.data.IslandState.Party,String> sale,java.util.function.ToIntBiFunction<cn.yumis.skyisland.data.IslandState.Party,String> buy){residentSale=sale;residentBuy=buy;}
    public ItemEconomy(GameConfig config, HolderLookup.Provider registries, long seed) {
        this.config = config; this.ops = registries.createSerializationContext(JsonOps.INSTANCE); this.seed = seed;
        for (GameConfig.Product p : config.products.values()) {
            JsonObject data = new JsonObject(); data.addProperty("id", p.canonical().get("itemId").getAsString()); data.addProperty("count", 1);
            data.add("components", p.canonical().get("componentPatch"));
            try {
                ItemStack stack = ItemStack.CODEC.parse(ops, data).getOrThrow();
                if (stack.isEmpty() || !BuiltInRegistries.ITEM.containsKey(Identifier.parse(p.canonical().get("itemId").getAsString()))) throw new IllegalArgumentException("物品为空或 ID 未注册");
                ItemStack roundtrip = decode(encode(stack));
                if (!matchesCanonical(roundtrip,stack,p.canonical().getAsJsonObject("componentPatch"))) throw new IllegalArgumentException("标准栈回编码后不匹配");
                Entry e = new Entry(p, stack); entries.put(p.id(), e); byItem.computeIfAbsent(stack.getItem(), _ -> new ArrayList<>()).add(e);
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("shop/products.toml：商品 " + p.id() + " 无法加载：" + error.getMessage(),error);
            }
        }
    }
    /** Only controls automatic supply; existing items, recipes and valuation remain available. */
    public boolean acquisitionAllowed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (ConstructionWands.specialTier(stack) >= 0) return config.constructionWandAcquisitionEnabled();
        if (stack.is(PrefabItems.BLUEPRINT) || stack.is(PrefabItems.AMMUNITION) || stack.is(PrefabBlocks.CANNON.asItem()))
            return config.prefabAcquisitionEnabled();
        return true;
    }
    public boolean canBuy(Entry entry) {
        return entry != null && entry.product().canBuy() && acquisitionAllowed(entry.template());
    }
    public DynamicOps<JsonElement> ops() { return ops; }
    public JsonElement encode(ItemStack stack) { return stack.isEmpty() ? JsonNull.INSTANCE : ItemStack.CODEC.encodeStart(ops, stack).getOrThrow(); }
    public ItemStack decode(JsonElement value) { return value == null || value.isJsonNull() ? ItemStack.EMPTY : ItemStack.CODEC.parse(ops, value).getOrThrow(); }
    public JsonArray capture(Container inventory) {
        JsonArray result = new JsonArray(); for (int i = 0; i < inventory.getContainerSize(); i++) result.add(encode(inventory.getItem(i))); return result;
    }
    public void restore(Container inventory, JsonElement data) {
        JsonArray array = data.getAsJsonArray();
        if (array.size() != inventory.getContainerSize()) throw new IllegalStateException("Inventory size changed");
        for (int i = 0; i < array.size(); i++) inventory.setItem(i, decode(array.get(i)));
        inventory.setChanged();
    }
    public void epoch(long epoch) { this.epoch = epoch; }
    public int marketBps(String product) {
        JsonObject market = config.object("shop/shops.toml").getAsJsonObject("market");
        int min = market.get("minPriceBps").getAsInt(), max = market.get("maxPriceBps").getAsInt();
        return new SplittableRandom(DailyShopSelector.mix(seed ^ product.hashCode() ^ DailyShopSelector.mix(epoch))).nextInt(min, max + 1);
    }
    public long buyPrice(Entry entry) {
        if(entry.template().is(cn.yumis.skyisland.prefab.PrefabItems.AMMUNITION))return config.object("gameplay/prefabs.toml").get("ammunitionPriceCoins").getAsLong();
        long price = purchaseValue(entry).multiply(Rational.of(entry.product().buyMultiplierBps(), 10000)).floorLongExact();
        if (price < 1) throw new IllegalArgumentException("商品价格配置无效");
        return price;
    }
    public boolean starter(Entry entry) {
        JsonObject p = config.object("shop/products.toml").getAsJsonObject("products").getAsJsonObject(entry.product().id());
        return p!=null && p.has("firstAcquisitionPremium") && p.get("firstAcquisitionPremium").getAsBoolean();
    }
    public void replaceProduct(GameConfig.Product product) {
        Entry old=entries.get(product.id());
        if(old==null)throw new IllegalArgumentException("商品已移除");
        Entry next=new Entry(product,old.template());entries.put(product.id(),next);
        byItem.get(old.template().getItem()).replaceAll(e->e.product().id().equals(product.id())?next:e);
    }
    public long buyPrice(Entry entry, cn.yumis.skyisland.data.IslandState.Party party) {
        return starter(entry) && party.acquiredStarterIds.contains(entry.product().id())
            ? purchaseValue(entry).floorLongExact() : buyPrice(entry);
    }
    private Rational purchaseValue(Entry entry) {
        return fullValue(entry.product().buyBaseCoins(),marketBps(entry.product().id()),entry.template(),config.object("products/quality.toml"),config.object("shop/enchantments.toml"),config.minimumBaseCoins);
    }
    public long buyPrice(Entry entry, cn.yumis.skyisland.data.IslandState.Party party, boolean permanent) {
        if(entry.template().is(cn.yumis.skyisland.prefab.PrefabItems.AMMUNITION))return config.object("gameplay/prefabs.toml").get("ammunitionPriceCoins").getAsLong();
        long premium = starter(entry) && party.acquiredStarterIds.contains(entry.product().id()) ? 10000 : entry.product().buyMultiplierBps();
        long price = purchaseValue(entry).multiply(Rational.of(premium,10000))
            .multiply(Rational.of(permanent ? config.regularBuyMultiplierBps : 10000,10000)).multiply(Rational.of(residentBuy.applyAsInt(party,BuiltInRegistries.ITEM.getKey(entry.template().getItem()).toString()),10000)).floorLongExact();
        if(price < 1)throw new IllegalArgumentException("商品购价配置无效");
        return price;
    }
    public static int grade(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : Math.clamp(data.copyTag().getIntOr("yumis_quality", 0), 0, 2);
    }
    public Entry match(ItemStack stack) {
        if (stack.isEmpty()) return null;
        List<Entry> candidates = byItem.get(stack.getItem()); if (candidates == null) return null;
        for (Entry entry : candidates) {
            if (matchesCanonical(stack,entry.template(),entry.product().canonical().getAsJsonObject("componentPatch"))) return entry;
        }
        return null;
    }
    /** Compare decoded values, since equivalent component codecs may encode a shorthand form. */
    public static boolean matchesCanonical(ItemStack actual,ItemStack template,JsonObject components) {
        if (!ItemStack.isSameItem(actual,template)) return false;
        for(String id:components.keySet()) {
            DataComponentType<?> type=BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(id));
            if(type==null)return false;
            Object expected=template.get(type),value=actual.get(type);
            if(type==DataComponents.STORED_ENCHANTMENTS && expected instanceof ItemEnchantments enchants && value instanceof ItemEnchantments current) {
                for(var e:enchants.entrySet())if(current.getLevel(e.getKey())!=e.getIntValue())return false;
            }else if(!Objects.equals(expected,value))return false;
        }
        return true;
    }
    public Rational full(Entry entry, ItemStack stack) {
        return fullValue(entry.product().baseCoins(),marketBps(entry.product().id()),stack,config.object("products/quality.toml"),config.object("shop/enchantments.toml"),config.minimumBaseCoins);
    }
    public static Rational fullValue(long base,int marketBps,ItemStack stack,JsonObject quality,JsonObject enchantments,long minimumBaseCoins) {
        // Purchase premiums are applied separately and never enter the buyback value.
        Rational value = Rational.of(base).multiply(Rational.of(marketBps, 10000))
            .max(Rational.of(minimumBaseCoins));
        JsonArray grades = quality.getAsJsonArray("grades");
        value = value.multiply(Rational.of(grades.get(grade(stack)).getAsJsonObject().get("valueBps").getAsLong(), 10000));
        ItemEnchantments enchants = stack.getOrDefault(stack.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        List<Holder<Enchantment>> seen = new ArrayList<>();
        JsonObject table = enchantments.getAsJsonObject("entries");
        for (var e : enchants.entrySet()) {
            Holder<Enchantment> holder = e.getKey(); int level = e.getIntValue();
            String id = holder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
            JsonObject row = table.getAsJsonObject(id);
            if (row == null || level < 1 || level > holder.value().getMaxLevel() || (!stack.is(Items.ENCHANTED_BOOK) && !holder.value().isSupportedItem(stack))) return Rational.ZERO;
            for (Holder<Enchantment> other : seen) if (!Enchantment.areCompatible(holder, other)) return Rational.ZERO;
            seen.add(holder);
            JsonElement factor = row.getAsJsonObject("levelMultiplierBps").get(Integer.toString(level));
            if (factor == null) return Rational.ZERO;
            value = value.multiply(Rational.of(factor.getAsLong(), 10000));
        }
        return value;
    }
    public static boolean safeToSell(ItemStack stack) {
        if (stack.has(DataComponents.CUSTOM_NAME) || stack.has(DataComponents.CONTAINER_LOOT)) return false;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null && contents.nonEmptyItemCopyStream().findAny().isPresent()) return false;
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null && bundle.itemCopyStream().findAny().isPresent()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag(); tag.remove("yumis_quality"); tag.remove("yumis_profession");
            if(!LotteryItemRarity.get(stack).isEmpty())tag.remove(LotteryItemRarity.KEY);
            if (!tag.isEmpty()) return false;
        }
        return true;
    }
    public Quote quote(ItemStack stack) {
        return quote(stack,null);
    }
    public Quote quote(ItemStack stack,cn.yumis.skyisland.data.IslandState.Party party) {
        Entry entry = match(stack); if (entry == null || !entry.product().canSell() || !safeToSell(stack)) return null;
        Rational full = full(entry, stack);
        Rational durability = stack.isDamageableItem() ? Durability.multiplier(stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage()) : Rational.ONE;
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var unit = new GameConfig.QuotaUnit(item, Rational.ONE);
        return new Quote(entry, full, full.multiply(durability).divide(Rational.of(config.saleDivisor)).multiply(Rational.of(party==null?10000:residentSale.applyAsInt(party,item),10000)), unit);
    }
    public List<SalePlanner.Input> saleInputs(Container container, int onlySlot) {
        return saleInputs(container,onlySlot,null);
    }
    public List<SalePlanner.Input> saleInputs(Container container, int onlySlot,cn.yumis.skyisland.data.IslandState.Party party) {
        List<SalePlanner.Input> inputs = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (onlySlot >= 0 && slot != onlySlot) continue;
            ItemStack stack = container.getItem(slot); Quote q = quote(stack,party);
            if (q != null && q.normalSale().signum() > 0)
                inputs.add(new SalePlanner.Input(slot, encode(stack).toString(), stack.getCount(), q.quota().group(), q.quota().units(), q.normalSale()));
        }
        return inputs;
    }
}
