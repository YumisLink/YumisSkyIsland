package cn.yumis.skyisland.config;

import com.google.gson.*;
import java.util.*;

/** Server-owned allowlist. Clients submit field keys, never file paths. */
public final class LiveConfigSchema {
    public record Field(String key,String label,String kind,long minimum,long maximum,String help) {
        public void validate(JsonElement value) {
            if(kind.equals("boolean")) { if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException(label+"必须为开关"); }
            else if(kind.equals("text")) { if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||value.getAsString().isBlank()||value.getAsString().length()>maximum)throw new IllegalArgumentException(label+"长度无效"); }
            else if(kind.equals("decimal")) {if(key.startsWith("saleQualityMultipliers/"))ProfessionSaleSettings.qualityMultiplierBps(value);else if(key.startsWith("saleMultipliers/"))ProfessionSaleSettings.multiplierBps(value);else ProfessionExperienceSettings.multiplierBps(value);}
            else ScoringSettings.integer(value,minimum,maximum);
        }
    }
    public record Group(String id,String name,String file,String help,List<Field> fields) { public Group { fields=List.copyOf(fields); } }
    public static final Map<String,Group> GROUPS;
    static {
        Map<String,Group> groups=new LinkedHashMap<>();
        add(groups,"economy","经济与死亡","economy.toml","称呼即时同步；费用和饥饿值从下次死亡／复活起生效。",
            bool("genshinMode","原神模式称呼"),num("death/firstCost","首笔死亡费用",0,Long.MAX_VALUE,"货币"),num("death/additionalCostPerDeath","死亡费用递增",0,Long.MAX_VALUE,"货币"),num("death/respawnFoodLevel","复活饥饿值",0,20,"满值20"));
        add(groups,"trading","原版交易","server_rules.toml","下次交互和结果提取时生效。",
            bool("villagerTradingEnabled","村民交易"),bool("wanderingTraderTradingEnabled","流浪商人交易"));
        List<Field> xp=new ArrayList<>();
        for(String job:ProfessionExperienceSettings.PROFESSIONS)xp.add(new Field("experienceMultipliers/"+job,switch(job){case "blacksmith"->"铁匠";case "chef"->"厨师";case "farmer"->"农夫";case "hunter"->"猎人";case "fisherman"->"渔夫";case "miner"->"矿工";default->"战斗大师";}+"经验获取倍率","decimal",0,ProfessionExperienceSettings.MAX_MULTIPLIER,"倍；1保持原速，0停止经验与积分；最多4位小数，叠加地区和居民加成"));
        xp.add(num("successfulFishingXp","成功获钓经验",0,Long.MAX_VALUE,"职业经验；地区宝藏箱固定经验不受此项控制"));xp.add(num("lavaFishingXpMultiplier","岩浆获钓经验倍率",0,Long.MAX_VALUE,"倍，乘在这一杆实际渔夫经验上，宝藏箱固定12同样乘"));xp.add(num("voidFishingXpMultiplier","虚空获钓经验倍率",0,Long.MAX_VALUE,"倍，乘在这一杆实际渔夫经验上，宝藏箱固定12同样乘"));xp.add(num("successfulHunterKillXp","成功击杀经验",0,Long.MAX_VALUE,"职业经验"));
        for(String job:List.of("blacksmith","chef","farmer","hunter","miner"))xp.add(num("successfulCraftXp/"+job,switch(job){case "blacksmith"->"铁匠";case "chef"->"厨师";case "farmer"->"农夫";case "miner"->"矿工";default->"猎人";}+"制造默认经验",0,Long.MAX_VALUE,"已有单品baseXp优先"));
        add(groups,"profession_xp","职业经验","products/professions.toml","只影响后续真实行动，不重算历史经验。",xp.toArray(Field[]::new));
        List<Field> sales=new ArrayList<>();
        for(String job:ProfessionSaleSettings.PROFESSIONS)sales.add(new Field("saleMultipliers/"+job,switch(job){case "blacksmith"->"铁匠";case "chef"->"厨师";case "farmer"->"农夫";case "hunter"->"猎人";case "fisherman"->"渔夫";case "miner"->"矿工";default->"战斗大师";}+"出售价格倍率","decimal",0,1000,job.equals("battle_master")?"倍；目前无专属产物，需先在产物目录配置归属":"倍；默认1，0使报价为0；普通及全部品质，最多4位小数"));
        for(int i=0;i<ProfessionSaleSettings.GRADES.size();i++)sales.add(new Field("saleQualityMultipliers/"+ProfessionSaleSettings.GRADES.get(i),List.of("普通","铜星","银星","金星","钻星","七彩星").get(i)+"出售倍率","decimal",0,10000,"倍；直接相对无星基价，只乘一次；最多4位小数，所有职业共用"));
        add(groups,"profession_sales","副职业出售","products/professions.toml","职业按产物目录匹配，再乘统一星级倍率；保留行情、附魔、耐久、居民和额度，已结算交易保留。",sales.toArray(Field[]::new));
        add(groups,"profession_shop","职业商店","products/profession_shop.toml","从下次购买、活动或使用起生效，已用果实计数保留。",
            num("fruitPrice","果实价格",1,Long.MAX_VALUE,"个人职业积分"),num("lavaBaitPrice","岩浆鱼饵价格",1,Long.MAX_VALUE,"个人职业积分"),num("voidBaitPrice","虚空鱼饵价格",1,Long.MAX_VALUE,"个人职业积分"),num("fruitDropBps","果实掉率",0,10000,"万分数，10=0.1%"),num("fruitUseCap","每种果实使用上限",1,100,"次"));
        add(groups,"blood_moon","血月","gameplay/blood_moon.toml","触发概率从下次夜间判定生效；刷怪和钓鱼从下次事件生效，不重抽今夜。",
            num("initialChanceBps","初始血月概率",0,10000,"万分数"),num("missIncrementBps","未触发后的概率增量",0,10000,"万分数"),num("hostileSpawnMultiplier","敌对刷怪倍率",0,20,"倍，0停止血月敌对刷怪尝试"),num("hostileLightLimit","敌对刷怪光照阈值",0,16,"仅允许光照小于此数"),num("treasureEggBps","宝藏池地区蛋概率",0,10000,"万分数"));
        add(groups,"lottery","抽奖／祈愿","gameplay/lottery.toml","仅影响后续操作；奖池及解锁等级手改重启，已提交奖励保留。",
            bool("enabled","抽奖开启"),bool("allItems","全部物品奖池"),num("experienceCost","每张券消耗经验等级",1,Integer.MAX_VALUE,"附魔等级"),num("tokenCost","每张券消耗代币",1,Integer.MAX_VALUE,"个人代币／原石"),
            num("rarityBps/rare","稀有基础概率",0,10000,"万分数，普通取剩余"),num("rarityBps/epic","史诗基础概率",0,10000,"万分数"),num("rarityBps/legendary","传说基础概率",0,10000,"万分数，三项合计不得超过10000"));
        add(groups,"refresh","刷新预算","islands/refresh.toml","下一刻采用新预算；已有异步读取正常结束，不追加强加载。",
            num("globalComputeBudgetMicrosPerTick","全服每刻计算预算",1,5000,"微秒，默认1000"),num("maxSectionsPerTick","每刻区段数量",1,64,"区段"),num("maxNewChunkRequestsPerTick","每刻新读取数",1,16,"请求"),num("maxConcurrentChunkRequests","同时读取数量",1,32,"请求"));
        List<Field> score=new ArrayList<>();
        for(int i=0;i<4;i++){if(i<3)score.add(num("tiers/default/"+i+"/upToCount","第"+(i+1)+"档累计上限",1,Long.MAX_VALUE,"块，三个阈值须递增"));score.add(num("tiers/default/"+i+"/multiplierBps","第"+(i+1)+"档倍率",0,10000,"万分数；逐块向下取整，最低1分"));}
        score.add(bool("notices/enabled","跨档队伍通告"));score.add(new Field("notices/message","通告文案","text",1,512,"可用{block}、{threshold}、{nextPoints}、{nextMultiplier}"));
        add(groups,"scoring","高分方块计分","islands/scoring.toml","仅基础分>5递减；保存按现有存量重算等级。基础分仅TOML手改、重启载入。",score.toArray(Field[]::new));
        GROUPS=Collections.unmodifiableMap(groups);
    }
    private static void add(Map<String,Group> map,String id,String name,String file,String help,Field... fields){map.put(id,new Group(id,name,file,help,List.of(fields)));}
    private static Field num(String key,String label,long min,long max,String help){return new Field(key,label,"integer",min,max,help);}
    private static Field bool(String key,String label){return new Field(key,label,"boolean",0,1,"开启／关闭");}
    public static JsonElement get(JsonObject document,String path){JsonElement value=document;for(String key:path.split("/"))value=value.isJsonArray()?value.getAsJsonArray().get(Integer.parseInt(key)):value.getAsJsonObject().get(key);return value;}
    public static void set(JsonObject document,String path,JsonElement value){String[] keys=path.split("/");JsonElement target=document;for(int i=0;i<keys.length-1;i++)target=target.isJsonArray()?target.getAsJsonArray().get(Integer.parseInt(keys[i])):target.getAsJsonObject().get(keys[i]);String last=keys[keys.length-1];if(target.isJsonArray())target.getAsJsonArray().set(Integer.parseInt(last),value.deepCopy());else target.getAsJsonObject().add(last,value.deepCopy());}
    public static JsonObject patch(Group group,JsonObject source,JsonObject changes){
        JsonObject next=source.deepCopy();Map<String,Field> allowed=new HashMap<>();group.fields.forEach(field->allowed.put(field.key,field));
        for(var entry:changes.entrySet()){Field field=allowed.get(entry.getKey());if(field==null)throw new IllegalArgumentException("此字段不支持实时编辑："+entry.getKey());field.validate(entry.getValue());set(next,field.key,entry.getValue());}
        if(group.id.equals("scoring"))ScoringSettings.parse(next);
        if(group.id.equals("lottery"))new LotterySettings(next);
        if(group.id.equals("profession_xp"))ProfessionExperienceSettings.validate(next);
        if(group.id.equals("profession_sales"))ProfessionSaleSettings.validate(next);
        return next;
    }
    private LiveConfigSchema() {}
}
