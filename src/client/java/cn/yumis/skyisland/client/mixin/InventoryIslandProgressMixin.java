package cn.yumis.skyisland.client.mixin;

import cn.yumis.skyisland.client.ClientCatalog;
import cn.yumis.skyisland.client.SkyIslandClient;
import java.math.BigDecimal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inventory draws over the normal HUD, so extract its progress label after the inventory itself. */
@Mixin(InventoryScreen.class)
public abstract class InventoryIslandProgressMixin {
    @Inject(method="extractRenderState",at=@At("TAIL"))
    private void yumis$islandProgress(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta,CallbackInfo ci){
        ClientCatalog model=SkyIslandClient.CATALOG;
        if(model==null||!model.state.has("partyId")||model.state.get("partyId").getAsString().isEmpty()
                ||!model.state.has("currentLevelScoreScaled")||!model.state.has("nextLevelScoreScaled"))return;
        BigDecimal current=new BigDecimal(model.state.get("currentLevelScoreScaled").getAsString()).movePointLeft(4);
        BigDecimal next=new BigDecimal(model.state.get("nextLevelScoreScaled").getAsString()).movePointLeft(4);
        String label="岛屿等级 "+model.level()+"   "+current.stripTrailingZeros().toPlainString()+"/"+next.stripTrailingZeros().toPlainString();
        var font=Minecraft.getInstance().font;
        int x=graphics.guiWidth()-font.width(label)-12,y=graphics.guiHeight()-44;
        graphics.fill(x-6,y-4,graphics.guiWidth()-6,y+13,0xBE101010);
        graphics.text(font,label,x,y,0xFFE6F5FF,true);
    }
}
