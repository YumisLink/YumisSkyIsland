package cn.yumis.skyisland.prefab;

import com.google.gson.*;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Normalized, immutable template; air outside selected regions is never implicit excavation. */
public final class PrefabTemplate {
    public record Ingredient(String item,int count) {}
    public record Cell(BlockPos pos,BlockState state,CompoundTag nbt,int stage,List<Ingredient> ingredients) {}
    public record AirRun(int x,int y,int z,int length) {}
    public final List<Cell> cells;
    public final List<AirRun> air;
    public final List<CompoundTag> entities;
    public final int width,height,depth;
    public final String revision;
    public final Map<String,Long> materials;
    public final List<Cell> fluids;
    public final long[] airPrefix;
    public PrefabTemplate(JsonObject o,String revision,PrefabRules rules) {
        this.revision=revision;JsonArray size=o.getAsJsonArray("size");width=size.get(0).getAsInt();height=size.get(1).getAsInt();depth=size.get(2).getAsInt();
        if(width<1||height<1||depth<1||(long)width*height*depth>rules.maximumVolume())throw new IllegalArgumentException("建筑体积超出限制");
        List<BlockState> palette=new ArrayList<>();
        try {for(var value:o.getAsJsonArray("palette")) {
            BlockState state=BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK,value.getAsString(),true).blockState();
            String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            if(id.contains("command_block")||Set.of("structure_block","structure_void","jigsaw","barrier","light","end_portal","end_gateway","nether_portal","spawner","trial_spawner","bedrock").contains(id))
                throw new IllegalArgumentException("模板包含禁止自动施工方块："+id);
            palette.add(state);
        }}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new IllegalArgumentException("模板包含26.2未知方块状态："+e.getMessage(),e);}
        List<Cell> list=new ArrayList<>();Set<Long> used=new HashSet<>();
        for(var value:o.getAsJsonArray("blocks")) {
            JsonArray row=value.getAsJsonArray();BlockPos p=new BlockPos(row.get(0).getAsInt(),row.get(1).getAsInt(),row.get(2).getAsInt());check(p);
            if(!used.add(p.asLong()))throw new IllegalArgumentException("模板坐标重复");
            BlockState state=palette.get(row.get(3).getAsInt());if(state.isAir())throw new IllegalArgumentException("空气必须使用独立留空范围");
            CompoundTag nbt=null;
            try {if(row.size()>4&&!row.get(4).isJsonNull()&&!row.get(4).getAsString().isBlank())nbt=TagParser.parseCompoundFully(row.get(4).getAsString());}
            catch(Exception e){throw new IllegalArgumentException("方块实体数据无效",e);}
            if(nbt!=null){for(String key:List.of("LootTable","loot_table","LootTableSeed","Command","LastOutput","RecipesUsed","last_output","command"))nbt.remove(key);}
            List<Ingredient> ingredients=ingredients(state);
            if(row.size()>6)for(var entry:row.get(6).getAsJsonArray()){var a=entry.getAsJsonArray();ingredients.add(new Ingredient(a.get(0).getAsString(),a.get(1).getAsInt()));}
            list.add(new Cell(p,state,nbt,row.size()>5&&row.get(5).getAsInt()>=0?row.get(5).getAsInt():stage(state),List.copyOf(ingredients)));
        }
        if(list.isEmpty()||list.size()>rules.maximumBlocks())throw new IllegalArgumentException("模板方块数超出限制或为空");
        list.sort(Comparator.comparingInt(Cell::stage).thenComparingInt(c->c.pos.getY()).thenComparingInt(c->c.pos.getZ()).thenComparingInt(c->c.pos.getX()));cells=List.copyOf(list);fluids=cells.stream().filter(c->!c.state.getFluidState().isEmpty()).toList();
        List<AirRun> runs=new ArrayList<>();long totalAir=0;
        if(o.has("air"))for(var value:o.getAsJsonArray("air")){var a=value.getAsJsonArray();var r=new AirRun(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),a.get(3).getAsInt());
            check(new BlockPos(r.x,r.y,r.z));check(new BlockPos(Math.addExact(r.x,r.length-1),r.y,r.z));if(r.length<1)throw new IllegalArgumentException("留空范围无效");runs.add(r);totalAir+=r.length;}
        if(totalAir+cells.size()>rules.maximumVolume())throw new IllegalArgumentException("模板范围超出限制");air=List.copyOf(runs);airPrefix=new long[air.size()+1];for(int i=0;i<air.size();i++)airPrefix[i+1]=airPrefix[i]+air.get(i).length;
        List<CompoundTag> es=new ArrayList<>();
        if(o.has("entities"))for(var value:o.getAsJsonArray("entities"))try{
            CompoundTag tag=TagParser.parseCompoundFully(value.getAsString());String id=tag.getStringOr("id","");
            if(!Set.of("minecraft:minecart","minecraft:hopper_minecart","minecraft:chest_minecart","minecraft:boat","minecraft:oak_boat","minecraft:item_frame","minecraft:glow_item_frame","minecraft:armor_stand","minecraft:painting").contains(id))continue;
            var position=tag.getListOrEmpty("Pos");if(position.size()!=3)throw new IllegalArgumentException("装配实体缺少位置");int[] bounds={width,height,depth};for(int axis=0;axis<3;axis++){double coordinate=position.getDoubleOr(axis,Double.NaN);if(!Double.isFinite(coordinate)||coordinate< -1||coordinate>bounds[axis]+1)throw new IllegalArgumentException("装配实体位置越界");}
            for(String key:List.of("UUID","UUIDMost","UUIDLeast","Passengers","Leash","Items","Item","ArmorItems","HandItems","equipment","Equipment","body_armor_item","LootTable","loot_table","Invulnerable","Fixed","Tags"))tag.remove(key);es.add(tag);
        }catch(Exception e){throw new IllegalArgumentException("装配实体数据无效",e);}
        if(es.size()>512)throw new IllegalArgumentException("装配实体过多");entities=List.copyOf(es);
        Map<String,Long> tally=new TreeMap<>();for(var cell:cells)for(var item:cell.ingredients)tally.merge(item.item,(long)item.count,Long::sum);
        for(var entity:entities)tally.merge(entityItem(entity),1L,Long::sum);materials=Collections.unmodifiableMap(new LinkedHashMap<>(tally));
    }
    private void check(BlockPos p){if(p.getX()<0||p.getX()>=width||p.getY()<0||p.getY()>=height||p.getZ()<0||p.getZ()>=depth)throw new IllegalArgumentException("模板位置越界");}
    public BlockPos rotate(BlockPos p,int rotation){return switch(Math.floorMod(rotation,4)){case 1->new BlockPos(depth-1-p.getZ(),p.getY(),p.getX());case 2->new BlockPos(width-1-p.getX(),p.getY(),depth-1-p.getZ());case 3->new BlockPos(p.getZ(),p.getY(),width-1-p.getX());default->p;};}
    public static Rotation rotation(int rotation){return Rotation.values()[Math.floorMod(rotation,4)];}
    public static String entityItem(CompoundTag tag){String id=tag.getStringOr("id","");return id.equals("minecraft:boat")?"minecraft:oak_boat":id;}
    public static int stage(BlockState s) {
        Block b=s.getBlock();String id=BuiltInRegistries.BLOCK.getKey(b).getPath();
        if(!s.getFluidState().isEmpty()&&(b instanceof LiquidBlock||b==Blocks.BUBBLE_COLUMN||s.hasProperty(BlockStateProperties.WATERLOGGED)&&s.getValue(BlockStateProperties.WATERLOGGED)))return 3;
        if(b==Blocks.FIRE||b==Blocks.SOUL_FIRE)return 6;
        if(b instanceof PistonBaseBlock)return 0;
        if(b instanceof ObserverBlock||b instanceof RedStoneWireBlock||b instanceof DiodeBlock||b instanceof RedstoneTorchBlock||id.contains("pressure_plate")||id.equals("lever")||id.endsWith("_button")||id.equals("redstone_block"))return 4;
        if(b==Blocks.TNT)return 6;
        if(b==Blocks.PISTON_HEAD)return 1;
        if(b instanceof BaseRailBlock)return 2;
        if(s.hasBlockEntity()||b instanceof BushBlock||b instanceof LadderBlock||b instanceof TorchBlock||b instanceof LanternBlock||b instanceof DoorBlock||b instanceof BedBlock||id.endsWith("_carpet")||id.contains("tripwire"))return 1;
        return 0;
    }
    public static List<Ingredient> ingredients(BlockState state) {
        List<Ingredient> out=new ArrayList<>();Item item=state.getBlock().asItem();int count=1;
        if(state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)&&state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER)count=0;
        if(state.hasProperty(BlockStateProperties.BED_PART)&&state.getValue(BlockStateProperties.BED_PART)==BedPart.HEAD)count=0;
        if(state.hasProperty(BlockStateProperties.SLAB_TYPE)&&state.getValue(BlockStateProperties.SLAB_TYPE)==SlabType.DOUBLE)count=2;
        if(state.hasProperty(BlockStateProperties.LAYERS))count=state.getValue(BlockStateProperties.LAYERS);
        if(state.getBlock()==Blocks.PISTON_HEAD){count=0;}
        else if(state.getBlock()==Blocks.WATER||state.getBlock()==Blocks.BUBBLE_COLUMN){item=Items.WATER_BUCKET;count=1;}
        else if(state.getBlock()==Blocks.LAVA){item=Items.LAVA_BUCKET;count=1;}
        else if(state.getBlock()==Blocks.FIRE||state.getBlock()==Blocks.SOUL_FIRE){item=Items.FIRE_CHARGE;count=1;}
        else if(state.getBlock()==Blocks.FARMLAND||state.getBlock()==Blocks.DIRT_PATH)item=Items.DIRT;
        else if(state.getBlock() instanceof FlowerPotBlock pot&&pot.getPotted()!=Blocks.AIR){out.add(new Ingredient("minecraft:flower_pot",1));item=pot.getPotted().asItem();}
        else if(state.getBlock()==Blocks.WHEAT)item=Items.WHEAT_SEEDS;
        else if(state.getBlock()==Blocks.BEETROOTS)item=Items.BEETROOT_SEEDS;
        else if(state.getBlock()==Blocks.CARROTS)item=Items.CARROT;
        else if(state.getBlock()==Blocks.POTATOES)item=Items.POTATO;
        else if(state.getBlock()==Blocks.KELP_PLANT)item=Items.KELP;
        else if(state.getBlock()==Blocks.TWISTING_VINES_PLANT)item=Items.TWISTING_VINES;
        else if(state.getBlock()==Blocks.WEEPING_VINES_PLANT)item=Items.WEEPING_VINES;
        else if(state.getBlock()==Blocks.CAVE_VINES||state.getBlock()==Blocks.CAVE_VINES_PLANT)item=Items.GLOW_BERRIES;
        else if(state.getBlock()==Blocks.SWEET_BERRY_BUSH)item=Items.SWEET_BERRIES;
        else if(state.getBlock()==Blocks.MELON_STEM||state.getBlock()==Blocks.ATTACHED_MELON_STEM)item=Items.MELON_SEEDS;
        else if(state.getBlock()==Blocks.PUMPKIN_STEM||state.getBlock()==Blocks.ATTACHED_PUMPKIN_STEM)item=Items.PUMPKIN_SEEDS;
        else if(state.getBlock()==Blocks.WATER_CAULDRON){item=Items.CAULDRON;out.add(new Ingredient("minecraft:water_bucket",1));}
        else if(state.getBlock()==Blocks.LAVA_CAULDRON){item=Items.CAULDRON;out.add(new Ingredient("minecraft:lava_bucket",1));}
        else if(state.getBlock()==Blocks.POWDER_SNOW_CAULDRON){item=Items.CAULDRON;out.add(new Ingredient("minecraft:powder_snow_bucket",1));}
        if(item==Items.AIR&&count>0)throw new IllegalArgumentException("模板方块无生存材料映射："+state);
        if(count>0)out.add(new Ingredient(BuiltInRegistries.ITEM.getKey(item).toString(),count));
        if(state.hasProperty(BlockStateProperties.WATERLOGGED)&&state.getValue(BlockStateProperties.WATERLOGGED))out.add(new Ingredient("minecraft:water_bucket",1));
        return out;
    }
}
