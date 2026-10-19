package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.HammerBreaks;
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
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Block.class)
public abstract class HarvestQuestMixin {
    @WrapMethod(method="dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V")
    private static void scopedFeet(BlockState state,net.minecraft.world.level.Level level,BlockPos pos,BlockEntity entity,Entity breaker,ItemStack tool,Operation<Void> original){
        ServerPlayer previous=HammerBreaks.feet();
        HammerBreaks.feet(null);
        try{original.call(state,level,pos,entity,breaker,tool);}finally{HammerBreaks.feet(previous);}
    }
    @Redirect(method="dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;"))
    private static List<ItemStack> harvest(BlockState state,ServerLevel world,BlockPos pos,BlockEntity entity,Entity breaker,ItemInstance tool){
        List<ItemStack> drops=Block.getDrops(state,world,pos,entity,breaker,tool);
        HammerBreaks.feet(null);
        if(world.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.BLOCK_DROPS)&&breaker instanceof ServerPlayer player){var rt=SkyServer.get(world.getServer());if(rt!=null){
            List<ItemStack> hammer=rt.professions.hammerDrops(player,state,tool.typeHolder().value());
            if(hammer!=null)drops=hammer;
            if(rt.veinMining.held(player))HammerBreaks.feet(player);
            String block=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),toolId=BuiltInRegistries.ITEM.getKey(tool.typeHolder().value()).toString();
            for(var stack:drops)if(!stack.isEmpty())rt.progression.action(player,"harvest",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),block,toolId);
            return rt.professions.harvest(player,cn.yumis.skyisland.server.ProfessionService.captureHarvest(state),drops);
        }}return drops;
    }
    @Inject(method="popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",at=@At("HEAD"),cancellable=true)
    private static void atFeet(net.minecraft.world.level.Level level,BlockPos pos,ItemStack stack,CallbackInfo ci){
        ServerPlayer player=HammerBreaks.feet();if(player==null)return;
        HammerBreaks.feet(null);
        try{Block.popResource(level,player.blockPosition(),stack);}finally{HammerBreaks.feet(player);}
        ci.cancel();
    }
}
