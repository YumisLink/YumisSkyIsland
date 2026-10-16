package cn.yumis.skyisland.client;

import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Dashed edges of the exposed faces of the server-selected block union. */
public final class VeinPreviewRenderer {
    private record Edge(int x1,int y1,int z1,int x2,int y2,int z2) {}
    private VeinPreviewRenderer(){}
    public static void register(ClientCatalog model){
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{
            if(model.veinPreview.isEmpty()||context.levelState().cameraRenderState==null)return;
            Vec3 camera=context.levelState().cameraRenderState.pos;if(camera==null)return;
            List<Edge> edges=outline(model.veinPreview);
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(),RenderTypes.linesTranslucent(),(pose,vertices)->{
                for(Edge edge:edges){
                    float dx=edge.x2-edge.x1,dy=edge.y2-edge.y1,dz=edge.z2-edge.z1;
                    for(int part=0;part<4;part++){
                        float a=part/4f,b=a+0.13f;
                        float ax=(float)(edge.x1+dx*a-camera.x),ay=(float)(edge.y1+dy*a-camera.y),az=(float)(edge.z1+dz*a-camera.z);
                        float bx=(float)(edge.x1+dx*b-camera.x),by=(float)(edge.y1+dy*b-camera.y),bz=(float)(edge.z1+dz*b-camera.z);
                        vertices.addVertex(pose,ax,ay,az).setColor(113,232,211,220).setNormal(pose,dx,dy,dz).setLineWidth(1.5f);
                        vertices.addVertex(pose,bx,by,bz).setColor(113,232,211,220).setNormal(pose,dx,dy,dz).setLineWidth(1.5f);
                    }
                }
            });
        });
    }
    private static List<Edge> outline(List<BlockPos> blocks){
        Set<BlockPos> selected=new HashSet<>(blocks);Set<Edge> edges=new LinkedHashSet<>();
        for(BlockPos p:blocks)for(Direction face:Direction.values()){
            if(selected.contains(p.relative(face)))continue;
            int x=p.getX(),y=p.getY(),z=p.getZ();
            switch(face){
                case DOWN -> square(edges,x,y,z,x+1,y,z,x+1,y,z+1,x,y,z+1);
                case UP -> square(edges,x,y+1,z,x+1,y+1,z,x+1,y+1,z+1,x,y+1,z+1);
                case NORTH -> square(edges,x,y,z,x+1,y,z,x+1,y+1,z,x,y+1,z);
                case SOUTH -> square(edges,x,y,z+1,x+1,y,z+1,x+1,y+1,z+1,x,y+1,z+1);
                case WEST -> square(edges,x,y,z,x,y,z+1,x,y+1,z+1,x,y+1,z);
                case EAST -> square(edges,x+1,y,z,x+1,y,z+1,x+1,y+1,z+1,x+1,y+1,z);
            }
        }
        return List.copyOf(edges);
    }
    private static void square(Set<Edge> e,int ax,int ay,int az,int bx,int by,int bz,int cx,int cy,int cz,int dx,int dy,int dz){
        add(e,ax,ay,az,bx,by,bz);add(e,bx,by,bz,cx,cy,cz);add(e,cx,cy,cz,dx,dy,dz);add(e,dx,dy,dz,ax,ay,az);
    }
    private static void add(Set<Edge> edges,int ax,int ay,int az,int bx,int by,int bz){
        if(ax>bx||ax==bx&&ay>by||ax==bx&&ay==by&&az>bz)edges.add(new Edge(bx,by,bz,ax,ay,az));
        else edges.add(new Edge(ax,ay,az,bx,by,bz));
    }
}
