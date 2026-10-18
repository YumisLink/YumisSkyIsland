package cn.yumis.skyisland.client.jei;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.core.HammerRules;
import cn.yumis.skyisland.item.Hammers;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.placement.IPlaceable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Optional JEI pages for hammer crafting, conversion and miner bonus rates. */
@JeiPlugin
public final class HammerJeiPlugin implements IModPlugin {
    private static final IRecipeType<Page> TYPE = IRecipeType.create(YumisSkyIsland.id("hammer"), Page.class);
    private static final IRecipeType<Page> DROPS = IRecipeType.create(YumisSkyIsland.id("miner_bonus"), Page.class);

    @Override public Identifier getPluginUid() { return YumisSkyIsland.id("jei_hammer"); }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        var helper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new HammerCategory(TYPE, "大锤", helper.createDrawableItemLike(Hammers.IRON)));
        registration.addRecipeCategories(new HammerCategory(DROPS, "矿工额外掉落", helper.createDrawableItemLike(Items.COBBLESTONE)));
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(TYPE, List.of(
            craft("stone", new ItemStack(Items.COBBLESTONE), new ItemStack(Hammers.STONE)),
            craft("iron", new ItemStack(Items.IRON_INGOT), new ItemStack(Hammers.IRON)),
            craft("diamond", new ItemStack(Items.DIAMOND), new ItemStack(Hammers.DIAMOND)),
            new Page("netherite", List.of(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), new ItemStack(Hammers.DIAMOND), new ItemStack(Items.NETHERITE_INGOT)), new ItemStack(Hammers.NETHERITE),
                List.of("锻造台升级", "保留附魔和名字")),
            convert("cobble", new ItemStack(Items.COBBLESTONE), new ItemStack(Items.GRAVEL), "圆石 → 砂砾"),
            convert("gravel", new ItemStack(Items.GRAVEL), new ItemStack(Items.DIRT), "砂砾 → 泥土"),
            convert("dirt", new ItemStack(Items.DIRT), new ItemStack(Items.SAND), "泥土 → 沙子")
        ));
        List<Page> drops = new ArrayList<>();
        int index = 0;
        for (HammerRules.Row row : HammerRules.rows()) {
            ItemStack output = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(row.itemId())), row.count());
            drops.add(new Page("drop_" + index++, List.of(new ItemStack(Items.COBBLESTONE)), output, List.of(
                    row.band() + "级  " + row.chance() + "  ×" + row.count(),
                    row.note())));
        }
        registration.addRecipes(DROPS, drops);
    }

    private static Page craft(String id, ItemStack material, ItemStack hammer) {
        return new Page(id, List.of(material, new ItemStack(Items.STICK)), hammer, List.of("工作台：上两排材料，中间一根棍"));
    }

    private static Page convert(String id, ItemStack block, ItemStack drop, String line) {
        return new Page(id, List.of(new ItemStack(Hammers.STONE), block), drop, List.of(line, "时运和精准采集不改变结果"));
    }

    private record Page(String id, List<ItemStack> inputs, ItemStack output, List<String> lines) {}

    private static final class HammerCategory implements IRecipeCategory<Page> {
        private final IRecipeType<Page> type;
        private final Component title;
        private final IDrawable icon;
        HammerCategory(IRecipeType<Page> type, String title, IDrawable icon) { this.type = type; this.title = Component.literal(title); this.icon = icon; }
        @Override public IRecipeType<Page> getRecipeType() { return type; }
        @Override public Component getTitle() { return title; }
        @Override public int getWidth() { return 170; }
        @Override public int getHeight() { return 96; }
        @Override public IDrawable getIcon() { return icon; }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, Page recipe, IFocusGroup focuses) {
            for (int i = 0; i < recipe.inputs.size(); i++) builder.addInputSlot(4 + i * 20, 4).add(recipe.inputs.get(i)).setStandardSlotBackground();
            if (!recipe.output.isEmpty()) builder.addOutputSlot(148, 4).add(recipe.output).setOutputSlotBackground();
        }
        @Override public void createRecipeExtras(IRecipeExtrasBuilder extras, Page recipe, IFocusGroup focuses) {
            int y = 28;
            for (String line : recipe.lines) {
                IPlaceable<?> text = extras.addText(Component.literal(line), 162, 11);
                text.setPosition(4, y);
                y += 12;
            }
        }
        @Override public Identifier getIdentifier(Page recipe) { return YumisSkyIsland.id("hammer_" + recipe.id); }
    }
}
