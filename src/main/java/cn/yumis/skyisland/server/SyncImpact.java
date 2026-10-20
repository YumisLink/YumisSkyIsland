package cn.yumis.skyisland.server;

import java.util.*;

/** Explicit publication effects; these run only after a transaction becomes durable. */
public record SyncImpact(List<Mark> marks) {
    public record Mark(String scope,String domain) {}
    public static SyncImpact none(){return new SyncImpact(List.of());}
    public static SyncImpact global(String... domains){return scoped("g",domains);}
    public static SyncImpact party(UUID id,String... domains){return id==null?none():scoped("t:"+id,domains);}
    public static SyncImpact person(UUID id,String... domains){return id==null?none():scoped("p:"+id,domains);}
    private static SyncImpact scoped(String scope,String[] domains){return new SyncImpact(Arrays.stream(domains).map(d->new Mark(scope,d)).toList());}
    public SyncImpact and(SyncImpact other){var all=new ArrayList<>(marks);all.addAll(other.marks);return new SyncImpact(List.copyOf(all));}
    public static SyncImpact members(SkyServer rt,UUID party,String... domains){
        var p=rt.state().parties.get(party);SyncImpact result=none();if(p!=null)for(UUID id:p.members)result=result.and(person(id,domains));return result;
    }
    public static SyncImpact score(SkyServer rt,UUID party){return party(party,"lottery","npc","features").and(members(rt,party,"lottery","accessories"));}
    public void publish(ViewSyncService sync){for(var mark:marks)sync.mark(mark.scope,mark.domain);}
}
