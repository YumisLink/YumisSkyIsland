package cn.yumis.skyisland.client.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Only the locally inspected XPlus version receives its optional compatibility injections. */
public final class OptionalFishingMixinPlugin implements IMixinConfigPlugin {
    private static final String SUPPORTED="1.5.1-fabric-mc26.2";
    private boolean compatible;

    @Override public void onLoad(String mixinPackage){
        var mod=FabricLoader.getInstance().getModContainer("autofish");
        compatible=mod.map(value->value.getMetadata().getVersion().getFriendlyString().equals(SUPPORTED)).orElse(false);
        if(mod.isPresent()&&!compatible)LoggerFactory.getLogger("YumisSkyIsland").info("XPlus Autofish {} 未经兼容验收，跳过特殊介质钓鱼兼容；已支持 {}",mod.get().getMetadata().getVersion().getFriendlyString(),SUPPORTED);
    }
    @Override public boolean shouldApplyMixin(String targetClassName,String mixinClassName){return !mixinClassName.endsWith(".AutoFishCompatMixin")||compatible;}
    @Override public String getRefMapperConfig(){return null;}
    @Override public void acceptTargets(Set<String> myTargets,Set<String> otherTargets){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String targetClassName,ClassNode targetClass,String mixinClassName,IMixinInfo mixinInfo){}
    @Override public void postApply(String targetClassName,ClassNode targetClass,String mixinClassName,IMixinInfo mixinInfo){}
}
