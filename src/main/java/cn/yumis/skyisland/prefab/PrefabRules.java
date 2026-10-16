package cn.yumis.skyisland.prefab;

import cn.yumis.skyisland.config.GameConfig;
import com.google.gson.JsonObject;

public record PrefabRules(int interval,int ammunitionCredits,long ammunitionPrice,int maximumBlocks,long maximumVolume,
                          int previewBatch,int validationBudget,String directory,boolean requireVerified) {
    public static PrefabRules read(GameConfig config) {
        JsonObject o=config.object("gameplay/prefabs.toml");
        int interval=o.get("placementIntervalTicks").getAsInt(),credits=o.get("ammunitionCredits").getAsInt();
        long price=o.get("ammunitionPriceCoins").getAsLong(),volume=o.get("maximumVolume").getAsLong();
        int blocks=o.get("maximumBlocks").getAsInt(),batch=o.get("previewBatchSize").getAsInt(),budget=o.get("validationBudget").getAsInt();
        if(interval<1||interval>100||credits<1||credits>1000000||price<1||blocks<1||blocks>1000000||volume<1||volume>67108864
            ||batch<1||batch>2048||budget<1||budget>16384)throw new IllegalArgumentException("蓝图大炮配置无效：间隔须1～100，容量和预算须在注释范围内");
        String directory=o.get("libraryDirectory").getAsString();
        if(directory.isBlank()||java.nio.file.Path.of(directory).isAbsolute()||java.nio.file.Path.of(directory).normalize().startsWith(".."))
            throw new IllegalArgumentException("建筑库必须使用实例内的相对目录");
        return new PrefabRules(interval,credits,price,blocks,volume,batch,budget,directory,o.get("requireVerifiedTemplates").getAsBoolean());
    }
}
