package cn.yumis.skyisland.item;

import cn.yumis.skyisland.server.FishingService;
import cn.yumis.skyisland.server.SkyServer;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

/** One registered item; the caught biome, medium and blood-moon state travel with the stack. */
public final class BiomeTreasureBox extends Item {
    public record Kind(String biome,String medium,boolean bloodMoon) {}
    private static final String BIOME="yumis_box_biome", MEDIUM="yumis_box_medium", BLOOD="yumis_box_blood";
    private static final Set<String> NETHER=Set.of("nether_wastes","soul_sand_valley","crimson_forest","warped_forest","basalt_deltas");
    private static final Set<String> END=Set.of("the_end","end_highlands","end_midlands","small_end_islands","end_barrens");
    public BiomeTreasureBox(Properties properties){super(properties);}
    public static ItemStack create(String biome,String medium,boolean bloodMoon){
        ItemStack stack=new ItemStack(ModItems.BIOME_TREASURE_BOX);
        CompoundTag tag=new CompoundTag();
        tag.putString(BIOME,biome);tag.putString(MEDIUM,medium);tag.putBoolean(BLOOD,bloodMoon);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(List.of(),List.of(),List.of(biome+"_"+medium+(bloodMoon?"_blood":"")),List.of()));
        MutableComponent name=Component.empty();
        if(bloodMoon)name.append("血月");
        if(medium.equals("lava"))name.append("岩浆");
        else if(medium.equals("void"))name.append("虚空");
        name.append(Component.translatable("biome.minecraft."+biome)).append("匣");
        stack.set(DataComponents.ITEM_NAME,name);
        return stack;
    }
    public static Kind kind(ItemStack stack){
        if(!stack.is(ModItems.BIOME_TREASURE_BOX))return null;
        CompoundTag tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        String biome=tag.getStringOr(BIOME,"");String medium=tag.getStringOr(MEDIUM,"");
        boolean blood=tag.getBooleanOr(BLOOD,false);
        if(!ModItems.BIOME_BALLS.containsKey(biome)||!List.of("water","lava","void").contains(medium))return null;
        if(medium.equals("lava")&&!NETHER.contains(biome)||medium.equals("void")&&!END.contains(biome))return null;
        if(blood&&(!medium.equals("water")||NETHER.contains(biome)||END.contains(biome)))return null;
        return new Kind(biome,medium,blood);
    }
    @Override public InteractionResult use(Level level,net.minecraft.world.entity.player.Player user,InteractionHand hand){
        ItemStack held=user.getItemInHand(hand);Kind kind=kind(held);
        if(kind==null)return InteractionResult.FAIL;
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(!(user instanceof ServerPlayer player)||!(level instanceof ServerLevel serverLevel))return InteractionResult.FAIL;
        SkyServer runtime=SkyServer.get(serverLevel.getServer());if(runtime==null||runtime.inventoryLocks.contains(player.getUUID()))return InteractionResult.FAIL;
        ItemStack reward=FishingService.openBox(runtime,player,kind);
        if(reward.isEmpty())return InteractionResult.FAIL;
        if(!player.isCreative())held.shrink(1);
        for(ItemStack part:runtime.professions.qualityParts(player,"fisherman",reward))
            if(!player.getInventory().add(part))player.drop(part,false);
        return InteractionResult.SUCCESS;
    }
}
