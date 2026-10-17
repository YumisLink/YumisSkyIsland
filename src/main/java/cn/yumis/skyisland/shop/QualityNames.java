package cn.yumis.skyisland.shop;

import java.awt.Color;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Display colors for the six production grades. Rainbow animation is rebuilt by the client and is not stored. */
public final class QualityNames {
    private static final int[] COLORS = {0, 0xB87333, 0xD8D8D8, 0xFFD54A, 0x3FE0CB, 0xFFFFFF};
    private QualityNames() {}

    public static int color(int grade) {
        return grade < 0 || grade >= COLORS.length ? 0xFFFFFF : COLORS[grade];
    }

    public static Component stars(int grade) {
        if (grade <= 0) return Component.empty();
        return Component.literal(" " + "★".repeat(grade)).withColor(color(grade));
    }

    public static Component rainbow(String text, int shift) {
        MutableComponent line = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            float hue = ((i * 24 + shift) % 360) / 360f;
            int rgb = Color.HSBtoRGB(hue, 0.85f, 1f) & 0xFFFFFF;
            line.append(Component.literal(String.valueOf(text.charAt(i))).withColor(rgb));
        }
        return line;
    }
}
