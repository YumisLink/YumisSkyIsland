package cn.yumis.skyisland.core;

import java.util.*;

/** Pure, replayable single-prize selection and personal pity transitions. */
public final class LotterySelector {
    public record Candidate(String id,int rarity,int minimumLevel) {}
    public record Policy(int rareBps,int epicBps,int legendaryBps,int softStart,int legendaryAt,int epicAt,int featuredBps) {
        public Policy {
            if(rareBps<0||epicBps<0||legendaryBps<0||(long)rareBps+epicBps+legendaryBps>10000||softStart<0||legendaryAt<=softStart||epicAt<1||featuredBps<0||featuredBps>10000)
                throw new IllegalArgumentException("抽奖概率或保底配置无效");
        }
        public int commonBps(){return 10000-rareBps-epicBps-legendaryBps;}
    }
    public record Pity(int withoutLegendary,int withoutEpic,boolean epicOwed,boolean featuredGuaranteed) {
        public Pity {if(withoutLegendary<0||withoutEpic<0)throw new IllegalArgumentException("保底计数无效");}
    }
    public record Result(Candidate prize,Pity next) {}
    private LotterySelector() {}
    public static int legendaryChance(Policy rules,int misses) {
        long next=(long)misses+1;if(next>=rules.legendaryAt)return 10000;
        if(next<=rules.softStart)return rules.legendaryBps;
        return rules.legendaryBps+(int)((10000L-rules.legendaryBps)*(next-rules.softStart)/(rules.legendaryAt-rules.softStart));
    }
    public static int rarity(Policy rules,Pity pity,int legendaryRoll,int otherRoll) {
        if(pity.epicOwed)return 2;
        if(legendaryRoll<legendaryChance(rules,pity.withoutLegendary))return 3;
        if((long)pity.withoutEpic+1>=rules.epicAt)return 2;
        if(otherRoll<rules.commonBps())return 0;
        if(otherRoll<rules.commonBps()+rules.rareBps)return 1;
        return 2;
    }
    public static boolean available(List<Candidate> all,int level,Policy rules) {
        boolean[] present=new boolean[4];for(var c:all)if(c.minimumLevel<=level)present[c.rarity]=true;
        return present[2]&&present[3]&&(rules.commonBps()==0||present[0])&&(rules.rareBps==0||present[1]);
    }
    public static Result draw(long seed,UUID owner,long sequence,int level,List<Candidate> all,String up,Policy rules,Pity pity) {
        if(sequence<0||!available(all,level,rules))throw new IllegalArgumentException("当前等级缺少可兑现的奖品或保底候选");
        var eligible=all.stream().filter(c->c.minimumLevel<=level).sorted(Comparator.comparing(Candidate::id)).toList();
        var random=new SplittableRandom(DailyShopSelector.mix(seed^DailyShopSelector.mix(owner.getMostSignificantBits())^Long.rotateLeft(DailyShopSelector.mix(owner.getLeastSignificantBits()),23)^DailyShopSelector.mix(sequence)));
        int rank=rarity(rules,pity,random.nextInt(10000),random.nextInt(Math.max(1,10000-rules.legendaryBps)));
        List<Candidate> bucket=eligible.stream().filter(c->c.rarity==rank).toList();Candidate chosen;
        if(rank==3) {
            Candidate featured=bucket.stream().filter(c->c.id.equals(up)).findFirst().orElseThrow(()->new IllegalArgumentException("当前UP不可抽取"));
            if(pity.featuredGuaranteed||bucket.size()==1||random.nextInt(10000)<rules.featuredBps)chosen=featured;
            else {var others=bucket.stream().filter(c->!c.id.equals(up)).toList();chosen=others.get(random.nextInt(others.size()));}
        }else chosen=bucket.get(random.nextInt(bucket.size()));
        int epic=rank==2?0:Math.min(rules.epicAt,pity.withoutEpic+1);
        return new Result(chosen,new Pity(rank==3?0:Math.addExact(pity.withoutLegendary,1),epic,rank==3&&epic>=rules.epicAt,
            rank==3?!chosen.id.equals(up):pity.featuredGuaranteed));
    }
    public static List<String> dailyOrder(long seed,UUID party,long epoch,List<Candidate> all,String yesterday) {
        List<String> order=new ArrayList<>(all.stream().filter(c->c.rarity==3).map(Candidate::id).sorted().toList());
        var random=new SplittableRandom(DailyShopSelector.mix(seed^party.getMostSignificantBits()^DailyShopSelector.mix(party.getLeastSignificantBits())^DailyShopSelector.mix(epoch)));
        for(int i=order.size()-1;i>0;i--)Collections.swap(order,i,random.nextInt(i+1));
        if(order.remove(yesterday))order.add(yesterday);
        return List.copyOf(order);
    }
    public static String featured(List<String> order,List<Candidate> all,int level) {
        Set<String> eligible=new HashSet<>();for(var c:all)if(c.rarity==3&&c.minimumLevel<=level)eligible.add(c.id);
        for(String id:order)if(eligible.contains(id))return id;return "";
    }
}
