package cn.yumis.skyisland.client;

import cn.yumis.skyisland.config.GameConfig;
import cn.yumis.skyisland.network.PrefabPayloads;
import cn.yumis.skyisland.prefab.*;
import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class PrefabClient {
    record PreviewBlock(BlockPos local,BlockState state) {}
    static JsonObject preview=new JsonObject(),cannon=new JsonObject();
    private static final List<PreviewBlock> blocks=new ArrayList<>(),surface=new ArrayList<>();
    private static final Map<BlockState,List<BlockStateModelPart>> models=new HashMap<>();
    private static String token="";
    static boolean visible;
    static int layer=-1;
    private PrefabClient() {}
    public static void register(KeyMapping.Category category){
        MenuScreens.register(PrefabBlocks.CANNON_MENU,BlueprintCannonScreen::new);
        var key=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.yumis_sky_island.prefab_position",InputConstants.KEY_G,category));
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(visible&&client.level!=null&&preview.has("dimension")&&!preview.get("dimension").getAsString().equals(client.level.dimension().identifier().toString())){visible=false;blocks.clear();surface.clear();send("preview_cancel");if(client.gui.screen() instanceof BlueprintPositionScreen)client.gui.setScreen(null);}while(key.consumeClick())if(client.player!=null&&client.gui.screen()==null&&visible)client.gui.setScreen(new BlueprintPositionScreen());});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{preview=new JsonObject();cannon=new JsonObject();blocks.clear();surface.clear();models.clear();token="";visible=false;layer=-1;});
        ClientPlayNetworking.registerGlobalReceiver(PrefabPayloads.Snapshot.TYPE,(payload,context)->{
            JsonObject data=JsonParser.parseString(payload.json()).getAsJsonObject();String kind=data.get("kind").getAsString();
            if(kind.equals("preview")){
                if(!token.equals(data.get("token").getAsString())){blocks.clear();surface.clear();token=data.get("token").getAsString();layer=-1;}
                preview=data;visible=true;if(data.get("open").getAsBoolean())Minecraft.getInstance().gui.setScreen(new BlueprintPositionScreen());
            }else if(kind.equals("preview_blocks")&&token.equals(data.get("token").getAsString())){
                for(var e:data.getAsJsonArray("blocks")){var a=e.getAsJsonArray();blocks.add(new PreviewBlock(new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()),Block.stateById(a.get(3).getAsInt())));}
                if(preview.has("count")&&blocks.size()>=preview.get("count").getAsInt())surface();
            }else if(kind.equals("positioned")){visible=false;blocks.clear();surface.clear();Minecraft.getInstance().gui.setScreen(null);}
            else if(kind.equals("cannon"))cannon=data;
        });
        ItemTooltipCallback.EVENT.register((stack,context,flags,lines)->{
            if(stack.is(PrefabItems.BLUEPRINT)){
                lines.add(Component.literal("先右键定位图纸，再放入蓝图大炮施工"));
                var selected=BlueprintSelection.read(stack);
                if(selected==null)lines.add(Component.literal("尚未选择施工位置"));
                else {lines.add(Component.literal("位置："+selected.x()+", "+selected.y()+", "+selected.z()+" · "+selected.dimension()));lines.add(Component.literal("旋转 "+selected.rotation()*90+"° · 替换"+(selected.replace()?"开启":"关闭")));}
                lines.add(Component.literal("每次开工消耗1张；成品费与弹药另计；不可撤销"));
            }else if(stack.is(PrefabItems.AMMUNITION))lines.add(Component.literal("施工量："+PrefabItems.ammunition(stack,SkyIslandClient.CATALOG.prefabAmmunitionCredits)+"；每实际放置1块消耗1次"));
            else if(stack.is(PrefabBlocks.CANNON.asItem()))lines.add(Component.literal("放入已定位图纸；读取炮内及六面相邻本队容器的建材"));
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{
            var mc=Minecraft.getInstance();if(!visible||mc.player==null||mc.level==null||!preview.has("origin")||context.levelState().cameraRenderState==null)return;
            Vec3 camera=context.levelState().cameraRenderState.pos;BlockPos origin=PrefabService.pos(preview.getAsJsonArray("origin"));int rotation=preview.get("rotation").getAsInt();int width=preview.get("sourceWidth").getAsInt(),depth=preview.get("sourceDepth").getAsInt();
            List<PreviewBlock> shown=surface.isEmpty()?blocks:surface;int submitted=0;List<BlockPos> collisions=new ArrayList<>();
            for(var block:shown){if(layer>=0&&block.local.getY()!=layer)continue;BlockPos p=origin.offset(rotate(block.local,rotation,width,depth));if(p.distToCenterSqr(camera.x,camera.y,camera.z)>128*128)continue;if(++submitted>6000)break;
                BlockState state=block.state.rotate(PrefabTemplate.rotation(rotation));boolean conflict=mc.level.hasChunkAt(p)&&(mc.level.getBlockState(p).hasBlockEntity()||!preview.get("replace").getAsBoolean()&&!mc.level.getBlockState(p).isAir()&&mc.level.getBlockState(p)!=state);
                if(conflict)collisions.add(p);
                var pose=context.poseStack();pose.pushPose();pose.translate(p.getX()-camera.x,p.getY()-camera.y,p.getZ()-camera.z);
                context.submitNodeCollector().submitBlockModel(pose,RenderTypes.translucentMovingBlock(),models.computeIfAbsent(state,PrefabClient::model),new int[]{conflict?0x90FF6670:0x749EDFFF},0xF000F0,0,0);pose.popPose();
            }
            int w=rotation%2==0?width:depth,d=rotation%2==0?depth:width,h=preview.get("height").getAsInt();
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(),RenderTypes.linesTranslucent(),(pose,vertices)->{
                for(int axis=0;axis<3;axis++)for(int a=0;a<2;a++)for(int b=0;b<2;b++){
                    float x=(float)(origin.getX()-camera.x)+(axis==0?0:a*w),y=(float)(origin.getY()-camera.y)+(axis==1?0:(axis==0?a:b)*h),z=(float)(origin.getZ()-camera.z)+(axis==2?0:b*d);
                    float dx=axis==0?w:0,dy=axis==1?h:0,dz=axis==2?d:0;
                    vertices.addVertex(pose,x,y,z).setColor(125,225,255,230).setNormal(pose,axis==0?1:0,axis==1?1:0,axis==2?1:0).setLineWidth(2);
                    vertices.addVertex(pose,x+dx,y+dy,z+dz).setColor(125,225,255,230).setNormal(pose,axis==0?1:0,axis==1?1:0,axis==2?1:0).setLineWidth(2);
                }
            });
        });
    }
    private static BlockPos rotate(BlockPos p,int r,int w,int d){return switch(r%4){case 1->new BlockPos(d-1-p.getZ(),p.getY(),p.getX());case 2->new BlockPos(w-1-p.getX(),p.getY(),d-1-p.getZ());case 3->new BlockPos(p.getZ(),p.getY(),w-1-p.getX());default->p;};}
    private static void surface(){
        Map<BlockPos,BlockState> positions=new HashMap<>();for(var b:blocks)positions.put(b.local,b.state);surface.clear();
        for(var b:blocks){boolean hidden=b.state.canOcclude();if(hidden)for(var direction:Direction.values()){BlockState n=positions.get(b.local.relative(direction));if(n==null||!n.canOcclude()){hidden=false;break;}}if(!hidden)surface.add(b);}
    }
    private static List<BlockStateModelPart> model(BlockState state){
        List<BlockStateModelPart> original=new ArrayList<>();Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(42),original);
        if(original.isEmpty()&&state.hasBlockEntity())Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(Blocks.GLASS.defaultBlockState()).collectParts(RandomSource.create(42),original);
        List<BlockStateModelPart> result=new ArrayList<>();for(var part:original){Map<Direction,List<BakedQuad>> quads=new HashMap<>();List<Direction> directions=new ArrayList<>(List.of(Direction.values()));directions.add(null);
            for(var direction:directions){List<BakedQuad> faces=new ArrayList<>();for(var quad:part.getQuads(direction)){var m=quad.materialInfo();var tinted=new BakedQuad.MaterialInfo(m.sprite(),m.layer(),m.itemRenderType(),0,false,m.lightEmission());faces.add(new BakedQuad(quad.position0(),quad.position1(),quad.position2(),quad.position3(),quad.packedUV0(),quad.packedUV1(),quad.packedUV2(),quad.packedUV3(),quad.direction(),tinted));}quads.put(direction,List.copyOf(faces));}
            result.add(new BlockStateModelPart(){public List<BakedQuad> getQuads(Direction direction){return quads.get(direction);}public boolean useAmbientOcclusion(){return false;}public net.minecraft.client.resources.model.sprite.Material.Baked particleMaterial(){return part.particleMaterial();}public int materialFlags(){return part.materialFlags()|BakedQuad.FLAG_TRANSLUCENT;}});
        }return List.copyOf(result);
    }
    static void send(String action){JsonObject data=new JsonObject();data.addProperty("action",action);send(data);}
    static void send(JsonObject data){if(ClientPlayNetworking.canSend(PrefabPayloads.Request.TYPE))ClientPlayNetworking.send(new PrefabPayloads.Request(data.toString()));}
    static void cannon(String action,BlockPos pos){cannon(action,pos,cannon.has("priceRevision")?cannon.get("priceRevision").getAsLong():-1);}
    static void cannon(String action,BlockPos pos,long revision){JsonObject request=new JsonObject();request.addProperty("action",action);request.addProperty("priceRevision",revision);request.add("pos",PrefabService.xyz(pos));send(request);}
}
