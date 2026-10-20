package cn.yumis.skyisland.client;

import cn.yumis.skyisland.network.ViewSyncPayloads;
import com.google.gson.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;

/** Connection-local conditional cache. Rendering records demand but never sends network requests. */
public final class ViewSyncClient {
    private static final class Entry {
        long version=-1,request=-1,target=-1;boolean check=true,loaded,failed;int sent=-100,last=-100,nextPage,pages,retries;
        JsonObject data;
        final StringBuilder staging=new StringBuilder();
    }
    private final ClientCatalog model;
    private final Map<String,Entry> entries=new HashMap<>();
    private final Map<Long,Integer> activity=new HashMap<>();
    private Set<String> wanted=Set.of(),current=Set.of();
    private String context="",viewToken="",quest="",editor="",tooltip="";
    private Object screen;
    private int tick,lastWatch=-100,tooltipTick=-100;
    private boolean clockRunning=true,gameRunning=true;
    private long sequence,clockNanos,clockEffective,clockWorld,clockGame,clockAnchor;
    public long queries,replies,bytes;
    public ViewSyncClient(ClientCatalog model){this.model=model;}
    private Entry entry(String key){return entries.computeIfAbsent(key,_->new Entry());}
    public void clear(){entries.clear();activity.clear();wanted=Set.of();current=Set.of();context="";viewToken="";quest="";editor="";tooltip="";screen=null;sequence=0;lastWatch=-100;clockNanos=0;}
    public void noteTooltip(ItemStack stack){if(stack.isEmpty())return;tooltip=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();tooltipTick=tick;}
    public void selectQuest(String id){if(!quest.equals(id)){quest=id;entry("quest:"+id).check=true;}}
    public void editor(String action,JsonObject body){editor="editor:"+action+":"+body;Entry e=entry(editor);e.check=true;e.failed=false;e.retries=0;}
    private String token(Object value){return value instanceof ControlPanelScreen p?"i:"+p.section()+":"+model.saleDesired:value instanceof AccessoryScreen a?"o:"+a.syncTab():value instanceof ProfessionScreen p?"p:"+p.syncTab():"";}
    public void closeEditor(){editor="";}
    public boolean ready(){Object active=Minecraft.getInstance().gui.screen();return screen==active&&viewToken.equals(token(active))&&!context.isEmpty()&&viewKeys(active).stream().allMatch(k->{Entry e=entry(k);return e.loaded&&!e.check&&e.request<0&&!e.failed;});}
    public boolean loaded(){return viewKeys(Minecraft.getInstance().gui.screen()).stream().allMatch(k->entry(k).loaded);}
    public long versionOf(String key){Entry e=entries.get(key);return e==null?-1:e.version;}
    public boolean stale(String key){Entry e=entries.get(key);return e==null||!e.loaded||e.check||e.request>=0||e.failed;}
    public void retry(){for(String key:current){Entry e=entry(key);e.failed=false;e.check=true;e.request=-1;e.retries=0;}}
    public boolean retryClick(double y){if(y>=30&&y<43&&!ready()){retry();return true;}return false;}
    public void status(GuiGraphicsExtractor g,int width){if(ready())return;boolean failed=current.stream().anyMatch(k->entry(k).failed);String text=failed?"同步失败，点击此处重试":loaded()?"更新中…":"正在加载…";var font=Minecraft.getInstance().font;g.fill(8,29,width-8,42,0xCC161A1D);g.text(font,text,12,32,failed?0xFFFFAAAA:0xFFFFDC96,false);}
    private Set<String> viewKeys(Object value){
        Set<String> keys=new LinkedHashSet<>();
        if(value instanceof ControlPanelScreen panel){
            switch(panel.section()){
                case 0,1 -> keys.addAll(List.of("catalog","economy","market","stock","trade","features","npcEffects"));
                case 2 -> {keys.add("tasks");if(!quest.isEmpty())keys.add("quest:"+quest);}
                case 3 -> keys.addAll(List.of("bounties","features","catalog"));
                case 4 -> keys.addAll(List.of("npc","draft"));
            }
            if(model.saleDesired)keys.addAll(List.of("catalog","economy","market","quotas","npcEffects"));
        }else if(value instanceof ProfessionScreen)keys.addAll(List.of("profession","guide","professionShop","accessories","buffs"));
        else if(value instanceof AccessoryScreen a){keys.add("accessories");if(a.syncTab()==1)keys.add("lottery");if(a.syncTab()==2)keys.addAll(List.of("buffs","profession","guide"));}
        else if(value instanceof PlayerSettingsScreen)keys.add("settings");
        else if(value instanceof CreativeModeInventoryScreen)keys.add("blueprints");
        else if(value instanceof SorterScreen sorter)keys.add(sorter.syncKey());
        else if(value instanceof BlueprintCannonScreen cannon)keys.add(cannon.syncKey());
        if(!editor.isEmpty()&&model.editing()&&(model.configWanted||value instanceof ControlPanelScreen||value instanceof LiveConfigScreen)){keys.add(editor);if(editor.startsWith("editor:item_open:"))keys.add("blueprints");}
        if(!keys.isEmpty())keys.add("display");return keys;
    }
    public void tick(){
        tick++;var mc=Minecraft.getInstance();if(mc.getConnection()==null||context.isEmpty()||!ClientPlayNetworking.canSend(ViewSyncPayloads.Query.TYPE))return;
        Object next=mc.gui.screen();String token=next instanceof ControlPanelScreen p?"i:"+p.section()+":"+model.saleDesired:next instanceof AccessoryScreen a?"o:"+a.syncTab():next instanceof ProfessionScreen p?"p:"+p.syncTab():"";
        Set<String> ui=viewKeys(next);
        if(screen!=next||!viewToken.equals(token)){for(String key:ui){Entry e=entry(key);e.check=true;e.failed=false;e.retries=0;}screen=next;viewToken=token;}
        current=Set.copyOf(ui);Set<String> desired=new LinkedHashSet<>(ui);desired.add("display");
        if(tick-tooltipTick<=3&&!tooltip.isEmpty()){
            desired.addAll(List.of("item:"+tooltip,"economy","market","scoring","npcEffects","buffs"));
            var item=BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(tooltip));
            if(item instanceof BlockItem block)desired.add("block:"+BuiltInRegistries.BLOCK.getKey(block.getBlock()));
            var product=model.match(new ItemStack(item));if(product!=null)desired.add("quota:"+product.quotaGroup());
        }
        Set<String> added=new HashSet<>(desired);added.removeAll(wanted);
        for(String key:added){Entry e=entry(key);e.check=true;e.failed=false;e.retries=0;}
        if(!desired.equals(wanted)||(desired.size()>1&&tick-lastWatch>=100)){
            JsonObject body=base("watch");JsonArray keys=new JsonArray();desired.forEach(keys::add);body.add("keys",keys);send(body);lastWatch=tick;
        }for(String key:wanted)if(!desired.contains(key)){Entry e=entry(key);e.request=-1;e.staging.setLength(0);}wanted=Set.copyOf(desired);
        JsonObject versions=new JsonObject();long request=++sequence;
        for(String key:desired){Entry e=entry(key);
            if(e.request>=0&&tick-activity.getOrDefault(e.request,e.sent)>=100){e.request=-1;e.staging.setLength(0);if(e.retries++>=1)e.failed=true;}
            if(!e.check||e.request>=0||e.failed||tick-e.last<4)continue;
            if(versions.size()==24){query(request,versions);versions=new JsonObject();request=++sequence;}
            e.request=request;e.sent=tick;e.last=tick;e.nextPage=0;e.staging.setLength(0);versions.addProperty(key,e.version);
        }if(!versions.isEmpty())query(request,versions);
        if(clockNanos!=0){long elapsed=gameElapsed(mc);model.state.addProperty("effectiveTicks",clockEffective+(gameRunning?elapsed:0));long left=Math.floorMod(clockAnchor-clockWorld-(clockRunning?elapsed:0),24000);model.state.addProperty("ticksLeft",left==0?24000:left);}
    }
    private long gameElapsed(Minecraft mc){return mc.level==null?0:Math.max(0,mc.level.getGameTime()-clockGame);}
    private JsonObject base(String action){JsonObject body=new JsonObject();body.addProperty("action",action);body.addProperty("context",context);return body;}
    private void query(long request,JsonObject versions){JsonObject body=base("query");body.addProperty("request",request);body.add("versions",versions);queries+=versions.size();activity.put(request,tick);send(body);if(activity.size()>512)activity.entrySet().removeIf(e->tick-e.getValue()>200);}
    private void send(JsonObject body){ClientPlayNetworking.send(new ViewSyncPayloads.Query(body.toString()));}
    public void receive(String json){
        replies++;bytes+=json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;JsonObject packet=JsonParser.parseString(json).getAsJsonObject();String kind=packet.get("kind").getAsString(),incoming=packet.get("context").getAsString();
        if(kind.equals("hud")){
            if(!incoming.equals(context)){clear();model.resetViewData();context=incoming;}
            JsonObject patch=packet.getAsJsonObject("data");if(patch.has("flightRemainingMs"))model.flightSnapshotClientTime=System.nanoTime();model.applyStatePatch(patch);return;
        }
        if(!incoming.equals(context))return;
        if(kind.equals("clock")){clockRunning=packet.get("clockRunning").getAsBoolean();gameRunning=packet.get("gameRunning").getAsBoolean();clockEffective=packet.get("effectiveTicks").getAsLong();clockWorld=packet.get("worldTime").getAsLong();clockGame=Minecraft.getInstance().level==null?packet.get("gameTime").getAsLong():Minecraft.getInstance().level.getGameTime();clockAnchor=packet.get("anchor").getAsLong();clockNanos=System.nanoTime();return;}
        if(kind.equals("error")){model.lastError=packet.get("message").getAsString();for(String key:wanted){Entry e=entry(key);if(e.request>=0){e.request=-1;e.failed=true;}}return;}
        if(kind.equals("dirty")){for(var change:packet.getAsJsonObject("versions").entrySet()){Entry e=entry(change.getKey());long target=change.getValue().getAsLong();if(target>e.version){e.target=target;e.check=true;e.request=-1;e.staging.setLength(0);e.failed=false;}}return;}
        String key=packet.get("key").getAsString();if(!wanted.contains(key))return;Entry e=entry(key);if(e.request!=packet.get("request").getAsLong())return;activity.put(e.request,tick);long version=packet.get("version").getAsLong();if(version<e.target)return;
        if(kind.equals("retry")){e.request=-1;e.last=tick;return;}
        if(kind.equals("same")){if(!e.loaded||e.version!=version)return;if(e.data!=null&&(key.startsWith("editor:")||key.startsWith("sorter@")||key.startsWith("cannon@")))model.applyView(key,e.data.deepCopy());e.check=false;e.request=-1;e.retries=0;return;}
        if(!kind.equals("data"))return;int page=packet.get("page").getAsInt(),pages=packet.get("pages").getAsInt();
        if(page!=e.nextPage||pages<1||pages>4096){e.request=-1;e.check=true;return;}
        if(page==0){e.pages=pages;e.target=version;}if(e.pages!=pages)return;
        e.staging.append(packet.get("data").getAsString());if(e.staging.length()>16*1024*1024){e.request=-1;e.failed=true;return;}e.nextPage++;
        if(e.nextPage==pages){JsonObject data=JsonParser.parseString(e.staging.toString()).getAsJsonObject();e.data=data.deepCopy();model.applyView(key,data);e.version=version;e.loaded=true;e.check=false;e.request=-1;e.failed=false;e.retries=0;e.staging.setLength(0);}
    }
}
