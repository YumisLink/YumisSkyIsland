package cn.yumis.skyisland.client;

import cn.yumis.skyisland.shop.QualityNames;
import java.awt.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/** Client-only frames. Reading quality never changes an item or its stacking components. */
public final class QualityFrames {
    private static final long PERIOD_NANOS = 4_000_000_000L;
    private static double phase;

    private QualityFrames() {}

    /** One clock sample for the entire GUI extraction, independent of world ticks and pauses. */
    public static void beginFrame() { phase = phaseAt(System.nanoTime()); }

    static double phaseAt(long nanos) {
        return Math.floorMod(nanos, PERIOD_NANOS) / (double) PERIOD_NANOS;
    }

    public static int grade(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        var tag = data.copyTag();
        int grade = tag.getIntOr("yumis_quality", 0);
        return grade >= 1 && grade <= 5 && !tag.getStringOr("yumis_profession", "").isBlank() ? grade : 0;
    }

    /** Bounds are the real slot/card bounds, in the caller's current GUI pose and scissor. */
    public static void draw(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int width, int height) {
        if (!SkyIslandClient.CATALOG.professionQualityFrames || width < 2 || height < 2) return;
        int grade = grade(stack);
        if (grade == 0) return;
        if (grade < 5) {
            g.outline(x, y, width, height, 0xFF000000 | QualityNames.color(grade));
            return;
        }
        double perimeter = 2.0 * (width + height);
        // Non-overlapping strips; corners belong to the horizontal edges.
        edge(g, x, y + 1, -(float) Math.PI / 2, width, 0, perimeter);
        edge(g, x + width - 1, y + 1, 0, height - 2, width + 1, perimeter);
        edge(g, x + width, y + height - 1, (float) Math.PI / 2, width, width + height, perimeter);
        edge(g, x + 1, y + height - 1, (float) Math.PI, height - 2, 2 * width + height + 1, perimeter);
    }

    /** A standalone icon has an 18x18 virtual slot. Cards call draw with their own bounds. */
    public static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        draw(g, stack, x - 1, y - 1, 18, 18);
        g.item(stack, x, y);
    }

    private static void edge(GuiGraphicsExtractor g, int x, int y, float rotation, int length,
                             int offset, double perimeter) {
        if (length <= 0) return;
        // At most about 28 quads per rainbow frame, batched by the native GUI pipeline.
        // Subdivide by hue, never by screen pixels or texture resolution.
        int segments = Math.min(length, Math.max(1, (int) Math.ceil(length * 24.0 / perimeter)));
        g.pose().pushMatrix();
        try {
            g.pose().translate(x, y);
            if (rotation != 0) g.pose().rotate(rotation);
            for (int i = 0; i < segments; i++) {
                int from = length * i / segments, to = length * (i + 1) / segments;
                g.fillGradient(0, from, 1, to, rainbow((offset + from) / perimeter), rainbow((offset + to) / perimeter));
            }
        } finally { g.pose().popMatrix(); }
    }

    private static int rainbow(double position) {
        double hue = position - phase;
        return Color.HSBtoRGB((float) (hue - Math.floor(hue)), 0.85f, 1f);
    }
}
