package cn.yumis.skyisland.server;

import cn.yumis.skyisland.core.ClaimGrid;
import cn.yumis.skyisland.data.IslandState;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public final class PartyService {
    private final SkyServer runtime;
    public PartyService(SkyServer runtime) { this.runtime = runtime; }
    public IslandState.Party active(UUID player) {
        IslandState.Person person = runtime.state().players.get(player);
        return person == null ? null : runtime.state().parties.get(person.activeParty);
    }
    public UUID findPlayer(String name) {
        return runtime.state().players.entrySet().stream().filter(e -> name.equalsIgnoreCase(e.getValue().name)).map(Map.Entry::getKey).findFirst().orElse(null);
    }
    public static IslandState.Person person(IslandState state, ServerPlayer player) {
        IslandState.Person person = state.players.computeIfAbsent(player.getUUID(), _ -> new IslandState.Person());
        person.name = player.getGameProfile().name(); return person;
    }
    public static IslandState.Party newParty(IslandState state, ServerPlayer owner) {
        if (state.nextSlot >= ClaimGrid.CAPACITY) throw new IllegalStateException("空岛区域已分配完");
        IslandState.Party party = new IslandState.Party(); party.id = UUID.randomUUID();
        party.slot = state.nextSlot++; party.owner = owner.getUUID(); party.name = owner.getGameProfile().name() + "的空岛";
        party.members.add(owner.getUUID());
        ClaimGrid.Region region = ClaimGrid.region(party.slot);
        party.homeX = region.centerX(); party.homeZ = region.centerZ();
        state.parties.put(party.id, party);
        IslandState.Person p = person(state, owner); p.activeParty = party.id; p.soloParty = party.id;
        return party;
    }
    public void home(ServerPlayer player) {
        IslandState.Party existing = active(player.getUUID());
        if (existing != null) { runtime.worlds.home(player, existing); return; }
        runtime.change(player, state -> {
            IslandState.Person p = person(state, player);
            IslandState.Party old = state.parties.get(p.soloParty);
            if (old != null && old.owner.equals(player.getUUID())) { p.activeParty = old.id; old.members.add(player.getUUID()); }
            else newParty(state, player);
        }, () -> runtime.worlds.home(player, active(player.getUUID())));
    }
    public void invite(ServerPlayer owner, ServerPlayer target) {
        IslandState.Party party = requireOwner(owner);
        if (party.members.contains(target.getUUID())) throw new IllegalArgumentException("玩家已经在本队");
        runtime.change(owner, s -> {
            IslandState.Person p = person(s, target); p.inviteParty = party.id; p.invitationExpiresAt = s.effectiveTicks + 6000;
        }, () -> { runtime.tell(owner, "已邀请 " + target.getGameProfile().name()); runtime.tell(target, owner.getGameProfile().name() + " 邀请你加入空岛，5分钟内输入 /is accept 或 /is reject"); });
    }
    public void accept(ServerPlayer player) {
        IslandState.Person person = runtime.state().players.get(player.getUUID());
        IslandState.Party target = person == null ? null : runtime.state().parties.get(person.inviteParty);
        if (target == null || person.invitationExpiresAt < runtime.state().effectiveTicks) throw new IllegalArgumentException("没有有效邀请");
        IslandState.Party old = active(player.getUUID());
        if (old != null && old.owner.equals(player.getUUID()) && old.members.size() > 1)
            throw new IllegalArgumentException("你是岛主，请先 /is transfer <队员> 手动移交再加入其他队伍");
        runtime.change(player, s -> {
            if (old != null) s.parties.get(old.id).members.remove(player.getUUID());
            IslandState.Person p = person(s, player); p.activeParty = target.id; p.inviteParty = null;
            s.parties.get(target.id).members.add(player.getUUID());
        }, () -> { runtime.tell(player, "已加入空岛；原个人岛与钱包保留休眠"); runtime.worlds.home(player, active(player.getUUID())); });
    }
    public void leave(ServerPlayer player) {
        IslandState.Party party = requireMember(player);
        if (party.owner.equals(player.getUUID())) {
            if (party.members.size() > 1) throw new IllegalArgumentException("岛主必须先 /is transfer <队员> 手动移交，才能离队");
            throw new IllegalArgumentException("你是唯一成员，无其他队伍可离开；可接受其他队伍邀请或重开本岛");
        }
        runtime.change(player, s -> detach(s, player, party.id), () -> runtime.worlds.home(player, active(player.getUUID())));
    }
    private static void detach(IslandState s, ServerPlayer player, UUID fromId) {
        IslandState.Party old = s.parties.get(fromId); old.members.remove(player.getUUID());
        IslandState.Person person = person(s, player); IslandState.Party solo = s.parties.get(person.soloParty);
        if (solo == null || !solo.owner.equals(player.getUUID()) || solo.id.equals(fromId)) {
            solo = newParty(s, player);
        } else { solo.members.add(player.getUUID()); person.activeParty = solo.id; }
        // A departure does not mint new full-price resource quotas.
        if (solo.marketEpoch == old.marketEpoch) {
            IslandState.Party restored = solo;
            old.quota.forEach((k, v) -> {
                var previous = cn.yumis.skyisland.core.Rational.parse(restored.quota.getOrDefault(k, "0"));
                if (cn.yumis.skyisland.core.Rational.parse(v).compareTo(previous) > 0) restored.quota.put(k, v);
            });
        } else { solo.marketEpoch = old.marketEpoch; solo.quota = new LinkedHashMap<>(old.quota); solo.dailyStock.clear(); }
    }
    public void transfer(ServerPlayer owner, UUID successor) {
        IslandState.Party party = requireOwner(owner);
        if (!party.members.contains(successor) || successor.equals(owner.getUUID())) throw new IllegalArgumentException("必须指定另一位现有队员");
        runtime.change(owner, s -> s.parties.get(party.id).owner = successor, () -> runtime.tell(owner, "岛主身份已手动移交"));
    }
    public void trust(ServerPlayer owner, UUID target, boolean enabled) {
        IslandState.Party party = requireOwner(owner);
        runtime.change(owner, s -> { if (enabled) s.parties.get(party.id).trusted.add(target); else s.parties.get(party.id).trusted.remove(target); }, () -> runtime.tell(owner, enabled ? "已授权建造及容器交互" : "已撤销授权"));
    }
    public void reject(ServerPlayer player) {
        runtime.change(player, s -> person(s, player).inviteParty = null, () -> runtime.tell(player, "已拒绝邀请"));
    }
    public void kick(ServerPlayer owner, ServerPlayer target) {
        IslandState.Party party = requireOwner(owner);
        if (owner.getUUID().equals(target.getUUID()) || !party.members.contains(target.getUUID())) throw new IllegalArgumentException("必须指定其他队员");
        runtime.change(owner, s -> detach(s, target, party.id), () -> { runtime.tell(target, "你已被移出队伍，原队伍资产保留"); runtime.worlds.home(target, active(target.getUUID())); });
    }
    public IslandState.Party requireMember(ServerPlayer player) {
        IslandState.Party p = active(player.getUUID()); if (p == null) throw new IllegalArgumentException("先输入 /is 创建或加入空岛"); return p;
    }
    public IslandState.Party requireOwner(ServerPlayer player) {
        IslandState.Party p = requireMember(player); if (!p.owner.equals(player.getUUID())) throw new IllegalArgumentException("仅岛主可以执行此操作"); return p;
    }
}
