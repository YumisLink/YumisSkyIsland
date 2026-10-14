package cn.yumis.skyisland.shop;

import cn.yumis.skyisland.core.MaterialAllocation;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Shared client preview/server allocation; task conditions are independent of sale products. */
public final class TaskMaterialPlanner {
    public record Requirement(ItemStack template, JsonObject components, int quantity, Integer minimumQuality) {}
    private TaskMaterialPlanner() {}
    public static boolean matches(ItemStack actual, Requirement requirement) {
        if (!ItemEconomy.safeToSell(actual) || !ItemStack.isSameItem(actual,requirement.template)) return false;
        if (requirement.minimumQuality != null && ItemEconomy.grade(actual) < requirement.minimumQuality) return false;
        for (String key : requirement.components.keySet()) {
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(key));
            if (type == null) return false;
            Object expected = requirement.template.get(type), value = actual.get(type);
            if (expected instanceof ItemEnchantments required) {
                if (required.isEmpty()) continue;
                if (!(value instanceof ItemEnchantments current)) return false;
                for (var enchantment : required.entrySet()) if (current.getLevel(enchantment.getKey()) < enchantment.getIntValue()) return false;
            } else if (expected instanceof PotionContents required && value instanceof PotionContents current) {
                if (!Objects.equals(required.potion(),current.potion())) return false;
                if (!required.customEffects().isEmpty() && !Objects.equals(required.customEffects(),current.customEffects())) return false;
            } else if (type == DataComponents.CUSTOM_DATA) {
                // Quality/profession tags are not whole-compound equality conditions.
                if (ItemEconomy.grade(actual) < ItemEconomy.grade(requirement.template)) return false;
            } else if (!Objects.equals(expected,value)) return false;
        }
        return true;
    }
    public static Optional<int[]> allocate(Container inventory, List<Requirement> requirements) {
        int slots = Math.min(36,inventory.getContainerSize());
        int[] counts = new int[slots], required = requirements.stream().mapToInt(Requirement::quantity).toArray();
        boolean[][] match = new boolean[slots][requirements.size()];
        for (int i = 0; i < slots; i++) {
            ItemStack stack = inventory.getItem(i); counts[i] = stack.getCount();
            for (int j = 0; j < requirements.size(); j++) match[i][j] = matches(stack,requirements.get(j));
        }
        return MaterialAllocation.allocate(counts,required,match);
    }
}
