package cn.yumis.skyisland.server;

import cn.yumis.skyisland.config.*;
import cn.yumis.skyisland.network.ConfigEditPayloads;
import cn.yumis.skyisland.shop.ItemEconomy;
import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ConfigEditorService {
    private final SkyServer rt;
    private final Map<String,Long> revisions=new HashMap<>();
    public ConfigEditorService(SkyServer rt){this.rt=rt;}
    public void request(ServerPlayer player,ConfigEditPayloads.Request request){
        try {
            rt.editors.require(player);
            JsonObject body=JsonParser.parseString(request.json()).getAsJsonObject();
            switch(request.action()){
                case "open" -> sendGroup(player,body.has("group")?body.get("group").getAsString():"economy",false);
                case "save" -> saveGroup(player,body);
                case "price_open" -> sendPrice(player,body.get("product").getAsString(),false);
                case "price_save" -> savePrice(player,body);
                case "product_open" -> sendProductForm(player,false);
                case "product_preview" -> sendProductPreview(player,body.get("itemId").getAsString());
                case "product_add" -> addProduct(player,body);
                default -> throw new IllegalArgumentException("未知配置编辑操作");
            }
        } catch(IOException|RuntimeException error){JsonObject data=new JsonObject();data.addProperty("kind","error");data.addProperty("message",error.getMessage()==null?"配置请求无效":error.getMessage());send(player,data);rt.sendState(player);}
    }
    private LiveConfigSchema.Group group(String id){var value=LiveConfigSchema.GROUPS.get(id);if(value==null)throw new IllegalArgumentException("此配置不支持实时编辑");return value;}
    private JsonObject header(String kind,String file){JsonObject data=new JsonObject();data.addProperty("kind",kind);data.addProperty("revision",revisions.getOrDefault(file,0L));data.addProperty("digest",rt.config.loadedDigest(file));return data;}
    private void requireCurrent(JsonObject body,String file){
        if(!rt.store.available())throw new IllegalStateException("数据正在保存，请稍后重试");
        long revision=ScoringSettings.integer(body.get("revision"),0,Long.MAX_VALUE);
        if(revision!=revisions.getOrDefault(file,0L)||!Objects.equals(body.get("digest").getAsString(),rt.config.loadedDigest(file)))throw new IllegalArgumentException("另一位管理员已修改配置，请重新打开后编辑");
    }
    public void sendGroup(ServerPlayer player,String id,boolean saved){
        rt.editors.require(player);var group=group(id);JsonObject data=header("group",group.file());
        data.addProperty("group",id);data.addProperty("name",id.equals("lottery")?cn.yumis.skyisland.core.LotteryNames.draw():group.name());data.addProperty("help",group.help());data.addProperty("saved",saved);
        JsonArray groups=new JsonArray(),fields=new JsonArray();
        for(var value:LiveConfigSchema.GROUPS.values()){JsonObject row=new JsonObject();row.addProperty("id",value.id());row.addProperty("name",value.id().equals("lottery")?cn.yumis.skyisland.core.LotteryNames.draw():value.name());groups.add(row);}
        for(var field:group.fields()){JsonObject row=new JsonObject();row.addProperty("key",field.key());row.addProperty("label",id.equals("lottery")?field.label().replace("代币",cn.yumis.skyisland.core.LotteryNames.token()).replace("抽奖",cn.yumis.skyisland.core.LotteryNames.draw()):field.label());row.addProperty("kind",field.kind());row.addProperty("minimum",field.minimum());row.addProperty("maximum",field.maximum());row.addProperty("help",field.help());row.add("value",LiveConfigSchema.get(rt.config.object(group.file()),field.key()).deepCopy());fields.add(row);}
        data.add("groups",groups);data.add("fields",fields);send(player,data);
    }
    private void saveGroup(ServerPlayer player,JsonObject body)throws IOException {
        var group=group(body.get("group").getAsString());requireCurrent(body,group.file());
        JsonObject next=LiveConfigSchema.patch(group,rt.config.object(group.file()),body.getAsJsonObject("changes"));
        rt.config.saveChecked(group.file(),next,body.get("digest").getAsString());
        revisions.merge(group.file(),1L,Math::addExact);
        rt.configurationChanged(group.file(),()->{if(rt.editors.enabled(player)&&rt.server.getPlayerList().getPlayer(player.getUUID())==player)sendGroup(player,group.id(),true);});
    }
    private ItemEconomy.Entry product(String id){var entry=rt.economy.entries.get(id);if(entry==null)throw new IllegalArgumentException("商品已不存在");return entry;}
    public void sendPrice(ServerPlayer player,String id,boolean saved){
        rt.editors.require(player);var entry=product(id);var p=entry.product();JsonObject data=header("price","shop/products.toml");
        data.addProperty("product",id);data.addProperty("name",entry.template().is(Items.ENCHANTED_BOOK)?p.name():entry.template().getHoverName().getString());data.addProperty("buyBase",p.buyBaseCoins());data.addProperty("saved",saved);
        boolean fixed=entry.template().is(cn.yumis.skyisland.prefab.PrefabItems.AMMUNITION);
        data.addProperty("editable",p.canBuy()&&!fixed);data.addProperty("message",fixed?"特殊弹药为固定售价，本页不提供改价":!p.canBuy()?"此商品仅收购，不提供购入基价编辑":"仅修改全服购入基价；收购估值、库存、等级和成品费不变");send(player,data);
    }
    private void savePrice(ServerPlayer player,JsonObject body)throws IOException {
        String file="shop/products.toml";requireCurrent(body,file);var entry=product(body.get("product").getAsString());
        if(!entry.product().canBuy()||entry.template().is(cn.yumis.skyisland.prefab.PrefabItems.AMMUNITION))throw new IllegalArgumentException("此商品不支持购入基价编辑");
        long price=ScoringSettings.integer(body.get("buyBase"),1,Long.MAX_VALUE);var replacement=entry.product().withBuyBase(price);
        // Validate both purchase contexts before touching disk, including 64-bit overflow.
        var candidate=new ItemEconomy.Entry(replacement,entry.template());
        cn.yumis.skyisland.data.IslandState.Party preview=new cn.yumis.skyisland.data.IslandState.Party();
        rt.economy.buyPrice(candidate,preview,true);rt.economy.buyPrice(candidate,preview,false);
        JsonObject next=rt.config.object(file).deepCopy(),rows=next.getAsJsonObject("products");
        if(rows.has(replacement.id()))rows.getAsJsonObject(replacement.id()).addProperty("buyBaseValueCoins",price);
        else{if(!next.has("buyBaseValueOverrides"))next.add("buyBaseValueOverrides",new JsonObject());next.getAsJsonObject("buyBaseValueOverrides").addProperty(replacement.id(),price);}
        rt.config.saveChecked(file,next,body.get("digest").getAsString());
        rt.config.products.put(replacement.id(),replacement);rt.economy.replaceProduct(replacement);revisions.merge(file,1L,Math::addExact);
        rt.priceRevision++;rt.configurationChanged(file,()->{if(rt.editors.enabled(player)&&rt.server.getPlayerList().getPlayer(player.getUUID())==player)sendPrice(player,replacement.id(),true);});
    }
    private void sendProductForm(ServerPlayer player,boolean saved){
        rt.editors.require(player);
        JsonObject data=header("product","shop/products.toml");
        data.addProperty("levelsDigest",rt.config.loadedDigest("shop/item_levels.toml"));
        data.addProperty("raritiesDigest",rt.config.loadedDigest("shop/rarities.toml"));
        data.addProperty("levelsRevision",revisions.getOrDefault("shop/item_levels.toml",0L));
        data.addProperty("raritiesRevision",revisions.getOrDefault("shop/rarities.toml",0L));
        data.addProperty("saved",saved);
        data.addProperty("message","普通商品立即进入常驻商店。更高稀有度从下次每日刷新进入候选，不改写今天已抽出的库存。基价同时作为收购价；之后单独改价只改购入基价。");
        send(player,data);
    }
    private void sendProductPreview(ServerPlayer player,String rawId){
        rt.editors.require(player);
        Identifier id=itemId(rawId);
        JsonObject data=new JsonObject();
        data.addProperty("kind","product_preview");
        data.addProperty("itemId",id.toString());
        data.addProperty("exists",rt.config.products.containsKey(id.toString()));
        data.addProperty("name",new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getHoverName().getString());
        data.addProperty("level",configuredLevel(id.toString()));
        data.addProperty("rarity",configuredRarity(id.toString()));
        data.addProperty("suggestedBase",suggestedBase(id.toString()));
        send(player,data);
    }
    private void addProduct(ServerPlayer player,JsonObject body)throws IOException{
        String productsFile="shop/products.toml",levelsFile="shop/item_levels.toml",raritiesFile="shop/rarities.toml";
        requireCurrent(body,productsFile);
        Identifier id=itemId(body.get("itemId").getAsString());
        if(rt.config.products.containsKey(id.toString()))throw new IllegalArgumentException("这个物品已经在商店里");
        String mode=body.get("tradeMode").getAsString();
        if(!Set.of("buy_sell","buy_only","sell_only").contains(mode))throw new IllegalArgumentException("交易方式无效");
        String rarity=body.get("rarity").getAsString();
        if(!Set.of("common","rare","epic","legendary").contains(rarity))throw new IllegalArgumentException("稀有度无效");
        int level=(int)ScoringSettings.integer(body.get("level"),0,100);
        long stock=ScoringSettings.integer(body.get("dailyStock"),-1,Integer.MAX_VALUE);
        if(stock==0)throw new IllegalArgumentException("每日库存须为正整数，或 -1 表示不限");
        long price=ScoringSettings.integer(body.get("buyBase"),1,Long.MAX_VALUE);
        ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.getValue(id));
        ItemStack roundtrip=rt.economy.decode(rt.economy.encode(stack));
        if(!ItemEconomy.matchesCanonical(roundtrip,stack,new JsonObject()))throw new IllegalArgumentException("这个物品不能作为标准商品保存");
        String name=displayName(body.has("name")?body.get("name").getAsString():"",stack);
        String category=shopCategory(body.has("shopCategory")?body.get("shopCategory").getAsString():"");
        JsonObject canonical=new JsonObject();
        canonical.addProperty("itemId",id.toString());
        canonical.add("componentPatch",new JsonObject());
        var product=new cn.yumis.skyisland.config.GameConfig.Product(id.toString(),name,canonical,price,price,10000L,mode,rarity,level,(int)stock,1,category);
        if(product.canBuy()){
            var candidate=new ItemEconomy.Entry(product,stack);
            var preview=new cn.yumis.skyisland.data.IslandState.Party();
            rt.economy.buyPrice(candidate,preview,true);
            rt.economy.buyPrice(candidate,preview,false);
        }
        JsonObject levels=rt.config.object(levelsFile).deepCopy();
        JsonObject rarities=rt.config.object(raritiesFile).deepCopy();
        boolean writeLevels=putNumber(levels.getAsJsonObject("itemLevels"),id.toString(),"unlockLevel",level,configuredLevel(id.toString()));
        boolean writeRarities=putText(rarities.getAsJsonObject("itemRarities"),id.toString(),"rarity",rarity,configuredRarity(id.toString()));
        if(writeLevels)requireDigest(body,levelsFile,"levelsDigest","levelsRevision");
        if(writeRarities)requireDigest(body,raritiesFile,"raritiesDigest","raritiesRevision");
        JsonObject next=rt.config.object(productsFile).deepCopy();
        JsonObject row=new JsonObject();
        row.addProperty("productId",id.toString());
        row.addProperty("name",name);
        row.addProperty("categoryHint",category.equals("other")?"其他":"管理员新增");
        row.addProperty("baseValueCoins",price);
        row.addProperty("buyBaseValueCoins",price);
        row.addProperty("buyMultiplierBps",10000);
        row.addProperty("tradeMode",mode);
        row.addProperty("dailyStock",stock);
        row.addProperty("offerWeight",1);
        row.addProperty("priceOrigin","administrator");
        row.addProperty("acquisitionNote","管理员从控制面板新增");
        row.addProperty("shopCategory",category);
        row.addProperty("shopRule","管理员新增商品");
        row.addProperty("firstAcquisitionPremium",false);
        row.add("canonicalStack",canonical);
        next.getAsJsonObject("products").add(id.toString(),row);
        if(writeLevels){rt.config.saveChecked(levelsFile,levels,body.get("levelsDigest").getAsString());revisions.merge(levelsFile,1L,Math::addExact);}
        if(writeRarities){rt.config.saveChecked(raritiesFile,rarities,body.get("raritiesDigest").getAsString());revisions.merge(raritiesFile,1L,Math::addExact);}
        rt.config.saveChecked(productsFile,next,body.get("digest").getAsString());
        rt.config.products.put(product.id(),product);
        rt.economy.addProduct(product,stack);
        revisions.merge(productsFile,1L,Math::addExact);
        rt.priceRevision++;
        String where=mode.equals("sell_only")?"。仅收购的物品不出现在购买列表，可以卖出。":rarity.equals("common")?"。普通商品可在常驻商店购买。":"。更高稀有度从下次每日刷新进入候选，今天已抽出的库存不变。";
        SkyServer.tell(player,"已加入商店："+name+where);
        rt.configurationChanged(productsFile,()->{if(rt.editors.enabled(player)&&rt.server.getPlayerList().getPlayer(player.getUUID())==player)sendProductForm(player,true);});
    }
    private void requireDigest(JsonObject body,String file,String digestField,String revisionField){
        if(!rt.store.available())throw new IllegalStateException("数据正在保存，请稍后重试");
        long revision=ScoringSettings.integer(body.get(revisionField),0,Long.MAX_VALUE);
        if(revision!=revisions.getOrDefault(file,0L)||!Objects.equals(body.get(digestField).getAsString(),rt.config.loadedDigest(file)))throw new IllegalArgumentException("另一位管理员已修改配置，请重新打开后编辑");
    }
    private Identifier itemId(String raw){
        Identifier id;
        try{id=Identifier.parse(raw);}catch(RuntimeException error){throw new IllegalArgumentException("物品ID无效");}
        if(!BuiltInRegistries.ITEM.containsKey(id)||BuiltInRegistries.ITEM.getValue(id)==Items.AIR)throw new IllegalArgumentException("物品未注册："+raw);
        return id;
    }
    private static String displayName(String raw,ItemStack stack){
        String name=raw==null?"":raw.trim().replaceAll("[\\p{Cntrl}]"," ");
        if(name.isBlank()||name.length()>64)name=stack.getHoverName().getString();
        name=name.trim();
        if(name.isEmpty()||name.length()>64)throw new IllegalArgumentException("物品名称无效");
        return name;
    }
    private static String shopCategory(String raw){
        String value=raw==null?"":raw.trim();
        if(value.equals("other")||value.matches("minecraft:[a-z0-9_./+-]{1,64}"))return value;
        return "other";
    }
    private int configuredLevel(String id){
        JsonObject levels=rt.config.object("shop/item_levels.toml").getAsJsonObject("itemLevels");
        if(levels==null||!levels.has(id))return 0;
        JsonElement value=levels.get(id);
        return value.isJsonPrimitive()?value.getAsInt():value.getAsJsonObject().get("unlockLevel").getAsInt();
    }
    private String configuredRarity(String id){
        JsonObject rarities=rt.config.object("shop/rarities.toml").getAsJsonObject("itemRarities");
        if(rarities==null||!rarities.has(id))return "common";
        JsonElement value=rarities.get(id);
        String rarity=value.isJsonPrimitive()?value.getAsString():value.getAsJsonObject().get("rarity").getAsString();
        return Set.of("common","rare","epic","legendary").contains(rarity)?rarity:"common";
    }
    private long suggestedBase(String id){
        JsonObject values=rt.config.object("shop/recipe_values.toml").getAsJsonObject("ingredientValues");
        if(values!=null&&values.has(id)&&values.get(id).getAsLong()>0)return values.get(id).getAsLong();
        return 100;
    }
    private static boolean putNumber(JsonObject table,String id,String objectKey,long value,long current){
        if(table==null)throw new IllegalArgumentException("等级配置缺少数据表");
        if(value==current)return false;
        JsonElement existing=table.get(id);
        if(existing!=null&&existing.isJsonObject())existing.getAsJsonObject().addProperty(objectKey,value);
        else table.addProperty(id,value);
        return true;
    }
    private static boolean putText(JsonObject table,String id,String objectKey,String value,String current){
        if(table==null)throw new IllegalArgumentException("稀有度配置缺少数据表");
        if(value.equals(current))return false;
        JsonElement existing=table.get(id);
        if(existing!=null&&existing.isJsonObject())existing.getAsJsonObject().addProperty(objectKey,value);
        else table.addProperty(id,value);
        return true;
    }
    private void send(ServerPlayer player,JsonObject data){if(ServerPlayNetworking.canSend(player,ConfigEditPayloads.Snapshot.TYPE))ServerPlayNetworking.send(player,new ConfigEditPayloads.Snapshot(data.toString()));}
}
