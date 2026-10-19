package cn.yumis.skyisland.config;

import com.google.gson.*;
import java.util.*;

/** Loaded once at startup; speed is virtual vanilla ticks per real server tick. */
public record FurnaceSettings(Map<String, Integer> speeds) {
    public static final List<String> TIERS=List.of("copper","iron","gold","emerald","diamond","netherite","ender");
    public FurnaceSettings { speeds=Map.copyOf(speeds); }
    public static FurnaceSettings parse(JsonObject json){
        JsonObject row=json.getAsJsonObject("speedMultipliers");
        if(row==null||!row.keySet().equals(new HashSet<>(TIERS)))throw new IllegalArgumentException("熔炉速度必须包含铜至末影全部七档");
        Map<String,Integer> speeds=new LinkedHashMap<>();
        for(String tier:TIERS){
            JsonElement value=row.get(tier);
            if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("熔炉速度必须为整数："+tier);
            int speed=value.getAsBigDecimal().intValueExact();
            if(speed<1||speed>128)throw new IllegalArgumentException("熔炉速度须为1～128倍："+tier);
            speeds.put(tier,speed);
        }
        return new FurnaceSettings(speeds);
    }
    public int speed(String tier){return speeds.get(tier);}
}
