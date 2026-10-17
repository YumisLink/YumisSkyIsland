package cn.yumis.skyisland.core;

import java.math.BigDecimal;

/** Shop and tooltip copy for the four profession fruits. */
public final class FruitText {
    private FruitText() {}
    public static String effect(String profession) {
        return switch (profession) {
            case "blacksmith" -> "每个护甲 +0.05";
            case "farmer" -> "每个最大生命 +0.1";
            case "chef" -> "0.01 生命再生";
            case "hunter" -> "1%掠夺";
            case "miner" -> "每个挖掘速度 +2%";
            default -> "";
        };
    }
    public static String current(String profession, int used) {
        int count = Math.max(0, used);
        return switch (profession) {
            case "blacksmith" -> "当前护甲 +" + number(count * 0.05);
            case "farmer" -> "当前最大生命 +" + number(count * 0.1);
            case "chef" -> "当前 " + number(count * 0.01) + " 生命再生";
            case "hunter" -> "当前 " + count + "%掠夺";
            case "miner" -> "当前挖掘速度 +" + number(count * 2) + "%";
            default -> "";
        };
    }
    public static String line(String profession, int used) {
        String effect = effect(profession);
        return effect.isEmpty() ? "" : effect + " · " + current(profession, used);
    }
    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
