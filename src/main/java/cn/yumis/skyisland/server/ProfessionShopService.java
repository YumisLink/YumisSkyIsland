package cn.yumis.skyisland.server;

import cn.yumis.skyisland.core.AccessoryCraft;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.item.ModItems;
import com.google.gson.JsonArray;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Personal profession coins never share the party wallet. */
public final class ProfessionShopService {
    private final SkyServer runtime;
    public ProfessionShopService(SkyServer runtime) { this.runtime=runtime; }
    public void buy(ServerPlayer player, String product, int quantity) {
        runtime.parties.requireMember(player);
        if (runtime.inventoryLocks.contains(player.getUUID())) throw new IllegalArgumentException("背包交易尚未完成");
        String[] parts=product.split(":",2);
        if (parts.length!=2 || quantity<1 || quantity>64) throw new IllegalArgumentException("职业商品或购买数量无效");
        String profession=parts[0], kind=parts[1];
        boolean fruit=kind.equals("fruit") && ModItems.FRUITS.containsKey(profession);
        boolean bait=profession.equals("fisherman") && (kind.equals("lava_bait") || kind.equals("void_bait"));
        boolean aura=profession.equals("battle_master") && kind.equals("slaughter_aura");
        if (!fruit && !bait && !aura) throw new IllegalArgumentException("职业商品不存在");
        if ((bait || aura) && quantity!=1) throw new IllegalArgumentException("该解锁只需购买一次");
        var prices=runtime.config.object("products/profession_shop.toml");
        long unit=fruit?prices.get("fruitPrice").getAsLong():aura?prices.get("slaughterAuraPrice").getAsLong():kind.equals("lava_bait")?prices.get("lavaBaitPrice").getAsLong():prices.get("voidBaitPrice").getAsLong();
        if(unit<1)throw new IllegalStateException("职业商店价格配置无效");
        BigInteger cost=BigInteger.valueOf(unit).multiply(BigInteger.valueOf(quantity));
        IslandState.Person person=runtime.state().players.get(player.getUUID());
        if(bait){
            var professions=runtime.config.object("products/professions.toml");
            int required=kind.equals("lava_bait")?professions.get("lavaFishingLevel").getAsInt():professions.get("voidFishingLevel").getAsInt();
            int level=runtime.professions.level(person,"fisherman");
            if(level<required)throw new IllegalArgumentException((kind.equals("lava_bait")?"熔岩钓鱼":"虚空钓鱼")+"需要渔夫 "+required+" 级");
        }
        if(person==null||person.professionCoins.getOrDefault(profession,BigInteger.ZERO).compareTo(cost)<0)throw new IllegalArgumentException("个人职业积分不足");
        if(bait && person.unlockedAccessories.contains(kind))throw new IllegalArgumentException("该鱼饵已解锁");
        if(aura && person.unlockedAccessories.contains("slaughter_aura"))throw new IllegalArgumentException("杀戮光环已解锁");
        if (bait) {
            runtime.change(player, SyncImpact.person(player.getUUID(),"profession","accessories"),state->{var p=state.players.get(player.getUUID());p.professionCoins.merge(profession,cost.negate(),BigInteger::add);p.unlockedAccessories.add(kind);},()->SkyServer.tell(player,"已永久解锁"+(kind.equals("lava_bait")?"熔岩鱼饵":"虚空鱼饵")));
            return;
        }
        if (aura) {
            boolean[] enabled={false};
            runtime.change(player, SyncImpact.person(player.getUUID(),"profession","accessories"),state->{
                var current=state.players.get(player.getUUID());
                current.professionCoins.merge(profession,cost.negate(),BigInteger::add);
                current.unlockedAccessories.add("slaughter_aura");
                int slots=Math.clamp(current.accessorySlots,3,AccessoryService.CATALOG.size());
                if(AccessoryCraft.fits(current.activeAccessories,"slaughter_aura",slots)){AccessoryCraft.enable(current.activeAccessories,"slaughter_aura");enabled[0]=true;}
            },()->{AccessoryService.apply(player,runtime.state().players.get(player.getUUID()));SkyServer.tell(player,"已永久解锁杀戮光环"+(enabled[0]?"，已自动启用":"，激活栏位已满，可在饰品面板启用"));});
            return;
        }
        Item item=ModItems.FRUITS.get(profession);
        JsonArray inventory=runtime.economy.capture(player.getInventory());
        int left=quantity;
        for(int slot=0;slot<36 && left>0;slot++){
            ItemStack stack=runtime.economy.decode(inventory.get(slot));
            if(!stack.is(item)||stack.getCount()>=stack.getMaxStackSize())continue;
            int add=Math.min(left,stack.getMaxStackSize()-stack.getCount());stack.grow(add);left-=add;
            inventory.set(slot,runtime.economy.encode(stack));
        }
        for(int slot=0;slot<36 && left>0;slot++){
            if(!runtime.economy.decode(inventory.get(slot)).isEmpty())continue;
            int add=Math.min(left,item.getDefaultMaxStackSize());left-=add;
            inventory.set(slot,runtime.economy.encode(new ItemStack(item,add)));
        }
        if(left>0)throw new IllegalArgumentException("背包空间不足");
        runtime.trades.commitInventory(player, SyncImpact.person(player.getUUID(),"profession"),inventory,state->state.players.get(player.getUUID()).professionCoins.merge(profession,cost.negate(),BigInteger::add),()->SkyServer.tell(player,"购买"+ProfessionService.name(profession)+"果实 ×"+quantity));
    }
}
