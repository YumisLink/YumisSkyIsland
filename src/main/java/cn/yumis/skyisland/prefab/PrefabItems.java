package cn.yumis.skyisland.prefab;

import cn.yumis.skyisland.YumisSkyIsland;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerPlayer;
import cn.yumis.skyisland.server.SkyServer;

/** Template identity remains stable when a player positions a blueprint. */
public final class PrefabItems {
    public static Item BLUEPRINT, AMMUNITION;
    public static DataComponentType<String> SELECTION;
    private PrefabItems() {}
    public static void register() {
        SELECTION=Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,YumisSkyIsland.id("blueprint_selection"),
            DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
        var id=YumisSkyIsland.id("prefab_blueprint");
        BLUEPRINT=Registry.register(BuiltInRegistries.ITEM,id,new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM,id)).stacksTo(1)) {
            @Override public InteractionResult useOn(UseOnContext context) {
                if(context.getPlayer() instanceof ServerPlayer player) {
                    var rt=SkyServer.get(player.level().getServer());
                    if(rt!=null)rt.prefabs.beginPreview(player,context.getClickedPos().relative(context.getClickedFace()),context.getHand());
                    else SkyServer.tell(player,"建筑图纸仅可在空岛使用");
                }
                return InteractionResult.SUCCESS;
            }
        });
        var ammo=YumisSkyIsland.id("special_ammunition");
        AMMUNITION=Registry.register(BuiltInRegistries.ITEM,ammo,new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM,ammo)).stacksTo(64)));
    }
    public static String templateId(ItemStack stack) {
        if(!stack.is(BLUEPRINT))return "";
        var data=stack.get(DataComponents.CUSTOM_DATA);
        return data==null?"":data.copyTag().getStringOr("yumis_prefab","");
    }
    public static ItemStack blueprint(String id,String name) {
        ItemStack stack=new ItemStack(BLUEPRINT);CompoundTag tag=new CompoundTag();tag.putString("yumis_prefab",id);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        stack.set(DataComponents.ITEM_NAME,net.minecraft.network.chat.Component.literal(name+"图纸"));return stack;
    }
    public static int ammunition(ItemStack stack,int full) {
        if(!stack.is(AMMUNITION))return 0;
        var data=stack.get(DataComponents.CUSTOM_DATA);
        return data==null?full:Math.clamp(data.copyTag().getIntOr("yumis_ammunition",full),1,full);
    }
    public static ItemStack ammunition(int credits,int full) {
        ItemStack stack=new ItemStack(AMMUNITION);
        if(credits!=full){CompoundTag tag=new CompoundTag();tag.putInt("yumis_ammunition",credits);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
        return stack;
    }
}
