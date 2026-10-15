package cn.yumis.skyisland.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.math.BigInteger;

/** Lower-left notices for purchases, medical fees and personal profession XP. */
public final class FloatingEventHud {
    private record Event(long started,String text,int color,String xpProfession,BigInteger xpAmount) {}
    private static final long LIFETIME=1_800_000_000L;
    private static final long XP_MERGE_WINDOW=200_000_000L;
    private final ArrayList<Event> events=new ArrayList<>();
    public void clear(){events.clear();}
    public void receive(String text,int color){events.add(new Event(System.nanoTime(),text,color,null,null));}
    public void receiveXp(String profession,BigInteger amount){
        long now=System.nanoTime();
        for(int i=events.size()-1;i>=0;i--){
            Event event=events.get(i);
            if(now-event.started>XP_MERGE_WINDOW)break;
            if(profession.equals(event.xpProfession)){
                BigInteger combined=event.xpAmount.add(amount);
                events.set(i,new Event(event.started,"+"+combined+" "+cn.yumis.skyisland.server.ProfessionService.name(profession)+"经验",event.color,profession,combined));
                return;
            }
        }
        events.add(new Event(now,"+"+amount+" "+cn.yumis.skyisland.server.ProfessionService.name(profession)+"经验",0xFF9DE5C7,profession,amount));
    }
    public void render(GuiGraphicsExtractor graphics){
        long now=System.nanoTime();events.removeIf(e->now-e.started>=LIFETIME);
        var font=Minecraft.getInstance().font;int index=0;
        for(int i=events.size()-1;i>=0;i--){Event event=events.get(i);
            double progress=Math.clamp((now-event.started)/(double)LIFETIME,0,1);
            int alpha=(int)(255*Math.min(1,(1-progress)*3));
            if(alpha<4)continue;
            int y=graphics.guiHeight()-78-index++*14-(int)(24*progress);
            graphics.text(font,event.text,12,y,(alpha<<24)|(event.color&0xFFFFFF),true);
        }
    }
}
