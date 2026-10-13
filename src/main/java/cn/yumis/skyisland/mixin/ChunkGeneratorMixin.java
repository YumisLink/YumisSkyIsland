package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.world.VoidMarker;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(method={"applyBiomeDecoration", "createStructures", "createReferences"}, at=@At("HEAD"), cancellable=true)
    private void empty(CallbackInfo ci) { if ((Object) this instanceof VoidMarker m && m.yumis$isVoid()) ci.cancel(); }
}
