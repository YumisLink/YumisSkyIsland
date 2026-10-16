package cn.yumis.skyisland.server;

import cn.yumis.skyisland.config.*;
import cn.yumis.skyisland.network.ConfigEditPayloads;
import cn.yumis.skyisland.shop.ItemEconomy;
import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

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
        data.addProperty("product",id);data.addProperty("name",entry.template().getHoverName().getString());data.addProperty("buyBase",p.buyBaseCoins());data.addProperty("saved",saved);
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
    private void send(ServerPlayer player,JsonObject data){if(ServerPlayNetworking.canSend(player,ConfigEditPayloads.Snapshot.TYPE))ServerPlayNetworking.send(player,new ConfigEditPayloads.Snapshot(data.toString()));}
}
