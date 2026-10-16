package cn.yumis.skyisland.server;

import cn.yumis.skyisland.core.MarketClock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.clock.WorldClocks;

/** Explicit administrator clock jumps; bounty effectiveTicks are never advanced here. */
public final class AdminTimeService {
    private AdminTimeService() {}
    static void setClock(SkyServer runtime,long ticks) {
        var clock=runtime.server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
        runtime.server.overworld().clockManager().setTotalTicks(clock,ticks);
    }
    public static void nextDay(SkyServer runtime) {
        if(!runtime.store.available()||runtime.closing)throw new IllegalStateException("数据正在保存，请稍后快进");
        long anchor=runtime.config.object("shop/shops.toml").getAsJsonObject("market").get("refreshAtDayTick").getAsLong();
        setClock(runtime,MarketClock.nextRefresh(runtime.server.overworld().getOverworldClockTime(),anchor));
    }
}
