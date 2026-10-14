package cn.yumis.skyisland.client;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import java.util.*;

/** All registered non-air items, plus valid creative component variants; no economy filtering. */
public final class ItemPickerCatalog {
    public record Item(String id,String name,ItemStack stack,JsonObject components) {}
    public record Property(String id,String name) {}
    private static List<Item> items;
    private ItemPickerCatalog() {}
    public static void clear(){items=null;}
    public static List<Item> items(){
        if(items!=null)return items;
        var minecraft=Minecraft.getInstance();if(minecraft.getConnection()==null)return List.of();
        var ops=RegistryOps.create(JsonOps.INSTANCE,minecraft.getConnection().registryAccess());
        Map<String,Item> catalog=new LinkedHashMap<>();
        java.util.function.Consumer<ItemStack> add=original->{
            if(original.isEmpty())return;
            try{
                ItemStack stack=original.copyWithCount(1);
                JsonObject encoded=ItemStack.CODEC.encodeStart(ops,stack).getOrThrow().getAsJsonObject();
                JsonObject components=encoded.has("components")?encoded.getAsJsonObject("components"):new JsonObject();
                String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),name=stack.getHoverName().getString();
                var enchants=EnchantmentHelper.getEnchantmentsForCrafting(stack);
                for(var enchantment:enchants.entrySet())name+=" · "+enchantment.getKey().value().description().getString()+" "+enchantment.getIntValue();
                catalog.putIfAbsent(id+"|"+components,new Item(id,name,stack,components.deepCopy()));
            }catch(RuntimeException ignored){/* A component variant not valid in this world's registry is not selectable. */}
        };
        for(var item:BuiltInRegistries.ITEM)add.accept(item.getDefaultInstance());
        CreativeModeTabs.tryRebuildTabContents(minecraft.getConnection().enabledFeatures(),true,minecraft.getConnection().registryAccess());
        for(var tab:CreativeModeTabs.tabs())for(var stack:tab.getDisplayItems())add.accept(stack);
        var result=new ArrayList<>(catalog.values());result.sort(Comparator.comparing(Item::name).thenComparing(Item::id));items=List.copyOf(result);return items;
    }
    public static List<Property> enchantments(){
        var minecraft=Minecraft.getInstance();if(minecraft.getConnection()==null)return List.of();
        var result=new ArrayList<Property>();
        for(var entry:minecraft.getConnection().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).entrySet())
            result.add(new Property(entry.getKey().identifier().toString(),entry.getValue().description().getString()));
        result.sort(Comparator.comparing(Property::name));return result;
    }
    public static List<Property> potions(){
        var result=new ArrayList<Property>();
        for(var entry:BuiltInRegistries.POTION.entrySet()){
            var holder=BuiltInRegistries.POTION.get(entry.getKey()).orElseThrow();
            result.add(new Property(entry.getKey().identifier().toString(),PotionContents.createItemStack(Items.POTION,holder).getHoverName().getString()));
        }
        result.sort(Comparator.comparing(Property::name).thenComparing(Property::id));return result;
    }
    public static boolean contains(String query,String name,String id){
        String lower=query.toLowerCase(Locale.ROOT);return name.toLowerCase(Locale.ROOT).contains(lower)||id.toLowerCase(Locale.ROOT).contains(lower);
    }
}
