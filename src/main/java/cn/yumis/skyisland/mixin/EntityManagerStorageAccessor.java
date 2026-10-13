package cn.yumis.skyisland.mixin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(PersistentEntitySectionManager.class)
public interface EntityManagerStorageAccessor {
    @Accessor("permanentStorage") EntityPersistentStorage<Entity> yumis$storage();
    @Accessor("sectionStorage") EntitySectionStorage<Entity> yumis$sections();
    @Invoker("ensureChunkQueuedForLoad") void yumis$load(long chunk);
}
