package cn.yumis.skyisland.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Sequence-deduplicated, fixed 200ms income windows, with an exact aggregate amount. */
public final class IncomeNotifications {
    public static final long WINDOW=200_000_000L,LIFETIME=1_800_000_000L;
    public record Gain(long start,BigInteger amount) {}
    private final ArrayList<Gain> active=new ArrayList<>();
    private long lastSequence=-1,pendingStart;
    private BigInteger pending=BigInteger.ZERO;
    public void clear(){active.clear();pending=BigInteger.ZERO;pendingStart=0;lastSequence=-1;}
    public void receive(long sequence,long amount,long now){
        if(sequence<=lastSequence||amount<=0)return;lastSequence=sequence;flush(now);
        if(pending.signum()==0)pendingStart=now;pending=pending.add(BigInteger.valueOf(amount));
    }
    private void flush(long now){
        if(pending.signum()>0&&now-pendingStart>=WINDOW){active.add(new Gain(pendingStart+WINDOW,pending));pending=BigInteger.ZERO;}
        active.removeIf(g->now-g.start()>=LIFETIME);
    }
    public List<Gain> visible(long now){flush(now);return List.copyOf(active);}
}
