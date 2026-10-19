package cn.yumis.skyisland.client.jei;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.client.ClientCatalog;
import cn.yumis.skyisland.item.BiomeTreasureBox;
import cn.yumis.skyisland.item.ModItems;
import cn.yumis.skyisland.server.FishingService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.placement.IPlaceable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Optional JEI presentation of the server's regional treasure tables. */
@JeiPlugin
public final class SkyFishingJeiPlugin implements IModPlugin {
    private static final IRecipeType<Catch> FISHING=IRecipeType.create(YumisSkyIsland.id("fishing"),Catch.class);
    private static final IRecipeType<Catch> BLOOD=IRecipeType.create(YumisSkyIsland.id("blood_moon_fishing"),Catch.class);
    private static final IRecipeType<Catch> CONTENTS=IRecipeType.create(YumisSkyIsland.id("treasure_box_contents"),Catch.class);
    private FishingCategory fishing,blood;
    private ContentsCategory contents;
    private int normalCount,bloodCount;

    @Override public Identifier getPluginUid(){return YumisSkyIsland.id("jei_fishing");}
    @Override public void registerRecipeCatalysts(mezz.jei.api.registration.IRecipeCatalystRegistration registration){
        var furnaces=cn.yumis.skyisland.furnace.FurnaceContent.BLOCKS.values().toArray(net.minecraft.world.level.ItemLike[]::new);
        registration.addCraftingStation(mezz.jei.api.constants.RecipeTypes.SMELTING,furnaces);
        registration.addCraftingStation(mezz.jei.api.constants.RecipeTypes.SMELTING_FUEL,furnaces);
    }
    @Override public void registerItemSubtypes(ISubtypeRegistration registration){
        registration.registerSubtypeInterpreter(ModItems.BIOME_TREASURE_BOX,(stack,context)->{
            BiomeTreasureBox.Kind kind=BiomeTreasureBox.kind(stack);
            return kind==null?"invalid":kind.biome()+"/"+kind.medium()+"/"+kind.bloodMoon();
        });
    }
    @Override public void registerCategories(IRecipeCategoryRegistration registration){
        fishing=new FishingCategory(FISHING,"钓鱼",registration.getJeiHelpers().getGuiHelper().createDrawableItemLike(Items.FISHING_ROD));
        blood=new FishingCategory(BLOOD,"血月钓鱼",registration.getJeiHelpers().getGuiHelper().createDrawableItemLike(Items.ZOMBIE_SPAWN_EGG));
        contents=new ContentsCategory(registration.getJeiHelpers().getGuiHelper().createDrawableItemLike(ModItems.BIOME_TREASURE_BOX));
        registration.addRecipeCategories(fishing,blood,contents);
    }
    @Override public void registerRecipes(IRecipeRegistration registration){
        List<Catch> normal=new ArrayList<>(),bloodMoon=new ArrayList<>(),all=new ArrayList<>();
        for(String biome:ModItems.BIOME_BALLS.keySet()){
            add(normal,all,biome,"water",false);
            if(!BiomeTreasureBox.NETHER_BIOMES.contains(biome)&&!BiomeTreasureBox.END_BIOMES.contains(biome))add(bloodMoon,all,biome,"water",true);
        }
        for(String biome:BiomeTreasureBox.NETHER_BIOMES){add(normal,all,biome,"lava",false);add(normal,all,biome,"void",false);}
        add(normal,all,"overworld","lava",false);add(normal,all,"overworld","void",false);
        add(normal,all,"the_end","lava",false);add(normal,all,"the_end","void",false);
        registration.addRecipes(FISHING,normal);
        registration.addRecipes(BLOOD,bloodMoon);
        registration.addRecipes(CONTENTS,all);
        normalCount=normal.size();bloodCount=bloodMoon.size();
    }
    @Override public void onRuntimeAvailable(IJeiRuntime runtime){
        YumisSkyIsland.LOGGER.info("JEI 钓鱼分类已注册：钓鱼 {} 条，血月钓鱼 {} 条",normalCount,bloodCount);
    }
    private static void add(List<Catch> recipes,List<Catch> all,String biome,String medium,boolean blood){
        Catch recipe=new Catch(biome,medium,blood);recipes.add(recipe);all.add(recipe);
    }
    private record Catch(String biome,String medium,boolean blood){
        ItemStack box(){return BiomeTreasureBox.create(biome,medium,blood);}
        Map<Item,Double> probabilities(){
            Map<Item,Double> values=new LinkedHashMap<>();
            for(var outcome:FishingService.treasureOutcomes(biome,medium,0,blood,BiomeTreasureBox.END_BIOMES.contains(biome),ClientCatalog.serverEggBps,ClientCatalog.serverAccessoryBoxBps))
                values.merge(outcome.item(),outcome.probability(),Double::sum);
            return values;
        }
    }
    private static final class FishingCategory implements IRecipeCategory<Catch>{
        private final IRecipeType<Catch> type;private final Component title;private final IDrawable icon;
        FishingCategory(IRecipeType<Catch> type,String title,IDrawable icon){this.type=type;this.title=Component.literal(title);this.icon=icon;}
        @Override public IRecipeType<Catch> getRecipeType(){return type;}
        @Override public Component getTitle(){return title;}
        @Override public int getWidth(){return 170;}
        @Override public int getHeight(){return 68;}
        @Override public IDrawable getIcon(){return icon;}
        @Override public void setRecipe(IRecipeLayoutBuilder builder,Catch recipe,IFocusGroup focuses){
            builder.addInputSlot(4,5).add(Items.FISHING_ROD).setStandardSlotBackground();
            builder.addOutputSlot(143,5).add(recipe.box()).setOutputSlotBackground();
        }
        @Override public void createRecipeExtras(IRecipeExtrasBuilder extras,Catch recipe,IFocusGroup focuses){
            Component place=recipe.biome().equals("overworld")?Component.literal("主世界"):recipe.biome().equals("the_end")&&!recipe.medium().equals("water")?Component.literal("末地"):Component.translatable("biome.minecraft."+recipe.biome());
            addText(extras,Component.literal("群系: ").append(place),28);
            String medium=switch(recipe.medium()){case "lava"->"熔岩";case "void"->"虚空";default->"水";};
            addText(extras,Component.literal("钓鱼介质: "+medium),41);
            addText(extras,Component.literal("右键开匣，按开启时的渔力和等级抽取"),54);
        }
        private static void addText(IRecipeExtrasBuilder extras,Component text,int y){
            // Target the shared placement signature, also present in JEI 30.16.
            IPlaceable<?> widget=extras.addText(text,155,11);
            widget.setPosition(4,y);
        }
        @Override public void getTooltip(ITooltipBuilder tooltip,Catch recipe,IRecipeSlotsView slots,double mouseX,double mouseY){
            // Older JEI text widgets do not support setTooltip.
            if(mouseX>=4&&mouseX<159&&mouseY>=54&&mouseY<65)
                tooltip.add(Component.literal("匣内容及逐项概率见“群系匣内容”分类。"));
        }
        @Override public Identifier getIdentifier(Catch recipe){
            return YumisSkyIsland.id((recipe.blood()?"blood_":"")+recipe.medium()+"_"+recipe.biome());
        }
    }
    private static final class ContentsCategory implements IRecipeCategory<Catch>{
        private final IDrawable icon;
        ContentsCategory(IDrawable icon){this.icon=icon;}
        @Override public IRecipeType<Catch> getRecipeType(){return CONTENTS;}
        @Override public Component getTitle(){return Component.literal("群系匣内容");}
        @Override public int getWidth(){return 182;}
        @Override public int getHeight(){return 105;}
        @Override public IDrawable getIcon(){return icon;}
        @Override public void setRecipe(IRecipeLayoutBuilder builder,Catch recipe,IFocusGroup focuses){
            builder.addInputSlot(4,4).add(recipe.box()).setStandardSlotBackground();
            int i=0;for(Item item:recipe.probabilities().keySet()){
                builder.addOutputSlot(4+(i%9)*19,27+(i/9)*19).add(new ItemStack(item)).setOutputSlotBackground()
                    .addRichTooltipCallback((slot,tooltip)->{
                        double chance=recipe.probabilities().getOrDefault(item,0.0);
                        tooltip.add(Component.literal(String.format(Locale.ROOT,"单次开匣概率：%.4f%%",chance*100)));
                        tooltip.add(Component.literal(ModItems.ACCESSORY_ICONS.containsValue(item)?"血月专属饰品概率固定，不受渔力影响":"原奖池内实际概率会随开启者渔力变化"));
                    });
                i++;
            }
        }
        @Override public void createRecipeExtras(IRecipeExtrasBuilder extras,Catch recipe,IFocusGroup focuses){
            IPlaceable<?> one=extras.addText(Component.literal("每次开启获得以下一种物品"),175,11);one.setPosition(4,76);
            IPlaceable<?> two=extras.addText(Component.literal("概率按零渔力和当前服务器配置计算"),175,11);two.setPosition(4,89);
        }
        @Override public void getTooltip(ITooltipBuilder tooltip,Catch recipe,IRecipeSlotsView slots,double mouseX,double mouseY){
            if(mouseX<4||mouseY<27)return;
            int column=(int)((mouseX-4)/19),row=(int)((mouseY-27)/19);
            if(column<0||column>=9||row<0||mouseX>=4+column*19+18||mouseY>=27+row*19+18)return;
            var entries=new ArrayList<>(recipe.probabilities().entrySet());int index=row*9+column;
            if(index>=entries.size())return;
            tooltip.add(Component.literal(String.format(Locale.ROOT,"单次开匣概率：%.4f%%",entries.get(index).getValue()*100)));
            tooltip.add(Component.literal(ModItems.ACCESSORY_ICONS.containsValue(entries.get(index).getKey())?"血月专属饰品概率固定，不受渔力影响":"原奖池内实际概率会随开启者渔力变化"));
        }
        @Override public Identifier getIdentifier(Catch recipe){return YumisSkyIsland.id("contents_"+(recipe.blood()?"blood_":"")+recipe.medium()+"_"+recipe.biome());}
    }
}
