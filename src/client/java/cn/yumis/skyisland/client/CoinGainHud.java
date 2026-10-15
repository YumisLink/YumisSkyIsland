package cn.yumis.skyisland.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import cn.yumis.skyisland.core.IncomeNotifications;
import cn.yumis.skyisland.core.Money;
import java.text.DecimalFormat;

/** Fixed 200ms income windows, independent of wallet snapshot changes. */
public final class CoinGainHud {
    private final IncomeNotifications gains=new IncomeNotifications();
    private final DecimalFormat format=new DecimalFormat("#,##0");
    public void clear(){gains.clear();}
    public void receive(long sequence,long amount){gains.receive(sequence,amount,System.nanoTime());}
    public void render(GuiGraphicsExtractor graphics,int walletY){
        long now=System.nanoTime();var active=gains.visible(now);var font=Minecraft.getInstance().font;int index=0;
        for(int i=active.size()-1;i>=0;i--){var gain=active.get(i);double t=Math.clamp((now-gain.start())/(double)IncomeNotifications.LIFETIME,0,1);
            int alpha=(int)(255*Math.min(1,(1-t)*3));String text=(gain.amount().signum()<0?"− ":"+ ")+format.format(gain.amount().abs())+Money.currencyName();
            if(alpha<4)continue;
            int y=walletY-17-index++*13-(int)(24*t);graphics.text(font,text,graphics.guiWidth()-font.width(text)-12,y,(alpha<<24)|(gain.amount().signum()<0?0xFF8A81:0xFFDD74),true);
        }
    }
}
