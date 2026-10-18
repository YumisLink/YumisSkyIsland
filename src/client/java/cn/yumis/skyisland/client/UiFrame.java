package cn.yumis.skyisland.client;

import net.minecraft.client.input.MouseButtonEvent;

/** Centers a smaller panel or enlarges spacing. Text stays at the native font size. */
public final class UiFrame {
    public final int ox, oy, width, height;
    public final float metric;

    private UiFrame(int ox, int oy, int width, int height, float metric) {
        this.ox = ox;
        this.oy = oy;
        this.width = width;
        this.height = height;
        this.metric = metric;
    }

    public static UiFrame of(int screenWidth, int screenHeight, String size) {
        if ("small".equals(size)) {
            int w = Math.min(screenWidth, Math.max(240, Math.round(screenWidth * 0.8f)));
            int h = Math.min(screenHeight, Math.max(180, Math.round(screenHeight * 0.8f)));
            return new UiFrame((screenWidth - w) / 2, (screenHeight - h) / 2, w, h, 0.8f);
        }
        if ("large".equals(size)) return new UiFrame(0, 0, screenWidth, screenHeight, 1.2f);
        return new UiFrame(0, 0, screenWidth, screenHeight, 1f);
    }

    public MouseButtonEvent move(MouseButtonEvent event) {
        if (ox == 0 && oy == 0) return event;
        return new MouseButtonEvent(event.x() - ox, event.y() - oy, event.buttonInfo());
    }
}
