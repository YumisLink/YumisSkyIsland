package cn.yumis.skyisland.config;

import cn.yumis.skyisland.core.*;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Commented TOML defaults are copied once; administrator values and comments are preserved. */
public final class GameConfig {
    public static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String[] FILES = {
        "server_rules.toml", "shop/products.toml", "shop/item_levels.toml", "shop/rarities.toml",
        "shop/enchantments.toml", "shop/shops.toml", "shop/sale_quotas.toml", "islands/scoring.toml",
        "islands/levels.toml", "islands/refresh.toml", "products/quality.toml", "products/professions.toml", "products/goods.toml",
        "automation/unlocks.toml", "automation/production.toml", "automation/shipping_chest.toml",
        "tasks/bounties.toml", "tasks/quests.toml"
    };
    private final Path configRoot = FabricLoader.getInstance().getConfigDir().resolve("yumis_sky_island");
    private final Map<String, JsonObject> files = new LinkedHashMap<>();
    public final Map<String, Product> products = new LinkedHashMap<>();
    public final Map<String, BlockValue> blocks = new HashMap<>();
    public final Map<String, QuotaUnit> quotaUnits = new HashMap<>();
    public final long[] levelThresholds;
    public final List<BlockScorer.Tier> scoreTiers;
    public final QuotaCurve quotaCurve;
    public final long saleDivisor, minimumBaseCoins, regularBuyMultiplierBps;

    public GameConfig() throws IOException {
        Map<String, JsonObject> legacy = new HashMap<>();
        for (String file : FILES) {
            Path old = configRoot.resolve(file.replace(".toml", ".json"));
            if (Files.exists(old)) try (Reader reader = Files.newBufferedReader(old, StandardCharsets.UTF_8)) {
                legacy.put(file, JsonParser.parseReader(reader).getAsJsonObject());
            }
        }
        for (String file : FILES) {
            Path target = configRoot.resolve(file);
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                try (InputStream in = GameConfig.class.getResourceAsStream("/defaults/yumis_sky_island/" + file)) {
                    if (in == null) throw new IOException("缺少默认配置 " + file);
                    Files.copy(in, target);
                }
                if (legacy.containsKey(file)) {
                    JsonObject imported = importLegacy(file, legacy.get(file).deepCopy(), TomlConfigIO.read(target), legacy);
                    TomlConfigIO.write(target, imported);
                }
            }
            files.put(file, TomlConfigIO.read(target));
        }
        for (String file : legacy.keySet()) {
            Path old = configRoot.resolve(file.replace(".toml", ".json"));
            Path backup = old.resolveSibling(old.getFileName() + ".before-toml.bak");
            if (!Files.exists(backup)) Files.move(old, backup);
        }
        JsonObject shops = object("shop/shops.toml");
        saleDivisor = shops.has("saleDivisor") ? shops.get("saleDivisor").getAsLong() : 100;
        regularBuyMultiplierBps = shops.has("regularBuyMultiplierBps") ? shops.get("regularBuyMultiplierBps").getAsLong() : 25000;
        JsonObject market = shops.getAsJsonObject("market");
        minimumBaseCoins = market.has("minimumCurrentBaseValueCoins") ? market.get("minimumCurrentBaseValueCoins").getAsLong() : market.get("minimumCurrentBuyCoins").getAsLong();
        if (saleDivisor < 1 || minimumBaseCoins < 1 || regularBuyMultiplierBps < 1) throw new IOException("商店最低价、收购除数和普通购价倍率必须为正数");
        JsonObject levels = object("shop/item_levels.toml").getAsJsonObject("itemLevels");
        JsonObject rarities = object("shop/rarities.toml").getAsJsonObject("itemRarities");
        for (var entry : object("shop/products.toml").getAsJsonObject("products").entrySet()) {
            JsonObject p = entry.getValue().getAsJsonObject();
            String mode = p.get("tradeMode").getAsString();
            if (!Set.of("buy_sell", "buy_only", "sell_only").contains(mode) || !p.has("baseValueCoins") || p.get("baseValueCoins").isJsonNull() || p.get("baseValueCoins").getAsLong() <= 0)
                throw new IOException("shop/products.toml：交易方式无效或基础价不是正数：" + entry.getKey());
            if (!entry.getKey().equals(p.get("productId").getAsString())) throw new IOException("shop/products.toml：商品表键与 productId 不一致：" + entry.getKey());
            JsonElement l = levels.get(entry.getKey()), r = rarities.get(entry.getKey());
            int level = l == null ? 0 : l.isJsonPrimitive() ? l.getAsInt() : l.getAsJsonObject().get("unlockLevel").getAsInt();
            String rarity = r == null ? "common" : r.isJsonPrimitive() ? r.getAsString() : r.getAsJsonObject().get("rarity").getAsString();
            products.put(entry.getKey(), new Product(entry.getKey(), p.get("name").getAsString(),
                p.getAsJsonObject("canonicalStack"), p.get("baseValueCoins").getAsLong(),
                p.has("buyBaseValueCoins") ? p.get("buyBaseValueCoins").getAsLong() : p.get("baseValueCoins").getAsLong(),
                p.has("buyMultiplierBps") ? p.get("buyMultiplierBps").getAsLong() : 10000L,
                p.get("tradeMode").getAsString(), rarity, level, p.get("dailyStock").getAsInt(),
                p.get("offerWeight").getAsInt(), p.has("shopCategory") ? p.get("shopCategory").getAsString() : p.get("categoryHint").getAsString()));
            Product added = products.get(entry.getKey());
            if (added.buyBaseCoins() < 1 || added.buyMultiplierBps() < 1 || added.level() < 0 || added.level() > 100 || added.stock() < 1 || added.weight() < 1 || !Set.of("common","rare","epic","legendary").contains(added.rarity()))
                throw new IOException("shop/products.toml：商品倍率、等级、库存、权重或稀有度无效：" + entry.getKey());
        }
        JsonObject scoring = object("islands/scoring.toml");
        for (var e : scoring.getAsJsonObject("blocks").entrySet()) {
            JsonObject b = e.getValue().getAsJsonObject();
            blocks.put(e.getKey(), new BlockValue(b.get("basePoints").getAsLong(), b.get("scoreGroup").getAsString()));
        }
        List<BlockScorer.Tier> tiers = new ArrayList<>();
        for (JsonElement e : scoring.getAsJsonObject("tiers").getAsJsonArray("default")) {
            JsonObject t = e.getAsJsonObject();
            tiers.add(new BlockScorer.Tier(t.get("upToCount").isJsonNull() ? Long.MAX_VALUE : t.get("upToCount").getAsLong(), t.get("multiplierBps").getAsInt()));
        }
        scoreTiers = List.copyOf(tiers);
        JsonArray thresholds = object("islands/levels.toml").getAsJsonArray("thresholds");
        levelThresholds = new long[thresholds.size()];
        for (JsonElement e : thresholds) { JsonObject t = e.getAsJsonObject(); levelThresholds[t.get("level").getAsInt()] = t.get("requiredPoints").getAsLong(); }
        JsonObject equivalents = object("shop/sale_quotas.toml").getAsJsonObject("itemEquivalents");
        for (var e : equivalents.entrySet()) {
            JsonObject q = e.getValue().getAsJsonObject();
            quotaUnits.put(e.getKey(), new QuotaUnit(q.get("quotaGroup").getAsString(), Rational.of(q.get("numerator").getAsLong(), q.get("denominator").getAsLong())));
        }
        List<QuotaCurve.Tier> quotaTiers=new ArrayList<>();
        for(JsonElement e:object("shop/sale_quotas.toml").getAsJsonArray("tiers")){var t=e.getAsJsonObject();quotaTiers.add(new QuotaCurve.Tier(t.get("upToUnits").isJsonNull()?null:Rational.of(t.get("upToUnits").getAsLong()),t.get("saleMultiplierBps").getAsInt()));}
        quotaCurve=new QuotaCurve(quotaTiers);
    }
    public JsonObject object(String path) { return files.get(path); }
    private JsonObject importLegacy(String file, JsonObject value, JsonObject defaults, Map<String, JsonObject> legacy) throws IOException {
        if (file.equals("shop/products.toml")) {
            Set<String> excluded = new HashSet<>();
            try (InputStream in = GameConfig.class.getResourceAsStream("/data/yumis_sky_island/commerce/legacy_excluded_products.json")) {
                if (in == null) throw new IOException("缺少历史商品政策");
                for (JsonElement id : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) excluded.add(id.getAsString());
            }
            JsonObject rows = value.getAsJsonObject("products");
            for (String id : new ArrayList<>(rows.keySet())) {
                JsonObject row = rows.getAsJsonObject(id);
                if (excluded.contains(id) || !Set.of("buy_sell","sell_only").contains(row.get("tradeMode").getAsString()) || !row.has("baseValueCoins") || row.get("baseValueCoins").isJsonNull() || row.get("baseValueCoins").getAsLong() <= 0) rows.remove(id);
            }
            value.addProperty("shopRevision", "control-panel-v4");
        } else if (file.equals("tasks/quests.toml")) {
            value.remove("supportedFeatures"); value.add("features", defaults.get("features").deepCopy());
            value.addProperty("revision", value.has("revision") ? value.get("revision").getAsLong() : 1);
            value.addProperty("schemaVersion",2);
            JsonObject oldProducts = legacy.containsKey("shop/products.toml") ? legacy.get("shop/products.toml").getAsJsonObject("products") : new JsonObject();
            boolean shipping = false;
            for (JsonElement e : value.getAsJsonArray("quests")) {
                JsonObject q = e.getAsJsonObject(); shipping |= q.get("id").getAsString().equals("place_shipping_chest");
                if (!q.has("kind")) q.addProperty("kind","main");
                if (!q.has("objective")) q.addProperty("objective","materials");
                for (JsonElement r : q.getAsJsonArray("requirements")) {
                    JsonObject requirement=r.getAsJsonObject();
                    if (requirement.has("productId")) {
                        String id=requirement.remove("productId").getAsString();
                        if (oldProducts.has(id)) {
                            JsonObject canonical=oldProducts.getAsJsonObject(id).getAsJsonObject("canonicalStack");
                            requirement.add("itemId",canonical.get("itemId").deepCopy()); requirement.add("components",canonical.get("componentPatch").deepCopy());
                        } else requirement.addProperty("itemId",id);
                    }
                }
            }
            if (!shipping) for (JsonElement q : defaults.getAsJsonArray("quests")) if (q.getAsJsonObject().get("id").getAsString().equals("place_shipping_chest")) value.getAsJsonArray("quests").add(q.deepCopy());
        } else if (file.equals("shop/shops.toml")) {
            JsonObject hidden=value.getAsJsonObject("hidden");
            for (String key : List.of("guaranteedLegendary","guaranteedEpic","guaranteeFallback")) if (!hidden.has(key)) hidden.add(key,defaults.getAsJsonObject("hidden").get(key).deepCopy());
            value.addProperty("shopRevision","control-panel-v4"); value.addProperty("saleQuotaConfig","shop/sale_quotas.toml");
        }
        return value;
    }
    public void saveObject(String path, JsonObject value) throws IOException {
        if (!files.containsKey(path)) throw new IOException("不是可编辑配置文件：" + path);
        TomlConfigIO.write(configRoot.resolve(path),value);
        files.put(path,value.deepCopy());
    }
    public Map<String, JsonObject> all() { return Collections.unmodifiableMap(files); }
    public void useRuntimeQuality(JsonObject quality){files.put("products/quality.toml",quality.deepCopy());}
    public record Product(String id, String name, JsonObject canonical, long baseCoins, long buyBaseCoins, long buyMultiplierBps, String tradeMode,
                          String rarity, int level, int stock, int weight, String category) {
        public boolean canBuy() { return tradeMode.equals("buy_sell") || tradeMode.equals("buy_only"); }
        public boolean canSell() { return tradeMode.equals("buy_sell") || tradeMode.equals("sell_only"); }
    }
    public record BlockValue(long points, String group) {}
    public record QuotaUnit(String group, Rational units) {}
}
