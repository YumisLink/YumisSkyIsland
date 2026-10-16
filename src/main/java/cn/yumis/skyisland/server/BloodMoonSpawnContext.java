package cn.yumis.skyisland.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;

/** Scoped only to one Overworld spawnForChunk call on the server thread. */
public final class BloodMoonSpawnContext {
    public record Context(NaturalSpawner.SpawnState state,int factor) {
        public boolean globalRoom(){
            long cap=(long)MobCategory.MONSTER.getMaxInstancesPerChunk()*state.getSpawnableChunkCount()*factor/289;
            return state.getMobCategoryCounts().getInt(MobCategory.MONSTER)<cap;
        }
    }
    private static final ThreadLocal<Context> ACTIVE=new ThreadLocal<>();
    private BloodMoonSpawnContext(){}
    public static void begin(NaturalSpawner.SpawnState state,int factor){ACTIVE.set(new Context(state,factor));}
    public static Context current(){return ACTIVE.get();}
    public static void end(){ACTIVE.remove();}
    public static Iterator<MobCategory> attempts(List<MobCategory> vanilla){
        Context blood=current();
        if(blood==null)return vanilla.iterator();
        int factor=Math.max(0,blood.factor());
        List<MobCategory> attempts=new ArrayList<>(vanilla.size()+factor);
        boolean listed=false;
        for(MobCategory category:vanilla){
            if(category==MobCategory.MONSTER){
                listed=true;
                for(int i=0;i<factor;i++)attempts.add(category);
            }else attempts.add(category);
        }
        if(!listed)for(int i=0;i<factor;i++)attempts.add(MobCategory.MONSTER);
        return new Iterator<>(){
            int index;
            @Override public boolean hasNext(){
                while(index<attempts.size()&&attempts.get(index)==MobCategory.MONSTER&&!blood.globalRoom())index++;
                return index<attempts.size();
            }
            @Override public MobCategory next(){
                if(!hasNext())throw new NoSuchElementException();
                return attempts.get(index++);
            }
        };
    }
}
