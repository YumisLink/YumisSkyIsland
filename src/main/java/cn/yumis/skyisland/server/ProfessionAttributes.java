package cn.yumis.skyisland.server;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.attribute.ModAttributes;
import cn.yumis.skyisland.data.IslandState;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Level attributes that sit beside profession fruits. */
public final class ProfessionAttributes {
    private ProfessionAttributes() {}

    public static void apply(SkyServer runtime, ServerPlayer player, IslandState.Person person) {
        int farm = person == null ? 0 : Math.min(1000, runtime.professions.level(person, "farmer"));
        int mine = person == null ? 0 : Math.min(1000, runtime.professions.level(person, "miner"));
        set(player, ModAttributes.FARM_DEGREE, "profession_farm", farm, AttributeModifier.Operation.ADD_VALUE);
        set(player, ModAttributes.MINING_POWER, "profession_mining_power", mine, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.BLOCK_BREAK_SPEED, "profession_mining", mine * 0.001, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static void set(ServerPlayer player, Holder<Attribute> attribute, String name, double amount, AttributeModifier.Operation operation) {
        var instance = player.getAttribute(attribute);
        if (instance == null) return;
        Identifier id = YumisSkyIsland.id(name);
        AttributeModifier previous = instance.getModifier(id);
        if (amount == 0) { if (previous != null) instance.removeModifier(id); return; }
        if (previous == null || previous.amount() != amount || previous.operation() != operation)
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }
}
