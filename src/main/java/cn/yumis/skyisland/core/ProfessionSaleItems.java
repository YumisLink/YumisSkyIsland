package cn.yumis.skyisland.core;

import java.util.*;

/** Local selection preferences, independent of prices and profession provenance. */
public final class ProfessionSaleItems {
    public static final List<String> DEFAULTS;
    static {
        var items=new ArrayList<String>();
        for(String id:("wheat sweet_berries carrot potato beetroot pumpkin melon_slice sugar_cane cocoa_beans nether_wart chorus_fruit "
            +"cod salmon pufferfish tropical_fish baked_potato beetroot_soup bread cake cooked_beef cooked_chicken cooked_cod cooked_mutton "
            +"cooked_porkchop cooked_rabbit cooked_salmon cookie golden_carrot mushroom_stew pumpkin_pie rabbit_stew suspicious_stew hay_block sugar").split(" "))items.add("minecraft:"+id);
        for(String id:"sky_carp lava_eel void_fish frost_trout desert_lungfish mushroom_fish barley white_radish purple_potato golden_beet crimson_wart sky_fruit".split(" "))items.add("yumis_sky_island:"+id);
        DEFAULTS=List.copyOf(items);
    }
    public static LinkedHashSet<String> normalize(Collection<String> values){
        var result=new LinkedHashSet<String>();
        for(String value:values)if(value!=null&&value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))result.add(value);
        return result;
    }
    private ProfessionSaleItems(){}
}
