package cn.yumis.skyisland.server;

import cn.yumis.skyisland.core.ViewVersions;
import cn.yumis.skyisland.network.ViewSyncPayloads;
import cn.yumis.skyisland.data.IslandState;
import com.google.gson.*;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Server-owned revisions, scoped subscriptions and bounded view delivery. All calls run on the server thread. */
public final class ViewSyncService {
    private static final Set<String> GLOBAL=Set.of("display","catalog","economy","scoring","guide","professionShop","market","blueprints");
    private static final Set<String> TEAM=Set.of("stock","quotas","trade","features","bounties","npc","npcEffects");
    private static final Set<String> PERSONAL=Set.of("profession","accessories","buffs","lottery","draft","settings","tasks");
    private final SkyServer rt;
    private long observedWorld=Long.MIN_VALUE,observedGame=Long.MIN_VALUE;
    private boolean clockRunning=true,gameRunning=true;
    public final ViewVersions versions=new ViewVersions();
    public final String session=UUID.randomUUID().toString();
    private record Watch(long version,int expires) {}
    private record Delivery(String context,String key,long version,String kind,String packet) {}
    private static final class Connection {
        String context="";final Map<String,Watch> watches=new HashMap<>();
        final ArrayDeque<Delivery> queue=new ArrayDeque<>();final Map<String,Integer> lastQuery=new HashMap<>();
        List<Object> hud=List.of();int lastClock;String dimension="";
    }
    private final Map<UUID,Connection> connections=new HashMap<>();
    public long packets,bytes,queries,notModified,dataPackets,dataBytes,dirtyPackets,hudPackets;
    public ViewSyncService(SkyServer rt){this.rt=rt;}
    public void mark(String scope,String domain){versions.mark(scope+"/"+domain);if(domain.startsWith("stock:"))versions.mark(scope+"/stockList");if(domain.startsWith("quota:"))versions.mark(scope+"/quotaList");}
    private final Map<String,Object> observations=new HashMap<>();
    public void observe(String scope,String key,Object value){String id=scope+"/"+key;if(!Objects.equals(observations.put(id,value),value))mark(scope,key);}
    private void observeInputs(ServerPlayer p,String key){
        var party=rt.parties.active(p.getUUID());
        if(party!=null&&(key.equals("npcEffects")||key.equals("npc")))observe(team(p),"npcEffects",rt.residents.effectStamp(party));
        if(key.equals("npc"))observe(team(p),"npc",rt.residents.busyStamp());
        if(key.equals("lottery")){int count=0;for(int slot=0;slot<36;slot++){var stack=p.getInventory().getItem(slot);if(stack.is(cn.yumis.skyisland.item.ModItems.LOTTERY_TICKET))count+=stack.getCount();}if(p.getOffhandItem().is(cn.yumis.skyisland.item.ModItems.LOTTERY_TICKET))count+=p.getOffhandItem().getCount();observe("p:"+p.getUUID(),"lottery",count);}
        if(key.equals("profession")){FishingService.applyLevel(rt,p);var attr=p.getAttribute(cn.yumis.skyisland.attribute.ModAttributes.FISHING_POWER);if(attr!=null)observe("p:"+p.getUUID(),key,List.of(attr.getBaseValue(),attr.getValue(),Set.copyOf(attr.getModifiers())));}
    }
    public void impact(SyncImpact impact){impact.publish(this);}
    public String context(ServerPlayer player){var p=rt.parties.active(player.getUUID());return session+"/"+(p==null?"-":p.id+"/"+p.overworldEpoch);}
    private Connection connection(ServerPlayer player){return connections.computeIfAbsent(player.getUUID(),_->new Connection());}
    private String team(ServerPlayer p){var party=rt.parties.active(p.getUUID());return "t:"+(party==null?"-":party.id);}
    private boolean allowed(ServerPlayer p,String key){
        if(key.length()>8192||new JsonPrimitive(key).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>8192)return false;
        if(GLOBAL.contains(key)||TEAM.contains(key)||PERSONAL.contains(key))return true;
        if(key.startsWith("editor:")){if(!rt.editors.enabled(p))return false;try{String[] parts=key.split(":",3);if(parts.length!=3||!Set.of("open","price_open","item_open","product_open").contains(parts[1]))return false;rt.configEditor.viewDependency(key);return true;}catch(RuntimeException error){return false;}}
        if(key.startsWith("quest:")||key.startsWith("block:")||key.startsWith("item:")||key.startsWith("stock:")||key.startsWith("quota:"))return key.indexOf(':')<key.length()-1;
        if(key.startsWith("sorter@"))return rt.sorting.viewAllowed(p,key);
        if(key.startsWith("cannon@"))return rt.prefabs.viewAllowed(p,key);
        return false;
    }
    public long version(ServerPlayer p,String key){
        String parent=key.startsWith("editor:")?rt.configEditor.viewDependency(key):key.startsWith("item:")?"catalog":key.startsWith("quest:")?"quest":key.startsWith("quota:")?"quotas":key.contains(":")?key.substring(0,key.indexOf(':')):key;
        String extra=key.equals("stock")?"stockList":key.equals("quotas")?"quotaList":key.equals("npc")?"npcEffects":key;
        return versions.version("g/"+key,"g/"+parent,team(p)+"/"+key,team(p)+"/"+parent,"p:"+p.getUUID()+"/"+key,"p:"+p.getUUID()+"/"+parent,team(p)+"/"+extra,key.startsWith("editor:")?"p:"+p.getUUID()+"/editor":"g/_none");
    }
    private String cacheKey(ServerPlayer p,String key){
        if(GLOBAL.contains(key)||key.startsWith("item:"))return "g/"+key;
        if(TEAM.contains(key)||key.startsWith("block:")||key.startsWith("stock:")||key.startsWith("quota:"))return team(p)+"/"+context(p)+"/"+key;
        return p.getUUID()+"/"+context(p)+"/"+rt.editors.enabled(p)+"/"+key;
    }
    public void request(ServerPlayer player,ViewSyncPayloads.Query query){
        try{
            JsonObject body=JsonParser.parseString(query.json()).getAsJsonObject();Connection c=connection(player);
            hud(player);if(!context(player).equals(body.get("context").getAsString()))return;
            String action=body.get("action").getAsString();
            if(action.equals("watch")){
                JsonArray keys=body.getAsJsonArray("keys");if(keys.size()>128)throw new IllegalArgumentException("查询范围过大");
                Set<String> desired=new HashSet<>();for(var value:keys){String key=value.getAsString();if(allowed(player,key)){desired.add(key);var old=c.watches.get(key);c.watches.put(key,new Watch(old==null?-1:old.version,key.equals("display")?Integer.MAX_VALUE:rt.tick+200));}}
                c.watches.keySet().retainAll(desired);c.queue.removeIf(d->!desired.contains(d.key));return;
            }
            if(!action.equals("query"))throw new IllegalArgumentException("未知数据查询");
            JsonObject known=body.getAsJsonObject("versions");if(known.size()>32)throw new IllegalArgumentException("查询范围过大");
            long request=body.get("request").getAsLong();
            for(var entry:known.entrySet()){
                String key=entry.getKey();if(!allowed(player,key))continue;
                if(rt.tick-c.lastQuery.getOrDefault(key,-100)<4){enqueue(c,key,version(player,key),envelope("retry",c.context,key,request,version(player,key)));continue;}c.lastQuery.put(key,rt.tick);queries++;
                observeInputs(player,key);long version=version(player,key);c.queue.removeIf(d->d.key.equals(key));
                if(entry.getValue().getAsLong()==version){notModified++;enqueue(c,key,version,envelope("same",c.context,key,request,version));}
                else{
                    int overhead=envelope("data",c.context,key,request,version).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length+128;
                    List<String> pieces=versions.fragments(cacheKey(player,key),version,Math.min(30000,32768-overhead),()->rt.viewSnapshot(player,key).toString());
                    for(int i=0;i<pieces.size();i++){JsonObject packet=envelope("data",c.context,key,request,version);packet.addProperty("page",i);packet.addProperty("pages",pieces.size());packet.addProperty("data",pieces.get(i));enqueue(c,key,version,packet);}
                }
                Watch watch=c.watches.get(key);if(watch!=null)c.watches.put(key,new Watch(version,key.equals("display")?Integer.MAX_VALUE:rt.tick+200));
            }
        }catch(RuntimeException error){JsonObject out=new JsonObject();out.addProperty("kind","error");out.addProperty("context",context(player));out.addProperty("message",error.getMessage()==null?"数据查询失败":error.getMessage());send(player,out);}
    }
    private static JsonObject envelope(String kind,String context,String key,long request,long version){JsonObject out=new JsonObject();out.addProperty("kind",kind);out.addProperty("context",context);out.addProperty("key",key);out.addProperty("request",request);out.addProperty("version",version);return out;}
    private void enqueue(Connection c,String key,long version,JsonObject packet){c.queue.add(new Delivery(c.context,key,version,packet.get("kind").getAsString(),packet.toString()));}
    public void joined(ServerPlayer p){hud(p);}
    public void disconnected(UUID id){connections.remove(id);lastHud.remove(id);observations.keySet().removeIf(k->k.startsWith("p:"+id+"/"));}
    public void hud(ServerPlayer player){
        Connection c=connection(player);String context=context(player);var party=rt.parties.active(player.getUUID());var person=rt.state().players.get(player.getUUID());
        boolean admin=Protection.admin(player),editing=rt.editors.enabled(player);
        long expiry=person==null?0:person.flightExpiryEpochMs;boolean flying=expiry>System.currentTimeMillis();
        List<Object> source=List.of(context,party==null?0L:party.coins,party==null?0:rt.level(party),party==null?"":Objects.toString(party.cachedPoints,"0"),party==null?"":Objects.toString(party.name,""),admin,editing,rt.state().bloodMoonActive,expiry,flying);
        if(source.equals(c.hud))return;
        if(!c.hud.isEmpty()&&(!Objects.equals(source.get(5),c.hud.get(5))||!Objects.equals(source.get(6),c.hud.get(6))))impact(SyncImpact.person(player.getUUID(),"tasks","quest","editor"));
        boolean changedContext=!context.equals(c.context);if(changedContext){c.context=context;c.watches.clear();c.queue.clear();c.lastQuery.clear();}
        JsonObject out=new JsonObject();out.addProperty("kind","hud");out.addProperty("context",context);out.addProperty("reset",changedContext);
        JsonObject data=rt.hudSnapshot(player);JsonObject delta=new JsonObject();
        // The small HUD is built only after its primitive source tuple changes.
        JsonObject old=c.hud.isEmpty()?new JsonObject():lastHud.getOrDefault(player.getUUID(),new JsonObject());
        for(var entry:data.entrySet())if(changedContext||!Objects.equals(old.get(entry.getKey()),entry.getValue()))delta.add(entry.getKey(),entry.getValue());
        if(!c.hud.isEmpty()&&Objects.equals(source.get(8),c.hud.get(8))&&Objects.equals(source.get(9),c.hud.get(9))&&!changedContext)delta.remove("flightRemainingMs");
        out.add("data",delta);c.hud=source;lastHud.put(player.getUUID(),data);send(player,out);clock(player,c);
    }
    private final Map<UUID,JsonObject> lastHud=new HashMap<>();
    private void clock(ServerPlayer p,Connection c){JsonObject out=new JsonObject();out.addProperty("kind","clock");out.addProperty("context",c.context);out.addProperty("clockRunning",clockRunning);out.addProperty("gameRunning",gameRunning);out.addProperty("effectiveTicks",rt.state().effectiveTicks);out.addProperty("worldTime",rt.server.overworld().getOverworldClockTime());out.addProperty("gameTime",rt.server.overworld().getGameTime());out.addProperty("anchor",rt.config.object("shop/shops.toml").getAsJsonObject("market").get("refreshAtDayTick").getAsLong());send(p,out);c.lastClock=rt.tick;}
    public void tick(){
        long world=rt.server.overworld().getOverworldClockTime(),game=rt.server.overworld().getGameTime();boolean reanchor=false;
        if(observedGame!=Long.MIN_VALUE){long wd=world-observedWorld,gd=game-observedGame;boolean nextClock=wd!=0,nextGame=gd!=0;reanchor=nextClock!=clockRunning||nextGame!=gameRunning||wd!=0&&wd!=gd;clockRunning=nextClock;gameRunning=nextGame;}observedWorld=world;observedGame=game;
        for(ServerPlayer p:rt.server.getPlayerList().getPlayers()){
            hud(p);Connection c=connection(p);c.watches.entrySet().removeIf(e->e.getValue().expires<=rt.tick||!allowed(p,e.getKey()));
            if(rt.tick%4==0){JsonObject dirty=new JsonObject();
                c.watches.replaceAll((key,w)->{observeInputs(p,key);long now=version(p,key);if(now!=w.version)dirty.addProperty(key,now);return new Watch(now,w.expires);});
                if(!dirty.isEmpty()){JsonObject out=new JsonObject();out.addProperty("kind","dirty");out.addProperty("context",c.context);out.add("versions",dirty);send(p,out);}
            }
            String dimension=p.level().dimension().identifier().toString();
            if(c.watches.keySet().stream().anyMatch(key->!key.equals("display"))&&(reanchor||!dimension.equals(c.dimension)||rt.tick-c.lastClock>=100))clock(p,c);c.dimension=dimension;
            for(int i=0;i<2&&!c.queue.isEmpty();){Delivery d=c.queue.remove();if(!d.context.equals(context(p))||d.version!=version(p,d.key)||!allowed(p,d.key))continue;if(d.kind.equals("data")){dataPackets++;dataBytes+=d.packet.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;}sendRaw(p,d.packet);i++;}
            if(c.lastQuery.size()>512)c.lastQuery.entrySet().removeIf(e->rt.tick-e.getValue()>200);
        }
    }
    public void send(ServerPlayer p,JsonObject packet){if(packet.get("kind").getAsString().equals("dirty"))dirtyPackets++;if(packet.get("kind").getAsString().equals("hud"))hudPackets++;sendRaw(p,packet.toString());}
    private void sendRaw(ServerPlayer p,String json){if(!ServerPlayNetworking.canSend(p,ViewSyncPayloads.Reply.TYPE))return;packets++;bytes+=json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;ServerPlayNetworking.send(p,new ViewSyncPayloads.Reply(json));}
}
