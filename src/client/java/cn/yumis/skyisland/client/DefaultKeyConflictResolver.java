package cn.yumis.skyisland.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Resolve only the two vanilla defaults claimed by the personal panels. */
public final class DefaultKeyConflictResolver {
    private DefaultKeyConflictResolver() {}
    public static void resolve(Minecraft client) {
        boolean changed=unbind(client.options.keyFriends,KeyMapping.get("key.yumis_sky_island.accessories"),InputConstants.KEY_O);
        changed|=unbind(client.options.keySocialInteractions,KeyMapping.get("key.yumis_sky_island.professions"),InputConstants.KEY_P);
        if(changed){KeyMapping.resetMapping();client.options.save();}
    }
    private static boolean unbind(KeyMapping vanilla,KeyMapping panel,int code) {
        var key=InputConstants.Type.KEYSYM.getOrCreate(code);
        if(panel==null||!panel.matches(key)||!vanilla.matches(key))return false;
        vanilla.setKey(InputConstants.UNKNOWN);vanilla.setDown(false);
        while(vanilla.consumeClick()){}
        return true;
    }
}
