package cn.yumis.skyisland.client;

import cn.yumis.skyisland.trash.TrashMenu;
import cn.yumis.skyisland.trash.TrashService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class TrashScreen extends AbstractContainerScreen<TrashMenu> {
    public static void register() { MenuScreens.register(TrashService.MENU, TrashScreen::new); }
    public TrashScreen(TrashMenu menu, Inventory inventory, Component title) { super(menu, inventory, title, TrashMenu.WIDTH, TrashMenu.HEIGHT); }

    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) { g.fill(0, 0, width, height, 0x88000000); }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int x, int y) {}
    @Override public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, UiTheme.PANEL);
        text(g, "自动垃圾桶  " + menu.sampleCount() + "/54", 8, 5, 0xFFECE8D3);
        text(g, "物品栏", 8, 128, 0xFFBBC7CC);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x, y = topPos + slot.y;
            g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF596064);
            g.fill(x, y, x + 16, y + 16, UiTheme.CARD);
        }
        text(g, "按物品ID匹配", 184, 7, 0xFFD0DEE3);
        button(g, 40, menu.enabled() ? "启用／点击暂停" : "暂停／点击启用", mouseX, mouseY);
        button(g, 64, "清空名单", mouseX, mouseY);
        var help = font.split(Component.literal("界面内暂停；关闭后删除匹配物品。\n\n样本不消耗，背包原物品也会删除。\n\n忽略品质、名称及附魔。"), 88);
        for (int i = 0; i < help.size(); i++) g.text(font, help.get(i), leftPos + 184, topPos + 92 + i * 10, 0xFFFFC18E, false);
        super.extractContents(g, mouseX, mouseY, delta);
        if (mouseY >= topPos + 18 && mouseY < topPos + 126 && mouseX >= leftPos + 8 && mouseX < leftPos + 170) {
            String tip = "仅匹配物品ID，忽略品质、名称及附魔。样本不消耗；关闭后背包原物品也会删除。拿着物品点击添加，空手点击移除，Shift点击背包快速添加。";
            var lines = font.split(Component.literal(tip), 220);
            int x = Math.clamp(mouseX + 12, 4, Math.max(4, width - 232));
            int y = Math.clamp(mouseY + 18, 4, Math.max(4, height - lines.size() * 11 - 12));
            g.fill(x, y, x + 228, y + lines.size() * 11 + 8, UiTheme.TOOLTIP);
            for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), x + 4, y + 4 + i * 11, 0xFFE8EEEA, false);
        }
    }

    private void text(GuiGraphicsExtractor g, String text, int x, int y, int color) { g.text(font, text, leftPos + x, topPos + y, color, false); }
    private void button(GuiGraphicsExtractor g, int y, String label, int mx, int my) {
        boolean hover = mx >= leftPos + 184 && mx < leftPos + 272 && my >= topPos + y && my < topPos + y + 18;
        g.fill(leftPos + 184, topPos + y, leftPos + 272, topPos + y + 18, hover ? UiTheme.HOVER : UiTheme.BUTTON);
        text(g, label, 184 + (88 - font.width(label)) / 2, y + 5, 0xFFE0E4E1);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double sx = event.x() - leftPos - 8, sy = event.y() - topPos - 18;
        if (sx >= 0 && sx < 162 && sy >= 0 && sy < 108 && (event.button() == 0 || event.button() == 1) && minecraft.gameMode != null) {
            int slot = (int) sy / 18 * 9 + (int) sx / 18;
            // Phantom slots reject vanilla pickup; forward a filter click explicitly without beginning a drag.
            minecraft.gameMode.handleContainerInput(menu.containerId, slot, event.button(), net.minecraft.world.inventory.ContainerInput.PICKUP, minecraft.player);
            return true;
        }
        if (event.button() == 0 && event.x() >= leftPos + 184 && event.x() < leftPos + 272 && minecraft.gameMode != null) {
            for (int i = 0; i < 2; i++) {
                int y = topPos + (i == 0 ? 40 : 64);
                if (event.y() >= y && event.y() < y + 18) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i); return true; }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
