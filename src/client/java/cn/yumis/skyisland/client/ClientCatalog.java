package cn.yumis.skyisland.client;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.core.*;
import cn.yumis.skyisland.item.SkyCreativeTabs;
import cn.yumis.skyisland.prefab.PrefabItems;
import cn.yumis.skyisland.shop.ItemEconomy;
import com.google.gson.*;
import com.mojang.serialization.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
    public static volatile int serverEggBps=2500;
    public record Product(String id,String name,ItemStack stack,JsonObject canonical,long base,long buyBase,long buyMultiplierBps,String rarity,int level,boolean buy,boolean sell,String category,String quotaGroup,Rational units,boolean starter) {
        @Override public String name(){return stack.is(Items.ENCHANTED_BOOK)&&name!=null&&!name.isBlank()?name:stack.getHoverName().getString();}
    }
    public record Category(String id,String name,ItemStack icon) {}
    public final Map<String,Product> products = new LinkedHashMap<>();
    private final Map<Item,List<Product>> byItem = new HashMap<>();
    public final Map<Item,String> categories = new HashMap<>();
    public final List<Category> tabs = new ArrayList<>();
    public final Set<String> permanentFavorites = new LinkedHashSet<>(), specialFavorites = new LinkedHashSet<>();
    private final Set<String> importedFavorites = new LinkedHashSet<>();
    public boolean showLockedItems, saleDesired, taskBusy;
    public boolean lotteryBusy;
    public JsonObject lastLotteryResult=new JsonObject();
    public long lotteryRequestSequence=-1;
    public long priceRevision,catalogRevision=-1;
    private long stagingRevision=-1;
    private int nextCatalogPage;
    private final Map<String,Product> stagingProducts=new LinkedHashMap<>();
    public JsonObject configState=new JsonObject();
    public boolean configWanted,configBusy,priceBusy;
    public String configMessage="";
    private cn.yumis.skyisland.config.ScoringSettings scoringSettings;
    public JsonObject editorState = new JsonObject();
    public final Map<String,JsonObject> questDetails = new HashMap<>();
    public final CoinGainHud coinGains=new CoinGainHud();
    public final FloatingEventHud events=new FloatingEventHud();
    public JsonObject state = new JsonObject(),prices = new JsonObject(),scoring = new JsonObject(),quality = new JsonObject(),enchantments = new JsonObject();
    public long flightSnapshotClientTime;
    public List<BlockPos> veinPreview=List.of();
    public JsonObject itemRarities=new JsonObject(),sale=new JsonObject();
    public String lastError="";
    public static final int MIN_CELL = 40, MAX_CELL = 96, DEFAULT_CELL = 48;
    public int cellSize = DEFAULT_CELL, revision;
    private long saleDivisor = 100, minimumBaseCoins = 100, regularBuyMultiplierBps=25000;
    public long prefabAmmunitionPrice=1000;
    public int prefabAmmunitionCredits=1000;
    private QuotaCurve quotaCurve=QuotaCurve.DEFAULT;
    private final Path preferences = FabricLoader.getInstance().getConfigDir().resolve("yumis_sky_island/client.toml");
    public ClientCatalog() {
        try {
            if(!Files.exists(preferences)){
                Files.createDirectories(preferences.getParent());
                try(var defaults=ClientCatalog.class.getResourceAsStream("/defaults/yumis_sky_island/client.toml")){
                    if(defaults==null)throw new IOException("缺少 client.toml 默认配置");Files.copy(defaults,preferences);
                }
                Path legacy=preferences.resolveSibling("client.json");
                if(Files.exists(legacy)){
                    JsonObject old=JsonParser.parseString(Files.readString(legacy,StandardCharsets.UTF_8)).getAsJsonObject();
                    cellSize=old.has("layoutVersion")?Math.clamp(old.get("cellSize").getAsInt(),MIN_CELL,MAX_CELL):Math.max(DEFAULT_CELL,Math.clamp(old.get("cellSize").getAsInt(),MIN_CELL,MAX_CELL));
                    if(old.has("favorites"))for(var id:old.getAsJsonArray("favorites"))importedFavorites.add(id.getAsString());
                    JsonObject data=loadPreferences();data.addProperty("cellSize",cellSize);cn.yumis.skyisland.config.TomlConfigIO.write(preferences,data);
                    Path backup=legacy.resolveSibling("client.json.before-toml.bak");if(!Files.exists(backup))Files.move(legacy,backup);
                }
            }
            JsonObject data=loadPreferences();
            cellSize=Math.clamp(data.get("cellSize").getAsInt(),MIN_CELL,MAX_CELL);
            showLockedItems=data.has("showLockedItems")&&data.get("showLockedItems").getAsBoolean();
            if(data.has("permanentFavorites"))for(var id:data.getAsJsonArray("permanentFavorites"))permanentFavorites.add(id.getAsString());
            if(data.has("specialFavorites"))for(var id:data.getAsJsonArray("specialFavorites"))specialFavorites.add(id.getAsString());
        }catch(Exception error){YumisSkyIsland.LOGGER.warn("无法读取控制面板偏好："+preferences,error);}
    }
    private JsonObject loadPreferences() throws IOException {
        try (var defaults=ClientCatalog.class.getResourceAsStream("/defaults/yumis_sky_island/client.toml")) {
            if(defaults==null)throw new IOException("缺少 client.toml 默认配置");
            return cn.yumis.skyisland.config.TomlConfigIO.readOrRepair(preferences,new String(defaults.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    public void clearConnection() { priceRevision=0;catalogRevision=-1;stagingRevision=-1;nextCatalogPage=0;stagingProducts.clear();scoringSettings=null;configState=new JsonObject();configWanted=false;configBusy=false;priceBusy=false;configMessage="";lotteryBusy=false;lotteryRequestSequence=-1;SkyCreativeTabs.blueprints(List.of()); coinGains.clear(); events.clear(); products.clear(); byItem.clear(); tabs.clear(); categories.clear(); state = new JsonObject(); prices = new JsonObject(); scoring = new JsonObject(); quality = new JsonObject(); enchantments = new JsonObject();itemRarities=new JsonObject();sale=new JsonObject();veinPreview=List.of();lastError="";questDetails.clear();ItemPickerCatalog.clear();editorState=new JsonObject();saleDesired=false;taskBusy=false; revision++; }
    public void receive(String json) {
        JsonObject packet = JsonParser.parseString(json).getAsJsonObject(); String kind = packet.get("kind").getAsString(); JsonElement data = packet.get("data");
        switch (kind) {
            case "catalog" -> {
                long incoming=packet.has("revision")?packet.get("revision").getAsLong():0;int page=packet.get("page").getAsInt();
                if(incoming<catalogRevision)return;
                if(page==0){stagingProducts.clear();stagingRevision=incoming;nextCatalogPage=0;}
                if(incoming!=stagingRevision||page!=nextCatalogPage)return;
                Minecraft mc = Minecraft.getInstance(); if (mc.getConnection() == null) return;
                var ops = RegistryOps.create(JsonOps.INSTANCE,mc.getConnection().registryAccess());
                for (JsonElement element : data.getAsJsonArray()) {
                    JsonObject row = element.getAsJsonObject(); ItemStack stack = ItemStack.CODEC.parse(ops,row.get("stack")).getOrThrow();
                    String catalogName=row.has("name")?row.get("name").getAsString():"";
                    Product p = new Product(row.get("id").getAsString(),catalogName,stack,row.getAsJsonObject("canonical"),row.get("base").getAsLong(),row.get("buyBase").getAsLong(),row.get("buyMultiplierBps").getAsLong(),row.get("rarity").getAsString(),row.get("level").getAsInt(),row.get("buy").getAsBoolean(),row.get("sell").getAsBoolean(),row.get("category").getAsString(),row.get("quotaGroup").getAsString(),Rational.parse(row.get("quotaUnits").getAsString()),row.has("firstAcquisitionPremium")&&row.get("firstAcquisitionPremium").getAsBoolean());
                    stagingProducts.put(p.id(),p);
                }
                nextCatalogPage++;
            }
            case "prices" -> { prices = data.getAsJsonObject(); revision++; }
            case "state" -> {
                JsonObject next=data.getAsJsonObject();
                if(!taskContext(state).equals(taskContext(next)))questDetails.clear();
                if(!partyId(state).equals(partyId(next))){coinGains.clear();events.clear();}
                state=next; flightSnapshotClientTime=System.nanoTime();
                if(!editing()){configWanted=false;configBusy=false;priceBusy=false;if(Minecraft.getInstance().gui.screen() instanceof LiveConfigScreen screen)screen.onClose();}
                if(state.has("lottery")&&state.getAsJsonObject("lottery").get("sequence").getAsLong()>lotteryRequestSequence)lotteryBusy=false;
                if(state.has("fishingTreasureEggBps"))serverEggBps=state.get("fishingTreasureEggBps").getAsInt();
                if(state.has("progression")&&state.getAsJsonObject("progression").has("quests"))for(var element:state.getAsJsonObject("progression").getAsJsonArray("quests")){
                    var row=element.getAsJsonObject();var cached=questDetails.get(row.get("id").getAsString());
                    if(cached!=null){cached.add("status",row.get("status"));cached.add("resetVersion",row.get("resetVersion"));cached.add("goals",row.get("goals").deepCopy());}
                }
                if(!editing()&&state.has("progression")&&state.getAsJsonObject("progression").has("quest")&&!state.getAsJsonObject("progression").get("quest").isJsonNull()){
                    var quest=state.getAsJsonObject("progression").getAsJsonObject("quest");questDetails.put(quest.get("id").getAsString(),quest.deepCopy());
                }
                revision++;
            }
            case "rules" -> {
                long incoming=packet.has("revision")?packet.get("revision").getAsLong():0;if(incoming!=stagingRevision||incoming<catalogRevision)return;
                JsonObject rules=data.getAsJsonObject();
                var score=cn.yumis.skyisland.config.ScoringSettings.parse(rules.getAsJsonObject("scoring"));
                products.clear();products.putAll(stagingProducts);byItem.clear();for(var p:products.values())byItem.computeIfAbsent(p.stack().getItem(),_->new ArrayList<>()).add(p);
                catalogRevision=incoming;priceRevision=rules.has("priceRevision")?rules.get("priceRevision").getAsLong():0;stagingProducts.clear();stagingRevision=-1;
                if(tabs.isEmpty())buildTabs();importFavorites();scoring=rules.getAsJsonObject("scoring");scoringSettings=score;
                quality=rules.getAsJsonObject("quality");enchantments=rules.getAsJsonObject("enchantments");itemRarities=rules.getAsJsonObject("itemRarities");saleDivisor=rules.get("saleDivisor").getAsLong();minimumBaseCoins=rules.get("minimumBaseCoins").getAsLong();regularBuyMultiplierBps=rules.get("regularBuyMultiplierBps").getAsLong();
                if(rules.has("prefabAmmunitionPrice"))prefabAmmunitionPrice=rules.get("prefabAmmunitionPrice").getAsLong();if(rules.has("prefabAmmunitionCredits"))prefabAmmunitionCredits=rules.get("prefabAmmunitionCredits").getAsInt();
                Money.currencyName(rules.get("genshinMode").getAsBoolean()?"摩拉":"金币");if(YumisSkyIsland.CONFIG!=null)YumisSkyIsland.CONFIG.useRuntimeQuality(quality);
                var movedBalls=new ArrayList<String>();if(specialFavorites.removeIf(id->{if(!id.startsWith("yumis_sky_island:biome_ball_"))return false;movedBalls.add(id);return true;})){permanentFavorites.addAll(movedBalls);save();}
                List<QuotaCurve.Tier> tiers=new ArrayList<>();for(JsonElement e:rules.getAsJsonArray("quotas")){var t=e.getAsJsonObject();tiers.add(new QuotaCurve.Tier(t.get("upToUnits").isJsonNull()?null:Rational.of(t.get("upToUnits").getAsLong()),t.get("saleMultiplierBps").getAsInt()));}quotaCurve=new QuotaCurve(tiers);updateCreativeBlueprints(rules);revision++;
            }
            case "sale_begin" -> {saleDesired=true;panel().showSale();}
            case "sale_preview" -> {
                if(saleDesired){sale=data.getAsJsonObject();lastError="";if(Minecraft.getInstance().gui.screen() instanceof ControlPanelScreen panel)panel.saleUpdated();revision++;}
            }
            case "sale_done" -> {sale=new JsonObject();if(saleDesired&&Minecraft.getInstance().gui.screen() instanceof ControlPanelScreen panel)panel.cancelSale(false);saleDesired=false;revision++;}
            case "request_error" -> {lastError=data.getAsJsonObject().get("message").getAsString();lotteryBusy=false;if(Minecraft.getInstance().gui.screen() instanceof ControlPanelScreen panel)panel.saleFailed();revision++;}
            case "lottery_result" -> {
                lastLotteryResult=data.getAsJsonObject().deepCopy();lotteryBusy=false;lastError="";revision++;
                String action=lastLotteryResult.has("action")?lastLotteryResult.get("action").getAsString():"";
                if(action.equals("draw")||action.equals("draw_10")){
                    var mc=Minecraft.getInstance();
                    var current=mc.gui.screen();
                    Screen parent=current instanceof LotteryResultScreen existing?existing.parent():current;
                    mc.gui.setScreen(new LotteryResultScreen(parent,lastLotteryResult,this));
                }
            }
            case "coin_gain", "coin_change" -> {var gain=data.getAsJsonObject();if(gain.get("partyId").getAsString().equals(partyId(state)))coinGains.receive(gain.get("sequence").getAsLong(),gain.get("amount").getAsLong());}
            case "purchase_notice" -> {var row=data.getAsJsonObject();if(row.get("partyId").getAsString().equals(partyId(state))){Product product=products.get(row.get("itemId").getAsString());String item=product==null?new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(row.get("itemId").getAsString()))).getHoverName().getString():product.stack().getHoverName().getString();events.receive(row.get("actor").getAsString()+"花了"+Money.format(row.get("cost").getAsLong())+"购买了"+item+" ×"+row.get("quantity").getAsInt(),0xFFE4CF91);}}
            case "death_notice" -> {var row=data.getAsJsonObject();if(row.get("partyId").getAsString().equals(partyId(state)))events.receive(row.get("actor").getAsString()+"死亡了，扣除"+Money.format(row.get("cost").getAsLong())+"作为医药费。",0xFFFFA6A6);}
            case "profession_xp" -> {var row=data.getAsJsonObject();events.receiveXp(row.get("profession").getAsString(),new java.math.BigInteger(row.get("amount").getAsString()));}
            case "open" -> panel();
            case "resident_open" -> panel().showResident(data.getAsJsonObject().has("resident")?data.getAsJsonObject().get("resident").getAsString():"");
            case "resident_notice" -> events.receive(data.getAsJsonObject().get("text").getAsString(),0xFF9ADBD1);
            case "profession_open" -> Minecraft.getInstance().gui.setScreen(new ProfessionScreen(this));
            case "accessory_open" -> Minecraft.getInstance().gui.setScreen(new AccessoryScreen(this));
            case "vein_preview" -> {var positions=new ArrayList<BlockPos>();for(var value:data.getAsJsonObject().getAsJsonArray("blocks")){var point=value.getAsJsonArray();positions.add(new BlockPos(point.get(0).getAsInt(),point.get(1).getAsInt(),point.get(2).getAsInt()));}veinPreview=List.copyOf(positions);}
            default -> { }
        }
    }
    private void updateCreativeBlueprints(JsonObject rules){
        List<ItemStack> validated=new ArrayList<>();
        if(rules.has("creativeBlueprints"))for(JsonElement entry:rules.getAsJsonArray("creativeBlueprints")){
            String id=entry.getAsString();
            Product product=products.get("yumis_sky_island:prefab/"+id);
            if(product!=null&&product.stack().is(PrefabItems.BLUEPRINT)&&id.equals(PrefabItems.templateId(product.stack())))
                validated.add(product.stack());
        }
        SkyCreativeTabs.blueprints(validated);
        var connection=Minecraft.getInstance().getConnection();
        if(connection!=null)CreativeModeTabs.tryRebuildTabContents(connection.enabledFeatures(),true,connection.registryAccess());
    }
    private void buildTabs() {
        Minecraft mc = Minecraft.getInstance(); if (mc.getConnection() == null) return;
        CreativeModeTabs.tryRebuildTabContents(mc.getConnection().enabledFeatures(),false,mc.getConnection().registryAccess());
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) if (tab.getType() == CreativeModeTab.Type.CATEGORY && tab.shouldDisplay()) {
            String id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).toString(); tabs.add(new Category(id,tab.getDisplayName().getString(),tab.getIconItem()));
            for (ItemStack item : tab.getDisplayItems()) categories.putIfAbsent(item.getItem(),id);
        }
        Item ball=cn.yumis.skyisland.item.ModItems.BIOME_BALLS.get("plains");
        if(ball==null&&!cn.yumis.skyisland.item.ModItems.BIOME_BALLS.isEmpty())ball=cn.yumis.skyisland.item.ModItems.BIOME_BALLS.values().iterator().next();
        tabs.add(new Category("biome_ball","【生态球】",ball==null?new ItemStack(Items.ENDER_PEARL):new ItemStack(ball)));
        tabs.add(new Category("other","其他商品",new ItemStack(Items.CHEST)));
    }
    public String category(Product p) { if(p.id().startsWith("yumis_sky_island:biome_ball_")||p.category().equals("biome_ball"))return "biome_ball"; return p.category().startsWith("minecraft:") || p.category().equals("other") ? p.category() : categories.getOrDefault(p.stack().getItem(),"other"); }
    public List<Category> shopTabs() { return tabs.stream().filter(c->products.values().stream().anyMatch(p->p.buy()&&category(p).equals(c.id()))).toList(); }
    public int level() { return state.has("level") ? state.get("level").getAsInt() : 0; }
    public long wallet() { return state.has("wallet") ? state.get("wallet").getAsLong() : 0; }
    public int bps(Product p) { return prices.has(p.id()) ? prices.get(p.id()).getAsInt() : 10000; }
    public Rational value(Product p,ItemStack stack) { return quality.has("grades") && enchantments.has("entries") ? ItemEconomy.fullValue(p.base(),bps(p),stack,quality,enchantments,minimumBaseCoins) : Rational.of(Math.max(minimumBaseCoins,Rational.of(p.base()).multiply(Rational.of(bps(p),10000)).floorLongExact())); }
    public long price(Product p){return price(p,0);}
    public long price(Product p,int shop) {
        if(p.stack().is(cn.yumis.skyisland.prefab.PrefabItems.AMMUNITION))return prefabAmmunitionPrice;
        boolean acquired=false;if(state.has("acquiredStarters"))for(var e:state.getAsJsonArray("acquiredStarters"))if(e.getAsString().equals(p.id()))acquired=true;
        Rational purchase=quality.has("grades")&&enchantments.has("entries")?ItemEconomy.fullValue(p.buyBase(),bps(p),p.stack(),quality,enchantments,minimumBaseCoins):Rational.of(Math.max(minimumBaseCoins,Rational.of(p.buyBase()).multiply(Rational.of(bps(p),10000)).floorLongExact()));
        boolean permanentPremium=shop==0&&!p.id().startsWith("yumis_sky_island:biome_ball_");
        return purchase.multiply(Rational.of(p.starter()&&acquired?10000:p.buyMultiplierBps(),10000)).multiply(Rational.of(permanentPremium?regularBuyMultiplierBps:10000,10000)).multiply(Rational.of(residentBuyBps(BuiltInRegistries.ITEM.getKey(p.stack().getItem()).toString()),10000)).floorLongExact();
    }
    private static String partyId(JsonObject value){return value.has("partyId")?value.get("partyId").getAsString():"";}
    public ItemStack decode(JsonElement data) { var connection=Minecraft.getInstance().getConnection();return data==null||data.isJsonNull()||connection==null?ItemStack.EMPTY:ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,connection.registryAccess()),data).getOrThrow(); }
    public String rarity(ItemStack stack) {String prize=cn.yumis.skyisland.shop.LotteryItemRarity.get(stack);if(!prize.isEmpty())return prize;var p=match(stack);if(p!=null)return p.rarity();String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();int wandTier=cn.yumis.skyisland.wand.ConstructionWands.specialTier(stack);return itemRarities.has(id)?itemRarities.get(id).isJsonPrimitive()?itemRarities.get(id).getAsString():itemRarities.getAsJsonObject(id).get("rarity").getAsString():wandTier>=0?cn.yumis.skyisland.wand.ConstructionWands.rarity(wandTier):"common";}
    public int stock(Product p) { return state.has("stock") ? state.getAsJsonObject("stock").has(p.id()) ? state.getAsJsonObject("stock").get(p.id()).getAsInt() : 0 : 0; }
    public String stockText(Product p){return stock(p)==-1?"无限":Integer.toString(stock(p));}
    public boolean hidden() { return state.has("hidden") && state.get("hidden").getAsBoolean(); }
    public boolean specialAllowed(){return state.has("progression")&&state.getAsJsonObject("progression").has("specialAllowed")&&state.getAsJsonObject("progression").get("specialAllowed").getAsBoolean();}
    public Product match(ItemStack stack) {
        List<Product> candidates = byItem.get(stack.getItem()); if (candidates == null) return null;
        for (Product product : candidates) {
            if(ItemEconomy.matchesCanonical(stack,product.stack(),product.canonical().getAsJsonObject("componentPatch")))return product;
        }
        return null;
    }
    public String productName(String id){Product product=products.get(id);if(product!=null)return product.stack().getHoverName().getString();var key=Identifier.tryParse(id);if(key!=null&&BuiltInRegistries.ITEM.containsKey(key))return new ItemStack(BuiltInRegistries.ITEM.getValue(key)).getHoverName().getString();return id;}
    private static String fruitProfession(ItemStack stack){for(var entry:cn.yumis.skyisland.item.ModItems.FRUITS.entrySet())if(stack.is(entry.getValue()))return entry.getKey();return null;}
    public void tooltip(ItemStack stack,List<Component> lines) {
        String fruit=fruitProfession(stack);
        if(fruit!=null){
            int used=0;if(state.has("consumedFruits")&&state.getAsJsonObject("consumedFruits").has(fruit))used=state.getAsJsonObject("consumedFruits").get(fruit).getAsInt();
            lines.add(Component.literal(FruitText.line(fruit,used)).withStyle(ChatFormatting.AQUA));
        }
        if(products.isEmpty()&&itemRarities.isEmpty()&&cn.yumis.skyisland.shop.LotteryItemRarity.get(stack).isEmpty())return;
        String rarity=rarity(stack);int color=RarityColors.color(rarity)&0xFFFFFF;
        if(!lines.isEmpty())lines.set(0,lines.getFirst().copy().withStyle(s->s.withColor(color)));
        lines.add(Component.literal("种类稀有度："+RarityColors.name(rarity)).withStyle(s->s.withColor(color)));
        Product p = match(stack);
        int grade = ItemEconomy.grade(stack);
        if (grade > 0 && quality.has("grades")) lines.add(Component.literal("制造品质：" + quality.getAsJsonArray("grades").get(grade).getAsJsonObject().get("name").getAsString()).withStyle(grade == 2 ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        if (p != null) {
            Rational full = value(p,stack), durability = stack.isDamageableItem() ? Durability.multiplier(stack.getMaxDamage()-stack.getDamageValue(),stack.getMaxDamage()) : Rational.ONE;
            lines.add(Component.literal("价值：" + Money.format(full.multiply(durability).floorLongExact()) + " / 件").withStyle(ChatFormatting.YELLOW));
            if (p.sell() && ItemEconomy.safeToSell(stack)) {
                Rational normal = full.multiply(durability).divide(Rational.of(saleDivisor)).multiply(Rational.of(residentSaleBps(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()),10000)), used = Rational.ZERO;
                if (state.has("quotas") && state.getAsJsonObject("quotas").has(p.quotaGroup())) used = Rational.parse(state.getAsJsonObject("quotas").get(p.quotaGroup()).getAsString());
                long quote = quotaCurve.quote(used,stack.getCount(),p.units(),normal).floorLongExact();
                lines.add(Component.literal("行情后基础价值：" + decimal(normal) + Money.currencyName() + " / 件").withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal("本队当前阶梯：" + Money.format(quote) + " / " + stack.getCount() + "件").withStyle(ChatFormatting.GREEN));
            }
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.dimension().equals(Level.OVERWORLD) && stack.getItem() instanceof BlockItem item && scoringSettings!=null) {
            String block = BuiltInRegistries.BLOCK.getKey(item.getBlock()).toString();long base=scoringSettings.base(block);
            if (base > 0 && state.has("counts")) {
                long count=state.getAsJsonObject("counts").has(block)?state.getAsJsonObject("counts").get(block).getAsLong():0;
                long placed=item.getBlock() instanceof BedBlock || item.getBlock() instanceof DoorBlock || item.getBlock() instanceof DoublePlantBlock ? 2 : 1;
                lines.add(Component.literal("放置岛经验：+" + decimal(BlockScorer.marginal(count,placed,base,scoringSettings.tiers())) + "点（主世界当前存量）").withStyle(ChatFormatting.AQUA));
            }
        }
    }
    public static String decimal(Rational n) { return new BigDecimal(n.numerator()).divide(new BigDecimal(n.denominator()),4,RoundingMode.DOWN).stripTrailingZeros().toPlainString(); }
    public Set<String> favorites(int shop){return shop==0?permanentFavorites:specialFavorites;}
    public void favorite(String product,int shop){var group=favorites(shop);if(!group.remove(product))group.add(product);revision++;save();}
    private void importFavorites(){
        if(importedFavorites.isEmpty())return;
        for(String id:importedFavorites){var p=products.get(id);(p!=null&&!p.rarity().equals("common")&&!p.id().startsWith("yumis_sky_island:biome_ball_")?specialFavorites:permanentFavorites).add(id);}
        importedFavorites.clear();save();
    }
    public void save(){
        try{
            JsonObject data=Files.exists(preferences)?loadPreferences():new JsonObject();
            data.addProperty("layoutVersion",3);data.addProperty("cellSize",cellSize);data.addProperty("showLockedItems",showLockedItems);
            JsonArray regular=new JsonArray(),special=new JsonArray();permanentFavorites.forEach(regular::add);specialFavorites.forEach(special::add);
            data.add("permanentFavorites",regular);data.add("specialFavorites",special);
            cn.yumis.skyisland.config.TomlConfigIO.write(preferences,data);
        }catch(IOException error){YumisSkyIsland.LOGGER.warn("无法保存控制面板偏好",error);lastError="偏好保存失败，请检查 client.toml";}
    }
    public boolean canBuy(Product p,int shop,int quantity){
        if(!p.buy()||quantity<1||level()<p.level()||!state.has("partyId")||state.get("partyId").getAsString().isEmpty())return false;
        if(shop==0&&!p.rarity().equals("common")&&!p.id().startsWith("yumis_sky_island:biome_ball_"))return false;
        if(shop==1&&(!specialAllowed()||!state.has("stock")||!state.getAsJsonObject("stock").has(p.id())||(stock(p)!=-1&&stock(p)<quantity)))return false;
        try{return wallet()>=Math.multiplyExact(price(p,shop),quantity);}catch(ArithmeticException error){return false;}
    }
    public void purchase(Product p,int shop,int quantity){
        if(!canBuy(p,shop,quantity)){lastError="等级、"+Money.currencyName()+"、权限或库存不足";return;}
        if(net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(cn.yumis.skyisland.network.ShopPayloads.Purchase.TYPE))
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new cn.yumis.skyisland.network.ShopPayloads.Purchase(p.id(),quantity,shop==0?"permanent":"special",state.get("epoch").getAsLong(),UUID.fromString(state.get("partyId").getAsString()),state.get("islandEpoch").getAsLong(),priceRevision,residentRevision()));
    }
    public JsonObject residents(){return state.has("residents")?state.getAsJsonObject("residents"):new JsonObject();}
    public long residentRevision(){return residents().has("effectVersion")?residents().get("effectVersion").getAsLong():0;}
    private Set<String> residentExperts(){Set<String> jobs=new HashSet<>();if(residents().has("experts"))for(var value:residents().getAsJsonArray("experts"))jobs.add(value.getAsString());return jobs;}
    public int residentSaleBps(String item){var npc=residents();return npc.has("ordinary")?ResidentRules.saleBps(item,npc.get("ordinary").getAsInt(),residentExperts(),npc.get("ordinarySaleBps").getAsInt(),npc.get("expertSaleBps").getAsInt()):10000;}
    private int residentBuyBps(String item){return ResidentRules.discounted(item,residentExperts())?10000-residents().get("rawDiscountBps").getAsInt():10000;}
    private ControlPanelScreen panel(){
        var minecraft=Minecraft.getInstance();if(minecraft.gui.screen() instanceof ControlPanelScreen existing)return existing;
        ControlPanelScreen created=new ControlPanelScreen(this);minecraft.gui.setScreen(created);return created;
    }
    public long taskRevision(){return state.has("progression")?state.getAsJsonObject("progression").get("revision").getAsLong():editorState.has("revision")?editorState.get("revision").getAsLong():1;}
    public boolean admin(){return state.has("admin")&&state.get("admin").getAsBoolean();}
    public boolean editing(){return admin()&&state.has("adminEditing")&&state.get("adminEditing").getAsBoolean();}
    public void editConfig(String action,JsonObject body){
        if(!editing()){lastError="请先使用 /is admin edit on 开启编辑模式";return;}
        if(net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(cn.yumis.skyisland.network.ConfigEditPayloads.Request.TYPE))net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new cn.yumis.skyisland.network.ConfigEditPayloads.Request(action,body.toString()));
    }
    public void receiveConfig(String json){
        JsonObject data=JsonParser.parseString(json).getAsJsonObject();configBusy=false;priceBusy=false;
        if(data.get("kind").getAsString().equals("error")){configMessage=data.get("message").getAsString();lastError=configMessage;return;}
        if(!editing())return;
        configMessage=data.has("saved")&&data.get("saved").getAsBoolean()?"已保存并立即生效":"";
        if(data.get("kind").getAsString().equals("group")&&configWanted){configState=data;var mc=Minecraft.getInstance();if(mc.gui.screen() instanceof LiveConfigScreen screen)screen.updated();else mc.gui.setScreen(new LiveConfigScreen(this));}
        if(data.get("kind").getAsString().equals("price")&&Minecraft.getInstance().gui.screen() instanceof ControlPanelScreen panel)panel.priceUpdated(data);
    }
    public void task(String action,JsonObject data){
        if(net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(cn.yumis.skyisland.network.TaskPayloads.Request.TYPE))
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new cn.yumis.skyisland.network.TaskPayloads.Request(action,data.toString()));
    }
    public void receiveTasks(String json){
        JsonObject packet=JsonParser.parseString(json).getAsJsonObject(),data=packet.getAsJsonObject("data");String kind=packet.get("kind").getAsString();
        switch(kind){
            case "editor" -> {boolean wasEditing=editorState.has("editing")&&editorState.get("editing").getAsBoolean();if(data.get("editing").getAsBoolean()!=wasEditing)lastError="";editorState=data;if(data.has("open")&&data.get("open").getAsBoolean())panel().selectSection(2);}
            case "detail" -> {if(data.get("revision").getAsLong()>=taskRevision())questDetails.put(data.get("id").getAsString(),data);}
            case "saved" -> {taskBusy=false;lastError="";questDetails.clear();if(Minecraft.getInstance().gui.screen() instanceof ControlPanelScreen panel)panel.taskSaved(data);}
            case "error" -> {taskBusy=false;lastError=data.get("message").getAsString();}
            default -> {}
        }
        revision++;
    }
    private static String taskContext(JsonObject state){
        if(!state.has("progression"))return "";
        JsonObject p=state.getAsJsonObject("progression");
        return state.get("partyId")+"/"+state.get("islandEpoch")+"/"+p.get("revision")+"/"+p.get("completed")+"/"+p.get("ready")+"/"+p.get("resetVersions")+"/"+state.get("taskEditing");
    }
}
