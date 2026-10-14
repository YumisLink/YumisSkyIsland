package cn.yumis.skyisland.client;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.Consumer;

/** An inline scrollable form with item/property autocomplete; it never creates another Screen. */
public final class QuestEditorPanel {
    private record Hit(int x,int y,int w,int h,Runnable action) {}
    private record Field(EditBox box,int x,int y,int w) {}
    private final ControlPanelScreen parent;
    private final ClientCatalog model;
    private final JsonObject draft;
    private final long baseRevision;
    private final List<Hit> buttons=new ArrayList<>(),suggestions=new ArrayList<>();
    private final List<Field> fields=new ArrayList<>();
    private final EditBox name,description,coins,experience,previous;
    private EditBox itemSearch,quantity,potion,enchantment,enchantLevel;
    private EditBox sourceBlock,tool;
    private JsonObject selectedRow;
    private boolean selectedReward,selectedGoal;
    private int selectedIndex=-1,formScroll,materialScroll,rewardScroll,formHeight=560;
    private int materialTop,rewardTop,propertyTop;
    private String selectedEnchantment="";
    private int itemResultsScroll;
    public QuestEditorPanel(ControlPanelScreen parent,JsonObject definition,long revision){
        this.parent=parent;model=parent.model();baseRevision=revision;
        if(definition==null){
            draft=new JsonObject();draft.addProperty("id","");draft.addProperty("name","新任务");draft.addProperty("description","");
            draft.addProperty("kind","main");draft.addProperty("objective","materials");draft.addProperty("rewardCoins",0);
            draft.add("requirements",new JsonArray());draft.add("rewardItems",new JsonArray());draft.add("unlockFeatures",new JsonArray());
        }else draft=definition.deepCopy();
        if(!draft.has("goals"))draft.add("goals",new JsonArray());
        if(!draft.has("rewardExperiencePoints"))draft.addProperty("rewardExperiencePoints",0);
        name=box("名称",draft.get("name").getAsString(),80,v->draft.addProperty("name",v));
        description=box("说明",draft.get("description").getAsString(),2048,v->draft.addProperty("description",v));
        coins=box("金币奖励",draft.get("rewardCoins").getAsString(),19,v->draft.addProperty("rewardCoins",integer(v,-1)));
        experience=box("原版经验点",draft.get("rewardExperiencePoints").getAsString(),10,v->draft.addProperty("rewardExperiencePoints",integer(v,-1)));
        previous=box("前置任务ID",draft.has("previousQuestId")?draft.get("previousQuestId").getAsString():"",64,v->{if(v.isBlank())draft.remove("previousQuestId");else draft.addProperty("previousQuestId",v);});
    }
    private EditBox box(String label,String value,int maximum,Consumer<String> callback){
        EditBox result=new EditBox(parent.uiFont(),0,0,100,20,Component.literal(label));
        result.setMaxLength(maximum);result.setValue(value);result.setCursorPosition(0);result.setHighlightPos(0);result.setResponder(callback);return result;
    }
    private static long integer(String value,long invalid){try{return Long.parseLong(value);}catch(NumberFormatException ignored){return invalid;}}
    private void text(GuiGraphicsExtractor g,String label,int x,int y,int color,int maximum){parent.textLine(g,label,x,y,color,maximum);}
    private void button(GuiGraphicsExtractor g,String label,int x,int y,int w,boolean active,Runnable action){
        parent.actionButton(g,label,x,y,w,active);if(active)buttons.add(new Hit(x,y,w,22,action));
    }
    private void field(GuiGraphicsExtractor g,EditBox box,int relativeX,int y,int w,int mx,int my,float delta){
        int x=parent.contentLeft()+relativeX,screenY=y-formScroll;
        box.setX(x);box.setY(screenY);box.setWidth(Math.max(40,w));box.active=!model.taskBusy;
        fields.add(new Field(box,x,screenY,w));
        box.extractRenderState(g,mx,my,delta);
    }
    public void render(GuiGraphicsExtractor g,int mx,int my,float delta){
        int left=parent.contentLeft(),w=parent.contentWidth(),top=104;
        formScroll=Math.clamp(formScroll,0,Math.max(0,formHeight-(parent.height-top-45)));
        buttons.clear();suggestions.clear();fields.clear();
        g.enableScissor(left,top,parent.width-8,parent.height-42);
        text(g,draft.get("id").getAsString().isEmpty()?"新增任务 · 保存后分配固定ID":"编辑 "+draft.get("id").getAsString(),left,top-formScroll,0xFF9DE5C7,w);
        label(g,"任务名称",left,top+19,w);field(g,name,0,top+31,w,mx,my,delta);
        label(g,"任务说明",left,top+56,w);field(g,description,0,top+68,w,mx,my,delta);
        label(g,"金币奖励（0为无）",left,top+93,110);field(g,coins,0,top+105,110,mx,my,delta);
        button(g,"类型："+switch(draft.get("kind").getAsString()){case "side"->"支线";case "profession"->"职业";default->"主线";},left+116,top+105-formScroll,Math.min(130,w-116),!model.taskBusy,()->{
            String kind=draft.get("kind").getAsString();String next=kind.equals("main")?"side":kind.equals("side")?"profession":"main";draft.addProperty("kind",next);
            if(next.equals("profession")){draft.addProperty("profession","fisherman");draft.addProperty("objective","tracked");draft.add("requirements",new JsonArray());}
            else{draft.remove("profession");draft.remove("previousQuestId");previous.setValue("");if(next.equals("main")&&draft.get("objective").getAsString().equals("tracked")){draft.addProperty("objective","materials");draft.add("goals",new JsonArray());}}
            selectedRow=null;
        });
        label(g,"原版经验点（0为无）",left,top+131,w);field(g,experience,0,top+143,110,mx,my,delta);
        button(g,"目标："+switch(draft.get("objective").getAsString()){case "tracked"->"追踪本人行动";case "place_shipping_chest"->"放置出货箱";default->"提交材料";},left,top+170-formScroll,Math.min(220,w),!model.taskBusy&&!draft.get("kind").getAsString().equals("profession"),()->{
            var types=draft.get("kind").getAsString().equals("main")?List.of("materials","place_shipping_chest"):List.of("materials","place_shipping_chest","tracked");
            String next=types.get((types.indexOf(draft.get("objective").getAsString())+1)%types.size());draft.addProperty("objective",next);
            draft.add("requirements",new JsonArray());draft.add("goals",new JsonArray());selectedRow=null;selectedIndex=-1;
        });
        int featureY=top+200;
        if(draft.get("kind").getAsString().equals("profession")){
            String profession=draft.get("profession").getAsString();
            button(g,"职业："+cn.yumis.skyisland.server.ProfessionService.name(profession),left,featureY-formScroll,Math.min(180,w),!model.taskBusy,()->{var jobs=cn.yumis.skyisland.server.QuestCatalog.PROFESSIONS;draft.addProperty("profession",jobs.get((jobs.indexOf(profession)+1)%jobs.size()));});
            featureY+=27;
        }
        if(!draft.get("kind").getAsString().equals("main")){label(g,"前置任务ID（留空为起点）",left,featureY,w);field(g,previous,0,featureY+13,w,mx,my,delta);featureY+=40;}
        int columns=Math.max(1,w/136),featureCount=0;
        var features=model.editorState.has("features")?model.editorState.getAsJsonArray("features"):new JsonArray();
        for(var element:features){
            var feature=element.getAsJsonObject();String id=feature.get("id").getAsString();boolean on=hasUnlock(id);
            int x=left+(featureCount%columns)*136,y=featureY+(featureCount/columns)*25-formScroll;
            button(g,(on?"☑ ":"☐ ")+feature.get("name").getAsString(),x,y,Math.min(130,w-(x-left)),!model.taskBusy,()->toggleUnlock(id));featureCount++;
        }
        int lists=featureY+Math.max(1,(featureCount+columns-1)/columns)*25+9,half=(w-8)/2;
        materialTop=lists;rewardTop=lists;
        boolean materials=draft.get("objective").getAsString().equals("materials");
        if(draft.get("objective").getAsString().equals("tracked"))drawGoals(g,left,lists,half);else drawList(g,"需求",draft.getAsJsonArray("requirements"),false,left,lists,half,materialScroll,materials);
        drawList(g,"奖励",draft.getAsJsonArray("rewardItems"),true,left+half+8,lists,half,rewardScroll,true);
        propertyTop=lists+115;
        if(selectedRow!=null)drawProperties(g,mx,my,delta,left,w,propertyTop);
        else text(g,"点击“添加”或某一行，直接在这里搜索物品并编辑属性。",left,propertyTop-formScroll,0xFFADB9C3,w);
        formHeight=propertyTop-top+225+(selectedRow==null||selectedGoal?0:enchantMap().size()*19);
        g.disableScissor();
        // Autocomplete is a bounded overlay inside this same form, not a navigation page.
        drawSuggestions(g,mx,my);
        buttons.add(new Hit(left,parent.height-31,110,22,()->save()));
        parent.actionButton(g,model.taskBusy?"正在保存…":"保存并立即生效",left,parent.height-31,135,!model.taskBusy);
        buttons.set(buttons.size()-1,new Hit(left,parent.height-31,135,22,()->{if(!model.taskBusy)save();}));
        parent.actionButton(g,"取消编辑",left+141,parent.height-31,80,!model.taskBusy);
        if(!model.taskBusy)buttons.add(new Hit(left+141,parent.height-31,80,22,()->parent.confirm("放弃此任务的未保存编辑？",()->parent.taskSaved(cancelData()))));
    }
    private JsonObject cancelData(){JsonObject data=new JsonObject();data.addProperty("action","cancel");data.addProperty("id",draft.get("id").getAsString());return data;}
    private void label(GuiGraphicsExtractor g,String value,int x,int y,int max){text(g,value,x,y-formScroll,0xFFADB9C3,max);}
    private void drawList(GuiGraphicsExtractor g,String label,JsonArray rows,boolean reward,int x,int y,int w,int scroll,boolean enabled){
        text(g,label+"（"+rows.size()+"）",x,y-formScroll,0xFFCDD7DD,w-55);
        button(g,"添加",x+w-48,y-4-formScroll,48,enabled&&!model.taskBusy,()->{
            if(rows.size()>=32){model.lastError="材料与奖励各最多32条";return;}
            JsonObject row=new JsonObject();row.addProperty(reward?"id":"itemId","minecraft:cobblestone");row.addProperty(reward?"count":"quantity",1);
            rows.add(row);if(reward)rewardScroll=Math.max(0,rows.size()-3);else materialScroll=Math.max(0,rows.size()-3);selectRow(row,reward,rows.size()-1);
        });
        int actual=Math.clamp(scroll,0,Math.max(0,rows.size()-3));
        for(int i=actual;i<Math.min(rows.size(),actual+3);i++){
            var row=rows.get(i).getAsJsonObject();int yy=y+22+(i-actual)*24-formScroll;
            String id=row.get(reward?"id":"itemId").getAsString();int index=i;
            boolean selected=selectedRow==row;g.fill(x,yy,x+w,yy+22,selected?0xCC354D51:0x66303A42);
            var template=template(row,reward);if(!template.isEmpty())g.item(template,x+2,yy+3);
            text(g,(template.isEmpty()?id:template.getHoverName().getString())+" ×"+row.get(reward?"count":"quantity").getAsString(),x+22,yy+7,0xFFE4E8EC,w-44);
            buttons.add(new Hit(x,yy,w-22,22,()->selectRow(row,reward,index)));
            button(g,"×",x+w-21,yy,21,!model.taskBusy,()->{rows.remove(index);selectedRow=null;selectedIndex=-1;});
        }
    }
    private void selectRow(JsonObject row,boolean reward,int index){
        blur();
        selectedGoal=false;
        selectedRow=row;selectedReward=reward;selectedIndex=index;selectedEnchantment="";itemResultsScroll=0;
        String id=row.get(reward?"id":"itemId").getAsString();
        itemSearch=box("搜索物品名称或ID","",100,v->{itemResultsScroll=0;});
        quantity=box("数量",row.get(reward?"count":"quantity").getAsString(),10,v->row.addProperty(reward?"count":"quantity",integer(v,-1)));
        potion=box("药水名称或ID",potionId(row),100,v->{});
        enchantment=box("附魔名称或ID","",100,v->{selectedEnchantment="";});
        enchantLevel=box("附魔等级","1",3,v->{});
        formScroll=Math.max(0,propertyTop-112);itemSearch.setFocused(true);
    }
    private void drawGoals(GuiGraphicsExtractor g,int x,int y,int w){
        var rows=draft.getAsJsonArray("goals");text(g,"追踪目标（"+rows.size()+"）",x,y-formScroll,0xFFCDD7DD,w-55);
        button(g,"添加",x+w-48,y-4-formScroll,48,!model.taskBusy,()->{if(rows.size()>=32){model.lastError="追踪目标最多32条";return;}var row=new JsonObject();row.addProperty("type","craft");row.addProperty("targetId","minecraft:cobblestone");row.addProperty("quantity",1);rows.add(row);materialScroll=Math.max(0,rows.size()-3);selectGoal(row,rows.size()-1);});
        materialScroll=Math.clamp(materialScroll,0,Math.max(0,rows.size()-3));
        for(int i=materialScroll;i<Math.min(rows.size(),materialScroll+3);i++){
            var row=rows.get(i).getAsJsonObject();int index=i,yy=y+22+(i-materialScroll)*24-formScroll;g.fill(x,yy,x+w,yy+22,selectedRow==row?0xCC354D51:0x66303A42);
            text(g,cn.yumis.skyisland.server.QuestCatalog.actionName(row.get("type").getAsString())+" "+row.get("targetId").getAsString()+" ×"+row.get("quantity").getAsString(),x+3,yy+7,0xFFE4E8EC,w-27);
            buttons.add(new Hit(x,yy,w-22,22,()->selectGoal(row,index)));button(g,"×",x+w-21,yy,21,!model.taskBusy,()->{rows.remove(index);selectedRow=null;});
        }
    }
    private void selectGoal(JsonObject row,int index){
        blur();selectedGoal=true;selectedReward=false;selectedRow=row;selectedIndex=index;itemResultsScroll=0;
        itemSearch=box("目标名称／ID",row.get("targetId").getAsString(),256,v->row.addProperty("targetId",v));
        quantity=box("数量",row.get("quantity").getAsString(),10,v->row.addProperty("quantity",integer(v,-1)));
        sourceBlock=box("来源方块ID",row.has("sourceBlockId")?row.get("sourceBlockId").getAsString():"",256,v->{if(v.isBlank())row.remove("sourceBlockId");else row.addProperty("sourceBlockId",v);});
        tool=box("工具ID",row.has("toolId")?row.get("toolId").getAsString():"",256,v->{if(v.isBlank())row.remove("toolId");else row.addProperty("toolId",v);});
        potion=null;enchantment=null;enchantLevel=null;formScroll=Math.max(0,propertyTop-112);
    }
    private net.minecraft.world.item.ItemStack template(JsonObject row,boolean reward){
        try{JsonObject data=row.deepCopy();data.addProperty("id",row.get(reward?"id":"itemId").getAsString());data.addProperty("count",1);return model.decode(data);}catch(RuntimeException ignored){return net.minecraft.world.item.ItemStack.EMPTY;}
    }
    private JsonObject components(){if(!selectedRow.has("components"))selectedRow.add("components",new JsonObject());return selectedRow.getAsJsonObject("components");}
    private JsonObject enchantMap(){return enchantMap(false);}
    private JsonObject enchantMap(boolean create){
        String key=selectedRow.get(selectedReward?"id":"itemId").getAsString().equals("minecraft:enchanted_book")?"minecraft:stored_enchantments":"minecraft:enchantments";
        if(!selectedRow.has("components")&&!create)return new JsonObject();
        JsonObject components=components();
        if(!components.has(key)){if(!create)return new JsonObject();components.add(key,new JsonObject());}
        JsonObject result=components.getAsJsonObject(key);return result.has("levels")?result.getAsJsonObject("levels"):result;
    }
    private static String potionId(JsonObject row){
        if(!row.has("components")||!row.getAsJsonObject("components").has("minecraft:potion_contents"))return "";
        JsonElement potion=row.getAsJsonObject("components").get("minecraft:potion_contents");
        return potion.isJsonPrimitive()?potion.getAsString():potion.getAsJsonObject().has("potion")?potion.getAsJsonObject().get("potion").getAsString():"";
    }
    private void drawProperties(GuiGraphicsExtractor g,int mx,int my,float delta,int left,int w,int top){
        if(selectedGoal){
            String type=selectedRow.get("type").getAsString();
            button(g,"行动："+cn.yumis.skyisland.server.QuestCatalog.actionName(type),left,top-formScroll,Math.min(210,w),!model.taskBusy,()->{var types=List.of("craft","smelt","fish","harvest","kill");String next=types.get((types.indexOf(type)+1)%types.size());selectedRow.addProperty("type",next);if(next.equals("kill"))itemSearch.setValue("minecraft:zombie");else if(type.equals("kill"))itemSearch.setValue("minecraft:cobblestone");if(!next.equals("harvest")){sourceBlock.setValue("");tool.setValue("");}});
            label(g,type.equals("kill")?"生物名称／注册ID":"物品名称／注册ID",left,top+27,w);field(g,itemSearch,0,top+40,w,mx,my,delta);
            label(g,"实际数量",left,top+67,w);field(g,quantity,0,top+80,110,mx,my,delta);
            if(type.equals("harvest")){label(g,"来源方块ID（空为不限）",left,top+107,w);field(g,sourceBlock,0,top+120,w,mx,my,delta);label(g,"工具ID（空为不限）",left,top+147,w);field(g,tool,0,top+160,w,mx,my,delta);}
            return;
        }
        String id=selectedRow.get(selectedReward?"id":"itemId").getAsString();
        text(g,(selectedReward?"奖励":"需求")+"第"+(selectedIndex+1)+"行 · 已选 "+id,left,top-formScroll,0xFF9DE5C7,w);
        label(g,"物品名称／ID搜索（从候选选择）",left,top+21,w);field(g,itemSearch,0,top+34,w,mx,my,delta);
        label(g,"数量",left,top+60,65);field(g,quantity,0,top+73,65,mx,my,delta);
        int grade=selectedReward?selectedRow.has("quality")?selectedRow.get("quality").getAsInt():0:selectedRow.has("minimumQuality")?selectedRow.get("minimumQuality").getAsInt():-1;
        String[] grades={"普通","精良","稀有","史诗","传说"};
        button(g,selectedReward?"品质："+grades[Math.clamp(grade,0,4)]:grade<0?"品质：不限":"至少："+grades[Math.clamp(grade,0,4)],left+73,top+73-formScroll,Math.min(160,w-73),!model.taskBusy,()->{
            int next=grade+1;if(next>4)next=selectedReward?0:-1;
            if(next<0)selectedRow.remove("minimumQuality");else selectedRow.addProperty(selectedReward?"quality":"minimumQuality",next);
        });
        label(g,"药水类型（无条件可清除）",left,top+101,w);
        field(g,potion,0,top+114,w-57,mx,my,delta);
        button(g,"清除",left+w-51,top+114-formScroll,51,!model.taskBusy,()->{components().remove("minecraft:potion_contents");potion.setValue("");potion.setFocused(false);});
        label(g,"附魔名称／ID 与最低等级",left,top+140,w);
        field(g,enchantment,0,top+153,w-116,mx,my,delta);field(g,enchantLevel,w-109,top+153,48,mx,my,delta);
        button(g,"添加",left+w-55,top+153-formScroll,55,!model.taskBusy,()->{
            String selected=selectedEnchantment.isEmpty()?enchantment.getValue():selectedEnchantment;
            if(ItemPickerCatalog.enchantments().stream().noneMatch(p->p.id().equals(selected))){model.lastError="请从附魔候选中选择有效附魔";return;}
            long level=integer(enchantLevel.getValue(),-1);if(level<1||level>255){model.lastError="附魔等级须为1～255；任务允许超原版常规等级";return;}
            enchantMap(true).addProperty(selected,level);enchantment.setFocused(false);
        });
        int y=top+182;
        for(var entry:new ArrayList<>(enchantMap().entrySet())){
            String idEnchantment=entry.getKey();String name=ItemPickerCatalog.enchantments().stream().filter(p->p.id().equals(idEnchantment)).map(ItemPickerCatalog.Property::name).findFirst().orElse(idEnchantment);
            text(g,name+" "+entry.getValue().getAsString(),left,y-formScroll,0xFFB9C7D4,w-50);
            button(g,"移除",left+w-48,y-5-formScroll,48,!model.taskBusy,()->enchantMap().remove(idEnchantment));y+=19;
        }
    }
    private void drawSuggestions(GuiGraphicsExtractor g,int mx,int my){
        if(selectedRow==null)return;
        EditBox focused=itemSearch.isFocused()?itemSearch:potion!=null&&potion.isFocused()?potion:enchantment!=null&&enchantment.isFocused()?enchantment:null;
        if(focused==null||focused.getY()<104||focused.getBottom()>parent.height-42)return;
        int x=parent.contentLeft(),y=focused.getBottom()+2,w=parent.contentWidth(),max=Math.min(5,(parent.height-45-y)/25);
        if(max<=0)return;
        if(focused==itemSearch){
            if(selectedGoal&&selectedRow.get("type").getAsString().equals("kill")){
                var types=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.stream().filter(t->ItemPickerCatalog.contains(itemSearch.getValue(),t.getDescription().getString(),net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(t).toString())).toList();
                itemResultsScroll=Math.clamp(itemResultsScroll,0,Math.max(0,types.size()-max));int end=Math.min(types.size(),itemResultsScroll+max);g.fill(x,y,x+w,y+(end-itemResultsScroll)*25,0xF018242B);
                for(int i=itemResultsScroll;i<end;i++){var type=types.get(i);String id=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();int yy=y+(i-itemResultsScroll)*25;text(g,type.getDescription().getString()+" · "+id,x+5,yy+8,0xFFE3EAEF,w-10);suggestions.add(new Hit(x,yy,w,25,()->{itemSearch.setValue(id);itemSearch.setFocused(false);}));}return;
            }
            var results=ItemPickerCatalog.items().stream().filter(p->ItemPickerCatalog.contains(itemSearch.getValue(),p.name(),p.id())).toList();
            itemResultsScroll=Math.clamp(itemResultsScroll,0,Math.max(0,results.size()-max));
            int end=Math.min(results.size(),itemResultsScroll+max);
            g.fill(x,y,x+w,y+(end-itemResultsScroll)*25,0xF018242B);
            for(int i=itemResultsScroll;i<end;i++){
                var item=results.get(i);int yy=y+(i-itemResultsScroll)*25;g.item(item.stack(),x+3,yy+4);
                text(g,item.name(),x+24,yy+3,0xFFE3EAEF,w-30);text(g,item.id(),x+24,yy+14,0xFF9DADBA,w-30);
                suggestions.add(new Hit(x,yy,w,25,()->{
                    if(selectedGoal){itemSearch.setValue(item.id());itemSearch.setFocused(false);return;}
                    selectedRow.addProperty(selectedReward?"id":"itemId",item.id());selectedRow.add("components",item.components().deepCopy());
                    itemSearch.setValue(item.name());itemSearch.setFocused(false);potion.setValue(potionId(selectedRow));
                }));
            }
        }else{
            var values=focused==potion?ItemPickerCatalog.potions():ItemPickerCatalog.enchantments();
            var result=values.stream().filter(p->ItemPickerCatalog.contains(focused.getValue(),p.name(),p.id())).limit(max).toList();
            g.fill(x,y,x+w,y+result.size()*25,0xF018242B);
            for(int i=0;i<result.size();i++){
                var property=result.get(i);int yy=y+i*25;text(g,property.name()+" · "+property.id(),x+5,yy+8,0xFFE3EAEF,w-10);
                suggestions.add(new Hit(x,yy,w,25,()->{
                    if(focused==potion){JsonObject value=new JsonObject();value.addProperty("potion",property.id());components().add("minecraft:potion_contents",value);}
                    else selectedEnchantment=property.id();
                    focused.setValue(property.id());focused.setFocused(false);
                }));
            }
        }
    }
    private boolean hasUnlock(String id){for(var value:draft.getAsJsonArray("unlockFeatures"))if(value.getAsString().equals(id))return true;return false;}
    private void toggleUnlock(String id){
        JsonArray list=draft.getAsJsonArray("unlockFeatures");
        for(int i=0;i<list.size();i++)if(list.get(i).getAsString().equals(id)){list.remove(i);return;}list.add(id);
    }
    private void save(){
        if(model.taskBusy)return;
        for(String rows:List.of("requirements","rewardItems"))for(var element:draft.getAsJsonArray(rows)){
            var row=element.getAsJsonObject();if(!row.has("components"))continue;var patch=row.getAsJsonObject("components");
            for(String key:List.of("minecraft:enchantments","minecraft:stored_enchantments"))if(patch.has(key)&&patch.get(key).isJsonObject()){
                var ench=patch.getAsJsonObject(key);if(ench.isEmpty()||ench.has("levels")&&ench.getAsJsonObject("levels").isEmpty())patch.remove(key);
            }
            if(patch.isEmpty())row.remove("components");
        }
        JsonObject body=new JsonObject();body.addProperty("id",draft.get("id").getAsString());body.addProperty("revision",baseRevision);body.add("definition",draft.deepCopy());
        model.lastError="";model.taskBusy=true;model.task("save",body);
    }
    public boolean clicked(MouseButtonEvent event,boolean twice){
        double x=event.x(),y=event.y();
        for(Hit hit:suggestions)if(ControlPanelScreen.hit(x,y,hit.x,hit.y,hit.w,hit.h)){hit.action.run();return true;}
        for(Hit hit:buttons)if((y>=104&&y<parent.height-42||y>=parent.height-32)&&ControlPanelScreen.hit(x,y,hit.x,hit.y,hit.w,hit.h)){hit.action.run();return true;}
        EditBox target=null;
        if(y>=104&&y<parent.height-42)for(Field field:fields)if(field.box.isMouseOver(x,y)){target=field.box;break;}
        for(Field field:fields)field.box.setFocused(field.box==target);
        if(target!=null)target.mouseClicked(event,twice);
        return true;
    }
    public void scroll(double x,double y,double amount){
        int left=parent.contentLeft(),half=(parent.contentWidth()-8)/2;
        if(itemSearch!=null&&itemSearch.isFocused()){itemResultsScroll-=(int)Math.signum(amount);return;}
        if(ControlPanelScreen.hit(x,y,left,materialTop+20-formScroll,half,75)){materialScroll=Math.clamp(materialScroll-(int)Math.signum(amount),0,Math.max(0,draft.getAsJsonArray(draft.get("objective").getAsString().equals("tracked")?"goals":"requirements").size()-3));return;}
        if(ControlPanelScreen.hit(x,y,left+half+8,rewardTop+20-formScroll,half,75)){rewardScroll=Math.clamp(rewardScroll-(int)Math.signum(amount),0,Math.max(0,draft.getAsJsonArray("rewardItems").size()-3));return;}
        formScroll=Math.max(0,formScroll-(int)Math.signum(amount)*26);
    }
    private List<EditBox> boxes(){var result=new ArrayList<EditBox>(List.of(name,description,coins,experience,previous));for(var box:Arrays.asList(itemSearch,quantity,potion,enchantment,enchantLevel,sourceBlock,tool))if(box!=null)result.add(box);return result;}
    private void blur(){for(var box:boxes())box.setFocused(false);}
    private EditBox focused(){for(var box:boxes())if(box.isFocused())return box;return null;}
    public boolean dismissSuggestion(){if(itemSearch!=null&&(itemSearch.isFocused()||potion!=null&&potion.isFocused()||enchantment!=null&&enchantment.isFocused())){blur();return true;}return false;}
    public boolean keyPressed(KeyEvent event){
        if((event.modifiers()&GLFW.GLFW_MOD_CONTROL)!=0&&event.key()==GLFW.GLFW_KEY_S){save();return true;}
        if(event.key()==GLFW.GLFW_KEY_TAB&&!fields.isEmpty()){
            int current=-1;for(int i=0;i<fields.size();i++)if(fields.get(i).box.isFocused())current=i;
            int next=Math.floorMod(current+((event.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0?-1:1),fields.size());
            for(int i=0;i<fields.size();i++)fields.get(i).box.setFocused(i==next);return true;
        }
        EditBox focus=focused();return focus!=null&&focus.keyPressed(event);
    }
    public boolean charTyped(CharacterEvent event){EditBox focus=focused();return focus!=null&&focus.charTyped(event);}
    public boolean preedit(PreeditEvent event){EditBox focus=focused();return focus!=null&&focus.preeditUpdated(event);}
}
