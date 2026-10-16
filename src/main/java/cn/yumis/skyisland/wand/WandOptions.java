package cn.yumis.skyisland.wand;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Stored on the wand, so vanilla smithing preserves the player's choices. */
public record WandOptions(int direction, int matching, boolean copyFacing, boolean random) {
    public static final String[] DIRECTIONS = {"不限方向", "水平", "竖直", "南北", "东西"};
    public static final String[] MATCHES = {"精确", "模糊", "任意承托"};
    public static final WandOptions DEFAULT = new WandOptions(0, 0, true, false);
    public WandOptions { direction = Math.clamp(direction, 0, 4); matching = Math.clamp(matching, 0, 2); }
    public static WandOptions read(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return new WandOptions(data.getIntOr("wand_direction",0), data.getIntOr("wand_matching",0),
                data.getBooleanOr("wand_copy_facing",true), data.getBooleanOr("wand_random",false));
    }
    public void write(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putInt("wand_direction",direction); data.putInt("wand_matching",matching);
        data.putBoolean("wand_copy_facing",copyFacing); data.putBoolean("wand_random",random);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
    }
}
