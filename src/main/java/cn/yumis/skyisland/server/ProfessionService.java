package cn.yumis.skyisland.server;
import cn.yumis.skyisland.config.GameConfig;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.shop.ItemEconomy;
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
import java.util.*;

/** The result stack gets one pending roll before transfer; XP is awarded only on a successful result take. */
public final class ProfessionService {
    private record Prepared(ItemStack identity, String profession, int quantity, ItemStack ordinary, ItemStack premium) {}
    private final Map<Slot, Prepared> prepared = new WeakHashMap<>();
    private final SkyServer runtime;
    private final Set<String> noXp = new HashSet<>();
    public ProfessionService(SkyServer runtime) {
        this.runtime = runtime;
        for (JsonElement e : runtime.config.object("products/professions.toml").getAsJsonArray("noXpItems")) noXp.add(e.getAsString());
        noXp.addAll(Set.of("minecraft:hay_block", "minecraft:bone_block", "minecraft:coal", "minecraft:redstone", "minecraft:lapis_lazuli", "minecraft:netherite_ingot", "minecraft:netherite_block", "minecraft:honeycomb_block"));
    }
    public int level(IslandState.Person person, String profession) {
        long xp = person == null ? 0 : person.professionXp.getOrDefault(profession,0L); JsonObject curve = runtime.config.object("products/professions.toml").getAsJsonObject("xpCurve");
        int result = 0; long required = 0;
        for (int level = 1; level <= curve.get("maxLevel").getAsInt(); level++) { required += curve.get("perLevelBase").getAsLong() + curve.get("perLevelGrowth").getAsLong() * (level - 1); if (xp < required) break; result = level; }
        return result;
    }
    public void prepare(AbstractContainerMenu menu, int index, ServerPlayer player) {
        if (index < 0 || index >= menu.slots.size() || runtime.inventoryLocks.contains(player.getUUID())) return;
        Slot slot = menu.getSlot(index);
        if (!(slot instanceof ResultSlot || slot instanceof FurnaceResultSlot || menu instanceof StonecutterMenu && index == 1)) return;
        ItemStack stack = slot.getItem(); if (stack.isEmpty()) return;
        runtime.progression.prepareProduction(slot);
        Prepared old = prepared.get(slot); if (old != null && old.identity() == stack) { copyQuality(old.premium(),stack); return; }
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); JsonObject goods = runtime.config.object("products/goods.toml").getAsJsonObject("items").getAsJsonObject(item);
        if (goods == null || noXp.contains(item) || player.isCreative()) return;
        if (slot.container instanceof ResultContainer results && results.getRecipeUsed() != null && results.getRecipeUsed().value() instanceof net.minecraft.world.item.crafting.RepairItemRecipe) return;
        String profession = goods.get("profession").getAsString(); int level = level(runtime.state().players.get(player.getUUID()), profession);
        ItemStack ordinary = stack.copy();
        if (!goods.get("qualityAllowed").getAsBoolean() || stack.is(Items.DIAMOND_PICKAXE) && level < runtime.config.object("products/professions.toml").get("diamondPickaxeMinimumQualityLevel").getAsInt()) { prepared.put(slot,new Prepared(stack,profession,stack.getCount(),ordinary,stack.copy())); return; }
        JsonArray bands = runtime.config.object("products/professions.toml").getAsJsonArray("qualityChanceBands"); JsonArray weights = bands.get(0).getAsJsonObject().getAsJsonArray("weights");
        for (JsonElement e : bands) if (e.getAsJsonObject().get("minLevel").getAsInt() <= level) weights = e.getAsJsonObject().getAsJsonArray("weights");
        int total = 0; for (JsonElement w : weights) total += w.getAsInt(); int roll = player.getRandom().nextInt(total), grade = 0;
        for (int i = 0; i < weights.size(); i++) { roll -= weights.get(i).getAsInt(); if (roll < 0) { grade = i; break; } }
        if (grade == 0) { prepared.put(slot,new Prepared(stack,profession,stack.getCount(),ordinary,stack.copy())); return; }
        cn.yumis.skyisland.shop.ItemQuality.apply(stack,runtime.config.object("products/quality.toml"),grade,profession);
        prepared.put(slot,new Prepared(stack,profession,stack.getCount(),ordinary,stack.copy()));slot.setChanged();
    }
    public void take(Slot slot, ServerPlayer player, int quantity) {
        if (quantity <= 0 || player.isCreative()) return;
        Prepared output = prepared.remove(slot); if (output == null) return;
        if (slot.container instanceof AbstractFurnaceBlockEntity && !output.identity().isEmpty()) copyQuality(output.ordinary(),output.identity());
        long base = runtime.config.object("products/professions.toml").getAsJsonObject("successfulCraftXp").get(output.profession()).getAsLong();
        long amount = Math.multiplyExact(base, Math.min(64,quantity));
        runtime.deferred(() -> {
            IslandState.Person p = PartyService.person(runtime.state(),player); int old = level(p,output.profession());
            p.professionXp.merge(output.profession(),amount,(a,b) -> Math.min(1_000_000_000L,a+b));
            int next = level(p,output.profession()); if (next > old) SkyServer.tell(player, name(output.profession()) + "升至 " + next + " 级");
        });
    }
    public void stonecutTake(Slot slot, ServerPlayer player) { Prepared p = prepared.get(slot); if (p != null) take(slot,player,p.quantity()); }
    public void fished(ServerPlayer player){
        if(player.isCreative()||runtime.parties.active(player.getUUID())==null)return;
        long xp=runtime.config.object("products/professions.toml").get("successfulFishingXp").getAsLong();
        if(xp<0||xp>1_000_000_000L)throw new IllegalArgumentException("成功钓鱼经验必须为0～10亿");
        if(xp==0)return;UUID id=player.getUUID();
        runtime.deferred(()->runtime.change(null,state->{var person=state.players.get(id);if(person!=null)person.professionXp.merge("fisherman",xp,(a,b)->Math.min(1_000_000_000L,a+b));},()->{}));
    }
    private static void copyQuality(ItemStack from,ItemStack to) {
        var data=from.get(DataComponents.CUSTOM_DATA); if(data==null)to.remove(DataComponents.CUSTOM_DATA);else to.set(DataComponents.CUSTOM_DATA,data);
        var durability=from.get(DataComponents.MAX_DAMAGE); if(durability==null)to.remove(DataComponents.MAX_DAMAGE);else to.set(DataComponents.MAX_DAMAGE,durability);
        var attributes=from.get(DataComponents.ATTRIBUTE_MODIFIERS);if(attributes==null)to.remove(DataComponents.ATTRIBUTE_MODIFIERS);else to.set(DataComponents.ATTRIBUTE_MODIFIERS,attributes);
    }
    public void afterClick(AbstractContainerMenu menu) {
        for(var slot:menu.slots){var p=prepared.get(slot);if(p!=null && slot.container instanceof AbstractFurnaceBlockEntity && slot.getItem()==p.identity())copyQuality(p.ordinary(),p.identity());}
    }
    public ItemStack automated(Container source, ItemStack outgoing) {
        if (!(source instanceof AbstractFurnaceBlockEntity)) return outgoing;
        for (var e : prepared.entrySet()) if (e.getKey().container == source && ItemStack.isSameItem(outgoing,e.getValue().ordinary())) {
            ItemStack result = e.getValue().ordinary().copyWithCount(outgoing.getCount()); result.setDamageValue(outgoing.getDamageValue()); return result;
        }
        return outgoing;
    }
    public static String name(String profession) { return switch (profession) { case "fisherman" -> "渔夫"; case "blacksmith" -> "铁匠"; case "stonemason" -> "石匠"; case "artisan" -> "工匠"; case "chef" -> "厨师"; case "farmer" -> "农夫"; case "hunter" -> "猎人"; default -> profession; }; }
    public static double miningBonus(ItemStack stack, GameConfig config) { return stack.has(DataComponents.TOOL) ? Math.clamp(config.object("products/quality.toml").getAsJsonArray("grades").get(ItemEconomy.grade(stack)).getAsJsonObject().get("attackAndMiningBonusBps").getAsInt(),0,1000)/10000.0 : 0; }
}
