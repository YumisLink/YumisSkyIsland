package cn.yumis.skyisland.core;

import java.util.function.DoubleSupplier;

/** Extra full copies. A bonus of 1 is one guaranteed extra copy; a fractional part is the chance of one more. */
public final class LootBonus {
    private LootBonus() {}

    public static int copies(double bonus, DoubleSupplier roll) {
        if (!(bonus > 0) || Double.isNaN(bonus)) return 1;
        int whole = (int) Math.floor(bonus);
        double fraction = bonus - whole;
        int extra = whole;
        if (fraction > 0 && roll.getAsDouble() < fraction) extra++;
        return 1 + extra;
    }
}
