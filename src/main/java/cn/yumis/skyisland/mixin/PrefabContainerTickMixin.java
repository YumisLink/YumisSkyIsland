package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.prefab.PrefabService;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A source furnace/other ticking inventory must not change a reserved slot during WAL IO. */
@Mixin(targets="net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class PrefabContainerTickMixin {
    @Shadow @Final private BlockEntity blockEntity;
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void reserve(CallbackInfo ci){if(cn.yumis.skyisland.server.Protection.locked(blockEntity.getLevel(),blockEntity.getBlockPos()))ci.cancel();}
}
