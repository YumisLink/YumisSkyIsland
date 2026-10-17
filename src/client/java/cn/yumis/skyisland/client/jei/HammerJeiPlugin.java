package cn.yumis.skyisland.client.jei;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.item.Hammers;
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

    @Override public Identifier getPluginUid() { return YumisSkyIsland.id("jei_hammer"); }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new HammerCategory(registration.getJeiHelpers().getGuiHelper().createDrawableItemLike(Hammers.IRON)));
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
            convert("dirt", new ItemStack(Items.DIRT), new ItemStack(Items.SAND), "泥土 → 沙子"),
            new Page("rates", List.of(new ItemStack(Items.COBBLESTONE)), ItemStack.EMPTY, List.of(
                "出矿只在锤子敲圆石时判定，连锁每一块都掷",
                "10级：1% 铁粒",
                "30级：5% 铁粒，1% 金粒",
                "50级：25% 铁粒，3% 金粒，1% 青金石或红石",
                "75级：100% 铁粒，8% 金粒，3% 青金石或红石，1% 钻石",
                "100级：20% 铁锭，50% 金粒，青金石15%，红石15%，3% 钻石，0.1% 远古残骸"))
        ));
    }

    private static Page craft(String id, ItemStack material, ItemStack hammer) {
        return new Page(id, List.of(material, new ItemStack(Items.STICK)), hammer, List.of("工作台：上两排材料，中间一根棍"));
    }

    private static Page convert(String id, ItemStack block, ItemStack drop, String line) {
        return new Page(id, List.of(new ItemStack(Hammers.STONE), block), drop, List.of(line, "时运和精准采集不改变结果"));
    }

    private record Page(String id, List<ItemStack> inputs, ItemStack output, List<String> lines) {}

    private static final class HammerCategory implements IRecipeCategory<Page> {
        private final IDrawable icon;
        HammerCategory(IDrawable icon) { this.icon = icon; }
        @Override public IRecipeType<Page> getRecipeType() { return TYPE; }
        @Override public Component getTitle() { return Component.literal("大锤"); }
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
