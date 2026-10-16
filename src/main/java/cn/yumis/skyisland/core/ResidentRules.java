package cn.yumis.skyisland.core;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pure settlement rules shared by authoritative prices and client previews. */
public final class ResidentRules {
    private ResidentRules() {}
    public static final List<String> JOBS=List.of("ordinary","smith","carpenter","enchanter","librarian","cook","gardener","prospector");
    public static final Set<String> WOOD_INPUTS;
    public static final Map<String,String> EQUIPMENT;
    static {
        Set<String> wood=new LinkedHashSet<>();
        for(String tree:List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","pale_oak")){wood.add("minecraft:"+tree+"_log");wood.add("minecraft:"+tree+"_planks");}
        for(String tree:List.of("crimson","warped")){wood.add("minecraft:"+tree+"_stem");wood.add("minecraft:"+tree+"_planks");}
        wood.add("minecraft:bamboo");wood.add("minecraft:bamboo_planks");WOOD_INPUTS=Set.copyOf(wood);
        Map<String,String> equipment=new LinkedHashMap<>();
        for(String material:List.of("wooden","iron","golden","diamond")){
            String job=material.equals("wooden")?"carpenter":material.equals("diamond")?"enchanter":"smith";
            for(String tool:List.of("axe","hoe","pickaxe","shovel","spear","sword"))equipment.put("minecraft:"+material+"_"+tool,job);
            if(!material.equals("wooden"))for(String armor:List.of("boots","chestplate","helmet","leggings"))equipment.put("minecraft:"+material+"_"+armor,job);
        }
        EQUIPMENT=Map.copyOf(equipment);
    }
    public static String name(String job){return switch(job){case "smith"->"铁匠";case "carpenter"->"木匠";case "enchanter"->"附魔大师";case "librarian"->"图书管理员";case "cook"->"厨师";case "gardener"->"园丁";case "prospector"->"探矿师";default->"普通居民";};}
    public static String station(String job){return "minecraft:"+switch(job){case "smith"->"smithing_table";case "carpenter"->"crafting_table";case "enchanter"->"enchanting_table";case "librarian"->"lectern";case "cook"->"smoker";case "gardener"->"composter";case "prospector"->"blast_furnace";default->"crafting_table";};}
    public static int capacity(long reputation){return reputation>=300?20:reputation>=100?15:10;}
    public static Random random(long seed,UUID party,UUID house,long round,String purpose){return new Random(seed^party.getMostSignificantBits()^party.getLeastSignificantBits()^house.getMostSignificantBits()^house.getLeastSignificantBits()^round*0x9E3779B97F4A7C15L^UUID.nameUUIDFromBytes(purpose.getBytes(StandardCharsets.UTF_8)).getLeastSignificantBits());}
    public static int saleBps(String item,int ordinary,Set<String> experts,int ordinaryBps,int expertBps){String job=EQUIPMENT.get(item);return Math.addExact(10000,Math.addExact(Math.multiplyExact(ordinary,ordinaryBps),job!=null&&experts.contains(job)?expertBps:0));}
    public static boolean discounted(String item,Set<String> jobs){return jobs.contains("carpenter")&&WOOD_INPUTS.contains(item)||jobs.contains("smith")&&Set.of("minecraft:iron_ingot","minecraft:gold_ingot").contains(item)||jobs.contains("enchanter")&&Set.of("minecraft:diamond","minecraft:emerald").contains(item);}
}
