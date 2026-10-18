package cn.yumis.skyisland.client;

import cn.yumis.skyisland.core.FoodHealthRules;
import cn.yumis.skyisland.core.FruitText;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Fruits are always listed. Foods appear after the player has eaten one. */
public final class PermanentBuffPanel {
    private static final String[] FRUITS = {"farmer", "hunter", "miner", "chef", "blacksmith", "battle_master"};
    private static final String[] NAMES = {"农夫", "猎人", "矿工", "厨师", "铁匠", "战斗大师"};
    private int scroll;
    private final ClientCatalog model;
    public PermanentBuffPanel(ClientCatalog model) { this.model = model; }

    public void render(GuiGraphicsExtractor g, Font font, int width, int height) {
        double total = 0;
        JsonObject health = model.state.getAsJsonObject("foodHealth");
        if (health != null) for (var entry : health.entrySet()) if (entry.getValue().isJsonPrimitive()) total += entry.getValue().getAsDouble();
        g.text(font, "心之钢 " + number(total) + " 点生命上限", 17, 87, 0xFFFFD56A, false);
        int top = 110, bottom = height - 18;
        List<String> lines = lines();
        int max = Math.max(0, lines.size() * 16 - (bottom - top));
        scroll = Math.clamp(scroll, 0, max);
        g.enableScissor(12, top, width - 12, bottom);
        for (int i = 0; i < lines.size(); i++) {
            int y = top + i * 16 - scroll;
            if (y + 16 < top || y >= bottom) continue;
            g.text(font, lines.get(i), 17, y, i < FRUITS.length ? 0xFFD6E8FF : 0xFFE7F6EA, false);
        }
        g.disableScissor();
    }

    public void scroll(double sy, int height) {
        int max = Math.max(0, lines().size() * 16 - (height - 18 - 110));
        scroll = Math.clamp(scroll - (int) Math.signum(sy) * 16, 0, max);
    }

    private List<String> lines() {
        List<String> lines = new ArrayList<>();
        JsonObject eatenFruits = model.state.getAsJsonObject("consumedFruits");
        int cap = 100;
        JsonObject shop = model.state.getAsJsonObject("professionShop");
        if (shop != null && shop.has("fruitUseCap")) cap = Math.clamp(shop.get("fruitUseCap").getAsInt(), 1, 100);
        for (int i = 0; i < FRUITS.length; i++) {
            int used = eatenFruits != null && eatenFruits.has(FRUITS[i]) ? eatenFruits.get(FRUITS[i]).getAsInt() : 0;
            String effect = FruitText.current(FRUITS[i], used);
            lines.add(NAMES[i] + "果实  " + used + "/" + cap + (effect.isEmpty() ? "" : "  " + effect));
        }
        JsonObject eaten = model.state.getAsJsonObject("foodEaten");
        JsonObject health = model.state.getAsJsonObject("foodHealth");
        if (eaten == null || eaten.entrySet().isEmpty()) {
            lines.add("还没有吃下会计入心之钢的食物");
            return lines;
        }
        for (var entry : eaten.entrySet()) {
            String id = entry.getKey();
            int count = entry.getValue().getAsInt();
            double granted = health != null && health.has(id) ? health.get(id).getAsDouble() : 0;
            lines.add(name(id) + "  已吃 " + count + "  距下一组 " + FoodHealthRules.untilNext(count) + "  已获得 " + number(granted));
        }
        return lines;
    }

    private static String name(String id) {
        try {
            Identifier key = Identifier.parse(id);
            if (BuiltInRegistries.ITEM.containsKey(key)) return new ItemStack(BuiltInRegistries.ITEM.getValue(key)).getHoverName().getString();
        } catch (RuntimeException ignored) {}
        return id;
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) return "0";
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
