package cn.yumis.skyisland.server;

/** Actual quick-transfer count passed from Slot to the stonecutter result slot. */
public interface StonecutTransferTracker {
    void yumis$transferred(net.minecraft.world.item.ItemStack output,int quantity);
}
