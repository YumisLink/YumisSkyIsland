package cn.yumis.skyisland.core;

import java.util.*;
import java.util.function.Supplier;

/** In-memory revisions and bounded serialized caches. Clean reads never invoke the builder. */
public final class ViewVersions {
    private long clock;
    private final Map<String,Long> revisions=new HashMap<>();
    private static final class Cached {
        final long version;final String value;int budget;List<String> parts;
        Cached(long version,String value){this.version=version;this.value=value;}
    }
    private final Map<String,Cached> cache=new LinkedHashMap<>(128,0.75f,true);
    public long builds,buildNanos,cacheHits,fragmentations;
    public long mark(String key){long next=++clock;revisions.put(key,next);return next;}
    public long version(String... dependencies){long result=0;for(String key:dependencies)result=Math.max(result,revisions.getOrDefault(key,0L));return result;}
    public String serialized(String key,long version,Supplier<String> builder){
        Cached old=cache.get(key);if(old!=null&&old.version==version){cacheHits++;return old.value;}
        long started=System.nanoTime();String value=builder.get();buildNanos+=System.nanoTime()-started;builds++;
        cache.put(key,new Cached(version,value));while(cache.size()>2048)cache.remove(cache.keySet().iterator().next());return value;
    }
    public List<String> fragments(String key,long version,int maximum,Supplier<String> builder){
        String value=serialized(key,version,builder);Cached cached=cache.get(key);
        if(cached.parts==null||cached.budget!=maximum){cached.parts=chunks(value,maximum);cached.budget=maximum;fragmentations++;}return cached.parts;
    }
    /** UTF-8 bounded pieces without splitting a surrogate pair. Envelope escaping is budgeted separately. */
    public static List<String> chunks(String value){return chunks(value,30000);}
    public static List<String> chunks(String value,int maximum){
        if(maximum<6)throw new IllegalArgumentException("Fragment budget is too small");
        List<String> out=new ArrayList<>();int start=0,bytes=0;
        for(int i=0;i<value.length();){int cp=value.codePointAt(i),n=Character.charCount(cp),size=cp<128?1:cp<2048?2:cp<65536?3:4;
            // Account for JSON string escaping, including Gson's optional HTML escaping.
            int budget=cp=='"'||cp=='\\'||cp=='\b'||cp=='\f'||cp=='\n'||cp=='\r'||cp=='\t'?2:
                cp<32||cp==0x2028||cp==0x2029||cp=='<'||cp=='>'||cp=='&'||cp=='='||cp=='\''?6:size;
            if(bytes+budget>maximum&&i>start){out.add(value.substring(start,i));start=i;bytes=0;}bytes+=budget;i+=n;
        }out.add(value.substring(start));return List.copyOf(out);
    }
}
