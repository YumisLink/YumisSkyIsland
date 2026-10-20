package cn.yumis.skyisland.server;
import cn.yumis.skyisland.core.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.BiConsumer;
import static net.minecraft.commands.Commands.*;
public final class SkyCommands {
    private static int run(CommandContext<CommandSourceStack> c,BiConsumer<SkyServer,ServerPlayer> task) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException(); SkyServer rt = SkyServer.get(c.getSource().getServer());
        if (rt == null) { SkyServer.tell(player,"需要使用 Yumi 空岛世界预设新建存档"); return 0; }
        try { task.accept(rt,player); return 1; } catch (IllegalArgumentException | IllegalStateException | ArithmeticException e) { SkyServer.tell(player,e.getMessage()); return 0; }
    }
    private static UUID target(SkyServer rt,String name) { UUID id = rt.parties.findPlayer(name); if (id == null) throw new IllegalArgumentException("没有找到已进入过服务器的玩家 " + name); return id; }
    private static int admin(CommandContext<CommandSourceStack> c,java.util.function.BiConsumer<SkyServer,CommandSourceStack> action) {
        var source=c.getSource();var runtime=SkyServer.get(source.getServer());
        if(runtime==null){source.sendFailure(Component.literal("当前不是 Yumi 空岛世界"));return 0;}
        try{action.accept(runtime,source);return 1;}
        catch(IllegalArgumentException|IllegalStateException|ArithmeticException error){source.sendFailure(Component.literal(error.getMessage()));return 0;}
    }
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var island = literal("is").executes(c -> run(c,(rt,p) -> rt.parties.home(p)))
            .then(literal("create").executes(c -> run(c,(rt,p) -> rt.parties.home(p))))
            .then(literal("home").executes(c -> run(c,(rt,p) -> rt.parties.home(p))))
            .then(literal("sethome").executes(c -> run(c,(rt,p) -> rt.worlds.setHome(p))))
            .then(literal("spawn").executes(c -> run(c,(rt,p) -> rt.worlds.lobby(p))))
            .then(literal("invite").then(argument("player",EntityArgument.player()).executes(c -> { var target = EntityArgument.getPlayer(c,"player"); return run(c,(rt,p) -> rt.parties.invite(p,target)); })))
            .then(literal("accept").executes(c -> run(c,(rt,p) -> rt.parties.accept(p))))
            .then(literal("reject").executes(c -> run(c,(rt,p) -> rt.parties.reject(p))))
            .then(literal("leave").executes(c -> run(c,(rt,p) -> rt.parties.leave(p))))
            .then(literal("transfer").then(argument("player",StringArgumentType.word()).executes(c -> run(c,(rt,p) -> rt.parties.transfer(p,target(rt,StringArgumentType.getString(c,"player")))))))
            .then(literal("kick").then(argument("player",EntityArgument.player()).executes(c -> { var target = EntityArgument.getPlayer(c,"player"); return run(c,(rt,p) -> rt.parties.kick(p,target)); })))
            .then(literal("trust").then(argument("player",StringArgumentType.word()).executes(c -> run(c,(rt,p) -> rt.parties.trust(p,target(rt,StringArgumentType.getString(c,"player")),true)))))
            .then(literal("untrust").then(argument("player",StringArgumentType.word()).executes(c -> run(c,(rt,p) -> rt.parties.trust(p,target(rt,StringArgumentType.getString(c,"player")),false)))))
            .then(literal("visit").then(argument("player",StringArgumentType.word()).executes(c -> run(c,(rt,p) -> { var party = rt.parties.active(target(rt,StringArgumentType.getString(c,"player"))); if (party == null) throw new IllegalArgumentException("对方没有有效空岛"); rt.worlds.home(p,party); }))))
            .then(literal("team").executes(c -> run(c,(rt,p) -> { var party = rt.parties.requireMember(p); for (UUID id : party.members) { var person = rt.state().players.get(id); SkyServer.tell(p,(id.equals(party.owner)?"岛主：":"队员：") + (person == null ? id : person.name)); } })))
            .then(literal("info").executes(c -> run(c,SkyCommands::info)).then(argument("player",StringArgumentType.word()).executes(c -> run(c,(rt,p) -> { var party = rt.parties.active(target(rt,StringArgumentType.getString(c,"player"))); if (party == null) throw new IllegalArgumentException("对方没有岛"); infoParty(rt,p,party); }))))
            .then(literal("level").executes(c -> run(c,SkyCommands::info)))
            .then(literal("top").executes(c -> run(c,(rt,p) -> rt.state().parties.values().stream().filter(x -> !x.members.isEmpty()).sorted(Comparator.<cn.yumis.skyisland.data.IslandState.Party>comparingInt(rt::level).reversed().thenComparing(rt::points,Comparator.reverseOrder())).limit(10).forEach(x -> SkyServer.tell(p,x.name + "：" + rt.level(x) + "级")))))
            .then(literal("profession").executes(c -> run(c,(rt,p) -> { for (String profession : QuestCatalog.PROFESSIONS) SkyServer.tell(p,ProfessionService.name(profession) + " " + rt.professions.level(rt.state().players.get(p.getUUID()),profession) + "级"); })))
            .then(literal("reset").executes(c -> run(c,(rt,p) -> rt.resets.propose(p))).then(literal("confirm").then(argument("code",StringArgumentType.word()).executes(c -> run(c,(rt,p) -> rt.resets.confirm(p,StringArgumentType.getString(c,"code")))))))
            .then(literal("help").executes(c -> run(c,(rt,p) -> SkyServer.tell(p,"/is home、invite、accept、leave、transfer、team、trust、untrust、visit、sethome、spawn、level、top、profession、reset；/refresh；/shop（I）；/sell hand|inventory；/balance"))));
        island.then(literal("admin").requires(hasPermission(LEVEL_GAMEMASTERS)).then(literal("coins").then(argument("player",EntityArgument.player()).then(argument("amount",LongArgumentType.longArg(0)).executes(c -> {
            ServerPlayer target = EntityArgument.getPlayer(c,"player"); long amount = LongArgumentType.getLong(c,"amount");
            var rt = SkyServer.get(c.getSource().getServer());
            if (rt == null) { c.getSource().sendFailure(Component.literal("当前不是 Yumi 空岛世界")); return 0; }
            try {
                var party = rt.parties.requireMember(target);
                rt.change(c.getSource().getPlayer(), SyncImpact.none(),s -> s.parties.get(party.id).coins = amount,() -> c.getSource().sendSuccess(() -> Component.literal("共享"+Money.currencyName()+"已设置为 " + Money.format(amount)),true));
                return 1;
            } catch (IllegalArgumentException e) { c.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
        })))));
        // Brigadier's unquoted word argument excludes Chinese; literal aliases accept both forms and complete natively.
        var professionPlayer = argument("player", EntityArgument.player());
        for (String profession : ProfessionService.ALL_PROFESSIONS) {
            for (String alias : List.of(profession, ProfessionService.name(profession))) {
                professionPlayer.then(literal(alias).then(argument("amount", StringArgumentType.word()).executes(c -> {
                    ServerPlayer recipient = EntityArgument.getPlayer(c, "player");
                    return admin(c, (rt, source) -> rt.professions.grantExperience(source, recipient, profession,
                        ProfessionService.positiveExperience(StringArgumentType.getString(c, "amount"))));
                })));
            }
        }
        island.then(literal("admin").requires(hasPermission(LEVEL_GAMEMASTERS))
            .then(literal("profession").then(literal("xp").then(literal("add").then(professionPlayer)))));
        island.then(literal("admin").requires(hasPermission(LEVEL_GAMEMASTERS))
            .then(literal("edit")
                .then(literal("on").executes(c -> run(c,(rt,p) -> rt.questEditor.setMode(p,true))))
                .then(literal("off").executes(c -> run(c,(rt,p) -> rt.questEditor.setMode(p,false))))));
        island.then(literal("admin").requires(hasPermission(LEVEL_GAMEMASTERS))
            .then(literal("accessory").then(literal("draws").then(argument("player",EntityArgument.player()).then(argument("count",IntegerArgumentType.integer(1)).executes(c->{
                ServerPlayer recipient=EntityArgument.getPlayer(c,"player");int count=IntegerArgumentType.getInteger(c,"count");
                return admin(c,(rt,source)->{
                    if(!rt.tryChange(source.getPlayer(), SyncImpact.person(recipient.getUUID(),"lottery"),state->{
                        var person=state.players.computeIfAbsent(recipient.getUUID(),id->new cn.yumis.skyisland.data.IslandState.Person());
                        person.name=recipient.getGameProfile().name();person.pendingAccessoryDraws=Math.addExact(person.pendingAccessoryDraws,count);
                    },()->source.sendSuccess(()->Component.literal("已为 "+recipient.getGameProfile().name()+" 增加 "+count+" 次饰品抽奖"),true),error->source.sendFailure(Component.literal("保存失败，次数未开放使用"))))
                        throw new IllegalStateException("数据正在保存，请稍后重试");
                });
            })))))
            .then(literal("nextday").executes(c->admin(c,(rt,source)->{AdminTimeService.nextDay(rt);source.sendSuccess(()->Component.literal("已快进到下一次每日刷新时刻；悬赏剩余时间保持"),true);})))
            .then(literal("bloodmoon").then(literal("tonight").executes(c->admin(c,(rt,source)->rt.bloodMoon.forceTonight(source.getPlayer(),()->source.sendSuccess(()->Component.literal("今晚血月已开启"),true)))))));
        island.then(literal("spawnball")
            .executes(c -> run(c, (rt,p) -> rt.spawnBalls.info(p)))
            .then(literal("info").executes(c -> run(c, (rt,p) -> rt.spawnBalls.info(p))))
            .then(literal("clear").executes(c -> run(c, (rt,p) -> rt.spawnBalls.clear(p)))));
        d.register(island);
        d.register(literal("refresh").executes(c -> run(c,(rt,p) -> rt.refresh.request(p))));
        d.register(literal("shop").executes(c -> run(c,(rt,p) -> rt.openShop(p))));
        d.register(literal("sell").executes(c -> run(c,(rt,p) -> rt.trades.sell(p,true))).then(literal("hand").executes(c -> run(c,(rt,p) -> rt.trades.sell(p,true)))).then(literal("inventory").executes(c -> run(c,(rt,p) -> rt.trades.sell(p,false)))));
        d.register(literal("balance").executes(c -> run(c,(rt,p) -> SkyServer.tell(p,Money.format(rt.parties.requireMember(p).coins)))));
    }
    private static void info(SkyServer rt,ServerPlayer player) { infoParty(rt,player,rt.parties.requireMember(player)); }
    private static void infoParty(SkyServer rt,ServerPlayer player,cn.yumis.skyisland.data.IslandState.Party party) {
        var region = ClaimGrid.region(party.slot); SkyServer.tell(player,party.name + " · " + rt.level(party) + "级 · " + new java.math.BigDecimal(rt.points(party),4).stripTrailingZeros().toPlainString() + "点 · " + Money.format(party.coins));
        SkyServer.tell(player,"范围 [" + region.minX() + "," + region.maxX() + ") × [" + region.minZ() + "," + region.maxZ() + ")，" + party.members.size() + "位成员");
        if (party.refresh != null) SkyServer.tell(player,(rt.refresh.isPaused(party.id)?"刷新已暂停，/refresh 继续；":"刷新进度 ") + party.refresh.cursor + "/" + party.refresh.chunks.size());
        if (party.resetInProgress) SkyServer.tell(player,"重开进度 " + party.resetCursor + "/" + (party.resetChunks == null ? "读取索引" : party.resetChunks.size()));
    }
}
