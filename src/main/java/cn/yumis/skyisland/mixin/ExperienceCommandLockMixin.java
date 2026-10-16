package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.SkyServer;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.ExperienceCommand;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reject the complete /xp mutation before any target changes or success is reported. */
@Mixin(ExperienceCommand.class)
public abstract class ExperienceCommandLockMixin {
    @Inject(method={"addExperience","setExperience"},at=@At("HEAD"),cancellable=true)
    private static void locked(CommandSourceStack source,Collection<? extends ServerPlayer> targets,int amount,
                               @Coerce Object type,CallbackInfoReturnable<Integer> ci){
        var runtime=SkyServer.get(source.getServer());
        if(runtime!=null&&targets.stream().anyMatch(player->runtime.trades.blocksExperienceMutation(player))){
            source.sendFailure(Component.literal("目标玩家的背包事务正在保存，请稍后修改经验"));ci.setReturnValue(0);
        }
    }
}
