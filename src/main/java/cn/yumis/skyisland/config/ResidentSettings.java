package cn.yumis.skyisland.config;

import cn.yumis.skyisland.core.ResidentRules;
import com.google.gson.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/** Executable settlement TOML. No prices, materials or completion flags come from clients. */
public final class ResidentSettings {
    public record Stack(String item,int count,int weight) {}
    public record Job(String id,String station,int level,long reputation,List<Stack> supplies,List<Stack> outputs,boolean random,int extraEvery,List<Stack> extra) {}
    public final int minAir,maxAir,searchBudget,arrivalBps,targetBps,graceDays,radius,baseCapacity,firstCapacity,lastCapacity,commissionLimit,commissionReward;
    public final long firstReputation,lastReputation;
    public final int ordinarySaleBps,expertSaleBps,discountBps,librarianXpBps,specialXpBps,chefEvery;
    public final Set<String> foods,lights;
    public final Map<String,Job> jobs=new LinkedHashMap<>();
    public final List<Stack> commissions;
    public ResidentSettings(JsonObject root){
        minAir=number(root,"minAir",1,200);maxAir=number(root,"maxAir",minAir,200);searchBudget=number(root,"searchBudget",201,16384);
        arrivalBps=number(root,"arrivalBps",0,10000);targetBps=number(root,"targetBps",0,10000);graceDays=number(root,"graceDays",1,100);radius=number(root,"roamingRadius",1,64);
        baseCapacity=number(root,"baseCapacity",1,100);firstCapacity=number(root,"firstCapacity",baseCapacity,100);lastCapacity=number(root,"lastCapacity",firstCapacity,100);
        firstReputation=number(root,"firstReputation",1,Integer.MAX_VALUE);lastReputation=number(root,"lastReputation",(int)firstReputation,Integer.MAX_VALUE);
        commissionLimit=number(root,"commissionLimit",1,20);commissionReward=number(root,"commissionReward",1,100000);
        ordinarySaleBps=number(root,"ordinarySaleBps",0,10000);expertSaleBps=number(root,"expertSaleBps",0,10000);discountBps=number(root,"rawDiscountBps",0,9900);
        librarianXpBps=number(root,"librarianXpBps",0,10000);specialXpBps=number(root,"specialXpBps",0,10000);chefEvery=number(root,"chefEvery",2,100);
        foods=strings(root.getAsJsonArray("foods"));lights=strings(root.getAsJsonArray("lights"));
        if(foods.isEmpty()||lights.isEmpty())throw new IllegalArgumentException("居民食物与光源白名单不能为空");
        for(String food:foods)validateItem(food);
        JsonObject rows=root.getAsJsonObject("jobs");
        for(String id:ResidentRules.JOBS){JsonObject row=rows.getAsJsonObject(id);if(row==null)throw new IllegalArgumentException("缺少居民职业："+id);
            String station=row.has("station")?row.get("station").getAsString():"";
            if(!station.isEmpty()&&BuiltInRegistries.BLOCK.getOptional(Identifier.parse(station)).isEmpty())throw new IllegalArgumentException("居民工作站未注册："+station);
            jobs.put(id,new Job(id,station,number(row,"minLevel",0,Integer.MAX_VALUE),number(row,"minReputation",0,Integer.MAX_VALUE),stacks(row.getAsJsonArray("supplies")),stacks(row.getAsJsonArray("outputs")),row.get("randomOutput").getAsBoolean(),number(row,"extraEvery",0,100000),stacks(row.getAsJsonArray("extraOutputs"))));
        }
        commissions=stacks(root.getAsJsonArray("commissions"));if(commissions.isEmpty())throw new IllegalArgumentException("居民委托表不能为空");
        for(Job job:jobs.values()){if(job.outputs.isEmpty())throw new IllegalArgumentException("居民产出表不能为空："+job.id);for(Stack stack:job.outputs)validateItem(stack.item);for(Stack stack:job.extra)validateItem(stack.item);job.outputs.stream().mapToInt(Stack::weight).reduce(0,Math::addExact);}
        for(Stack stack:commissions)validateItem(stack.item);
    }
    public int capacity(long reputation){return reputation>=lastReputation?lastCapacity:reputation>=firstReputation?firstCapacity:baseCapacity;}
    public boolean unlocked(String job,int level,long reputation){Job row=jobs.get(job);return row!=null&&level>=row.level&&reputation>=row.reputation;}
    public List<Stack> outputs(Job job,long batches,Random random){
        List<Stack> result=new ArrayList<>();
        if(job.random){int total=job.outputs.stream().mapToInt(Stack::weight).reduce(0,Math::addExact);int pick=random.nextInt(total);for(Stack row:job.outputs){pick-=row.weight;if(pick<0){result.add(row);break;}}}
        else result.addAll(job.outputs);
        if(job.extraEvery>0&&(batches+1)%job.extraEvery==0)result.addAll(job.extra);
        return result;
    }
    private static int number(JsonObject root,String key,int min,int max){try{int n=root.get(key).getAsBigDecimal().intValueExact();if(n<min||n>max)throw new IllegalArgumentException();return n;}catch(RuntimeException error){throw new IllegalArgumentException("居民配置字段无效："+key+"，范围"+min+"～"+max,error);}}
    private static Set<String> strings(JsonArray array){Set<String> out=new LinkedHashSet<>();for(JsonElement value:array)out.add(value.getAsString());return Set.copyOf(out);}
    private static List<Stack> stacks(JsonArray array){List<Stack> result=new ArrayList<>();if(array!=null)for(JsonElement value:array){JsonObject row=value.getAsJsonObject();String id=row.get("item").getAsString();if(!id.equals("#planks"))validateItem(id);int count=number(row,"count",1,4096),weight=row.has("weight")?number(row,"weight",1,100000):1;result.add(new Stack(id,count,weight));}return List.copyOf(result);}
    private static void validateItem(String id){if(BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElse(Items.AIR)==Items.AIR)throw new IllegalArgumentException("居民物品未注册："+id);}
}
