package cn.yumis.skyisland.server;
import cn.yumis.skyisland.config.GameConfig;
import cn.yumis.skyisland.core.ProfessionRules;
import cn.yumis.skyisland.core.HarvestContext;
import cn.yumis.skyisland.core.HarvestRules;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.shop.ItemEconomy;
import cn.yumis.skyisland.shop.ItemQuality;
import cn.yumis.skyisland.item.ModItems;
import com.google.gson.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.math.BigInteger;

/** The result stack gets one pending roll before transfer; XP is awarded only on a successful result take. */
public final class ProfessionService {
    private record Prepared(String profession, int itemLevel, ItemStack template, boolean qualityAllowed) {}
    private record Produced(String profession, int itemLevel, ItemStack template, int quantity, boolean qualityAllowed) {}
    private static final class Click {
        final ServerPlayer player;
        final ItemStack[] inventory = new ItemStack[36];
        final ItemStack carried;
        final List<Produced> outputs = new ArrayList<>();
        Click(AbstractContainerMenu menu, ServerPlayer player) {
            this.player=player;
            for (int i=0;i<36;i++) inventory[i]=player.getInventory().getItem(i).copy();
            carried=menu.getCarried().copy();
        }
    }
    private final Map<Slot, Prepared> prepared = new WeakHashMap<>();
    private final Map<AbstractContainerMenu, Click> clicks = new IdentityHashMap<>();
    private final SkyServer runtime;
    private final Set<String> noXp = new HashSet<>();
    public ProfessionService(SkyServer runtime) {
        this.runtime = runtime;
        for (JsonElement e : runtime.config.object("products/professions.toml").getAsJsonArray("noXpItems")) noXp.add(e.getAsString());
        noXp.addAll(Set.of("minecraft:hay_block", "minecraft:bone_block", "minecraft:coal", "minecraft:redstone", "minecraft:lapis_lazuli", "minecraft:netherite_ingot", "minecraft:netherite_block", "minecraft:honeycomb_block"));
    }
    public int level(IslandState.Person person, String profession) {
        BigInteger xp = person == null ? BigInteger.ZERO : person.professionXp.getOrDefault(profession,BigInteger.ZERO);
        JsonObject config=runtime.config.object("products/professions.toml");
        JsonObject curve = config.getAsJsonObject(profession.equals("blacksmith")?"blacksmithXpCurve":"xpCurve");
        return ProfessionRules.level(xp,curve.get("perLevelBase").getAsLong(),curve.get("perLevelGrowth").getAsLong(),curve.get("post100Growth").getAsLong());
    }
    public int itemLevel(String id) {
        JsonObject source=runtime.config.object("products/professions.toml").getAsJsonObject("sourceItems");
        if(source!=null && source.has(id))return source.getAsJsonObject(id).get("itemLevel").getAsInt();
        JsonObject goods=runtime.config.object("products/goods.toml").getAsJsonObject("items").getAsJsonObject(id);
        if(goods!=null && goods.has("itemLevel"))return goods.get("itemLevel").getAsInt();
        GameConfig.Product product=runtime.config.products.get(id);
        return product==null ? 0 : ((product.level()+9)/10)*10;
    }
    private int roll(ServerPlayer player,int difference) {
        if(difference<0)return 0;
        JsonArray bands=runtime.config.object("products/professions.toml").getAsJsonArray("qualityDifference");
        JsonObject selected=bands.get(0).getAsJsonObject();
        for(JsonElement value:bands)if(difference>=value.getAsJsonObject().get("minDifference").getAsInt())selected=value.getAsJsonObject();
        return ProfessionRules.roll(difference,selected.get("silverBps").getAsInt(),selected.get("goldBps").getAsInt(),player.getRandom()::nextInt);
    }
    public void prepare(AbstractContainerMenu menu, int index, ServerPlayer player) {
        if (index < 0 || index >= menu.slots.size() || runtime.inventoryLocks.contains(player.getUUID())) return;
        Slot slot = menu.getSlot(index);
        if (!(slot instanceof ResultSlot || slot instanceof FurnaceResultSlot || menu instanceof StonecutterMenu && index == 1 || menu instanceof SmithingMenu smithing && index == smithing.getResultSlot())) return;
        ItemStack stack = slot.getItem(); if (stack.isEmpty()) return;
        runtime.progression.prepareProduction(slot);
        clicks.computeIfAbsent(menu,_ -> new Click(menu,player));
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); JsonObject goods = runtime.config.object("products/goods.toml").getAsJsonObject("items").getAsJsonObject(item);
        if (goods == null || player.isCreative()) return;
        if (slot.container instanceof ResultContainer results && results.getRecipeUsed() != null && results.getRecipeUsed().value() instanceof net.minecraft.world.item.crafting.RepairItemRecipe) return;
        prepared.put(slot,new Prepared(goods.get("profession").getAsString(),goods.get("itemLevel").getAsInt(),stack.copy(),goods.get("qualityAllowed").getAsBoolean()));
    }
    public void take(Slot slot, ServerPlayer player, int quantity) {
        if (quantity <= 0 || player.isCreative()) return;
        Prepared output = prepared.get(slot); if (output == null) return;
        for(var entry:clicks.entrySet())if(entry.getKey().slots.contains(slot)){
            entry.getValue().outputs.add(new Produced(output.profession(),output.itemLevel(),output.template(),quantity,output.qualityAllowed()));break;
        }
    }
    public void stonecutTake(Slot slot, ServerPlayer player) { Prepared p = prepared.get(slot); if (p != null) take(slot,player,p.template().getCount()); }
    public void afterClick(AbstractContainerMenu menu) {
        Click click=clicks.remove(menu);
        for(var slot:menu.slots)prepared.remove(slot);
        if(click==null)return;
        for(Produced output:click.outputs){
            int difference=level(runtime.state().players.get(click.player.getUUID()),output.profession())-output.itemLevel();
            if(output.qualityAllowed())replaceGained(menu,click,output,difference);
            String item=BuiltInRegistries.ITEM.getKey(output.template().getItem()).toString();
            if(!noXp.contains(item)){
                JsonObject goods=runtime.config.object("products/goods.toml").getAsJsonObject("items").getAsJsonObject(item);
                long base=goods!=null&&goods.has("baseXp")?goods.get("baseXp").getAsLong():runtime.config.object("products/professions.toml").getAsJsonObject("successfulCraftXp").get(output.profession()).getAsLong();
                award(click.player,output.profession(),BigInteger.valueOf(ProfessionRules.experiencePerUnit(base,difference)).multiply(BigInteger.valueOf(output.quantity())));
            }
        }
        menu.broadcastChanges();
    }
    public ItemStack automated(Container source, ItemStack outgoing) {
        // Result stacks stay ordinary until a real player's completed transfer is observed.
        return outgoing;
    }
    private void replaceGained(AbstractContainerMenu menu,Click click,Produced output,int difference){
        if(difference<0)return;
        List<ItemStack> premium=new ArrayList<>();
        int unbreakableBps=runtime.config.object("products/quality.toml").get("goldUnbreakableChanceBps").getAsInt();
        for(int i=0;i<output.quantity();i++){
            int grade=roll(click.player,difference);if(grade==0)continue;
            ItemStack part=output.template().copyWithCount(1);
            ItemQuality.apply(part,runtime.config.object("products/quality.toml"),grade,output.profession(),ProfessionRules.unbreakable(grade,unbreakableBps,click.player.getRandom()::nextInt));
            premium.add(part);
        }
        int remaining=premium.size(),removedFromCursor=0;
        for(int slot=0;slot<36 && remaining>0;slot++){
            ItemStack current=click.player.getInventory().getItem(slot);
            if(!ItemStack.isSameItemSameComponents(current,output.template()))continue;
            ItemStack before=click.inventory[slot];
            int original=ItemStack.isSameItemSameComponents(before,output.template())?before.getCount():0;
            int take=Math.min(remaining,Math.max(0,current.getCount()-original));
            if(take>0){current.shrink(take);remaining-=take;}
        }
        ItemStack carried=menu.getCarried();
        if(remaining>0 && ItemStack.isSameItemSameComponents(carried,output.template())){
            int original=ItemStack.isSameItemSameComponents(click.carried,output.template())?click.carried.getCount():0;
            removedFromCursor=Math.min(remaining,Math.max(0,carried.getCount()-original));
            if(removedFromCursor>0){carried.shrink(removedFromCursor);remaining-=removedFromCursor;}
        }
        int backed=premium.size()-remaining;
        if(backed<premium.size())cn.yumis.skyisland.YumisSkyIsland.LOGGER.warn("品质替换仅找到 {}/{} 件已交付的物品",backed,premium.size());
        for(int i=0;i<backed;i++){
            ItemStack part=premium.get(i);
            if(removedFromCursor>0 && menu.getCarried().isEmpty()) {menu.setCarried(part);removedFromCursor--;continue;}
            if(!click.player.getInventory().add(part))click.player.drop(part,false);
        }
    }
    private void award(ServerPlayer player,String profession,BigInteger amount){
        if(amount.signum()<=0)return;
        boolean fruit = ModItems.FRUITS.containsKey(profession) && player.getRandom().nextInt(10000) < runtime.config.object("products/profession_shop.toml").get("fruitDropBps").getAsInt();
        UUID id=player.getUUID();
        int multiplier=runtime.residents.xpBps(runtime.parties.active(player.getUUID()),profession);
        runtime.deferred(()->{
            int[] before={0};
            BigInteger[] awarded={amount};
            runtime.change(null,state->{
                IslandState.Person person=PartyService.person(state,player);before[0]=level(person,profession);
                BigInteger scaled=amount.multiply(BigInteger.valueOf(multiplier)).add(BigInteger.valueOf(person.residentXpRemainders.getOrDefault(profession,0)));BigInteger[] parts=scaled.divideAndRemainder(BigInteger.valueOf(10000));awarded[0]=parts[0];person.residentXpRemainders.put(profession,parts[1].intValueExact());
                person.professionXp.merge(profession,awarded[0],BigInteger::add);
                person.professionCoins.merge(profession,awarded[0],BigInteger::add);
            },()->{
                JsonObject event=new JsonObject();event.addProperty("profession",profession);event.addProperty("amount",awarded[0].toString());
                runtime.send(player,"profession_xp",event);
                int after=level(runtime.state().players.get(id),profession);
                if(profession.equals("fisherman"))FishingService.applyLevel(runtime,player);
                if(after>before[0])SkyServer.tell(player,name(profession)+"升至 "+after+" 级");
                if(fruit){ItemStack prize=new ItemStack(ModItems.FRUITS.get(profession));if(!player.getInventory().add(prize))player.drop(prize,false);SkyServer.tell(player,"获得"+name(profession)+"果实");}
            });
        });
    }
    public void fished(ServerPlayer player,Collection<ItemStack> loot){
        if(player.isCreative()||loot.isEmpty())return;
        if(loot.stream().anyMatch(stack->stack.is(ModItems.BIOME_TREASURE_BOX))){
            award(player,"fisherman",BigInteger.valueOf(12));
            return;
        }
        int itemLevel=0;List<ItemStack> parts=new ArrayList<>();
        for(ItemStack stack:loot)if(!stack.isEmpty()){
            String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();itemLevel=Math.max(itemLevel,itemLevel(id));
            parts.addAll(qualityParts(player,"fisherman",stack));
        }
        loot.clear();loot.addAll(parts);
        int difference=level(runtime.state().players.get(player.getUUID()),"fisherman")-itemLevel;
        long base=runtime.config.object("products/professions.toml").get("successfulFishingXp").getAsLong();
        award(player,"fisherman",BigInteger.valueOf(ProfessionRules.experiencePerUnit(base,difference)));
    }
    public static HarvestContext captureHarvest(BlockState original){
        int age = switch (original.getBlock()) {
            case CropBlock crop -> crop.getAge(original);
            case CocoaBlock ignored -> original.getValue(CocoaBlock.AGE);
            case NetherWartBlock ignored -> original.getValue(NetherWartBlock.AGE);
            case SweetBerryBushBlock ignored -> original.getValue(SweetBerryBushBlock.AGE);
            default -> -1;
        };
        return new HarvestContext(BuiltInRegistries.BLOCK.getKey(original.getBlock()).toString(),age);
    }
    public List<ItemStack> harvest(ServerPlayer player,HarvestContext sourceBlock,List<ItemStack> drops){
        if(player.isCreative())return drops;
        JsonObject source=runtime.config.object("products/professions.toml").getAsJsonObject("sourceItems");
        List<ItemStack> result=new ArrayList<>();
        for(ItemStack stack:drops){
            String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            JsonObject entry=source==null?null:source.getAsJsonObject(id);
            if(entry==null||!entry.get("profession").getAsString().equals("farmer")){result.add(stack);continue;}
            result.addAll(qualityParts(player,"farmer",stack));
            if(!HarvestRules.canAwardFarmerXp(sourceBlock,id))continue;
            int difference=level(runtime.state().players.get(player.getUUID()),"farmer")-entry.get("itemLevel").getAsInt();
            long base=runtime.config.object("products/professions.toml").getAsJsonObject("successfulCraftXp").get("farmer").getAsLong();
            award(player,"farmer",BigInteger.valueOf(ProfessionRules.experiencePerUnit(base,difference)).multiply(BigInteger.valueOf(stack.getCount())));
        }
        return result;
    }
    public List<ItemStack> qualityParts(ServerPlayer player,String profession,ItemStack stack){
        if(stack.isEmpty()||ItemEconomy.grade(stack)>0)return List.of(stack);
        int difference=level(runtime.state().players.get(player.getUUID()),profession)-itemLevel(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if(difference<0)return List.of(stack);
        List<ItemStack> result=new ArrayList<>();
        int unbreakableBps=runtime.config.object("products/quality.toml").get("goldUnbreakableChanceBps").getAsInt();
        for(int i=0;i<stack.getCount();i++){
            ItemStack part=stack.copyWithCount(1);int grade=roll(player,difference);
            if(grade>0)ItemQuality.apply(part,runtime.config.object("products/quality.toml"),grade,profession,ProfessionRules.unbreakable(grade,unbreakableBps,player.getRandom()::nextInt));
            result.add(part);
        }
        return result;
    }
    public boolean hunterMob(String entityId){JsonObject mobs=runtime.config.object("products/professions.toml").getAsJsonObject("hunterMobs");return mobs!=null&&mobs.has(entityId);}
    /** One fractional roll per kill; vanilla loot-table outputs share the same multiplier. */
    public int hunterLootCopies(ServerPlayer player) {
        IslandState.Person person=runtime.state().players.get(player.getUUID());
        int fruits=person==null?0:Math.clamp(person.consumedFruits.getOrDefault("hunter",0),0,100);
        double bonus=fruits*0.01;
        int whole=(int)Math.floor(bonus);
        return 1+whole+(player.getRandom().nextDouble()<bonus-whole?1:0);
    }
    public void hunterKill(ServerPlayer player,String entityId){
        if(player.isCreative()||!hunterMob(entityId))return;
        int mobLevel=runtime.config.object("products/professions.toml").getAsJsonObject("hunterMobs").getAsJsonObject(entityId).get("itemLevel").getAsInt();
        int difference=level(runtime.state().players.get(player.getUUID()),"hunter")-mobLevel;
        long base=runtime.config.object("products/professions.toml").get("successfulHunterKillXp").getAsLong();
        award(player,"hunter",BigInteger.valueOf(ProfessionRules.experiencePerUnit(base,difference)));
    }
    public static String name(String profession) { return switch (profession) { case "fisherman" -> "渔夫"; case "blacksmith" -> "铁匠"; case "chef" -> "厨师"; case "farmer" -> "农夫"; case "hunter" -> "猎人"; default -> profession; }; }
    public static double miningBonus(ItemStack stack, GameConfig config) { return stack.has(DataComponents.TOOL) ? config.object("products/quality.toml").getAsJsonArray("grades").get(ItemEconomy.grade(stack)).getAsJsonObject().get("attributeBonusBps").getAsInt()/10000.0 : 0; }
}
