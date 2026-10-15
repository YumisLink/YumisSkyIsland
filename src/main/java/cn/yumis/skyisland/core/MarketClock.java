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
}
