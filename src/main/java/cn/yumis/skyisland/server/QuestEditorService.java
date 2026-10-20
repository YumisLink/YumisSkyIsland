package cn.yumis.skyisland.server;

import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.network.TaskPayloads;
import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Per-player editor mode; all authority and revision checks remain on the server. */
public final class QuestEditorService {
    private final SkyServer rt;
    public QuestEditorService(SkyServer rt){this.rt=rt;}
    public boolean editing(ServerPlayer p) {
        return rt.editors.enabled(p);
    }
    public void forget(ServerPlayer p){rt.editors.forget(p);}
    public void setMode(ServerPlayer player, boolean enabled) {
        rt.editors.set(player,enabled);
    }
    public void request(ServerPlayer player, TaskPayloads.Request request) {
        try {
            JsonObject body=JsonParser.parseString(request.json()).getAsJsonObject();
            switch(request.action()){
                case "detail" -> sendDetail(player,body.get("id").getAsString());
                case "claim" -> {
                    var p=rt.parties.requireMember(player);
                    if(!p.id.toString().equals(body.get("partyId").getAsString())||p.overworldEpoch!=body.get("islandEpoch").getAsLong())throw new IllegalArgumentException("队伍或岛屿已变化，请重新确认");
                    rt.progression.claimQuest(player,body.get("id").getAsString(),body.get("revision").getAsLong(),body.get("resetVersion").getAsLong());
                }
                case "save", "delete", "move", "reset" -> {
                    requireEditor(player,body.get("revision").getAsLong());
                    if(request.action().equals("reset"))resetProgress(player,body);
                    else editDefinition(player,request.action(),body);
                }
                default -> throw new IllegalArgumentException("未知任务操作");
            }
        } catch (IOException | RuntimeException error) {
            JsonObject data=new JsonObject();data.addProperty("action",request.action());data.addProperty("message",error.getMessage()==null?"任务请求无效":error.getMessage());
            send(player,"error",data);rt.sendState(player);
            if(Protection.admin(player))sendEditor(player);
        }
    }
    private void requireAdmin(ServerPlayer p){if(!Protection.admin(p))throw new IllegalArgumentException("仅管理员可以修改任务");}
    private void requireEditor(ServerPlayer p,long revision){
        requireAdmin(p);
        rt.editors.require(p);
        if(!rt.store.available())throw new IllegalStateException("数据正在保存，请稍后重试");
        if(revision!=rt.progression.catalog().revision)throw new IllegalArgumentException("另一位管理员已修改任务，请查看最新定义后重新编辑");
    }
    private void editDefinition(ServerPlayer player,String action,JsonObject body)throws IOException{
        JsonObject document=rt.config.object("tasks/quests.toml").deepCopy();
        JsonArray rows=document.getAsJsonArray("quests");
        String id=body.has("id")?body.get("id").getAsString():"";
        int index=-1;
        for(int i=0;i<rows.size();i++)if(rows.get(i).getAsJsonObject().get("id").getAsString().equals(id)){index=i;break;}
        if(action.equals("save")){
            JsonObject definition=body.getAsJsonObject("definition").deepCopy();
            definition.remove("description");
            if(id.isEmpty()){
                id="quest_"+UUID.randomUUID().toString().replace("-","");
                definition.addProperty("id",id);rows.add(definition);
            }else{
                if(index<0)throw new IllegalArgumentException("任务已被删除");
                if(!id.equals(definition.get("id").getAsString()))throw new IllegalArgumentException("已有任务 ID 不可修改");
                rows.set(index,definition);
            }
        }else{
            if(index<0)throw new IllegalArgumentException("任务已被删除");
            if(action.equals("delete")){
                confirmed(body);rows.remove(index);
            }else{
                int direction=body.get("direction").getAsInt();
                if(direction!=-1&&direction!=1)throw new IllegalArgumentException("排序方向无效");
                int other=index+direction;
                if(other<0||other>=rows.size())throw new IllegalArgumentException(direction<0?"任务已在最上方":"任务已在最下方");
                JsonElement row=rows.get(index);rows.set(index,rows.get(other));rows.set(other,row);
            }
        }
        document.addProperty("revision",Math.addExact(rt.progression.catalog().revision,1));
        rt.progression.saveDefinitions(document); // Validate and atomically replace before publishing.
        JsonObject ack=new JsonObject();ack.addProperty("action",action);ack.addProperty("id",id);ack.addProperty("revision",rt.progression.catalog().revision);
        send(player,"saved",ack);
        rt.views.impact(SyncImpact.global("tasks","quest","editor"));
        rt.syncAll();
        if(!action.equals("delete"))sendDetail(player,id);
    }
    private void resetProgress(ServerPlayer player,JsonObject body){
        confirmed(body);String id=body.get("id").getAsString();var quest=rt.progression.catalog().get(id);
        rt.change(player, SyncImpact.global("tasks","quest"),state -> {
            if(quest.kind().equals("main"))for(IslandState.Party p:state.parties.values()){
                p.completedQuestIds.remove(id);p.readyQuestIds.remove(id);
                if(p.mainQuestProgress!=null)p.mainQuestProgress.remove(id);
                p.questResetVersions.merge(id,1L,Math::addExact);
            }
            else for(IslandState.Person p:state.players.values()){
                p.completedQuestIds.remove(id);p.readyQuestIds.remove(id);p.questProgress.remove(id);p.questResetVersions.merge(id,1L,Math::addExact);
            }
        },() -> {sendEditor(player);sendDetail(player,id);SkyServer.tell(player,"已重置全服该任务进度；其它任务、已有奖励与功能解锁保留");});
    }
    private static void confirmed(JsonObject body){if(!body.has("confirmed")||!body.get("confirmed").getAsBoolean())throw new IllegalArgumentException("请先确认此管理员操作");}
    public void sendDetail(ServerPlayer player,String id){
        send(player,"detail",detail(player,id));
    }
    public JsonObject detail(ServerPlayer player,String id){
        var catalog=rt.progression.catalog();var detail=catalog.detail(rt.parties.active(player.getUUID()),rt.state().players.get(player.getUUID()),catalog.get(id));
        if(editing(player))detail.add("definition",catalog.get(id).definition().deepCopy());return detail;
    }
    public void sendEditor(ServerPlayer player){
        sendEditor(player,false);
    }
    private void sendEditor(ServerPlayer player,boolean open){
        JsonObject data=new JsonObject();data.addProperty("canEdit",Protection.admin(player));data.addProperty("editing",editing(player));
        data.addProperty("revision",rt.progression.catalog().revision);
        data.addProperty("open",open);
        if(Protection.admin(player)){data.add("features",rt.progression.catalog().featureList());data.add("quests",rt.progression.catalog().summaries(rt.parties.active(player.getUUID()),rt.state().players.get(player.getUUID())));}
        send(player,"editor",data);
    }
    private void send(ServerPlayer player,String kind,JsonObject data){
        if(!ServerPlayNetworking.canSend(player,TaskPayloads.Snapshot.TYPE))return;
        JsonObject packet=new JsonObject();packet.addProperty("kind",kind);packet.add("data",data);
        ServerPlayNetworking.send(player,new TaskPayloads.Snapshot(packet.toString()));
    }
}
