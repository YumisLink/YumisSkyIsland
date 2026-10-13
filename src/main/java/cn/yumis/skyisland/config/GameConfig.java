package cn.yumis.skyisland.config;

import cn.yumis.skyisland.core.*;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Editable JSON defaults are copied once; existing administrator edits are preserved. */
public final class GameConfig {
    public static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String[] FILES = {
        "server_rules.json", "shop/products.json", "shop/item_levels.json", "shop/rarities.json",
        "shop/enchantments.json", "shop/shops.json", "shop/sale_quotas.json", "islands/scoring.json",
        "islands/levels.json", "islands/refresh.json", "products/quality.json", "products/professions.json", "products/goods.json",
        "automation/unlocks.json", "automation/production.json", "automation/shipping_chest.json",
        "tasks/bounties.json", "tasks/quests.json"
    };
    private final Map<String, JsonObject> files = new LinkedHashMap<>();
    public final Map<String, Product> products = new LinkedHashMap<>();
    public final Map<String, BlockValue> blocks = new HashMap<>();
    public final Map<String, QuotaUnit> quotaUnits = new HashMap<>();
    public final long[] levelThresholds;
    public final List<BlockScorer.Tier> scoreTiers;
    public final QuotaCurve quotaCurve;
    public final long saleDivisor, minimumBaseCoins;

    public GameConfig() throws IOException {
        Path root = FabricLoader.getInstance().getConfigDir().resolve("yumis_sky_island");
        for (String file : FILES) {
            Path target = root.resolve(file);
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                try (InputStream in = GameConfig.class.getResourceAsStream("/defaults/yumis_sky_island/" + file)) {
                    if (in == null) throw new IOException("Missing default " + file);
                    Files.copy(in, target);
                }
            }
            try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
                files.put(file, JsonParser.parseReader(reader).getAsJsonObject());
            }
        }
        migrateShop(root);
        JsonObject shops = object("shop/shops.json");
        saleDivisor = shops.has("saleDivisor") ? shops.get("saleDivisor").getAsLong() : 10;
        JsonObject market = shops.getAsJsonObject("market");
        minimumBaseCoins = market.has("minimumCurrentBaseValueCoins") ? market.get("minimumCurrentBaseValueCoins").getAsLong() : market.get("minimumCurrentBuyCoins").getAsLong();
        if (saleDivisor < 1 || minimumBaseCoins < 1) throw new IOException("Invalid shop price floor or sale divisor");
        JsonObject levels = object("shop/item_levels.json").getAsJsonObject("itemLevels");
        JsonObject rarities = object("shop/rarities.json").getAsJsonObject("itemRarities");
        for (var entry : object("shop/products.json").getAsJsonObject("products").entrySet()) {
            JsonObject p = entry.getValue().getAsJsonObject();
            if (p.get("tradeMode").getAsString().equals("disabled")) continue;
            JsonElement l = levels.get(entry.getKey()), r = rarities.get(entry.getKey());
            int level = l == null ? 0 : l.isJsonPrimitive() ? l.getAsInt() : l.getAsJsonObject().get("unlockLevel").getAsInt();
            String rarity = r == null ? "common" : r.isJsonPrimitive() ? r.getAsString() : r.getAsJsonObject().get("rarity").getAsString();
            products.put(entry.getKey(), new Product(entry.getKey(), p.get("name").getAsString(),
                p.getAsJsonObject("canonicalStack"), p.get("baseValueCoins").getAsLong(),
                p.has("buyMultiplierBps") ? p.get("buyMultiplierBps").getAsLong() : 10000L,
                p.get("tradeMode").getAsString(), rarity, level, p.get("dailyStock").getAsInt(),
                p.get("offerWeight").getAsInt(), p.has("shopCategory") ? p.get("shopCategory").getAsString() : p.get("categoryHint").getAsString()));
            if (products.get(entry.getKey()).buyMultiplierBps() < 1) throw new IOException("Invalid purchase multiplier: " + entry.getKey());
        }
        JsonObject scoring = object("islands/scoring.json");
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
        JsonArray thresholds = object("islands/levels.json").getAsJsonArray("thresholds");
        levelThresholds = new long[thresholds.size()];
        for (JsonElement e : thresholds) { JsonObject t = e.getAsJsonObject(); levelThresholds[t.get("level").getAsInt()] = t.get("requiredPoints").getAsLong(); }
        JsonObject equivalents = object("shop/sale_quotas.json").getAsJsonObject("itemEquivalents");
        for (var e : equivalents.entrySet()) {
            JsonObject q = e.getValue().getAsJsonObject();
            quotaUnits.put(e.getKey(), new QuotaUnit(q.get("quotaGroup").getAsString(), Rational.of(q.get("numerator").getAsLong(), q.get("denominator").getAsLong())));
        }
        List<QuotaCurve.Tier> quotaTiers=new ArrayList<>();
        for(JsonElement e:object("shop/sale_quotas.json").getAsJsonArray("tiers")){var t=e.getAsJsonObject();quotaTiers.add(new QuotaCurve.Tier(t.get("upToUnits").isJsonNull()?null:Rational.of(t.get("upToUnits").getAsLong()),t.get("saleMultiplierBps").getAsInt()));}
        quotaCurve=new QuotaCurve(quotaTiers);
    }
    public JsonObject object(String path) { return files.get(path); }
    private void migrateShop(Path root) throws IOException {
        JsonObject current = object("shop/products.json");
        if (current.has("shopRevision") && current.get("shopRevision").getAsString().equals("materials-tasks-v3")) return;
        JsonObject defaults;
        try (InputStream in = GameConfig.class.getResourceAsStream("/defaults/yumis_sky_island/shop/products.json")) {
            if (in == null) throw new IOException("Missing shop migration defaults");
            defaults = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        Path target = root.resolve("shop/products.json"), backup = root.resolve("shop/products.before-materials-tasks-v3.json");
        if (!Files.exists(backup)) Files.copy(target, backup);
        JsonObject products = current.getAsJsonObject("products");
        for (var e : defaults.getAsJsonObject("products").entrySet()) {
            if (!products.has(e.getKey())) continue;
            JsonObject p = products.getAsJsonObject(e.getKey()), policy = e.getValue().getAsJsonObject();
            for (String key : List.of("tradeMode", "shopRule", "firstAcquisitionPremium")) if (policy.has(key)) p.add(key, policy.get(key).deepCopy());
            if (policy.has("baseValueCoins") && policy.get("baseValueCoins").isJsonNull()) p.add("baseValueCoins", JsonNull.INSTANCE);
        }
        current.addProperty("shopRevision", "materials-tasks-v3");
        Files.writeString(target, JSON.toJson(current), StandardCharsets.UTF_8);
    }
    public Map<String, JsonObject> all() { return Collections.unmodifiableMap(files); }
    public void useRuntimeQuality(JsonObject quality){files.put("products/quality.json",quality.deepCopy());}
    public record Product(String id, String name, JsonObject canonical, long baseCoins, long buyMultiplierBps, String tradeMode,
                          String rarity, int level, int stock, int weight, String category) {
        public boolean canBuy() { return tradeMode.equals("buy_sell") || tradeMode.equals("buy_only"); }
        public boolean canSell() { return tradeMode.equals("buy_sell") || tradeMode.equals("sell_only"); }
    }
    public record BlockValue(long points, String group) {}
    public record QuotaUnit(String group, Rational units) {}
}
