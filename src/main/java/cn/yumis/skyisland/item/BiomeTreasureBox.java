package cn.yumis.skyisland.item;

import cn.yumis.skyisland.server.FishingService;
import cn.yumis.skyisland.server.SkyServer;
import cn.yumis.skyisland.shop.ItemEconomy;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

/** One registered item; the caught biome, medium and blood-moon state travel with the stack. */
public final class BiomeTreasureBox extends Item {
    public record Kind(String biome,String medium,boolean bloodMoon) {}
    private static final String BIOME="yumis_box_biome", MEDIUM="yumis_box_medium", BLOOD="yumis_box_blood";
    public static final Set<String> NETHER_BIOMES=Set.of("nether_wastes","soul_sand_valley","crimson_forest","warped_forest","basalt_deltas");
    public static final Set<String> END_BIOMES=Set.of("the_end","end_highlands","end_midlands","small_end_islands","end_barrens");
    public BiomeTreasureBox(Properties properties){super(properties);}
    public static ItemStack create(String biome,String medium,boolean bloodMoon){
        ItemStack stack=new ItemStack(ModItems.BIOME_TREASURE_BOX);
        CompoundTag tag=new CompoundTag();
        tag.putString(BIOME,biome);tag.putString(MEDIUM,medium);tag.putBoolean(BLOOD,bloodMoon);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(List.of(),List.of(),List.of(biome+"_"+medium+(bloodMoon?"_blood":"")),List.of()));
        MutableComponent name=Component.empty();
        if(biome.equals("overworld"))name.append("主世界").append(medium.equals("lava")?"熔岩":"虚空");
        else if((medium.equals("lava")||medium.equals("void"))&&biome.equals("the_end"))name.append("末地").append(medium.equals("lava")?"熔岩":"虚空");
        else {
            if(bloodMoon)name.append("血月");
            if(medium.equals("lava"))name.append("熔岩");
            else if(medium.equals("void"))name.append("虚空");
            name.append(Component.translatable("biome.minecraft."+biome));
        }
        name.append("匣");
        stack.set(DataComponents.ITEM_NAME,name);
        return stack;
    }
    public static Kind kind(ItemStack stack){
        if(!stack.is(ModItems.BIOME_TREASURE_BOX))return null;
        CompoundTag tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        String biome=tag.getStringOr(BIOME,"");String medium=tag.getStringOr(MEDIUM,"");
        boolean blood=tag.getBooleanOr(BLOOD,false);
        if(!List.of("water","lava","void").contains(medium))return null;
        boolean registered=ModItems.BIOME_BALLS.containsKey(biome);
        if(medium.equals("water")){
            if(!registered)return null;
            if(blood&&(NETHER_BIOMES.contains(biome)||END_BIOMES.contains(biome)))blood=false;
            return new Kind(biome,medium,blood);
        }
        if(!registered&&!biome.equals("overworld"))return null;
        return new Kind(biome,medium,false);
    }
    @Override public InteractionResult use(Level level,net.minecraft.world.entity.player.Player user,InteractionHand hand){
        ItemStack held=user.getItemInHand(hand);Kind kind=kind(held);
        if(kind==null)return InteractionResult.FAIL;
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(!(user instanceof ServerPlayer player)||!(level instanceof ServerLevel serverLevel))return InteractionResult.FAIL;
        SkyServer runtime=SkyServer.get(serverLevel.getServer());if(runtime==null||runtime.inventoryLocks.contains(player.getUUID()))return InteractionResult.FAIL;
        ItemStack reward=FishingService.openBox(runtime,player,kind);
        if(reward.isEmpty())return InteractionResult.FAIL;
        Component boxName=held.getHoverName();
        if(!player.isCreative())held.shrink(1);
        List<ItemStack> parts=runtime.professions.qualityParts(player,"fisherman",reward);
        // Inventory insertion zeroes the count. 26.2 then reports that stack as air, so name it first.
        Component announcement=opened(boxName,parts,runtime.config.object("products/quality.toml"));
        for(ItemStack part:parts)
            if(!player.getInventory().add(part))player.drop(part,false);
        SkyServer.tell(player,announcement);
        return InteractionResult.SUCCESS;
    }
    /** Chat body without the shared prefix. Quality is named only above ordinary; enchantments are written out. */
    static Component opened(Component boxName,List<ItemStack> parts,JsonObject quality){
        MutableComponent message=Component.literal("打开了").append(boxName).append("，获得 ");
        for(int i=0;i<parts.size();i++){
            if(i>0)message.append("、");
            ItemStack part=parts.get(i);
            int grade=ItemEconomy.grade(part);
            if(grade>0)message.append(quality.getAsJsonArray("grades").get(grade).getAsJsonObject().get("name").getAsString()).append(" ");
            message.append(described(part));
        }
        return message;
    }
    private static Component described(ItemStack stack){
        ItemEnchantments enchants=stack.getOrDefault(stack.is(Items.ENCHANTED_BOOK)?DataComponents.STORED_ENCHANTMENTS:DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
        if(enchants.isEmpty())return stack.getHoverName();
        MutableComponent text=Component.empty().append(stack.getHoverName()).append("（");
        boolean first=true;
        for(var entry:enchants.entrySet()){
            if(!first)text.append("、");
            first=false;
            text.append(Enchantment.getFullname(entry.getKey(),entry.getIntValue()));
        }
        return text.append("）");
    }
}
