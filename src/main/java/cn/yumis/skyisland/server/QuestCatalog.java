package cn.yumis.skyisland.server;

import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.shop.*;
import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Validated immutable task definitions, separate from party progress and sale products. */
public final class QuestCatalog {
    public static final List<String> PROFESSIONS = List.of("fisherman","farmer","blacksmith","chef","hunter");
    public record Goal(String type, String targetId, int quantity, String sourceBlockId, String toolId) {
        public String key(){return type+"/"+targetId+"/"+sourceBlockId+"/"+toolId;}
        public boolean matches(String event,String target,String block,String tool){return type.equals(event)&&targetId.equals(target)&&(sourceBlockId.isEmpty()||sourceBlockId.equals(block))&&(toolId.isEmpty()||toolId.equals(tool));}
    }
    public record Feature(String id, String name, boolean defaultUnlocked) {}
    public record Quest(JsonObject definition, List<TaskMaterialPlanner.Requirement> requirements, List<ItemStack> rewards, List<Goal> goals) {
        public String id() { return definition.get("id").getAsString(); }
        public String kind() { return definition.get("kind").getAsString(); }
        public String objective() { return definition.get("objective").getAsString(); }
        public String profession(){return definition.has("profession")?definition.get("profession").getAsString():"";}
        public String previous(){return definition.has("previousQuestId")?definition.get("previousQuestId").getAsString():"";}
    }
    public record Progress(Set<String> completed,Set<String> ready,Map<String,Long> resetVersions,Map<String,Map<String,Integer>> counters) {}
    public static Progress progress(IslandState.Party party,IslandState.Person person,Quest quest){
        if(quest.kind().equals("main"))return party==null?null:new Progress(party.completedQuestIds,party.readyQuestIds,party.questResetVersions,Map.of());
        return person==null?null:new Progress(person.completedQuestIds,person.readyQuestIds,person.questResetVersions,person.questProgress);
    }
    public final long revision;
    public final Map<String, Feature> features;
    public final List<Quest> quests;
    private final ItemEconomy economy;
    private final Map<String,Quest> byId;

    public QuestCatalog(JsonObject document, ItemEconomy economy, JsonObject quality) {
        this.economy = economy;
        revision = document.has("revision") ? document.get("revision").getAsLong() : 1;
        if (revision < 1) throw new IllegalArgumentException("tasks/quests.toml：revision 必须为正整数");
        Map<String,Feature> f = new LinkedHashMap<>();
        for (JsonElement element : document.getAsJsonArray("features")) {
            JsonObject row = element.getAsJsonObject();
            String id = text(row,"id",128);
            if (!id.matches("[a-z0-9_.:/-]+") || f.put(id,new Feature(id,text(row,"name",80),row.has("defaultUnlocked") && row.get("defaultUnlocked").getAsBoolean())) != null)
                throw new IllegalArgumentException("tasks/quests.toml：功能 ID 无效／重复：" + id);
        }
        features = Collections.unmodifiableMap(f);
        List<Quest> definitions = new ArrayList<>();
        Map<String,Quest> lookup = new LinkedHashMap<>();
        if (document.getAsJsonArray("quests").size() > 256) throw new IllegalArgumentException("成长任务最多256条");
        for (JsonElement element : document.getAsJsonArray("quests")) {
            JsonObject q = element.getAsJsonObject().deepCopy();
            String id = text(q,"id",64);
            try {
                if (!id.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("ID只能包含小写字母、数字、_、-、.");
                text(q,"name",80);
                q.remove("description");
                if (!q.has("kind")) q.addProperty("kind","main");
                if (!q.has("objective")) q.addProperty("objective","materials");
                if (!Set.of("main","side","profession").contains(q.get("kind").getAsString())) throw new IllegalArgumentException("任务类型必须为 main、side 或 profession");
                if (!Set.of("materials","place_shipping_chest","tracked").contains(q.get("objective").getAsString())) throw new IllegalArgumentException("未知任务目标");
                if(q.get("kind").getAsString().equals("profession")&&(!q.has("profession")||!PROFESSIONS.contains(q.get("profession").getAsString())||!q.get("objective").getAsString().equals("tracked")))throw new IllegalArgumentException("职业任务必须指定有效职业并使用 tracked 目标");
                if(q.get("kind").getAsString().equals("main")&&q.get("objective").getAsString().equals("tracked"))throw new IllegalArgumentException("追踪目标用于个人任务");
                if (q.get("rewardCoins").getAsLong() < 0) throw new IllegalArgumentException("货币奖励不能为负");
                if(!q.has("rewardExperiencePoints"))q.addProperty("rewardExperiencePoints",0);
                if(q.get("rewardExperiencePoints").getAsLong()<0||q.get("rewardExperiencePoints").getAsLong()>Integer.MAX_VALUE)throw new IllegalArgumentException("经验点奖励必须为非负整数");
                if (q.getAsJsonArray("requirements").size() > 32 || q.getAsJsonArray("rewardItems").size() > 32) throw new IllegalArgumentException("材料和奖励各最多32条");
                if (!q.get("objective").getAsString().equals("materials") && !q.getAsJsonArray("requirements").isEmpty()) throw new IllegalArgumentException("放置和追踪目标不使用提交材料");
                List<Goal> goals=new ArrayList<>();Set<String> goalKeys=new HashSet<>();
                if(!q.has("goals"))q.add("goals",new JsonArray());
                if(q.getAsJsonArray("goals").size()>32)throw new IllegalArgumentException("追踪目标最多32条");
                for(var elementGoal:q.getAsJsonArray("goals")){
                    var g=elementGoal.getAsJsonObject();String type=text(g,"type",32),target=text(g,"targetId",256);
                    if(!Set.of("craft","smelt","fish","harvest","kill").contains(type))throw new IllegalArgumentException("未知追踪行动");
                    if(!(type.equals("kill")?BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.parse(target)):BuiltInRegistries.ITEM.containsKey(Identifier.parse(target))))throw new IllegalArgumentException("追踪目标未注册："+target);
                    long amount=g.get("quantity").getAsLong();if(amount<1||amount>Integer.MAX_VALUE)throw new IllegalArgumentException("追踪数量必须为正整数");
                    String block=g.has("sourceBlockId")?g.get("sourceBlockId").getAsString():"",tool=g.has("toolId")?g.get("toolId").getAsString():"";
                    if(!block.isEmpty()&&!BuiltInRegistries.BLOCK.containsKey(Identifier.parse(block)))throw new IllegalArgumentException("采集来源方块未注册");
                    if(!tool.isEmpty()&&!BuiltInRegistries.ITEM.containsKey(Identifier.parse(tool)))throw new IllegalArgumentException("采集工具未注册");
                    if(!type.equals("harvest")&&(!block.isEmpty()||!tool.isEmpty()))throw new IllegalArgumentException("来源方块和工具仅用于采集");
                    Goal goal=new Goal(type,target,(int)amount,block,tool);if(!goalKeys.add(goal.key()))throw new IllegalArgumentException("重复追踪目标");goals.add(goal);
                }
                if(q.get("objective").getAsString().equals("tracked")!=!goals.isEmpty())throw new IllegalArgumentException("tracked 目标必须包含追踪条件，其它目标不能包含追踪条件");
                List<TaskMaterialPlanner.Requirement> materials = new ArrayList<>();
                for (JsonElement row : q.getAsJsonArray("requirements")) materials.add(requirement(row.getAsJsonObject(),economy));
                long materialTotal=materials.stream().mapToLong(TaskMaterialPlanner.Requirement::quantity).sum();
                if(materialTotal>Integer.MAX_VALUE)throw new IllegalArgumentException("材料总数量超出整数上限");
                List<ItemStack> rewards = new ArrayList<>();
                for (JsonElement row : q.getAsJsonArray("rewardItems")) {
                    JsonObject data = row.getAsJsonObject();
                    ItemStack stack = validStack(data,economy);
                    if (data.has("quality")) ItemQuality.apply(stack,quality,data.get("quality").getAsInt(),null);
                    rewards.add(stack);
                }
                for (JsonElement key : q.getAsJsonArray("unlockFeatures")) if (!features.containsKey(key.getAsString())) throw new IllegalArgumentException("不是预设功能：" + key.getAsString());
                if (q.toString().length() > 48000) throw new IllegalArgumentException("单任务配置超过48000字符");
                Quest quest = new Quest(q,List.copyOf(materials),List.copyOf(rewards),List.copyOf(goals));
                if (lookup.put(id,quest) != null) throw new IllegalArgumentException("任务 ID 重复");
                definitions.add(quest);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("tasks/quests.toml：任务 " + id + "：" + e.getMessage(),e);
            }
        }
        byId = Collections.unmodifiableMap(lookup);
        for(Quest quest:definitions){
            Set<String> visited=new HashSet<>();Quest current=quest;
            while(!current.previous().isEmpty()){
                if(!visited.add(current.id()))throw new IllegalArgumentException("任务前置存在循环："+quest.id());
                Quest prior=lookup.get(current.previous());
                if(prior==null||!prior.kind().equals(quest.kind())||!prior.profession().equals(quest.profession()))throw new IllegalArgumentException("前置任务不存在或不属于同一任务线："+quest.id());
                current=prior;
            }
        }
        definitions.sort(Comparator.comparingInt(q->switch(q.kind()){case "main"->0;case "side"->1;default->2;}));
        quests = List.copyOf(definitions);
    }

    public static TaskMaterialPlanner.Requirement requirement(JsonObject data, ItemEconomy economy) {
        long quantity = data.get("quantity").getAsLong();
        if (quantity < 1 || quantity > Integer.MAX_VALUE) throw new IllegalArgumentException("材料数量必须为正整数");
        JsonObject encoded = new JsonObject(); encoded.addProperty("id",text(data,"itemId",256)); encoded.addProperty("count",1);
        JsonObject components = data.has("components") ? data.getAsJsonObject("components").deepCopy() : new JsonObject();
        encoded.add("components",components);
        ItemStack template = validStack(encoded,economy);
        if (!ItemEconomy.safeToSell(template)) throw new IllegalArgumentException("任务材料不可要求命名物、非空容器或额外自定义数据");
        Integer grade = data.has("minimumQuality") ? data.get("minimumQuality").getAsInt() : null;
        if (grade != null && (grade < 0 || grade > 4)) throw new IllegalArgumentException("最低品质必须为0～4");
        return new TaskMaterialPlanner.Requirement(template,components,(int)quantity,grade);
    }
    private static ItemStack validStack(JsonObject data, ItemEconomy economy) {
        String id = text(data,"id",256);
        if (!BuiltInRegistries.ITEM.containsKey(Identifier.parse(id))) throw new IllegalArgumentException("物品 ID 未注册：" + id);
        ItemStack stack = economy.decode(data);
        if (stack.isEmpty()) throw new IllegalArgumentException("物品模板为空：" + id);
        return stack;
    }
    private static String text(JsonObject object, String key, int maximum) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isString()) throw new IllegalArgumentException("缺少文本字段 " + key);
        String value = object.get(key).getAsString();
        if (value.isBlank() || value.length() > maximum) throw new IllegalArgumentException(key + "为空或超过" + maximum + "字");
        return value;
    }
    public Quest get(String id) {
        Quest quest = byId.get(id);
        if (quest == null) throw new IllegalArgumentException("任务已删除或不存在");
        return quest;
    }
    public Quest nextMain(IslandState.Party p) {
        return quests.stream().filter(q -> q.kind().equals("main") && !p.completedQuestIds.contains(q.id())).findFirst().orElse(null);
    }
    public boolean available(IslandState.Party p, Quest quest) {return available(p,null,quest);}
    public boolean available(IslandState.Party p,IslandState.Person person,Quest quest) {
        Progress progress=progress(p,person,quest);
        if(p==null||progress==null||progress.completed().contains(quest.id()))return false;
        if(!quest.previous().isEmpty()&&!progress.completed().contains(quest.previous()))return false;
        if (!quest.kind().equals("main")) return true;
        Quest next = nextMain(p); return next != null && next.id().equals(quest.id());
    }
    public int count(IslandState.Party p,IslandState.Person person,Quest q,Goal goal){var progress=progress(p,person,q);return progress==null?0:progress.counters().getOrDefault(q.id(),Map.of()).getOrDefault(goal.key(),0);}
    public long resetVersion(IslandState.Party p,IslandState.Person person,Quest q){var progress=progress(p,person,q);return progress==null?0:progress.resetVersions().getOrDefault(q.id(),0L);}
    public String status(IslandState.Party p, Quest quest) {return status(p,null,quest);}
    public String status(IslandState.Party p,IslandState.Person person,Quest quest) {
        Progress progress=progress(p,person,quest);
        if(p==null||progress==null)return "locked";
        if(progress.completed().contains(quest.id()))return "completed";
        if(!available(p,person,quest))return "locked";
        if(quest.objective().equals("tracked"))return quest.goals().stream().allMatch(g->count(p,person,quest,g)>=g.quantity())?"ready":"pending";
        return quest.objective().equals("place_shipping_chest") ? progress.ready().contains(quest.id()) ? "ready" : "pending" : "available";
    }
    public JsonArray summaries(IslandState.Party party) {return summaries(party,null);}
    public JsonArray summaries(IslandState.Party party,IslandState.Person person) {
        JsonArray array = new JsonArray();
        for (Quest quest : quests) {
            JsonObject row = new JsonObject();
            row.addProperty("id",quest.id()); row.add("name",quest.definition.get("name"));
            row.addProperty("kind",quest.kind()); row.addProperty("objective",quest.objective()); row.addProperty("status",status(party,person,quest));
            row.add("goals",goalDetails(party,person,quest));row.addProperty("resetVersion",resetVersion(party,person,quest));
            array.add(row);
        }
        return array;
    }
    public JsonObject detail(IslandState.Party party, Quest quest) {return detail(party,null,quest);}
    public JsonObject detail(IslandState.Party party,IslandState.Person person,Quest quest) {
        JsonObject data = quest.definition.deepCopy();
        data.addProperty("revision",revision); data.addProperty("status",status(party,person,quest));
        data.addProperty("resetVersion",resetVersion(party,person,quest));data.add("goals",goalDetails(party,person,quest));
        int index = 0;
        for (JsonElement row : data.getAsJsonArray("requirements")) {
            var requirement = quest.requirements.get(index++); JsonObject r = row.getAsJsonObject();
            r.add("stack",economy.encode(requirement.template()));
            JsonObject canonical = new JsonObject(); canonical.addProperty("itemId",r.get("itemId").getAsString()); canonical.add("componentPatch",requirement.components());
            r.add("canonical",canonical);
        }
        JsonArray items = new JsonArray(); quest.rewards.forEach(stack -> items.add(economy.encode(stack)));
        data.add("rewardItems",items);
        return data;
    }
    private JsonArray goalDetails(IslandState.Party party,IslandState.Person person,Quest quest){
        JsonArray rows=new JsonArray();
        for(Goal goal:quest.goals()){
            JsonObject row=new JsonObject();row.addProperty("type",goal.type());row.addProperty("targetId",goal.targetId());row.addProperty("quantity",goal.quantity());row.addProperty("progress",count(party,person,quest,goal));
            if(!goal.sourceBlockId().isEmpty())row.addProperty("sourceBlockId",goal.sourceBlockId());
            if(!goal.toolId().isEmpty())row.addProperty("toolId",goal.toolId());
            rows.add(row);
        }return rows;
    }
    public static String actionName(String type){return switch(type){case "craft"->"制作／加工";case "smelt"->"手动领取熔炼";case "fish"->"钓获";case "harvest"->"亲自采集";case "kill"->"亲自击杀";default->type;};}
    public JsonArray featureList() {
        JsonArray array = new JsonArray();
        features.values().forEach(f -> {JsonObject o = new JsonObject(); o.addProperty("id",f.id);o.addProperty("name",f.name);o.addProperty("defaultUnlocked",f.defaultUnlocked);array.add(o);});
        return array;
    }
}
