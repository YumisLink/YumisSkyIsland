package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(Block.class)
public abstract class HarvestQuestMixin {
    @Redirect(method="dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;"))
    private static List<ItemStack> harvest(BlockState state,ServerLevel world,BlockPos pos,BlockEntity entity,Entity breaker,ItemInstance tool){
        List<ItemStack> drops=Block.getDrops(state,world,pos,entity,breaker,tool);
        if(world.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.BLOCK_DROPS)&&breaker instanceof ServerPlayer player){var rt=SkyServer.get(world.getServer());if(rt!=null){
            String block=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),toolId=BuiltInRegistries.ITEM.getKey(tool.typeHolder().value()).toString();
            for(var stack:drops)if(!stack.isEmpty())rt.progression.action(player,"harvest",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),block,toolId);
            return rt.professions.harvest(player,cn.yumis.skyisland.server.ProfessionService.captureHarvest(state),drops);
        }}return drops;
    }
}
