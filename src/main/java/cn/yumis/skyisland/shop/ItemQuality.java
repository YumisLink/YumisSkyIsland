package cn.yumis.skyisland.shop;

import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;

/** Idempotent quality application shared by manufacturing and configured task rewards. */
public final class ItemQuality {
    private static final Identifier DAMAGE = Identifier.fromNamespaceAndPath("yumis_sky_island","quality_damage");
    private ItemQuality() {}
    public static void apply(ItemStack stack, JsonObject rules, int grade, String profession) {
        if (grade < 0 || grade >= rules.getAsJsonArray("grades").size()) throw new IllegalArgumentException("品质须为0～4");
        CompoundTag tags = stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        tags.remove("yumis_quality"); tags.remove("yumis_profession");
        if (grade > 0) {
            tags.putInt("yumis_quality",grade);
            if (profession != null && !profession.isBlank()) tags.putString("yumis_profession",profession);
        }
        if (tags.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA); else stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tags));
        Integer baseDurability = stack.getItem().components().get(DataComponents.MAX_DAMAGE);
        if (baseDurability != null) stack.set(DataComponents.MAX_DAMAGE,baseDurability);
        ItemAttributeModifiers current = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (current != null) {
            var builder = ItemAttributeModifiers.builder();
            for (var entry : current.modifiers()) if (!entry.modifier().id().equals(DAMAGE))
                builder.add(entry.attribute(),entry.modifier(),entry.slot(),entry.display());
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS,builder.build());
        }
        if (grade == 0) return;
        JsonObject attributes = rules.getAsJsonArray("grades").get(grade).getAsJsonObject();
        if (baseDurability != null) {
            long maximum = (long)baseDurability * attributes.get("durabilityBps").getAsInt() / 10000;
            if (maximum < 1 || maximum > Integer.MAX_VALUE) throw new IllegalArgumentException("品质耐久超出范围");
            stack.set(DataComponents.MAX_DAMAGE,(int)maximum);
        }
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (current != null && (item.endsWith("_sword") || item.endsWith("_axe") || item.endsWith("_spear") || stack.is(Items.MACE))) {
            var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS,modifiers.withModifierAdded(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(DAMAGE,Math.clamp(attributes.get("attackAndMiningBonusBps").getAsInt(),0,1000)/10000.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE),EquipmentSlotGroup.MAINHAND));
        }
    }
}
