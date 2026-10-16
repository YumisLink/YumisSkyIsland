package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.SkyServer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.BiConsumer;

/** A ripe bush harvested by right click has real loot, just like a broken crop. */
@Mixin(SweetBerryBushBlock.class)
public abstract class SweetBerryHarvestMixin {
    @WrapOperation(method="useWithoutItem",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;dropFromBlockInteractLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemInstance;Lnet/minecraft/world/entity/Entity;Ljava/util/function/BiConsumer;)Z"))
    private boolean gradeRightClickHarvest(ServerLevel world, ResourceKey<LootTable> table, BlockState state, BlockEntity entity,
            ItemInstance tool, Entity actor, BiConsumer<ServerLevel, ItemStack> emitter, Operation<Boolean> original,
            BlockState originalState, Level originalWorld, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer))
            return original.call(world,table,state,entity,tool,actor,emitter);
        SkyServer runtime=SkyServer.get(world.getServer());
        if(runtime==null)return original.call(world,table,state,entity,tool,actor,emitter);
        BiConsumer<ServerLevel,ItemStack> graded=(level,stack)->{
            if(!stack.isEmpty())runtime.progression.action(serverPlayer,"harvest",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),"minecraft:sweet_berry_bush",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(serverPlayer.getMainHandItem().getItem()).toString());
            for(ItemStack part:runtime.professions.harvest(serverPlayer,cn.yumis.skyisland.server.ProfessionService.captureHarvest(state),List.of(stack)))emitter.accept(level,part);
        };
        return original.call(world,table,state,entity,tool,actor,graded);
    }
}
