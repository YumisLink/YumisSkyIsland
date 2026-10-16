package cn.yumis.skyisland.config;

import cn.yumis.skyisland.core.LotterySelector;
import com.google.gson.*;
import java.util.*;

/** Server-only prize definitions. Never serialize this object into an ordinary client snapshot. */
public final class LotterySettings {
    public static final List<String> RARITIES=List.of("common","rare","epic","legendary");
    public record Prize(String id,String itemId,String rarity,int islandLevel,JsonObject components) {}
    public final boolean enabled;
    public final int experienceCost,tokenCost,taskReward,levelReward,unlockLevel;
    public final LotterySelector.Policy policy;
    public final List<Prize> prizes;
    public LotterySettings(JsonObject data) {
        if(data==null)throw new IllegalArgumentException("缺少抽奖配置");
        if(!data.has("enabled")||!data.get("enabled").isJsonPrimitive()||!data.getAsJsonPrimitive("enabled").isBoolean())throw new IllegalArgumentException("抽奖开关必须为布尔值");
        if(!data.has("prizes")||!data.get("prizes").isJsonArray())throw new IllegalArgumentException("缺少抽奖奖品数组");
        enabled=data.get("enabled").getAsBoolean();
        experienceCost=number(data,"experienceCost",1,Integer.MAX_VALUE);tokenCost=number(data,"tokenCost",1,Integer.MAX_VALUE);
        taskReward=number(data,"taskTokenReward",0,Integer.MAX_VALUE);levelReward=number(data,"levelTokenReward",0,Integer.MAX_VALUE);
        JsonObject weights=data.getAsJsonObject("rarityBps"),pity=data.getAsJsonObject("pity");
        policy=new LotterySelector.Policy(number(weights,"rare",0,10000),number(weights,"epic",0,10000),number(weights,"legendary",0,10000),
            number(pity,"softStart",0,1000000),number(pity,"legendaryAt",1,1000000),number(pity,"epicAt",1,1000000),number(data,"featuredBps",0,10000));
        List<Prize> rows=new ArrayList<>();Set<String> ids=new HashSet<>();int minimum=Integer.MAX_VALUE;
        for(var value:data.getAsJsonArray("prizes")) {
            var row=value.getAsJsonObject();String id=row.get("id").getAsString(),item=row.get("itemId").getAsString(),rarity=row.get("rarity").getAsString();
            if(id.isBlank()||id.length()>128||!ids.add(id)||item.isBlank()||!RARITIES.contains(rarity))throw new IllegalArgumentException("奖品ID重复或稀有度无效："+id);
            int level=number(row,"islandLevel",0,Integer.MAX_VALUE);
            rows.add(new Prize(id,item,rarity,level,row.has("components")?row.getAsJsonObject("components").deepCopy():new JsonObject()));
            if(rarity.equals("legendary"))minimum=Math.min(minimum,level);
        }
        if(rows.isEmpty()||rows.size()>4096||minimum==Integer.MAX_VALUE)throw new IllegalArgumentException("奖池必须有1～4096项且至少含一项传说");
        prizes=List.copyOf(rows);unlockLevel=minimum;
    }
    private static int number(JsonObject data,String key,int min,int max) {
        try { var raw=data.get(key);if(raw==null||!raw.isJsonPrimitive()||!raw.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException();int value=raw.getAsBigDecimal().intValueExact();if(value<min||value>max)throw new IllegalArgumentException();return value; }
        catch(RuntimeException error){throw new IllegalArgumentException("抽奖配置字段无效："+key,error);}
    }
}
