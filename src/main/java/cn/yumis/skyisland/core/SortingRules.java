package cn.yumis.skyisland.core;

import java.util.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Pure planning: item IDs select candidates; complete components decide destination stacking. */
public final class SortingRules {
    public record Move(int sourceSlot, int count, List<ItemStack> target, int nextCursor) {}
    private SortingRules() {}
    public static boolean inRange(double ax,double ay,double az,double bx,double by,double bz) {
        double x=ax-bx,y=ay-by,z=az-bz;return x*x+y*y+z*z<=10000;
    }
    public static Move plan(List<ItemStack> source,List<ItemStack> target,int cursor,int limit) {
        Set<Item> kinds=new HashSet<>();for(var stack:target)if(!stack.isEmpty())kinds.add(stack.getItem());
        if(kinds.isEmpty()||source.isEmpty())return null;
        for(int n=0;n<source.size();n++){
            int slot=Math.floorMod(cursor+n,source.size());ItemStack from=source.get(slot);
            if(from.isEmpty()||!kinds.contains(from.getItem()))continue;
            List<ItemStack> result=new ArrayList<>();for(var stack:target)result.add(stack.copy());
            int remaining=Math.min(Math.clamp(limit,1,64),from.getCount()),requested=remaining;
            for(int pass=0;pass<2&&remaining>0;pass++)for(int i=0;i<result.size()&&remaining>0;i++){
                ItemStack into=result.get(i);
                if(pass==0&&(into.isEmpty()||!ItemStack.isSameItemSameComponents(from,into)))continue;
                if(pass==1&&!into.isEmpty())continue;
                int room=into.isEmpty()?Math.min(64,from.getMaxStackSize()):Math.max(0,Math.min(64,into.getMaxStackSize())-into.getCount());
                int amount=Math.min(room,remaining);if(amount==0)continue;
                if(into.isEmpty())result.set(i,from.copyWithCount(amount));else into.grow(amount);
                remaining-=amount;
            }
            if(remaining<requested)return new Move(slot,requested-remaining,List.copyOf(result),(slot+1)%source.size());
        }
        return null;
    }
}
