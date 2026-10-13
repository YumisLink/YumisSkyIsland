package cn.yumis.skyisland.client;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.core.*;
import cn.yumis.skyisland.shop.ItemEconomy;
import com.google.gson.*;
import com.mojang.serialization.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.*;
import java.util.*;

public final class ClientCatalog {
    public record Product(String id,String name,ItemStack stack,JsonObject canonical,long base,long buyMultiplierBps,String rarity,int level,boolean buy,boolean sell,String category,String quotaGroup,Rational units,boolean starter) {}
    public record Category(String id,String name,ItemStack icon) {}
    public final Map<String,Product> products = new LinkedHashMap<>();
    private final Map<Item,List<Product>> byItem = new HashMap<>();
    public final Map<Item,String> categories = new HashMap<>();
    public final List<Category> tabs = new ArrayList<>();
    public final Set<String> favorites = new LinkedHashSet<>();
    public JsonObject state = new JsonObject(),prices = new JsonObject(),scoring = new JsonObject(),quality = new JsonObject(),enchantments = new JsonObject();
    public JsonObject itemRarities=new JsonObject(),sale=new JsonObject();
    public String lastError="";
    public static final int MIN_CELL = 40, MAX_CELL = 96, DEFAULT_CELL = 48;
    public int cellSize = DEFAULT_CELL, revision;
    private long saleDivisor = 100, minimumBaseCoins = 100;
    private QuotaCurve quotaCurve=QuotaCurve.DEFAULT;
    private final Path preferences = FabricLoader.getInstance().getConfigDir().resolve("yumis_sky_island/client.json");
    public ClientCatalog() {
        if (Files.exists(preferences)) try (Reader in = Files.newBufferedReader(preferences,StandardCharsets.UTF_8)) { JsonObject data = JsonParser.parseReader(in).getAsJsonObject(); cellSize = data.has("layoutVersion") ? Math.clamp(data.get("cellSize").getAsInt(),MIN_CELL,MAX_CELL) : Math.max(DEFAULT_CELL,Math.clamp(data.get("cellSize").getAsInt(),MIN_CELL,MAX_CELL)); for (JsonElement e : data.getAsJsonArray("favorites")) favorites.add(e.getAsString()); } catch (Exception e) { YumisSkyIsland.LOGGER.warn("无法读取商店偏好",e); }
    }
    public void clearConnection() { products.clear(); byItem.clear(); tabs.clear(); categories.clear(); state = new JsonObject(); prices = new JsonObject(); scoring = new JsonObject(); quality = new JsonObject(); enchantments = new JsonObject();itemRarities=new JsonObject();sale=new JsonObject();lastError=""; revision++; }
    public void receive(String json) {
        JsonObject packet = JsonParser.parseString(json).getAsJsonObject(); String kind = packet.get("kind").getAsString(); JsonElement data = packet.get("data");
        if(kind.equals("rules")&&data.getAsJsonObject().has("itemRarities"))itemRarities=data.getAsJsonObject().getAsJsonObject("itemRarities");
        switch (kind) {
            case "catalog" -> {
                if (packet.get("page").getAsInt() == 0) { products.clear(); byItem.clear(); }
                Minecraft mc = Minecraft.getInstance(); if (mc.getConnection() == null) return;
                var ops = RegistryOps.create(JsonOps.INSTANCE,mc.getConnection().registryAccess());
                for (JsonElement element : data.getAsJsonArray()) {
                    JsonObject row = element.getAsJsonObject(); ItemStack stack = ItemStack.CODEC.parse(ops,row.get("stack")).getOrThrow();
                    Product p = new Product(row.get("id").getAsString(),row.get("name").getAsString(),stack,row.getAsJsonObject("canonical"),row.get("base").getAsLong(),row.get("buyMultiplierBps").getAsLong(),row.get("rarity").getAsString(),row.get("level").getAsInt(),row.get("buy").getAsBoolean(),row.get("sell").getAsBoolean(),row.get("category").getAsString(),row.get("quotaGroup").getAsString(),Rational.parse(row.get("quotaUnits").getAsString()),row.has("firstAcquisitionPremium")&&row.get("firstAcquisitionPremium").getAsBoolean());
                    products.put(p.id(),p); byItem.computeIfAbsent(stack.getItem(),_ -> new ArrayList<>()).add(p);
                }
                if (tabs.isEmpty()) buildTabs(); revision++;
            }
            case "prices" -> { prices = data.getAsJsonObject(); revision++; }
            case "state" -> { state = data.getAsJsonObject(); revision++; }
            case "rules" -> { JsonObject rules = data.getAsJsonObject(); scoring = rules.getAsJsonObject("scoring"); quality = rules.getAsJsonObject("quality"); enchantments = rules.getAsJsonObject("enchantments");saleDivisor=rules.get("saleDivisor").getAsLong();minimumBaseCoins=rules.get("minimumBaseCoins").getAsLong();if(YumisSkyIsland.CONFIG!=null)YumisSkyIsland.CONFIG.useRuntimeQuality(quality);List<QuotaCurve.Tier> tiers=new ArrayList<>();for(JsonElement e:rules.getAsJsonArray("quotas")){var t=e.getAsJsonObject();tiers.add(new QuotaCurve.Tier(t.get("upToUnits").isJsonNull()?null:Rational.of(t.get("upToUnits").getAsLong()),t.get("saleMultiplierBps").getAsInt()));}quotaCurve=new QuotaCurve(tiers);revision++; }
            case "sale_preview" -> {sale=data.getAsJsonObject();if(sale.get("open").getAsBoolean()){lastError="";Minecraft.getInstance().gui.setScreen(new SaleScreen(this));}else if(Minecraft.getInstance().gui.screen() instanceof SaleScreen screen)screen.updated();revision++;}
            case "sale_done" -> {sale=new JsonObject();if(Minecraft.getInstance().gui.screen() instanceof SaleScreen)Minecraft.getInstance().gui.setScreen(new ShopScreen(this));}
            case "request_error" -> {lastError=data.getAsJsonObject().get("message").getAsString();if(Minecraft.getInstance().gui.screen() instanceof SaleScreen screen)screen.failed();revision++;}
            case "open" -> Minecraft.getInstance().gui.setScreen(new ShopScreen(this));
            default -> { }
        }
    }
    private void buildTabs() {
        Minecraft mc = Minecraft.getInstance(); if (mc.getConnection() == null) return;
        CreativeModeTabs.tryRebuildTabContents(mc.getConnection().enabledFeatures(),false,mc.getConnection().registryAccess());
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) if (tab.getType() == CreativeModeTab.Type.CATEGORY && tab.shouldDisplay()) {
            String id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).toString(); tabs.add(new Category(id,tab.getDisplayName().getString(),tab.getIconItem()));
            for (ItemStack item : tab.getDisplayItems()) categories.putIfAbsent(item.getItem(),id);
        }
        tabs.add(new Category("other","其他商品",new ItemStack(Items.CHEST)));
    }
    public String category(Product p) { return p.category().startsWith("minecraft:") || p.category().equals("other") ? p.category() : categories.getOrDefault(p.stack().getItem(),"other"); }
    public List<Category> shopTabs() { return tabs.stream().filter(c->products.values().stream().anyMatch(p->p.buy()&&category(p).equals(c.id()))).toList(); }
    public int level() { return state.has("level") ? state.get("level").getAsInt() : 0; }
    public long wallet() { return state.has("wallet") ? state.get("wallet").getAsLong() : 0; }
    public int bps(Product p) { return prices.has(p.id()) ? prices.get(p.id()).getAsInt() : 10000; }
    public Rational value(Product p,ItemStack stack) { return quality.has("grades") && enchantments.has("entries") ? ItemEconomy.fullValue(p.base(),bps(p),stack,quality,enchantments,minimumBaseCoins) : Rational.of(Math.max(minimumBaseCoins,Rational.of(p.base()).multiply(Rational.of(bps(p),10000)).floorLongExact())); }
    public long price(Product p) { boolean acquired=false;if(state.has("acquiredStarters"))for(var e:state.getAsJsonArray("acquiredStarters"))if(e.getAsString().equals(p.id()))acquired=true;return value(p,p.stack()).multiply(Rational.of(p.starter()&&acquired?10000:p.buyMultiplierBps(),10000)).floorLongExact(); }
    public ItemStack decode(JsonElement data) { var connection=Minecraft.getInstance().getConnection();return data==null||data.isJsonNull()||connection==null?ItemStack.EMPTY:ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,connection.registryAccess()),data).getOrThrow(); }
    public String rarity(ItemStack stack) {var p=match(stack);if(p!=null)return p.rarity();String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();return itemRarities.has(id)?itemRarities.get(id).isJsonPrimitive()?itemRarities.get(id).getAsString():itemRarities.getAsJsonObject(id).get("rarity").getAsString():"common";}
    public int stock(Product p) { return state.has("stock") ? state.getAsJsonObject("stock").has(p.id()) ? state.getAsJsonObject("stock").get(p.id()).getAsInt() : 0 : 0; }
    public boolean hidden() { return state.has("hidden") && state.get("hidden").getAsBoolean(); }
    public boolean specialAllowed(){return !state.has("progression")||state.getAsJsonObject("progression").get("specialAllowed").getAsBoolean();}
    public Product match(ItemStack stack) {
        List<Product> candidates = byItem.get(stack.getItem()); if (candidates == null) return null;
        for (Product product : candidates) {
            if(ItemEconomy.matchesCanonical(stack,product.stack(),product.canonical().getAsJsonObject("componentPatch")))return product;
        }
        return null;
    }
    public void tooltip(ItemStack stack,List<Component> lines) {
        if(products.isEmpty()&&itemRarities.isEmpty())return;
        String rarity=rarity(stack);int color=RarityColors.color(rarity)&0xFFFFFF;
        if(!lines.isEmpty())lines.set(0,lines.getFirst().copy().withStyle(s->s.withColor(color)));
        lines.add(Component.literal("种类稀有度："+RarityColors.name(rarity)).withStyle(s->s.withColor(color)));
        Product p = match(stack);
        int grade = ItemEconomy.grade(stack);
        if (grade > 0 && quality.has("grades")) lines.add(Component.literal("制造品质：" + quality.getAsJsonArray("grades").get(grade).getAsJsonObject().get("name").getAsString()).withStyle(grade >= 3 ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        if (p != null) {
            Rational full = value(p,stack), durability = stack.isDamageableItem() ? Durability.multiplier(stack.getMaxDamage()-stack.getDamageValue(),stack.getMaxDamage()) : Rational.ONE;
            lines.add(Component.literal("价值：" + Money.format(full.multiply(durability).floorLongExact()) + " / 件").withStyle(ChatFormatting.YELLOW));
            if (p.sell() && ItemEconomy.safeToSell(stack)) {
                Rational normal = full.multiply(durability).divide(Rational.of(saleDivisor)), used = Rational.ZERO;
                if (state.has("quotas") && state.getAsJsonObject("quotas").has(p.quotaGroup())) used = Rational.parse(state.getAsJsonObject("quotas").get(p.quotaGroup()).getAsString());
                long quote = quotaCurve.quote(used,stack.getCount(),p.units(),normal).floorLongExact();
                lines.add(Component.literal("常规收购：" + decimal(normal) + "金币 / 件").withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal("本队当前阶梯：" + Money.format(quote) + " / " + stack.getCount() + "件").withStyle(ChatFormatting.GREEN));
            }
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.dimension().equals(Level.OVERWORLD) && stack.getItem() instanceof BlockItem item && scoring.has("blocks")) {
            String block = BuiltInRegistries.BLOCK.getKey(item.getBlock()).toString(); JsonObject b = scoring.getAsJsonObject("blocks").getAsJsonObject(block);
            if (b != null && b.get("basePoints").getAsLong() > 0 && state.has("counts")) {
                String group = b.get("scoreGroup").getAsString(); long count = state.has("counts") && state.getAsJsonObject("counts").has(group) ? state.getAsJsonObject("counts").get(group).getAsLong() : 0;
                List<BlockScorer.Tier> tiers = new ArrayList<>(); for (JsonElement e : scoring.getAsJsonObject("tiers").getAsJsonArray("default")) { var t = e.getAsJsonObject(); tiers.add(new BlockScorer.Tier(t.get("upToCount").isJsonNull()?null:t.get("upToCount").getAsLong(),t.get("multiplierBps").getAsInt())); }
                long placed=item.getBlock() instanceof BedBlock || item.getBlock() instanceof DoorBlock || item.getBlock() instanceof DoublePlantBlock ? 2 : 1;
                lines.add(Component.literal("放置岛经验：+" + decimal(BlockScorer.marginal(count,placed,b.get("basePoints").getAsLong(),tiers)) + "点（主世界当前存量）").withStyle(ChatFormatting.AQUA));
            }
        }
    }
    public static String decimal(Rational n) { return new BigDecimal(n.numerator()).divide(new BigDecimal(n.denominator()),4,RoundingMode.DOWN).stripTrailingZeros().toPlainString(); }
    public void favorite(String product) { if (!favorites.remove(product)) favorites.add(product); revision++; save(); }
    public void save() {
        JsonObject json = new JsonObject(); json.addProperty("layoutVersion",2);json.addProperty("cellSize",cellSize); JsonArray ids = new JsonArray(); favorites.forEach(ids::add); json.add("favorites",ids);
        try { Files.createDirectories(preferences.getParent()); Files.writeString(preferences,json.toString(),StandardCharsets.UTF_8); } catch (IOException e) { YumisSkyIsland.LOGGER.warn("无法保存商店偏好",e); }
    }
}
