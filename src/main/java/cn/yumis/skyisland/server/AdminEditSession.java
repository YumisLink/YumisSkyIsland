package cn.yumis.skyisland.server;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** One session gate for every current and future administrator editor. */
public final class AdminEditSession {
    private final SkyServer rt;
    private final Set<UUID> enabled=new HashSet<>();
    public AdminEditSession(SkyServer rt){this.rt=rt;}
    public boolean enabled(ServerPlayer player){if(!Protection.admin(player))enabled.remove(player.getUUID());return enabled.contains(player.getUUID());}
    public void require(ServerPlayer player){if(!Protection.admin(player))throw new IllegalArgumentException("仅管理员可编辑配置");if(!enabled(player))throw new IllegalArgumentException("请先使用 /is admin edit on 开启编辑模式");}
    public void forget(ServerPlayer player){enabled.remove(player.getUUID());}
    public void set(ServerPlayer player,boolean on){
        if(!Protection.admin(player))throw new IllegalArgumentException("仅管理员可开启编辑模式");
        if(on)enabled.add(player.getUUID());else enabled.remove(player.getUUID());
        rt.sendState(player);
        SkyServer.tell(player,on?"管理员编辑模式已开启；Ctrl＋】打开实时配置，商店和任务可直接编辑":"管理员编辑模式已关闭");
    }
}
