package cn.yumis.skyisland.config;

import cn.yumis.skyisland.core.*;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Commented TOML defaults are copied once; a file that cannot be read or pass startup checks is replaced by that default. */
public final class GameConfig {
    public static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(GameConfig.class);
    @FunctionalInterface private interface Check { void run() throws IOException; }
    /** A broken prize catalog must disable draws, never invent replacement prizes or levels. */
    static JsonObject readLottery(Path file) {
        try{return TomlConfigIO.read(file);}
        catch(IOException invalid){LOGGER.error("抽奖配置读取失败，保留原文件并停用抽取："+file,invalid);return new JsonObject();}
    }
    private static final String[] FILES = {
        "server_rules.toml", "shop/products.toml", "shop/item_levels.toml", "shop/rarities.toml",
        "shop/enchantments.toml", "shop/shops.toml", "shop/sale_quotas.toml", "shop/recipe_values.toml", "islands/scoring.toml",
        "islands/levels.toml", "islands/refresh.toml", "products/quality.toml", "products/professions.toml", "products/goods.toml", "products/profession_shop.toml",
        "gameplay/blood_moon.toml", "gameplay/lottery.toml", "gameplay/residents.toml",
        "tasks/bounties.toml", "tasks/quests.toml", "economy.toml", "gameplay/construction_wand.toml", "gameplay/prefabs.toml"
    };
    private final Path configRoot = FabricLoader.getInstance().getConfigDir().resolve("yumis_sky_island");
    private final Map<String, JsonObject> files = new LinkedHashMap<>();
    private final Map<String, String> loadedDigests = new HashMap<>();
    private Set<String> registeredBlocks = Set.of();
    public ScoringSettings scoring;
    public final Map<String, Product> products = new LinkedHashMap<>();
    public final Map<String, BlockValue> blocks = new HashMap<>();
    public final Map<String, QuotaUnit> quotaUnits = new HashMap<>();
    public List<BlockScorer.Tier> scoreTiers;
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
            String defaults;
            try (InputStream in = GameConfig.class.getResourceAsStream("/defaults/yumis_sky_island/" + file)) {
                if (in == null) throw new IOException("缺少默认配置 " + file);
                defaults = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                Files.writeString(target, defaults, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                if (legacy.containsKey(file)) {
                    JsonObject imported = importLegacy(file, legacy.get(file).deepCopy(), TomlConfigIO.read(target), legacy);
                    TomlConfigIO.write(target, imported);
                }
            }
            files.put(file, file.equals("gameplay/lottery.toml")?readLottery(target):TomlConfigIO.readOrRepair(target, defaults, true));
            loadedDigests.put(file, digest(target));
        }
        for (String file : legacy.keySet()) {
            Path old = configRoot.resolve(file.replace(".toml", ".json"));
            Path backup = old.resolveSibling(old.getFileName() + ".before-toml.bak");
            if (!Files.exists(backup)) Files.move(old, backup);
        }
        once("gameplay/construction_wand.toml", this::constructionWandAcquisitionEnabled);
        once("gameplay/prefabs.toml", this::prefabAcquisitionEnabled);
        once("islands/levels.toml", () -> BlockScorer.requirementBasePoints(object("islands/levels.toml").get("requirementBasePoints").getAsLong()));
        once("shop/shops.toml", this::validateShops);
        once("economy.toml", this::validateEconomy);
        once("shop/item_levels.toml", this::validateItemLevels);
        once("shop/rarities.toml", this::validateRarities);
        once("shop/products.toml", this::loadProducts);
        once("islands/scoring.toml", () -> publishScoring(ScoringSettings.parse(object("islands/scoring.toml"))));
        once("shop/sale_quotas.toml", () -> quotaCurve(object("shop/sale_quotas.toml")));
        once("products/professions.toml", this::validateProfessions);
        once("products/quality.toml", this::validateQuality);
        once("products/goods.toml", this::validateGoods);
        JsonObject shops = object("shop/shops.toml");
        JsonObject market = shops.getAsJsonObject("market");
        Money.currencyName(object("economy.toml").get("genshinMode").getAsBoolean() ? "摩拉" : "金币");
        saleDivisor = shops.has("saleDivisor") ? shops.get("saleDivisor").getAsLong() : 100;
        regularBuyMultiplierBps = shops.has("regularBuyMultiplierBps") ? shops.get("regularBuyMultiplierBps").getAsLong() : 25000;
        minimumBaseCoins = market.has("minimumCurrentBaseValueCoins") ? market.get("minimumCurrentBaseValueCoins").getAsLong() : market.get("minimumCurrentBuyCoins").getAsLong();
        quotaCurve = quotaCurve(object("shop/sale_quotas.toml"));
    }
    private void once(String file, Check check) throws IOException {
        try { check.run(); }
        catch (RuntimeException | IOException invalid) {
            LOGGER.warn("配置 {} 无效（{}），已删除原文件并重新生成默认配置", file, invalid.getMessage()==null?invalid.getClass().getSimpleName():invalid.getMessage());
            rewriteDefault(file);
            check.run();
        }
    }
    private void rewriteDefault(String file) throws IOException {
        String defaults;
        try (InputStream in = GameConfig.class.getResourceAsStream("/defaults/yumis_sky_island/" + file)) {
            if (in == null) throw new IOException("缺少默认配置 " + file);
            defaults = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Path target = configRoot.resolve(file);
        TomlConfigIO.replaceWithDefault(target, defaults);
        files.put(file, TomlConfigIO.read(target));
        loadedDigests.put(file, digest(target));
    }
    private void validateShops() throws IOException {
        JsonObject shops = object("shop/shops.toml");
        long sale = shops.has("saleDivisor") ? shops.get("saleDivisor").getAsLong() : 100;
        long multiplier = shops.has("regularBuyMultiplierBps") ? shops.get("regularBuyMultiplierBps").getAsLong() : 25000;
        JsonObject market = shops.getAsJsonObject("market");
        long minimum = market.has("minimumCurrentBaseValueCoins") ? market.get("minimumCurrentBaseValueCoins").getAsLong() : market.get("minimumCurrentBuyCoins").getAsLong();
        if (sale < 1 || minimum < 1 || multiplier < 1) throw new IOException("商店最低价、收购除数和普通购价倍率必须为正数");
        long refresh = market.get("refreshAtDayTick").getAsLong();
        if (refresh < 0 || refresh >= 24000) throw new IOException("每日刷新时间须在0～23999游戏刻之间");
    }
    private void validateEconomy() throws IOException {
        JsonElement mode = object("economy.toml").get("genshinMode");
        if (mode == null || !mode.isJsonPrimitive() || !mode.getAsJsonPrimitive().isBoolean()) throw new IOException("economy.toml：genshinMode 必须为 true 或 false");
        JsonObject death = object("economy.toml").getAsJsonObject("death");
        if (death.get("firstCost").getAsLong() < 0 || death.get("additionalCostPerDeath").getAsLong() < 0 || death.get("respawnFoodLevel").getAsInt() < 0 || death.get("respawnFoodLevel").getAsInt() > 20)
            throw new IOException("死亡费或复活饥饿值配置无效");
    }
    private void validateItemLevels() throws IOException {
        JsonObject levels = object("shop/item_levels.toml").getAsJsonObject("itemLevels");
        if (levels == null) throw new IOException("shop/item_levels.toml：缺少 itemLevels");
        for (var entry : levels.entrySet()) {
            JsonElement value = entry.getValue();
            int level = value.isJsonPrimitive() ? value.getAsInt() : value.getAsJsonObject().get("unlockLevel").getAsInt();
            if (level < 0 || level > 100) throw new IOException("shop/item_levels.toml：等级无效：" + entry.getKey());
        }
    }
    private void validateRarities() throws IOException {
        JsonObject rarities = object("shop/rarities.toml").getAsJsonObject("itemRarities");
        if (rarities == null) throw new IOException("shop/rarities.toml：缺少 itemRarities");
        for (var entry : rarities.entrySet()) {
            JsonElement value = entry.getValue();
            String rarity = value.isJsonPrimitive() ? value.getAsString() : value.getAsJsonObject().get("rarity").getAsString();
            if (!Set.of("common", "rare", "epic", "legendary").contains(rarity)) throw new IOException("shop/rarities.toml：稀有度无效：" + entry.getKey());
        }
    }
    private void loadProducts() throws IOException {
        products.clear();
        JsonObject levels = object("shop/item_levels.toml").getAsJsonObject("itemLevels");
        JsonObject rarities = object("shop/rarities.toml").getAsJsonObject("itemRarities");
        JsonObject productRows=object("shop/products.toml").getAsJsonObject("products");
        JsonObject ingredientValues=object("shop/recipe_values.toml").getAsJsonObject("ingredientValues");
        for (var entry : productRows.entrySet()) {
            JsonObject p = entry.getValue().getAsJsonObject();
            if(p.has("recipeMaterials"))for(JsonElement material:p.getAsJsonArray("recipeMaterials")){
                String id=material.getAsString();
                JsonObject priced=productRows.getAsJsonObject(id);
                if((priced==null||!priced.has("baseValueCoins")||priced.get("baseValueCoins").getAsLong()<=0)
                    &&(ingredientValues==null||!ingredientValues.has(id)||ingredientValues.get(id).getAsLong()<=0))
                    throw new IOException("职业装备配方缺少材料基础估值："+entry.getKey()+" → "+id);
            }
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
            if (added.buyBaseCoins() < 1 || added.buyMultiplierBps() < 1 || added.level() < 0 || added.level() > 100 || (added.stock()!=-1&&added.stock()<1) || added.weight() < 1 || !Set.of("common","rare","epic","legendary").contains(added.rarity()))
                throw new IOException("shop/products.toml：商品倍率、等级、库存、权重或稀有度无效：" + entry.getKey());
        }
    }
    private QuotaCurve quotaCurve(JsonObject quotas) {
        List<QuotaCurve.Tier> quotaTiers=new ArrayList<>();
        for(JsonElement e:quotas.getAsJsonArray("tiers")){var t=e.getAsJsonObject();quotaTiers.add(new QuotaCurve.Tier(t.get("upToUnits").isJsonNull()?null:Rational.of(t.get("upToUnits").getAsLong()),t.get("saleMultiplierBps").getAsInt()));}
        return new QuotaCurve(quotaTiers);
    }
    private void validateProfessions() throws IOException {
        JsonObject profession=object("products/professions.toml"),curve=profession.getAsJsonObject("xpCurve");
        if(curve.get("perLevelBase").getAsLong()<1||curve.get("perLevelGrowth").getAsLong()<0||curve.get("post100Growth").getAsLong()<0)
            throw new IOException("职业经验曲线必须为非负增长且首级至少需要1点经验");
        for(String key:List.of("lavaFishingXpMultiplier","voidFishingXpMultiplier")){
            if(!profession.has(key)||!profession.get(key).isJsonPrimitive()||profession.get(key).getAsLong()<0)
                throw new IOException("钓鱼经验倍率必须为非负整数："+key);
        }
        int previous=-1;
        for(JsonElement entry:profession.getAsJsonArray("qualityDifference")){
            JsonObject band=entry.getAsJsonObject();int minimum=band.get("minDifference").getAsInt();
            int sum=0;for(String key:java.util.List.of("copperBps","silverBps","goldBps","diamondBps","prismaticBps")){int chance=band.get(key).getAsInt();if(chance<0)throw new IOException("职业品质等级差与概率配置无效");sum+=chance;}
            if(minimum<=previous||sum>10000)throw new IOException("职业品质等级差与概率配置无效");
            previous=minimum;
        }
        if(profession.getAsJsonArray("qualityDifference").isEmpty()||profession.getAsJsonArray("qualityDifference").get(0).getAsJsonObject().get("minDifference").getAsInt()!=0)
            throw new IOException("职业品质必须从等级差0开始配置");
        for(var entry:profession.getAsJsonObject("sourceItems").entrySet())if(entry.getValue().getAsJsonObject().get("itemLevel").getAsInt()<0)
            throw new IOException("产物等级不能为负数："+entry.getKey());
        for(var entry:profession.getAsJsonObject("hunterMobs").entrySet())if(entry.getValue().getAsJsonObject().get("itemLevel").getAsInt()<0)
            throw new IOException("猎物等级不能为负数："+entry.getKey());
    }
    private void validateQuality() throws IOException {
        JsonArray grades=object("products/quality.toml").getAsJsonArray("grades");
        String[] gradeIds={"ordinary","copper","silver","gold","diamond","prismatic"};
        if(grades.size()!=gradeIds.length)throw new IOException("品质档位必须依次为普通、铜星、银星、金星、钻星、七彩星");
        for(int i=0;i<gradeIds.length;i++){
            JsonObject grade=grades.get(i).getAsJsonObject();
            if(!grade.get("id").getAsString().equals(gradeIds[i]))throw new IOException("品质档位必须依次为普通、铜星、银星、金星、钻星、七彩星");
            int unbreakable=grade.has("unbreakableBps")?grade.get("unbreakableBps").getAsInt():0;
            if(unbreakable<0||unbreakable>10000)throw new IOException("品质无限耐久概率须为0～10000");
        }
    }
    private void validateGoods() throws IOException {
        for(var entry:object("products/goods.toml").getAsJsonObject("items").entrySet())if(entry.getValue().getAsJsonObject().get("itemLevel").getAsInt()<0)
            throw new IOException("职业物品等级不能为负数："+entry.getKey());
    }
    public JsonObject object(String path) { return files.get(path); }
    public boolean constructionWandAcquisitionEnabled() { return acquisitionEnabled("gameplay/construction_wand.toml"); }
    public boolean prefabAcquisitionEnabled() { return acquisitionEnabled("gameplay/prefabs.toml"); }
    private boolean acquisitionEnabled(String path) {
        JsonElement value = object(path).get("acquisitionEnabled");
        if (value == null) return true;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException(path + "：acquisitionEnabled 必须为 true 或 false");
        return value.getAsBoolean();
    }
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
        loadedDigests.put(path,digest(configRoot.resolve(path)));
    }
    public static String digest(Path path) throws IOException {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public String loadedDigest(String path) { return loadedDigests.get(path); }
    public void saveChecked(String path, JsonObject value, String expected) throws IOException {
        if (!files.containsKey(path) || !Objects.equals(expected,loadedDigests.get(path))) throw new IOException("配置已更新，请重新打开编辑器");
        Path target=configRoot.resolve(path);
        if (!expected.equals(digest(target))) throw new IOException("TOML已被人工修改，请先重启载入，再使用实时编辑器");
        TomlConfigIO.writeChecked(target,value,expected);
        files.put(path,value.deepCopy());loadedDigests.put(path,digest(target));
    }
    public void validateScoringRegistry() {
        Set<String> ids=new HashSet<>();
        for (var id:net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet()) ids.add(id.toString());
        for(String id:scoring.points().keySet()) if(!ids.contains(id)) throw new IllegalArgumentException("islands/scoring.toml：方块ID未注册："+id);
        registeredBlocks=Set.copyOf(ids);
    }
    public BlockValue blockValue(String id) {
        if (!registeredBlocks.contains(id)) return null;
        return new BlockValue(scoring.base(id),id);
    }
    public void publishScoring(ScoringSettings next) {
        scoring=next;scoreTiers=next.tiers();blocks.clear();
        next.points().forEach((id,points)->blocks.put(id,new BlockValue(next.base(id),id)));
    }
    public Map<String, JsonObject> all() { return Collections.unmodifiableMap(files); }
    public void useRuntimeQuality(JsonObject quality){files.put("products/quality.toml",quality.deepCopy());}
    public record Product(String id, String name, JsonObject canonical, long baseCoins, long buyBaseCoins, long buyMultiplierBps, String tradeMode,
                          String rarity, int level, int stock, int weight, String category) {
        public boolean canBuy() { return tradeMode.equals("buy_sell") || tradeMode.equals("buy_only"); }
        public boolean canSell() { return tradeMode.equals("buy_sell") || tradeMode.equals("sell_only"); }
        public Product withBuyBase(long value) { return new Product(id,name,canonical,baseCoins,value,buyMultiplierBps,tradeMode,rarity,level,stock,weight,category); }
    }
    public record BlockValue(long points, String group) {}
    public record QuotaUnit(String group, Rational units) {}
}
