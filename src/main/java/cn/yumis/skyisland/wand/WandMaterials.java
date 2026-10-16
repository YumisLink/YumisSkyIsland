package cn.yumis.skyisland.wand;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.block.*;

/** One-level container access. Sparse slots and unrelated components are retained on write-back. */
final class WandMaterials {
    private record Source(ItemStack stack,Runnable flush) {}
    private final List<Source> sources=new ArrayList<>();
    private final boolean creative;
    WandMaterials(Player player,boolean copy) {
        creative=player.isCreative();
        for(int i=9;i<36;i++)addLoose(player,i,copy);
        for(int i=9;i<36;i++)addContainer(player,i,copy);
        for(int i=0;i<9;i++)addContainer(player,i,copy);
        addContainer(player,40,copy);
        for(int i=0;i<9;i++)addLoose(player,i,copy);
        addLoose(player,40,copy);
    }
    static boolean ordinary(ItemStack stack) {
        return !stack.isEmpty()&&stack.getComponentsPatch().isEmpty()&&stack.getItem() instanceof BlockItem item&&supported(item.getBlock());
    }
    static boolean supported(Block block) {
        return !block.defaultBlockState().hasBlockEntity()&&!(block instanceof EntityBlock)
                &&!(block instanceof BedBlock)&&!(block instanceof DoorBlock)&&!(block instanceof DoublePlantBlock)
                &&!block.defaultBlockState().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF);
    }
    private void addLoose(Player player,int slot,boolean copy) {
        ItemStack stack=player.getInventory().getItem(slot);
        if(ordinary(stack))sources.add(new Source(copy?stack.copy():stack,()->player.getInventory().setChanged()));
    }
    private void addContainer(Player player,int slot,boolean copy) {
        ItemStack parent=player.getInventory().getItem(slot);
        // Containers are sources, never build materials. No recursion into their children.
        if(parent.getItem() instanceof BlockItem item&&item.getBlock() instanceof ShulkerBoxBlock) {
            var contents=parent.get(DataComponents.CONTAINER);if(contents==null)return;
            List<ItemStack> children=new ArrayList<>(contents.allItemsCopyStream().toList());
            Runnable flush=copy?()->{}:()->{parent.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(children));player.getInventory().setChanged();};
            for(ItemStack child:children)if(ordinary(child))sources.add(new Source(child,flush));
        } else if(parent.getItem() instanceof BundleItem) {
            var contents=parent.get(DataComponents.BUNDLE_CONTENTS);if(contents==null)return;
            List<ItemStack> children=new ArrayList<>(contents.itemCopyStream().toList());
            Runnable flush=copy?()->{}:()->{parent.set(DataComponents.BUNDLE_CONTENTS,new BundleContents(children.stream().filter(s->!s.isEmpty()).map(ItemStackTemplate::fromNonEmptyStack).toList()));player.getInventory().setChanged();};
            for(ItemStack child:children)if(ordinary(child))sources.add(new Source(child,flush));
        }
    }
    int available(Item item) { return creative?Integer.MAX_VALUE:sources.stream().filter(s->s.stack.is(item)).mapToInt(s->s.stack.getCount()).sum(); }
    boolean take(Item item,int count) {
        if(creative)return true;
        if(available(item)<count)return false;
        for(Source source:sources)if(source.stack.is(item)&&!source.stack.isEmpty()) {
            int take=Math.min(count,source.stack.getCount());source.stack.shrink(take);source.flush.run();count-=take;if(count==0)break;
        }
        return true;
    }
    List<Item> choices(Player player) {
        LinkedHashSet<Item> selected=new LinkedHashSet<>();
        for(int i=0;i<9;i++){var stack=player.getInventory().getItem(i);if(ordinary(stack))selected.add(stack.getItem());}
        if(ordinary(player.getOffhandItem()))selected.add(player.getOffhandItem().getItem());
        return new ArrayList<>(selected);
    }
    Item fallback(net.minecraft.world.level.block.state.BlockState state,WandRules rules,int matching) {
        Item exact=state.getBlock().asItem();if(exact!=Items.AIR&&available(exact)>0)return exact;
        if(matching==1)for(Source source:sources) {
            if(source.stack.isEmpty()||!(source.stack.getItem() instanceof BlockItem block))continue;
            String a=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            String b=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString();
            if(rules.matches(a,b,1))return source.stack.getItem();
        }
        return exact;
    }
}
