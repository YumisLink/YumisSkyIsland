package cn.yumis.skyisland.client;
import cn.yumis.skyisland.npc.ResidentEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
public final class ResidentRenderer extends HumanoidMobRenderer<ResidentEntity,AvatarRenderState,PlayerModel> {
    private final PlayerModel wide,slim;
    private final PlayerSkinRenderCache skins;
    public ResidentRenderer(EntityRendererProvider.Context context){super(context,new PlayerModel(context.bakeLayer(ModelLayers.PLAYER),false),0.5F);wide=model;slim=new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM),true);skins=context.getPlayerSkinRenderCache();}
    @Override public AvatarRenderState createRenderState(){return new AvatarRenderState();}
    @Override public Identifier getTextureLocation(AvatarRenderState state){return state.skin.body().texturePath();}
    @Override public void extractRenderState(ResidentEntity entity,AvatarRenderState state,float partial){super.extractRenderState(entity,state,partial);state.skin=skins.getOrDefault(entity.profile()).playerSkin();state.showHat=true;state.showJacket=true;state.showLeftPants=true;state.showRightPants=true;state.showLeftSleeve=true;state.showRightSleeve=true;}
    @Override public void submit(AvatarRenderState state,PoseStack pose,SubmitNodeCollector nodes,CameraRenderState camera){model=state.skin.model()==PlayerModelType.SLIM?slim:wide;super.submit(state,pose,nodes,camera);}
}
