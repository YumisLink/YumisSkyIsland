package cn.yumis.skyisland.core;

/** Game-day boundary for an absolute overworld clock; 22000 ticks is 04:00. */
public final class MarketClock {
    private MarketClock() {}
    public static long day(long clockTime,long refreshAtDayTick) {
        if(refreshAtDayTick<0||refreshAtDayTick>=24000)throw new IllegalArgumentException("每日刷新刻数超出范围");
        return Math.floorDiv(Math.addExact(clockTime,24000-refreshAtDayTick),24000);
    }
    public static boolean crossed(long lastRefreshedDay,long clockTime,long refreshAtDayTick) {
        return day(clockTime,refreshAtDayTick)>lastRefreshedDay;
    }
    public static long nextRefresh(long clockTime,long refreshAtDayTick) {
        if(refreshAtDayTick<0||refreshAtDayTick>=24000)throw new IllegalArgumentException("每日刷新刻数超出范围");
        long left=Math.floorMod(refreshAtDayTick-Math.floorMod(clockTime,24000),24000);
        return Math.addExact(clockTime,left==0?24000:left);
    }
    public static long tonight(long clockTime) {
        long time=Math.floorMod(clockTime,24000);
        return time>=13000?clockTime:Math.addExact(clockTime,13000-time);
    }
}
