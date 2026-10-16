package cn.yumis.skyisland.server;

/** Set by the bobber mixin and read only at a real successful reel-in. */
public interface FishingMediumAccess {
    String yumis$medium();
    boolean yumis$biting();
}
