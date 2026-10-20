package cn.yumis.skyisland.server;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.core.FoodHealthRules;
import cn.yumis.skyisland.data.IslandState;
import java.util.LinkedHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/** Counts foods the player actually finishes and keeps the resulting max-health bonus. */
public final class FoodHealth {
    private static final Identifier MODIFIER = YumisSkyIsland.id("food_health");
    private FoodHealth() {}

    public static void consumed(ServerPlayer player, ItemStack before, ItemStack after) {
        if (player == null || before == null || before.isEmpty() || !consumedOne(before, after)) return;
        FoodProperties food = before.get(DataComponents.FOOD);
        if (food == null) return;
        double saturation = restored(food);
        SkyServer runtime = SkyServer.get(player.level().getServer());
        if (runtime == null) return;
        String id = BuiltInRegistries.ITEM.getKey(before.getItem()).toString();
        int[] eaten = {0};
        double[] gain = {0};
        var name = new ItemStack(before.getItem()).getHoverName();
        runtime.deferred(() -> runtime.change(player, SyncImpact.person(player.getUUID(),"buffs"), state -> {
            IslandState.Person person = state.players.get(player.getUUID());
            if (person == null) return;
            ensure(person);
            int previous = person.foodEaten.getOrDefault(id, 0);
            int count = Math.addExact(previous, 1);
            eaten[0] = count;
            person.foodEaten.put(id, count);
            person.perMealFoodHealth.add(id);
            double added = FoodHealthRules.added(saturation, previous, count);
            gain[0] = added;
            if (added > 0) person.foodHealth.merge(id, added, Double::sum);
        }, () -> {
            var current = runtime.server.getPlayerList().getPlayer(player.getUUID());
            if (current == null || eaten[0] == 0) return;
            apply(current, runtime.state().players.get(player.getUUID()));
            current.sendOverlayMessage(net.minecraft.network.chat.Component.literal("已吃").append(name)
                .append(eaten[0] + "个｜本次生命上限＋" + FoodHealthRules.number(gain[0])));
        }));
    }

    /** 26.2 already stores nutrition × modifier × 2 in {@link FoodProperties#saturation()}. */
    public static double restored(FoodProperties food) {
        return food.saturation();
    }

    /** Drop test-period rewards that were multiplied by nutrition and 2 a second time. */
    public static void correctInflated(SkyServer runtime, ServerPlayer player) {
        IslandState.Person person = runtime.state().players.get(player.getUUID());
        if (!inflated(person)) return;
        runtime.change(player, SyncImpact.person(player.getUUID(),"buffs"), state -> {
            IslandState.Person current = state.players.get(player.getUUID());
            if (current == null) return;
            ensure(current);
            for (var entry : current.foodEaten.entrySet()) {
                if (current.perMealFoodHealth.contains(entry.getKey())) continue;
                double correct = reward(entry.getKey(), entry.getValue());
                if (correct < 0) continue;
                double stored = current.foodHealth.getOrDefault(entry.getKey(), 0.0);
                if (stored > correct + 0.0001) current.foodHealth.put(entry.getKey(), correct);
            }
        }, () -> apply(player, runtime.state().players.get(player.getUUID())));
    }

    public static void apply(ServerPlayer player, IslandState.Person person) {
        var instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) return;
        double amount = 0;
        if (person != null && person.foodHealth != null) {
            for (double value : person.foodHealth.values()) if (value > 0 && Double.isFinite(value)) amount += value;
        }
        AttributeModifier previous = instance.getModifier(MODIFIER);
        if (amount <= 0) { if (previous != null) instance.removeModifier(MODIFIER); return; }
        if (previous == null || previous.amount() != amount)
            instance.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    public static void ensure(IslandState.Person person) {
        if (person.foodEaten == null) person.foodEaten = new LinkedHashMap<>();
        if (person.foodHealth == null) person.foodHealth = new LinkedHashMap<>();
        if (person.perMealFoodHealth == null) person.perMealFoodHealth = new java.util.LinkedHashSet<>();
    }

    private static boolean inflated(IslandState.Person person) {
        if (person == null || person.foodEaten == null || person.foodEaten.isEmpty()) return false;
        ensure(person);
        for (var entry : person.foodEaten.entrySet()) {
            if (person.perMealFoodHealth.contains(entry.getKey())) continue;
            double correct = reward(entry.getKey(), entry.getValue());
            if (correct >= 0 && person.foodHealth.getOrDefault(entry.getKey(), 0.0) > correct + 0.0001) return true;
        }
        return false;
    }

    private static double reward(String id, int count) {
        double stacks = FoodHealthRules.stacks(count);
        if (stacks <= 0) return 0;
        Identifier key;
        try { key = Identifier.parse(id); }
        catch (RuntimeException error) { return -1; }
        if (!BuiltInRegistries.ITEM.containsKey(key)) return -1;
        FoodProperties food = new ItemStack(BuiltInRegistries.ITEM.getValue(key)).get(DataComponents.FOOD);
        if (food == null) return -1;
        return FoodHealthRules.total(restored(food), stacks);
    }

    static boolean consumedOne(ItemStack before, ItemStack after) {
        if (after == null || after.isEmpty()) return true;
        if (!after.is(before.getItem())) return true;
        return after.getCount() < before.getCount();
    }
}
