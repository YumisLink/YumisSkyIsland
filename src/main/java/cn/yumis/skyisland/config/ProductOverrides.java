package cn.yumis.skyisland.config;

import com.google.gson.*;
import java.util.Set;

/** Final economic definitions in products.toml; source assets remain authoritative for identity. */
public final class ProductOverrides {
    public static final Set<String> FIELDS=Set.of("baseValueCoins","buyBaseValueCoins","tradeMode","tradeEnabled","rarity","unlockLevel","dailyStock","buildFee");
    public static JsonObject row(JsonObject document,String id){
        var table=document.getAsJsonObject("productOverrides");
        return table==null||!table.has(id)?new JsonObject():table.getAsJsonObject(id);
    }
    public static long number(JsonObject row,String key,long fallback,long min,long max){return row.has(key)?ScoringSettings.integer(row.get(key),min,max):fallback;}
    public static boolean flag(JsonObject row,String key,boolean fallback){
        if(!row.has(key))return fallback;
        if(!row.get(key).isJsonPrimitive()||!row.getAsJsonPrimitive(key).isBoolean())throw new IllegalArgumentException("交易启停须为布尔值");
        return row.get(key).getAsBoolean();
    }
    public static GameConfig.Product apply(GameConfig.Product p,JsonObject document){
        JsonObject row=row(document,p.id());
        for(String key:row.keySet())if(!FIELDS.contains(key))throw new IllegalArgumentException("未知物品管理字段："+key);
        String mode=row.has("tradeMode")?row.get("tradeMode").getAsString():p.tradeMode();
        String rarity=row.has("rarity")?row.get("rarity").getAsString():p.rarity();
        if(!Set.of("buy_sell","buy_only","sell_only").contains(mode)||!Set.of("common","rare","epic","legendary").contains(rarity))throw new IllegalArgumentException("交易方式或稀有度无效");
        boolean enabled=flag(row,"tradeEnabled",p.tradeEnabled());
        int stock=(int)number(row,"dailyStock",p.stock(),-1,Integer.MAX_VALUE);
        if(stock==0)throw new IllegalArgumentException("每日库存须为正整数或-1");
        if(row.has("buildFee"))number(row,"buildFee",1,1,Long.MAX_VALUE);
        return new GameConfig.Product(p.id(),p.name(),p.canonical(),number(row,"baseValueCoins",p.baseCoins(),1,Long.MAX_VALUE),
            number(row,"buyBaseValueCoins",p.buyBaseCoins(),1,Long.MAX_VALUE),p.buyMultiplierBps(),mode,rarity,
            (int)number(row,"unlockLevel",p.level(),0,100),stock,p.weight(),p.category(),enabled);
    }
    public static JsonObject describe(GameConfig.Product p){
        var row=new JsonObject();row.addProperty("baseValueCoins",p.baseCoins());row.addProperty("buyBaseValueCoins",p.buyBaseCoins());
        row.addProperty("tradeMode",p.tradeMode());row.addProperty("tradeEnabled",p.tradeEnabled());row.addProperty("rarity",p.rarity());
        row.addProperty("unlockLevel",p.level());row.addProperty("dailyStock",p.stock());return row;
    }
    private ProductOverrides(){}
}
