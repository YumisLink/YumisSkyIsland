package cn.yumis.skyisland.wand;

import cn.yumis.skyisland.config.GameConfig;
import com.google.gson.*;
import java.util.*;

public record WandRules(List<Integer> limits,int searchLimit,int undoDepth,List<Set<String>> fuzzyGroups) {
    public static WandRules load(GameConfig config) {
        JsonObject data=config.object("gameplay/construction_wand.toml");
        List<Integer> limits=new ArrayList<>();
        for (JsonElement value:data.getAsJsonArray("placementLimits")) {
            int limit=value.getAsInt();
            if(limit<1||limit>256)throw new IllegalArgumentException("建造杖上限须在1～256之间");
            limits.add(limit);
        }
        if(limits.size()!=6)throw new IllegalArgumentException("建造杖必须配置六档上限");
        int search=data.get("searchLimit").getAsInt(),undo=data.get("undoDepth").getAsInt();
        if(search<256||search>4096||undo<1||undo>3)throw new IllegalArgumentException("建造杖搜索上限256～4096，撤销记录1～3");
        List<Set<String>> groups=new ArrayList<>();
        for(JsonElement row:data.getAsJsonArray("fuzzyGroups")) {
            Set<String> group=new HashSet<>();for(JsonElement id:row.getAsJsonArray())group.add(id.getAsString());
            groups.add(Set.copyOf(group));
        }
        return new WandRules(List.copyOf(limits),search,undo,List.copyOf(groups));
    }
    public boolean matches(String first,String other,int mode) {
        if(mode==2||first.equals(other))return true;
        return mode==1&&fuzzyGroups.stream().anyMatch(group->group.contains(first)&&group.contains(other));
    }
}
