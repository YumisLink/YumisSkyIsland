package cn.yumis.skyisland.client;

import cn.yumis.skyisland.core.Money;
import cn.yumis.skyisland.network.ShopPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Custom transparent shop with native-size text and two-line product names. */
public final class ShopScreen extends Screen {
    private static final int TOP = 106, FOOTER = 40, SLIDER_WIDTH = 120;
    private final ClientCatalog model;
    private List<ClientCatalog.Product> visible = List.of();
    private int mode, scroll, categoryScroll, lastRevision = -1, columns, left, rowHeight;
    private String category = "all", search = "";
    private boolean searchFocused, dragSlider;
    private ClientCatalog.Product hovered, detail;
    private int quantity = 1;

    public ShopScreen(ClientCatalog model) { super(Component.literal("空岛商店")); this.model = model; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float delta) { }
    private int sidebar() { return width < 500 ? 106 : 132; }
    private int sliderX() { return width - 137; }
    private int tabWidth(){return Math.max(42,Math.min(width<500?72:86,(sliderX()-24)/4-4));}
    private int iconSize() { return Math.min(48, Math.max(16, (model.cellSize - 8) / 16 * 16)); }
    private List<ClientCatalog.Category> categories() {
        var result = new ArrayList<ClientCatalog.Category>();
        result.add(new ClientCatalog.Category("all", "全部分类", net.minecraft.world.item.ItemStack.EMPTY));
        result.addAll(model.shopTabs());
        return result;
    }
    private void layout() {
        model.cellSize = Math.clamp(model.cellSize, ClientCatalog.MIN_CELL, ClientCatalog.MAX_CELL);
        int start = sidebar() + 12;
        columns = Math.max(1, Math.min(30, (width - start - 12) / model.cellSize));
        left = start + Math.max(0, (width - start - 12 - columns * model.cellSize) / 2);
        rowHeight = iconSize() + 3 * font.lineHeight + 22;
        if (lastRevision != model.revision) {
            var items = new ArrayList<ClientCatalog.Product>();
            for (var p : model.products.values()) {
                if (!p.buy()) continue;
                if (mode == 0 && !p.rarity().equals("common")) continue;
                if (mode == 1 && (!model.state.has("stock") || !model.state.getAsJsonObject("stock").has(p.id()))) continue;
                if (mode == 2 && !model.favorites.contains(p.id())) continue;
                if (!category.equals("all") && !model.category(p).equals(category)) continue;
                if (!search.isBlank() && !p.name().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)) && !p.id().contains(search.toLowerCase(Locale.ROOT))) continue;
                items.add(p);
            }
            items.sort(Comparator.comparing((ClientCatalog.Product p) -> !model.favorites.contains(p.id())).thenComparingInt(ClientCatalog.Product::level).thenComparing(ClientCatalog.Product::name));
            visible = items; lastRevision = model.revision;
        }
        int rows = Math.max(1, (height - FOOTER - TOP) / rowHeight), totalRows = (visible.size() + columns - 1) / columns;
        scroll = Math.clamp(scroll, 0, Math.max(0, totalRows - rows));
    }
    private void invalidate() { lastRevision = -1; scroll = 0; }
    private static boolean inside(double x, double y, int l, int t, int w, int h) { return x >= l && x < l + w && y >= t && y < t + h; }
    private String ellipsis(String value, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(value) <= maxWidth) return value;
        return font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("…"))) + "…";
    }
    private void text(GuiGraphicsExtractor g, String value, int x, int y, int color, int maxWidth) {
        g.text(font, ellipsis(value, maxWidth), x, y, color, false);
    }
    private void button(GuiGraphicsExtractor g, String label, int x, int y, int w, int h, boolean selected, boolean hot) {
        g.fill(x, y, x + w, y + h, selected ? 0xCC354D51 : hot ? 0xB5393939 : 0xA61B1B1B);
        if (selected) g.fill(x, y + h - 1, x + w, y + h, 0xFF71DCC9);
        text(g, label, x + 6, y + (h - font.lineHeight) / 2, 0xFFE9EBEE, w - 12);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        layout(); hovered = null;
        g.fill(0, 0, width, height, 0xB0000000); g.fill(6, 6, width - 6, 32, 0xB51A1D20);
        text(g, "YUMI · 空岛商店", 14, 14, 0xFFF3F5F7, width / 2 - 16);
        String wallet = Money.format(model.wallet());
        text(g, wallet, width - Math.min(width / 2 - 16, font.width(wallet)) - 14, 14, 0xFFFFDC78, width / 2 - 16);
        String[] modes = {"常驻商店", model.hidden() ? "隐藏商店" : "每日商店", "收藏"};
        int tabWidth = tabWidth();
        for (int i = 0; i < 3; i++) button(g, modes[i], 8 + i * (tabWidth + 4), 38, tabWidth, 24, mode == i, inside(mx, my, 8 + i * (tabWidth + 4), 38, tabWidth, 24));
        if (model.state.has("ticksLeft")) {
            long seconds = Math.max(0, model.state.get("ticksLeft").getAsLong()) / 20;
            long day = model.state.has("epoch") ? model.state.get("epoch").getAsLong() + 1 : 1;
            int x = 16 + 4 * (tabWidth + 4);
            text(g, "第 " + day + " 日 · 刷新 " + String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60), x, 46, 0xFFAFC4C1, sliderX() - x - 10);
        }
        button(g,"任务",8+3*(tabWidth+4),38,tabWidth,24,false,inside(mx,my,8+3*(tabWidth+4),38,tabWidth,24));
        text(g, "格子 " + model.cellSize, sliderX(), 35, 0xFFC4C7CD, SLIDER_WIDTH);
        g.fill(sliderX(), 53, sliderX() + SLIDER_WIDTH, 56, 0xFF55595D);
        int knob = sliderX() + (model.cellSize - ClientCatalog.MIN_CELL) * SLIDER_WIDTH / (ClientCatalog.MAX_CELL - ClientCatalog.MIN_CELL);
        g.fill(knob - 2, 49, knob + 3, 61, 0xFF79D9C2);
        int sx = sidebar() + 12;
        g.fill(sx, 72, width - 12, 96, searchFocused ? 0xD7333A3E : 0xAC252A2D);
        text(g, search.isEmpty() && !searchFocused ? "搜索物品名 / ID" : search + (searchFocused ? "_" : ""), sx + 6, 79, 0xFFDDDEE0, width - sx - 24);
        g.fill(6, 64, sidebar() + 6, height - FOOTER, 0xA017191D);
        var categories = categories(); int available = Math.max(1, (height - 130) / 26);
        categoryScroll = Math.clamp(categoryScroll, 0, Math.max(0, categories.size() - available));
        for (int i = categoryScroll; i < Math.min(categories.size(), categoryScroll + available); i++) {
            var c = categories.get(i); int y = 72 + (i - categoryScroll) * 26;
            boolean active = category.equals(c.id());
            g.fill(10, y, sidebar() + 2, y + 24, active ? 0xCC354D51 : inside(mx, my, 10, y, sidebar() - 8, 24) ? 0xA936393C : 0x45181818);
            if (!c.icon().isEmpty()) g.item(c.icon(), 13, y + 4);
            text(g, c.name(), c.icon().isEmpty() ? 16 : 34, y + 8, active ? 0xFFB3F2E5 : 0xFFD3D5D7, sidebar() - (c.icon().isEmpty() ? 20 : 38));
        }
        text(g, "岛等级 " + model.level(), 14, height - 57, 0xFF8DDAC8, sidebar() - 20);
        int rows = Math.max(1, (height - FOOTER - TOP) / rowHeight), first = scroll * columns, last = Math.min(visible.size(), first + rows * columns);
        g.enableScissor(left, TOP, width - 8, height - FOOTER);
        for (int index = first; index < last; index++) {
            var p = visible.get(index); int local = index - first;
            int x = left + local % columns * model.cellSize, y = TOP + local / columns * rowHeight, w = model.cellSize - 2, h = rowHeight - 4;
            boolean hot = inside(mx, my, x, y, w, h), locked = model.level() < p.level() || (!p.rarity().equals("common") && (!model.specialAllowed() || model.stock(p) <= 0));
            g.fill(x, y, x + w, y + h, hot ? 0xBB3C4145 : 0x731E2225);
            if (model.favorites.contains(p.id())) g.fill(x, y, x + w, y + 2, 0xFFEBC36C);
            if (hot) hovered = p;
            int icon = iconSize();
            g.pose().pushMatrix(); g.pose().translate(x + (w - icon) / 2f, y + 5); g.pose().scale(icon / 16f, icon / 16f); g.item(p.stack(), 0, 0); g.pose().popMatrix();
            String firstLine = font.plainSubstrByWidth(p.name(), w - 6), rest = p.name().substring(firstLine.length());
            int nameColor = cn.yumis.skyisland.core.RarityColors.color(p.rarity());
            text(g, firstLine, x + 3, y + icon + 10, nameColor, w - 6);
            if (!rest.isEmpty()) text(g, rest, x + 3, y + icon + 12 + font.lineHeight, nameColor, w - 6);
            int bps = model.bps(p), color = locked ? 0xFF777B81 : bps < 10000 ? 0xFF68DF93 : bps > 10000 ? 0xFFFF7A76 : 0xFFF4D67A;
            text(g, compact(model.price(p), w - 6), x + 3, y + h - font.lineHeight - 5, color, w - 6);
        }
        g.disableScissor();
        if (visible.isEmpty()) text(g, model.products.isEmpty() ? "正在同步商品资料…" : "此分类暂无商品；每日物资按队伍等级随机抽取", left + 8, TOP + 18, 0xFFA0A7AC, width - left - 24);
        g.fill(6, height - 35, width - 6, height - 5, 0xCF171C20);
        button(g, "卖出手持", sidebar() + 12, height - 31, 86, 24, false, inside(mx, my, sidebar() + 12, height - 31, 86, 24));
        button(g, "卖出背包材料", sidebar() + 102, height - 31, 120, 24, false, inside(mx, my, sidebar() + 102, height - 31, 120, 24));
        text(g, "A 收藏 · 左键详情 · 右键买1 · 滚轮翻页", sidebar() + 234, height - 23, 0xFFADB4BA, width - sidebar() - 252);
        if (detail != null) drawDetail(g, mx, my); else if (hovered != null) drawTooltip(g, mx, my, hovered);
    }
    private String compact(long value, int maxWidth) {
        long unit = value >= 100_000_000 ? 100_000_000 : value >= 10_000 ? 10_000 : 1;
        if (unit == 1) return Long.toString(value);
        String suffix = unit == 100_000_000 ? "亿" : "万";
        for (int decimals = 2; decimals >= 0; decimals--) {
            String label = String.format(Locale.ROOT, "%." + decimals + "f", value / (double) unit) + suffix;
            if (font.width(label) <= maxWidth) return label;
        }
        return Long.toString(value / unit) + suffix;
    }
    private static String rarity(String value) { return switch (value) { case "rare" -> "稀有"; case "epic" -> "史诗"; case "legendary" -> "传说"; default -> "普通"; }; }
    private void drawTooltip(GuiGraphicsExtractor g, int mx, int my, ClientCatalog.Product p) {
        List<Component> lines = new ArrayList<>(); lines.add(Component.literal(p.name()));
        lines.add(Component.literal("种类稀有度：" + rarity(p.rarity()) + " · 解锁等级 " + p.level()));
        if (!p.rarity().equals("common")) lines.add(Component.literal("本队今日剩余库存：" + model.stock(p)));
        lines.add(Component.literal("购买：" + Money.format(model.price(p))));
        lines.add(Component.literal("行情：" + String.format(Locale.ROOT, "%+.2f%%", (model.bps(p) - 10000) / 100.0)));
        model.tooltip(p.stack(), lines);
        int tw = Math.min(360, width - 20);
        var wrapped = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (Component line : lines) wrapped.addAll(font.split(line, tw - 16));
        int th = wrapped.size() * 12 + 14, x = Math.clamp(mx + 10, 8, Math.max(8, width - tw - 8)), y = Math.clamp(my + 12, 8, Math.max(8, height - th - 8));
        g.fill(x, y, x + tw, y + th, 0xF20B1115); g.fill(x, y, x + 2, y + th, 0xFF70CEB8);
        for (int i = 0; i < wrapped.size(); i++) g.text(font, wrapped.get(i), x + 8, y + 8 + i * 12, i == 0 ? 0xFFF4E7BA : 0xFFCDD4D9, false);
    }
    private void drawDetail(GuiGraphicsExtractor g, int mx, int my) {
        int w = Math.min(420, width - 24), h = Math.min(232, height - 20), x = (width - w) / 2, y = (height - h) / 2;
        g.fill(0, 0, width, height, 0x95000000); g.fill(x, y, x + w, y + h, 0xF2141A20); g.fill(x, y, x + w, y + 2, 0xFF77D6BF);
        g.pose().pushMatrix(); g.pose().translate(x + 14, y + 18); g.pose().scale(3f, 3f); g.item(detail.stack(), 0, 0); g.pose().popMatrix();
        text(g, detail.name(), x + 76, y + 20, cn.yumis.skyisland.core.RarityColors.color(detail.rarity()), w - 90);
        text(g, rarity(detail.rarity()) + " · 岛等级 " + detail.level(), x + 76, y + 40, 0xFFB2BBC7, w - 90);
        text(g, "每件 " + Money.format(model.price(detail)), x + 14, y + 78, 0xFF9BE2C7, w - 28);
        if (!detail.rarity().equals("common")) text(g, "队伍共享库存：" + model.stock(detail), x + 14, y + 97, 0xFFBBC7D3, w - 28);
        int[] amounts = {1, 16, 64};
        for (int i = 0; i < 3; i++) button(g, "×" + amounts[i], x + 14 + i * 72, y + h - 98, 66, 24, quantity == amounts[i], inside(mx, my, x + 14 + i * 72, y + h - 98, 66, 24));
        boolean can = model.level() >= detail.level() && (detail.rarity().equals("common") || model.specialAllowed() && model.stock(detail) >= quantity);
        text(g, "合计 " + Money.format(Math.multiplyExact(model.price(detail), quantity)), x + 14, y + h - 59, 0xFFFFD67B, w - 28);
        button(g, can ? "购买" : "等级或库存不足", x + 14, y + h - 34, w - 102, 24, false, inside(mx, my, x + 14, y + h - 34, w - 102, 24));
        button(g, "返回", x + w - 78, y + h - 34, 64, 24, false, inside(mx, my, x + w - 78, y + h - 34, 64, 24));
    }
    private void send(String action, String product, int count) { if (ClientPlayNetworking.canSend(ShopPayloads.Request.TYPE)) ClientPlayNetworking.send(new ShopPayloads.Request(action, product, count)); }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double x = e.x(), y = e.y(); layout();
        if (detail != null) {
            int w = Math.min(420, width - 24), h = Math.min(232, height - 20), dx = (width - w) / 2, dy = (height - h) / 2; int[] amounts = {1, 16, 64};
            for (int i = 0; i < 3; i++) if (inside(x, y, dx + 14 + i * 72, dy + h - 98, 66, 24)) { quantity = amounts[i]; return true; }
            if (inside(x, y, dx + w - 78, dy + h - 34, 64, 24)) { detail = null; return true; }
            if (inside(x, y, dx + 14, dy + h - 34, w - 102, 24)) { send("buy", detail.id(), quantity); return true; }
            return true;
        }
        int tabWidth = tabWidth();
        if(inside(x,y,8+3*(tabWidth+4),38,tabWidth,24)){net.minecraft.client.Minecraft.getInstance().gui.setScreen(new TaskScreen(model));return true;}
        for (int i = 0; i < 3; i++) if (inside(x, y, 8 + i * (tabWidth + 4), 38, tabWidth, 24)) { mode = i; invalidate(); return true; }
        if (inside(x, y, sliderX() - 4, 47, SLIDER_WIDTH + 8, 18)) { dragSlider = true; slider(x); return true; }
        searchFocused = inside(x, y, sidebar() + 12, 72, width - sidebar() - 24, 24); if (searchFocused) return true;
        var categories = categories(); int ci = (int) ((y - 72) / 26) + categoryScroll;
        if (inside(x, y, 10, 72, sidebar() - 8, Math.max(1, (height - 130) / 26) * 26) && ci >= 0 && ci < categories.size()) { category = categories.get(ci).id(); invalidate(); return true; }
        if (inside(x, y, sidebar() + 12, height - 31, 86, 24)) { send("sell_hand", "", 0); return true; }
        if (inside(x, y, sidebar() + 102, height - 31, 120, 24)) { send("sell_inventory", "", 0); return true; }
        if (hovered != null) { if (e.button() == 1) send("buy", hovered.id(), (e.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 64 : 1); else { detail = hovered; quantity = 1; } return true; }
        return super.mouseClicked(e, doubleClick);
    }
    private void slider(double x) { model.cellSize = Math.clamp(ClientCatalog.MIN_CELL + (int) Math.round((x - sliderX()) * (ClientCatalog.MAX_CELL - ClientCatalog.MIN_CELL) / SLIDER_WIDTH), ClientCatalog.MIN_CELL, ClientCatalog.MAX_CELL); scroll = 0; }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { if (dragSlider) { slider(e.x()); return true; } return super.mouseDragged(e, dx, dy); }
    @Override public boolean mouseReleased(MouseButtonEvent e) { if (dragSlider) { dragSlider = false; model.save(); return true; } return super.mouseReleased(e); }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) { if (detail != null) return true; if (x < sidebar() + 8) categoryScroll -= Math.signum(sy); else scroll -= Math.signum(sy) * 3; layout(); return true; }
    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_ESCAPE) { if (detail != null) { detail = null; return true; } if (searchFocused) { searchFocused = false; return true; } onClose(); return true; }
        if (searchFocused) { if (e.key() == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) { search = search.substring(0, search.offsetByCodePoints(search.length(), -1)); invalidate(); } return true; }
        if (e.key() == GLFW.GLFW_KEY_A && hovered != null) { model.favorite(hovered.id()); invalidate(); return true; }
        return super.keyPressed(e);
    }
    @Override public boolean charTyped(CharacterEvent e) { if (searchFocused && e.isAllowedChatCharacter() && search.length() < 80) { search += e.codepointAsString(); invalidate(); return true; } return super.charTyped(e); }
    @Override public void onClose() { model.save(); super.onClose(); }
}
