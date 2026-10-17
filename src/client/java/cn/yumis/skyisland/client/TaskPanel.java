package cn.yumis.skyisland.client;

import cn.yumis.skyisland.core.*;
import cn.yumis.skyisland.network.ShopPayloads;
import cn.yumis.skyisland.shop.TaskMaterialPlanner;
import com.google.gson.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Direct task/bounty sections within the same ControlPanelScreen. */
public final class TaskPanel {
    private final ControlPanelScreen parent;
    private final ClientCatalog model;
    private String selected="",requested="";
    private int listScroll,detailScroll,bountyScroll;
    private final Set<String> deleted=new HashSet<>();
    private QuestEditorPanel editor;
    public TaskPanel(ControlPanelScreen parent){this.parent=parent;model=parent.model();}
    private JsonObject data(){return model.state.has("progression")?model.state.getAsJsonObject("progression"):new JsonObject();}
    private List<JsonObject> rows(){
        JsonArray source=data().has("quests")?data().getAsJsonArray("quests"):model.editorState.has("quests")?model.editorState.getAsJsonArray("quests"):new JsonArray();
        if(model.editorState.has("revision")&&model.editorState.get("revision").getAsLong()>model.taskRevision()&&model.editorState.has("quests"))source=model.editorState.getAsJsonArray("quests");
        var result=new ArrayList<JsonObject>();
        for(var row:source){
            JsonObject quest=row.getAsJsonObject();
            if(deleted.contains(quest.get("id").getAsString()))continue;
            if(!model.editing() && quest.has("status") &&
                    (quest.get("status").getAsString().equals("locked") || quest.get("status").getAsString().equals("completed")))continue;
            result.add(quest);
        }
        return result;
    }
    public void tick(){
        var rows=rows();
        if(rows.stream().noneMatch(q->q.get("id").getAsString().equals(selected))){selected=rows.isEmpty()?"":rows.getFirst().get("id").getAsString();requested="";}
        if(!selected.isEmpty()&&(!model.questDetails.containsKey(selected)||model.editing()&&!model.questDetails.get(selected).has("definition"))){
            String key=selected+"@"+model.taskRevision()+":"+model.editing();
            if(!requested.equals(key)){JsonObject request=new JsonObject();request.addProperty("id",selected);model.task("detail",request);requested=key;}
        }
        if(editor!=null&&!model.editing())editor=null;
    }
    private JsonObject detail(){return model.questDetails.get(selected);}
    private String status(){
        for(var row:rows())if(row.get("id").getAsString().equals(selected))return row.get("status").getAsString();
        return "locked";
    }
    private static String statusLabel(String value){return switch(value){case "completed"->"已完成";case "locked"->"尚未解锁";case "ready"->"可以领取";case "pending"->"等待完成目标";default->"提交材料";};}
    private void text(GuiGraphicsExtractor g,String value,int x,int y,int color,int max){parent.textLine(g,value,x,y,color,max);}
    private void button(GuiGraphicsExtractor g,String value,int x,int y,int w,boolean active){parent.actionButton(g,value,x,y,w,active);}
    private static boolean hit(double x,double y,int a,int b,int w,int h){return ControlPanelScreen.hit(x,y,a,b,w,h);}
    public void render(GuiGraphicsExtractor g,int mx,int my,float delta){
        tick();
        int left=parent.contentLeft(),right=parent.contentWidth();
        g.fill(6,64,parent.sidebar()+6,parent.height-8,UiTheme.CARD);
        text(g,"成长任务",12,73,0xFFFFFFFF,parent.sidebar()-16);
        var rows=rows();int visible=Math.max(1,(parent.height-145)/37);
        listScroll=Math.clamp(listScroll,0,Math.max(0,rows.size()-visible));
        for(int i=listScroll;i<Math.min(rows.size(),listScroll+visible);i++){
            var row=rows.get(i);int y=95+(i-listScroll)*37;boolean active=row.get("id").getAsString().equals(selected);
            g.fill(10,y,parent.sidebar()+2,y+34,active?UiTheme.SELECTED:0x66292929);
            String kind=row.get("kind").getAsString();String name=(kind.equals("profession")?"职 · ":kind.equals("side")?"支 · ":"主 · ")+row.get("name").getAsString();
            text(g,name,14,y+5,titleColor(kind),parent.sidebar()-18);
            text(g,statusLabel(row.get("status").getAsString()),14,y+20,row.get("status").getAsString().equals("completed")?0xFF82CAB3:0xFFADB9C3,parent.sidebar()-18);
        }
        if(model.editing()){
            button(g,"新任务",left,70,65,!model.taskBusy);button(g,"上移",left+71,70,48,!selected.isEmpty()&&!model.taskBusy);button(g,"下移",left+125,70,48,!selected.isEmpty()&&!model.taskBusy);
        }
        if(editor!=null){editor.render(g,mx,my,delta);return;}
        var quest=detail();
        if(quest==null){text(g,rows.isEmpty()?"请先 /is 建岛；管理员可开启修改模式配置任务":"正在同步任务详情…",left,112,0xFFBBC6CE,right);return;}
        int top=model.editing()?104:74,y=top-detailScroll;
        g.enableScissor(left,top,parent.width-8,parent.height-65);
        text(g,quest.get("name").getAsString(),left,y,titleColor(quest.get("kind").getAsString()),right);y+=18;
        y+=8;
        if(quest.get("objective").getAsString().equals("place_shipping_chest")){text(g,"目标：本人在本队岛成功放置出货箱",left,y,0xFF9DE5C7,right);y+=16;}
        if(quest.has("goals"))for(var element:quest.getAsJsonArray("goals")){
            var goal=element.getAsJsonObject();String type=goal.get("type").getAsString();var id=net.minecraft.resources.Identifier.parse(goal.get("targetId").getAsString());
            String name=type.equals("kill")?net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(id).getDescription().getString():new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id)).getHoverName().getString();
            String label=cn.yumis.skyisland.server.QuestCatalog.actionName(type)+" "+name+"  "+goal.get("progress").getAsInt()+"/"+goal.get("quantity").getAsInt();
            text(g,label,left,y,0xFF9DE5C7,right);y+=18;
            var conditions=new ArrayList<String>();
            if(goal.has("sourceBlockId"))conditions.add("来源："+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse(goal.get("sourceBlockId").getAsString())).getName().getString());
            if(goal.has("toolId"))conditions.add("工具："+new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(goal.get("toolId").getAsString()))).getHoverName().getString());
            if(!conditions.isEmpty()){text(g,String.join("；",conditions),left,y,0xFFAFC3CE,right);y+=18;}
        }
        for(var row:quest.getAsJsonArray("requirements")){
            var requirement=row.getAsJsonObject();var template=model.decode(requirement.get("stack"));
            var compiled=compile(requirement);
            int have=0;if(Minecraft.getInstance().player!=null)for(int i=0;i<36;i++){var actual=Minecraft.getInstance().player.getInventory().getItem(i);if(TaskMaterialPlanner.matches(actual,compiled))have+=actual.getCount();}
            g.item(template,left,y);text(g,"提交 "+template.getHoverName().getString()+" ×"+compiled.quantity()+"（背包 "+have+"）",left+23,y+5,RarityColors.color(model.rarity(template)),right-23);y+=24;
            if(requirement.has("minimumQuality")){text(g,"品质至少 "+gradeName(requirement.get("minimumQuality").getAsInt()),left,y,0xFFAFC3CE,right);y+=14;}
            String properties=enchantments(template,true);if(!properties.isEmpty()){text(g,properties,left,y,0xFFAFC3CE,right);y+=14;}
        }
        for(var reward:quest.getAsJsonArray("rewardItems")){var stack=model.decode(reward);g.item(stack,left,y);text(g,"奖励："+stack.getHoverName().getString()+" ×"+stack.getCount(),left+23,y+5,RarityColors.color(model.rarity(stack)),right-23);y+=25;String properties=enchantments(stack,false);if(!properties.isEmpty()){text(g,properties,left,y,0xFFAFC3CE,right);y+=14;}int grade=cn.yumis.skyisland.shop.ItemEconomy.grade(stack);if(grade>0){text(g,"品质："+gradeName(grade),left,y,0xFFAFC3CE,right);y+=14;}}
        if(quest.get("rewardCoins").getAsLong()>0){text(g,"奖励："+Money.format(quest.get("rewardCoins").getAsLong()),left,y,0xFFFFDC78,right);y+=15;}
        if(quest.has("rewardExperiencePoints")&&quest.get("rewardExperiencePoints").getAsInt()>0){text(g,"奖励："+quest.get("rewardExperiencePoints").getAsInt()+"点原版经验",left,y,0xFF9DE5C7,right);y+=15;}
        if(model.state.has("lottery")){var lottery=model.state.getAsJsonObject("lottery");if(lottery.has("taskTokenReward")){text(g,"奖励："+lottery.get("taskTokenReward").getAsInt()+cn.yumis.skyisland.core.LotteryNames.token()+(quest.has("kind")&&quest.get("kind").getAsString().equals("main")?"（全队每人）":""),left,y,0xFFB5B2FF,right);y+=15;}}
        if(quest.has("rewardAccessoryDraws")&&quest.get("rewardAccessoryDraws").getAsInt()>0){text(g,"奖励：饰品抽奖 +"+quest.get("rewardAccessoryDraws").getAsInt(),left,y,0xFF7DDEA5,right);y+=15;}
        for(var feature:quest.getAsJsonArray("unlockFeatures")){text(g,"永久解锁："+featureName(feature.getAsString()),left,y,0xFF9DE5C7,right);y+=15;}
        text(g,statusLabel(status()),left,y+5,0xFFADB9C3,right);
        g.disableScissor();
        button(g,canClaim(quest)?quest.get("objective").getAsString().equals("materials")?"提交／领取奖励":"领取奖励":statusLabel(status()),left,parent.height-32,135,canClaim(quest));
        if(model.editing()){
            button(g,"编辑",left+141,parent.height-32,48,!model.taskBusy);
            button(g,"删除",left+195,parent.height-32,48,!model.taskBusy);
            button(g,"重置全服此任务",left+249,parent.height-32,125,!model.taskBusy);
        }
    }
    private static String gradeName(int grade){return new String[]{"普通","铜星","银星","金星","钻星","七彩星"}[Math.clamp(grade,0,5)];}
    private static int titleColor(String kind){return switch(kind){case "main"->0xFFFFD700;case "profession"->0xFF5599FF;default->0xFFFFFFFF;};}
    private static String enchantments(net.minecraft.world.item.ItemStack stack,boolean minimum){var labels=new ArrayList<String>();for(var entry:net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet())labels.add(entry.getKey().value().description().getString()+(minimum?" ≥":" ")+entry.getIntValue());return labels.isEmpty()?"":"附魔："+String.join("、",labels);}
    private TaskMaterialPlanner.Requirement compile(JsonObject row){return new TaskMaterialPlanner.Requirement(model.decode(row.get("stack")),row.has("components")?row.getAsJsonObject("components"):new JsonObject(),row.get("quantity").getAsInt(),row.has("minimumQuality")?row.get("minimumQuality").getAsInt():null);}
    private boolean canClaim(JsonObject quest){
        if(Minecraft.getInstance().player==null||Set.of("locked","completed","pending").contains(status()))return false;
        var requirements=new ArrayList<TaskMaterialPlanner.Requirement>();for(var row:quest.getAsJsonArray("requirements"))requirements.add(compile(row.getAsJsonObject()));
        return TaskMaterialPlanner.allocate(Minecraft.getInstance().player.getInventory(),requirements).isPresent();
    }
    public boolean clicked(MouseButtonEvent event,boolean twice){
        double x=event.x(),y=event.y();int left=parent.contentLeft();
        if(model.editing()){
            if(hit(x,y,left,70,65,22)){beginEditor(null);return true;}
            if(hit(x,y,left+71,70,48,22)){move(-1);return true;}if(hit(x,y,left+125,70,48,22)){move(1);return true;}
        }
        int index=(int)((y-95)/37)+listScroll;var rows=rows();
        if(hit(x,y,10,95,parent.sidebar()-8,Math.max(1,(parent.height-145)/37)*37)&&index>=0&&index<rows.size()){
            String next=rows.get(index).get("id").getAsString();
            if(editor!=null){parent.confirm("切换任务将放弃未保存编辑。",()->{editor=null;select(next);});}else select(next);
            return true;
        }
        if(editor!=null)return editor.clicked(event,twice);
        JsonObject quest=detail();if(quest==null)return true;
        if(hit(x,y,left,parent.height-32,135,22)&&canClaim(quest)){
            JsonObject body=actionBody();body.addProperty("partyId",model.state.get("partyId").getAsString());body.addProperty("islandEpoch",model.state.get("islandEpoch").getAsLong());
            body.addProperty("revision",quest.get("revision").getAsLong());body.addProperty("resetVersion",quest.get("resetVersion").getAsLong());
            parent.confirm((quest.get("objective").getAsString().equals("materials")?"确认提交材料并领取：":"确认领取奖励：")+quest.get("name").getAsString(),()->model.task("claim",body));return true;
        }
        if(model.editing()&&!model.taskBusy){
            if(hit(x,y,left+141,parent.height-32,48,22)){beginEditor(quest);return true;}
            if(hit(x,y,left+195,parent.height-32,48,22)){JsonObject body=actionBody();body.addProperty("confirmed",true);parent.confirm("删除此任务定义；队伍已有进度、已解锁与未解锁功能均保持。",()->{model.taskBusy=true;model.task("delete",body);});return true;}
            if(hit(x,y,left+249,parent.height-32,125,22)){JsonObject body=actionBody();body.addProperty("confirmed",true);parent.confirm("清除全服此任务的完成及目标记录；保留其它任务和已解锁功能。重做可再次领奖。",()->model.task("reset",body));return true;}
        }
        return true;
    }
    private JsonObject actionBody(){JsonObject body=new JsonObject();body.addProperty("id",selected);body.addProperty("revision",model.taskRevision());return body;}
    private void select(String id){selected=id;requested="";detailScroll=0;}
    private void beginEditor(JsonObject detail){
        if(model.taskBusy)return;
        if(detail!=null&&!detail.has("definition")){model.questDetails.remove(selected);requested="";return;}
        editor=new QuestEditorPanel(parent,detail==null?null:detail.getAsJsonObject("definition"),model.taskRevision());
    }
    private void move(int direction){if(selected.isEmpty()||model.taskBusy)return;JsonObject body=actionBody();body.addProperty("direction",direction);model.taskBusy=true;model.task("move",body);}
    public void saved(JsonObject data){if(data.get("action").getAsString().equals("delete")){deleted.add(data.get("id").getAsString());selected="";}else selected=data.get("id").getAsString();editor=null;requested="";}
    public boolean scrolled(double x,double y,double amount){if(x<parent.contentLeft())listScroll-=(int)Math.signum(amount);else if(editor!=null)editor.scroll(x,y,amount);else detailScroll=Math.max(0,detailScroll-(int)Math.signum(amount)*24);return true;}
    public boolean keyPressed(KeyEvent event){if(editor==null)return false;if(event.key()==GLFW.GLFW_KEY_ESCAPE){if(!editor.dismissSuggestion())parent.confirm("取消未保存的任务编辑？",()->editor=null);return true;}return editor.keyPressed(event);}
    public boolean charTyped(CharacterEvent event){return editor!=null&&editor.charTyped(event);}
    public boolean preedit(PreeditEvent event){return editor!=null&&editor.preedit(event);}
    public boolean hasDraft(){return editor!=null;}
    public void discard(){editor=null;}

    public void renderBounties(GuiGraphicsExtractor g,int mx,int my,float delta){
        var data=data();if(!data.has("offers")){text(g,"请先 /is 创建或加入空岛",12,78,0xFFBBC6CE,parent.width-24);return;}
        String tokenReward=model.state.has("lottery")?" · 每次另得"+model.state.getAsJsonObject("lottery").get("taskTokenReward").getAsInt()+cn.yumis.skyisland.core.LotteryNames.token():"";
        text(g,"悬赏格 "+data.get("slots").getAsInt()+"/"+data.get("maximumSlots").getAsInt()+" · 全队共享"+tokenReward,12,68,0xFF9DE5C7,parent.width-24);
        button(g,data.get("slots").getAsInt()>=data.get("maximumSlots").getAsInt()?"格数已满":"购格 "+Money.format(data.get("slotCost").getAsLong()),12,84,Math.min(230,parent.width-24),data.get("slots").getAsInt()<data.get("maximumSlots").getAsInt());
        var offers=data.getAsJsonArray("offers");int visible=Math.max(1,(parent.height-146)/55);bountyScroll=Math.clamp(bountyScroll,0,Math.max(0,offers.size()-visible));
        for(int i=bountyScroll;i<Math.min(offers.size(),bountyScroll+visible);i++){
            var row=offers.get(i).getAsJsonObject();int y=112+(i-bountyScroll)*55;g.fill(10,y,parent.width-10,y+51,UiTheme.CARD);
            if(!row.has("material")||row.get("material").isJsonNull()){text(g,"格 "+(i+1)+" · 暂无合格悬赏；请先卖出普通材料",16,y+18,0xFFBBC6CE,parent.width-32);continue;}
            var reward=model.decode(row.get("rewardStack"));g.item(reward,16,y+5);
            text(g,reward.getHoverName().getString()+" ×1",39,y+7,RarityColors.color(model.rarity(reward)),parent.width-128);
            text(g,"提交 "+model.productName(row.get("material").getAsString())+" ×"+row.get("quantity").getAsInt(),16,y+24,0xFFDDE6EA,parent.width-130);
            long ticks=Math.max(0,row.get("expiresAt").getAsLong()-model.state.get("effectiveTicks").getAsLong());
            text(g,"更替 "+ticks/24000+"日 "+ticks%24000/20/60+"分（报价已锁定）",16,y+38,0xFFADB9C3,parent.width-130);
            button(g,"兑换",parent.width-83,y+14,60,data.get("bountyAllowed").getAsBoolean()&&data.get("specialAllowed").getAsBoolean());
        }
    }
    public boolean bountyClicked(MouseButtonEvent event){
        var data=data();if(!data.has("offers"))return true;double x=event.x(),y=event.y();
        String party=model.state.get("partyId").getAsString();long epoch=model.state.get("islandEpoch").getAsLong();
        if(hit(x,y,12,84,Math.min(230,parent.width-24),22)&&data.get("slots").getAsInt()<data.get("maximumSlots").getAsInt()){
            int paid=data.get("paidSlots").getAsInt();parent.confirm("花费 "+Money.format(data.get("slotCost").getAsLong())+" 为全队增加一个永久悬赏格？",()->ClientPlayNetworking.send(new ShopPayloads.Request("bounty_slot",party+"|"+epoch,paid)));return true;
        }
        var offers=data.getAsJsonArray("offers");int visible=Math.max(1,(parent.height-146)/55);
        for(int i=bountyScroll;i<Math.min(offers.size(),bountyScroll+visible);i++)if(hit(x,y,parent.width-83,126+(i-bountyScroll)*55,60,22)){
            var row=offers.get(i).getAsJsonObject();if(!row.has("material")||row.get("material").isJsonNull())return true;
            String id=row.get("slot").getAsInt()+":"+row.get("generation").getAsLong()+":"+epoch;
            parent.confirm("提交 "+model.productName(row.get("material").getAsString())+" ×"+row.get("quantity").getAsInt()+"，获得 "+model.productName(row.get("reward").getAsString())+"。",()->ClientPlayNetworking.send(new ShopPayloads.Request("bounty_claim",party+"|"+id,0)));return true;
        }
        return true;
    }
    public boolean bountyScrolled(double amount){bountyScroll-=(int)Math.signum(amount);return true;}
    private static String featureName(String id){return switch(id){case "bounty.use"->"悬赏";case "shop.special"->"每日／隐藏商店";case "automation.shipping"->"出货箱";default->id;};}
}
