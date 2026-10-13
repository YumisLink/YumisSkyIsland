package cn.yumis.skyisland.mixin;
import net.minecraft.world.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(CompoundContainer.class)
public interface CompoundContainerAccessor {
    @Accessor("container1") Container yumis$first();
    @Accessor("container2") Container yumis$second();
}
