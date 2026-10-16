package cn.yumis.skyisland.core;

public final class DeathRules {
    private DeathRules() {}
    public static long cost(long previousDeaths,long firstCost,long additionalCost) {
        if(previousDeaths<0||firstCost<0||additionalCost<0)throw new IllegalArgumentException("死亡费用参数不能为负");
        long death=Math.addExact(previousDeaths,1);
        return Math.addExact(Math.multiplyExact(firstCost,death),Math.multiplyExact(additionalCost,Math.multiplyExact(previousDeaths,death)/2));
    }
}
