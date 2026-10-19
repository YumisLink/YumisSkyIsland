package cn.yumis.skyisland.server;

import cn.yumis.skyisland.config.*;
import cn.yumis.skyisland.core.*;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.network.ConfigEditPayloads;
import cn.yumis.skyisland.prefab.PrefabItems;
import cn.yumis.skyisland.shop.ItemEconomy;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;

/** One checked products.toml write publishes a complete economic definition. */
public final class ItemManagementService {
    private static final String FILE="shop/products.toml",PREFIX="yumis_sky_island:prefab/";
    private static final List<String> SOURCES=List.of(FILE,"shop/item_levels.toml","shop/rarities.toml","shop/shops.toml","shop/enchantments.toml","products/quality.toml","gameplay/prefabs.toml","gameplay/lottery.toml");
    private static final Set<String> REQUEST_FIELDS=Set.of("product","stack","changes","revision","digest","sourceDigests","prefabDigest");
    private static final Set<String> VARIANT_COMPONENTS=Set.of("minecraft:potion_contents","minecraft:stored_enchantments","minecraft:enchantments","minecraft:dyed_color","minecraft:instrument","minecraft:ominous_bottle_amplifier","minecraft:fireworks","minecraft:firework_explosion","minecraft:banner_patterns","minecraft:base_color","minecraft:trim","minecraft:block_state","minecraft:painting/variant");
    private final SkyServer rt;
    private record Candidate(GameConfig.Product product,ItemStack stack,boolean exists,JsonObject changes){}
    public ItemManagementService(SkyServer rt){this.rt=rt;}
    public void request(ServerPlayer player,String action,JsonObject body)throws IOException{
        rt.editors.require(player);
        for(String key:body.keySet())if(!REQUEST_FIELDS.contains(key))throw new IllegalArgumentException("未知管理请求字段："+key);
        if(action.equals("item_open")&&!body.has("product")&&!body.has("stack")){send(player,header());return;}
        if(!action.equals("item_open"))requireCurrent(body,action.equals("item_save"));
        Candidate candidate=candidate(body);
        if(action.equals("item_save")){save(player,body,candidate);return;}
        send(player,detail(player,candidate,false));
    }
    private JsonObject header(){
        JsonObject out=new JsonObject();out.addProperty("kind","item");out.addProperty("revision",rt.rulesRevision);out.addProperty("digest",rt.config.loadedDigest(FILE));
        JsonObject digests=new JsonObject();for(String file:SOURCES)digests.addProperty(file,rt.config.loadedDigest(file));out.add("sourceDigests",digests);
        out.addProperty("prefabDigest",rt.prefabs.catalog.sourceDigest());return out;
    }
    private void requireCurrent(JsonObject body,boolean disk)throws IOException{
        if(!body.has("revision")||body.get("revision").getAsLong()!=rt.rulesRevision||!Objects.equals(text(body,"digest"),rt.config.loadedDigest(FILE)))throw new IllegalArgumentException("配置已更新，请重新选择物品后编辑");
        if(!disk)return;
        if(!rt.store.available())throw new IllegalStateException("数据正在保存，请稍后重试");
        JsonObject digests=body.getAsJsonObject("sourceDigests");if(digests==null)throw new IllegalArgumentException("缺少配置摘要，请重新打开管理器");
        for(String file:SOURCES)rt.config.requireDiskCurrent(file,text(digests,file));
        rt.prefabs.catalog.requireSourceCurrent(text(body,"prefabDigest"));
    }
    private Candidate candidate(JsonObject body){
        ItemEconomy.Entry entry=null;ItemStack stack;boolean exists;
        if(body.has("product")){
            entry=rt.economy.entries.get(body.get("product").getAsString());if(entry==null)throw new IllegalArgumentException("商品不存在，请刷新目录");stack=entry.template().copyWithCount(1);exists=true;
        }else{
            if(!body.has("stack"))throw new IllegalArgumentException("请先选择物品");
            stack=rt.economy.decode(body.get("stack")).copyWithCount(1);
            if(stack.isEmpty()||!BuiltInRegistries.ITEM.containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem())))throw new IllegalArgumentException("物品未注册");
            for(var existing:rt.economy.entries.values())if(ItemStack.isSameItemSameComponents(stack,existing.template())){entry=existing;break;}
            exists=entry!=null;
            if(!exists)validateNew(stack);
        }
        var p=entry==null?newProduct(stack):entry.product();
        if(stack.is(PrefabItems.AMMUNITION))p=p.withBuyBase(rt.economy.ammunitionPrice());
        JsonObject changes=body.has("changes")?body.getAsJsonObject("changes").deepCopy():new JsonObject();
        for(String key:changes.keySet())if(!ProductOverrides.FIELDS.contains(key))throw new IllegalArgumentException("字段不允许编辑："+key);
        if(changes.has("buildFee")&&!p.id().startsWith(PREFIX))throw new IllegalArgumentException("只有建筑图纸提供成品费");
        JsonObject document=new JsonObject(),table=new JsonObject();table.add(p.id(),changes);document.add("productOverrides",table);
        p=ProductOverrides.apply(p,document);validatePrice(p,stack);
        if(p.id().startsWith(PREFIX)&&!rt.prefabs.catalog.registeredBlueprints().contains(p.id().substring(PREFIX.length())))throw new IllegalArgumentException("建筑模板未安装或未通过验收");
        return new Candidate(p,stack,exists,changes);
    }
    private void validateNew(ItemStack stack){
        JsonObject encoded=rt.economy.encode(stack).getAsJsonObject();
        JsonObject patch=encoded.has("components")?encoded.getAsJsonObject("components"):new JsonObject();
        if(patch.toString().length()>16000)throw new IllegalArgumentException("组件过大，无法安全分包展示");
        for(String key:patch.keySet())if(!VARIANT_COMPONENTS.contains(key))throw new IllegalArgumentException("此组件变体须由已有配置或已验收建筑目录提供："+key);
        if(stack.has(DataComponents.CUSTOM_DATA)||!ItemEconomy.safeToSell(stack))throw new IllegalArgumentException("请选择无自定义身份、命名和容器内容的标准物品");
        ItemStack decoded=rt.economy.decode(encoded);if(!ItemStack.isSameItemSameComponents(stack,decoded))throw new IllegalArgumentException("物品组件往返校验失败");
    }
    private GameConfig.Product newProduct(ItemStack stack){
        String item=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();JsonObject encoded=rt.economy.encode(stack).getAsJsonObject(),canonical=new JsonObject();
        canonical.addProperty("itemId",item);canonical.add("componentPatch",encoded.has("components")?encoded.getAsJsonObject("components").deepCopy():new JsonObject());
        String id=item;
        if(!ItemStack.isSameItemSameComponents(stack,stack.getItem().getDefaultInstance())||rt.economy.entries.containsKey(id)){
            try{id+="/variant_"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sorted(canonical).toString().getBytes(StandardCharsets.UTF_8)));}
            catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        }
        if(id.length()>256||rt.economy.entries.containsKey(id))throw new IllegalArgumentException("商品标识冲突或过长");
        return new GameConfig.Product(id,stack.getHoverName().getString(),canonical,100,100,10000,"buy_sell","common",0,64,1,"other");
    }
    private void validatePrice(GameConfig.Product p,ItemStack stack){
        var entry=new ItemEconomy.Entry(p,stack);var party=new IslandState.Party();
        if(!stack.is(PrefabItems.AMMUNITION)){rt.economy.buyPrice(entry,party,true);rt.economy.buyPrice(entry,party,false);}
        int maximum=rt.config.object("shop/shops.toml").getAsJsonObject("market").get("maxPriceBps").getAsInt();
        var full=ItemEconomy.fullValue(p.baseCoins(),maximum,stack,rt.config.object("products/quality.toml"),rt.config.object("shop/enchantments.toml"),rt.config.minimumBaseCoins);
        if(full.signum()<=0)throw new IllegalArgumentException("组件或附魔无法合法估值");
        full.floorLongExact();
        if(!stack.is(PrefabItems.AMMUNITION))ItemEconomy.fullValue(p.buyBaseCoins(),maximum,stack,rt.config.object("products/quality.toml"),rt.config.object("shop/enchantments.toml"),rt.config.minimumBaseCoins).multiply(Rational.of(p.buyMultiplierBps(),10000)).multiply(Rational.of(Math.max(10000,rt.config.regularBuyMultiplierBps),10000)).floorLongExact();
    }
    private JsonObject detail(ServerPlayer player,Candidate c,boolean saved){
        var p=c.product();var out=header();out.addProperty("saved",saved);out.addProperty("product",p.id());out.addProperty("name",p.name());out.add("stack",rt.economy.encode(c.stack()));out.add("fields",ProductOverrides.describe(p));
        out.addProperty("exists",c.exists());out.addProperty("fixedPurchase",c.stack().is(PrefabItems.AMMUNITION));out.addProperty("blueprint",p.id().startsWith(PREFIX));
        out.addProperty("source",p.id().startsWith(PREFIX)?"外置已验收建筑目录 + products.toml 管理覆盖":c.exists()?"products.toml（管理覆盖优先）":"注册物品，未定价");
        if(p.id().startsWith(PREFIX)){var d=rt.prefabs.catalog.definitions.get(p.id().substring(PREFIX.length()));out.getAsJsonObject("fields").addProperty("buildFee",ProductOverrides.number(c.changes(),"buildFee",d.buildFee(),1,Long.MAX_VALUE));}
        var active=rt.parties.active(player.getUUID());var party=active==null?new IslandState.Party():active;var entry=new ItemEconomy.Entry(p,c.stack());
        // A missing team has neither resident effects nor a market premium.
        long permanent,special;Rational normal;
        if(active==null){
            var buy=ItemEconomy.fullValue(p.buyBaseCoins(),10000,c.stack(),rt.config.object("products/quality.toml"),rt.config.object("shop/enchantments.toml"),rt.config.minimumBaseCoins).multiply(Rational.of(p.buyMultiplierBps(),10000));
            special=c.stack().is(PrefabItems.AMMUNITION)?p.buyBaseCoins():buy.floorLongExact();permanent=c.stack().is(PrefabItems.AMMUNITION)?p.buyBaseCoins():buy.multiply(Rational.of(p.id().startsWith("yumis_sky_island:biome_ball_")?10000:rt.config.regularBuyMultiplierBps,10000)).floorLongExact();
            normal=ItemEconomy.fullValue(p.baseCoins(),10000,c.stack(),rt.config.object("products/quality.toml"),rt.config.object("shop/enchantments.toml"),rt.config.minimumBaseCoins).divide(Rational.of(rt.config.saleDivisor));
        }else{permanent=c.stack().is(PrefabItems.AMMUNITION)?p.buyBaseCoins():rt.economy.buyPrice(entry,party,!p.id().startsWith("yumis_sky_island:biome_ball_"));special=c.stack().is(PrefabItems.AMMUNITION)?p.buyBaseCoins():rt.economy.buyPrice(entry,party,false);normal=rt.economy.full(entry,c.stack()).divide(Rational.of(rt.config.saleDivisor));}
        String item=BuiltInRegistries.ITEM.getKey(c.stack().getItem()).toString();normal=normal.multiply(rt.config.cropSales.multiplier(item,0)).multiply(Rational.of(active==null?10000:rt.residents.saleBps(active,item),10000));
        Rational used=active==null?Rational.ZERO:Rational.parse(active.quota.getOrDefault(item,"0"));
        out.addProperty("permanentPrice",permanent);out.addProperty("specialPrice",special);out.addProperty("normalSale",normal.toString());out.addProperty("currentSale",rt.config.quotaCurve.quote(used,1,Rational.ONE,normal).floorLongExact());
        out.addProperty("previewContext",active==null?"中性行情，无队伍加成":"当前队伍行情、居民效果与已用额度");
        out.addProperty("ammunitionCredits",rt.prefabs.rules.ammunitionCredits());return out;
    }
    private void save(ServerPlayer player,JsonObject body,Candidate c)throws IOException{
        var p=c.product();JsonObject prepared=detail(player,c,false);JsonObject next=rt.config.object(FILE).deepCopy();
        if(p.id().startsWith(PREFIX)){
            var definition=rt.prefabs.catalog.definitions.get(p.id().substring(PREFIX.length()));
            if(!rt.prefabs.catalog.available(definition)||!Objects.equals(definition.sha256(),GameConfig.digest(rt.prefabs.catalog.file(definition))))throw new IOException("模板文件已变化，请重新验收后编辑");
        }
        if(!c.exists()){
            var row=new JsonObject();row.addProperty("productId",p.id());row.addProperty("name",p.name());row.addProperty("categoryHint","管理员新增");row.addProperty("baseValueCoins",p.baseCoins());row.addProperty("buyBaseValueCoins",p.buyBaseCoins());row.addProperty("buyMultiplierBps",p.buyMultiplierBps());row.addProperty("tradeMode",p.tradeMode());row.addProperty("dailyStock",p.stock());row.addProperty("offerWeight",1);row.addProperty("shopCategory",p.category());row.addProperty("priceOrigin","administrator");row.add("canonicalStack",p.canonical().deepCopy());next.getAsJsonObject("products").add(p.id(),row);
        }
        JsonObject record=ProductOverrides.describe(p);
        if(p.id().startsWith(PREFIX))record.addProperty("buildFee",prepared.getAsJsonObject("fields").get("buildFee").getAsLong());
        if(!next.has("productOverrides"))next.add("productOverrides",new JsonObject());next.getAsJsonObject("productOverrides").add(p.id(),record);
        Runnable publishBlueprint=p.id().startsWith(PREFIX)?rt.prefabs.catalog.prepareEconomics(p,record.get("buildFee").getAsLong()):()->{};
        // All computation, source checks and price validation complete before the only disk mutation.
        rt.config.saveChecked(FILE,next,text(body,"digest"));
        rt.config.products.put(p.id(),p);if(c.exists())rt.economy.replaceProduct(p);else rt.economy.addProduct(p,c.stack());
        publishBlueprint.run();
        rt.priceRevision++;rt.configurationChanged(FILE,()->{if(rt.editors.enabled(player)&&rt.server.getPlayerList().getPlayer(player.getUUID())==player)send(player,detail(player,new Candidate(p,c.stack(),true,record),true));});
    }
    private static JsonElement sorted(JsonElement value){
        if(value.isJsonObject()){var out=new JsonObject();value.getAsJsonObject().keySet().stream().sorted().forEach(key->out.add(key,sorted(value.getAsJsonObject().get(key))));return out;}
        if(value.isJsonArray()){var out=new JsonArray();for(var item:value.getAsJsonArray())out.add(sorted(item));return out;}return value;
    }
    private static String text(JsonObject object,String key){return object.has(key)?object.get(key).getAsString():"";}
    private static void send(ServerPlayer player,JsonObject out){if(ServerPlayNetworking.canSend(player,ConfigEditPayloads.Snapshot.TYPE))ServerPlayNetworking.send(player,new ConfigEditPayloads.Snapshot(out.toString()));}
}
