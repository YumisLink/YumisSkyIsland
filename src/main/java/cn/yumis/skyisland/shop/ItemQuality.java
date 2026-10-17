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
    private static final Identifier ARMOR = Identifier.fromNamespaceAndPath("yumis_sky_island","quality_armor");
    private ItemQuality() {}
    public static void apply(ItemStack stack, JsonObject rules, int grade, String profession) {
        apply(stack,rules,grade,profession,false);
    }
    public static void apply(ItemStack stack, JsonObject rules, int grade, String profession, boolean unbreakable) {
        if (grade < 0 || grade >= rules.getAsJsonArray("grades").size()) throw new IllegalArgumentException("品质超出档位范围");
        CompoundTag tags = stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        int previousGrade=tags.getIntOr("yumis_quality",0);
        tags.remove("yumis_quality"); tags.remove("yumis_profession");
        if (grade > 0) {
            tags.putInt("yumis_quality",grade);
            if (profession != null && !profession.isBlank()) tags.putString("yumis_profession",profession);
        }
        if (tags.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA); else stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tags));
        // Stackable goods keep only the quality tags. Copying attribute modifiers makes otherwise identical rolls refuse to stack.
        if (tagsOnly(stack)) return;
        Integer baseDurability = stack.getItem().components().get(DataComponents.MAX_DAMAGE);
        if (baseDurability != null) stack.set(DataComponents.MAX_DAMAGE,baseDurability);
        ItemAttributeModifiers current = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (current != null) {
            var builder = ItemAttributeModifiers.builder();
            for (var entry : current.modifiers()) if (!entry.modifier().id().equals(DAMAGE) && !entry.modifier().id().equals(ARMOR))
                builder.add(entry.attribute(),entry.modifier(),entry.slot(),entry.display());
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS,builder.build());
        }
        if(previousGrade>0)stack.remove(DataComponents.UNBREAKABLE);
        if (grade == 0) return;
        JsonObject attributes = rules.getAsJsonArray("grades").get(grade).getAsJsonObject();
        if (baseDurability != null) {
            long maximum = (long)baseDurability * attributes.get("durabilityBps").getAsInt() / 10000;
            if (maximum < 1 || maximum > Integer.MAX_VALUE) throw new IllegalArgumentException("品质耐久超出范围");
            stack.set(DataComponents.MAX_DAMAGE,(int)maximum);
        }
        if (unbreakable && baseDurability != null) stack.set(DataComponents.UNBREAKABLE,net.minecraft.util.Unit.INSTANCE);
        double bonus = attributes.get("attributeBonusBps").getAsInt() / 10000.0;
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        boolean melee = item.endsWith("_sword") || item.endsWith("_axe") || item.endsWith("_spear") || stack.is(Items.MACE) || stack.is(Items.TRIDENT);
        if (current != null && bonus > 0) {
            var builder = ItemAttributeModifiers.builder();
            for (var entry : stack.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers()) builder.add(entry.attribute(),entry.modifier(),entry.slot(),entry.display());
            for (var entry : current.modifiers()) {
                if(entry.modifier().id().equals(DAMAGE)||entry.modifier().id().equals(ARMOR))continue;
                if (entry.attribute().equals(Attributes.ARMOR) && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE)
                    builder.add(Attributes.ARMOR,new AttributeModifier(ARMOR,entry.modifier().amount()*bonus,AttributeModifier.Operation.ADD_VALUE),entry.slot());
                if (melee && entry.attribute().equals(Attributes.ATTACK_DAMAGE) && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE)
                    builder.add(Attributes.ATTACK_DAMAGE,new AttributeModifier(DAMAGE,entry.modifier().amount()*bonus,AttributeModifier.Operation.ADD_VALUE),entry.slot());
            }
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS,builder.build());
        }
    }
    /** No durability and no attack or armor bonus: quality must not add components that differ between rolls. */
    private static boolean tagsOnly(ItemStack stack) {
        if (stack.getItem().components().get(DataComponents.MAX_DAMAGE) != null) return false;
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return true;
        for (var entry : modifiers.modifiers()) {
            if (entry.attribute().equals(Attributes.ARMOR) || entry.attribute().equals(Attributes.ATTACK_DAMAGE)) return false;
        }
        return true;
    }
}
