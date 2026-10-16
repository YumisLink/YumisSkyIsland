package cn.yumis.skyisland.attribute;

import cn.yumis.skyisland.YumisSkyIsland;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

public final class ModAttributes {
    public static final double MAX_FISHING_POWER = 1000;
    public static final Holder<Attribute> FISHING_POWER = Registry.registerForHolder(
            BuiltInRegistries.ATTRIBUTE, YumisSkyIsland.id("fishing_power"),
            new RangedAttribute("attribute.name.yumis_sky_island.fishing_power", 0, 0, MAX_FISHING_POWER).setSyncable(true));

    private ModAttributes() {}

    public static void register() {
        FabricDefaultAttributeRegistry.MODIFY.register(context ->
                context.modify(EntityTypes.PLAYER, (type, builder) -> builder.add(FISHING_POWER)));
    }
}
