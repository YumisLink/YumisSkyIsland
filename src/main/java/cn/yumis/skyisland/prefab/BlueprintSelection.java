package cn.yumis.skyisland.prefab;

import cn.yumis.skyisland.config.GameConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

public record BlueprintSelection(String template,String revision,String dimension,int x,int y,int z,int rotation,
                                 boolean replace,UUID party,long islandEpoch) {
    public BlockPos origin(){return new BlockPos(x,y,z);}
    public static BlueprintSelection read(ItemStack stack) {
        try {String data=stack.get(PrefabItems.SELECTION);return data==null?null:GameConfig.JSON.fromJson(data,BlueprintSelection.class);}
        catch(RuntimeException e){return null;}
    }
    public void write(ItemStack stack){stack.set(PrefabItems.SELECTION,GameConfig.JSON.toJson(this));}
}
