package cn.yumis.skyisland.config;

import cn.yumis.skyisland.core.Rational;
import com.google.gson.*;
import java.util.*;

/** A single profession per sold item; exact multipliers shared by quotes and client previews. */
public record ProfessionSaleSettings(Map<String,Integer> multipliersBps, Map<String,String> itemProfessions, List<Integer> qualityMultipliersBps) {
    public static final List<String> PROFESSIONS = ProfessionExperienceSettings.PROFESSIONS;
    public static final List<String> GRADES = List.of("ordinary","copper","silver","gold","diamond","prismatic");
    private static final List<Integer> DEFAULT_QUALITY = List.of(10000,20000,60000,150000,300000,1000000);
    public static final ProfessionSaleSettings NONE = parse(new JsonObject(),new JsonObject());
    public ProfessionSaleSettings {
        multipliersBps=Map.copyOf(multipliersBps);itemProfessions=Map.copyOf(itemProfessions);qualityMultipliersBps=List.copyOf(qualityMultipliersBps);
    }
    public static int multiplierBps(JsonElement value) {
        try { return ProfessionExperienceSettings.multiplierBps(value); }
        catch(IllegalArgumentException invalid){throw new IllegalArgumentException("副职业出售倍率须为0～1000的数字，最多4位小数",invalid);}
    }
    public static void validate(JsonObject document) {
        JsonElement qualities=document.get("saleQualityMultipliers");
        if(qualities!=null){
            if(!qualities.isJsonObject()||!qualities.getAsJsonObject().keySet().equals(new HashSet<>(GRADES)))throw new IllegalArgumentException("出售星级倍率须恰好包含普通、铜、银、金、钻、七彩六档");
            qualities.getAsJsonObject().entrySet().forEach(entry->qualityMultiplierBps(entry.getValue()));
        }
        JsonElement value=document.get("saleMultipliers");
        if(value==null)return;
        if(!value.isJsonObject())throw new IllegalArgumentException("saleMultipliers 必须为副职业出售倍率表");
        for(var entry:value.getAsJsonObject().entrySet()){
            if(!PROFESSIONS.contains(entry.getKey()))throw new IllegalArgumentException("未知出售职业："+entry.getKey());
            multiplierBps(entry.getValue());
        }
    }
    public static int qualityMultiplierBps(JsonElement value){
        String error="出售星级倍率须为0～10000的数字，最多4位小数";
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(error);
        try{
            var amount=value.getAsBigDecimal();
            if(amount.signum()<0||amount.compareTo(java.math.BigDecimal.valueOf(10000))>0)throw new IllegalArgumentException(error);
            return amount.movePointRight(4).intValueExact();
        }catch(ArithmeticException|NumberFormatException invalid){throw new IllegalArgumentException(error,invalid);}
    }
    public static ProfessionSaleSettings parse(JsonObject professions,JsonObject goods) {
        validate(professions);
        Map<String,Integer> rates=new LinkedHashMap<>();
        JsonObject values=professions.getAsJsonObject("saleMultipliers");
        for(String job:PROFESSIONS)rates.put(job,values!=null&&values.has(job)?multiplierBps(values.get(job)):10000);
        Map<String,String> items=new LinkedHashMap<>();
        catalog(items,goods.getAsJsonObject("items"));
        catalog(items,professions.getAsJsonObject("sourceItems"));
        JsonObject extra=professions.getAsJsonObject("saleItemProfessions");
        if(extra!=null)for(var entry:extra.entrySet()){
            if(!entry.getKey().matches("[a-z0-9_.-]+:[a-z0-9/._-]+")||!entry.getValue().isJsonPrimitive()
                ||!entry.getValue().getAsJsonPrimitive().isString()||!PROFESSIONS.contains(entry.getValue().getAsString()))
                throw new IllegalArgumentException("副职业出售产物归属无效："+entry.getKey());
            items.put(entry.getKey(),entry.getValue().getAsString());
        }
        JsonObject qualities=professions.getAsJsonObject("saleQualityMultipliers");
        List<Integer> quality=qualities==null?DEFAULT_QUALITY:GRADES.stream().map(grade->qualityMultiplierBps(qualities.get(grade))).toList();
        return new ProfessionSaleSettings(rates,items,quality);
    }
    private static void catalog(Map<String,String> items,JsonObject rows){
        if(rows!=null)for(var entry:rows.entrySet()){
            JsonObject row=entry.getValue().getAsJsonObject();
            if(row.has("profession")&&PROFESSIONS.contains(row.get("profession").getAsString()))items.put(entry.getKey(),row.get("profession").getAsString());
        }
    }
    public Rational multiplier(String item){
        String job=itemProfessions.get(item);
        return Rational.of(job==null?10000:multipliersBps.getOrDefault(job,10000),10000);
    }
    public Rational qualityMultiplier(int grade){return Rational.of(qualityMultipliersBps.get(Math.clamp(grade,0,5)),10000);}
    public JsonObject snapshot(){
        JsonObject document=new JsonObject(),rates=new JsonObject(),items=new JsonObject(),quality=new JsonObject();
        multipliersBps.forEach((job,bps)->rates.addProperty(job,new java.math.BigDecimal(bps).movePointLeft(4)));
        itemProfessions.forEach(items::addProperty);
        for(int i=0;i<GRADES.size();i++)quality.addProperty(GRADES.get(i),new java.math.BigDecimal(qualityMultipliersBps.get(i)).movePointLeft(4));
        document.add("saleMultipliers",rates);document.add("saleItemProfessions",items);document.add("saleQualityMultipliers",quality);return document;
    }
}
