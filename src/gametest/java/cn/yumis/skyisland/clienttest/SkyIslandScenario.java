package cn.yumis.skyisland.clienttest;
import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.block.*;
import cn.yumis.skyisland.client.*;
import cn.yumis.skyisland.core.*;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.server.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.inventory.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import com.google.gson.*;
import org.lwjgl.glfw.GLFW;
import java.util.UUID;

/** A real integrated server and client, isolated from the user's launcher instance. */
@SuppressWarnings("UnstableApiUsage")
public final class SkyIslandScenario implements FabricClientGameTest {
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    @Override public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1920,1080);
        context.runOnClient(mc->{mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.resizeGui();mc.options.languageCode="zh_cn";mc.getLanguageManager().setSelected("zh_cn");});
        var language=context.computeOnClient(mc->mc.reloadResourcePacks());context.waitFor(mc->language.isDone(),800);language.join();
        try(var world=context.worldBuilder().adjustSettings(settings->{
            var sky=settings.getNormalPresetList().stream().filter(p->p.preset().unwrapKey().map(k->k.identifier().equals(YumisSkyIsland.id("sky_island"))).orElse(false)).findFirst().orElseThrow();
            settings.setWorldType(sky);settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);settings.setAllowCommands(false);
        }).create()){
            TestServerContext server=world.getServer();TestServerConnection connection=world.getConnection();
            server.waitFor(s->SkyServer.get(s)!=null&&SkyServer.get(s).state().lobbyReady&&SkyServer.get(s).store.available(),1200);
            server.runOnServer(s->{var p=connection.getServerPlayer();check(!Protection.admin(p),"Test player must have ordinary permissions");check(ClaimGrid.isLobby(p.blockPosition().getX(),p.blockPosition().getZ()),"First spawn must be in the far lobby");check(!Protection.canBuild(s.overworld(),p,p.blockPosition().below()),"Lobby must reject ordinary building");SkyServer.get(s).parties.home(p);});
            server.waitFor(s->{var rt=SkyServer.get(s);var p=rt.parties.active(connection.getServerPlayer().getUUID());return p!=null&&p.starterReady&&rt.refresh.isRunning(p.id);},1600);
            BlockPos scored=server.computeOnServer(s->{var rt=SkyServer.get(s);var party=rt.parties.active(connection.getServerPlayer().getUUID());var region=ClaimGrid.region(party.slot);var pos=new BlockPos(region.centerX()-2,82,region.centerZ()-2);s.overworld().setBlock(pos,Blocks.GOLD_BLOCK.defaultBlockState(),3);return pos;});
            server.waitFor(s->{var rt=SkyServer.get(s);var p=rt.parties.active(connection.getServerPlayer().getUUID());return !rt.refresh.isRunning(p.id)&&rt.store.available();},6000);
            server.runOnServer(s->{var rt=SkyServer.get(s);var party=rt.parties.active(connection.getServerPlayer().getUUID());String group=rt.config.blocks.get("minecraft:gold_block").group();check(party.blockCounts.getOrDefault(group,0L)==1,"Refresh must retain a concurrent placement exactly once");s.overworld().setBlock(scored,Blocks.AIR.defaultBlockState(),3);check(party.blockCounts.getOrDefault(group,0L)==0,"Breaking a scored block must remove its points");});
            connection.waitForChunksDownload();connection.waitForChunksRender();
            server.runOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();var party=rt.parties.active(p.getUUID());var region=ClaimGrid.region(party.slot);int dirt=0;for(int x=region.centerX()-3;x<region.centerX()+3;x++)for(int z=region.centerZ()-3;z<region.centerZ()+3;z++)for(int y=75;y<81;y++)if(s.overworld().getBlockState(new BlockPos(x,y,z)).is(Blocks.DIRT))dirt++;check(dirt==216,"Starter island must contain exactly 6×6×6 dirt");check(Protection.canBuild(s.overworld(),p,p.blockPosition().below()),"Members can build on their island");rt.change(p,state->state.parties.get(party.id).coins=145235522L,()->{});});
            server.waitFor(s->SkyServer.get(s).store.available(),400);
            server.runOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();rt.trades.buy(p,"minecraft:cobblestone",16);});
            server.waitFor(s->SkyServer.get(s).store.available(),400);
            server.runOnServer(s->{var p=connection.getServerPlayer();int count=0;for(int i=0;i<36;i++)if(p.getInventory().getItem(i).is(Items.COBBLESTONE))count+=p.getInventory().getItem(i).getCount();check(count==16,"Server purchase must deliver the requested quantity");});
            int catalogSize=server.computeOnServer(s->SkyServer.get(s).economy.entries.size());
            context.waitFor(mc->SkyIslandClient.CATALOG.products.size()==catalogSize,800);
            server.runOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();
                // An actual world binds the enchantment/item tags used by Fabric's native checks.
                var book=new ItemStack(Items.BOOK);
                check(book.get(net.minecraft.core.component.DataComponents.ENCHANTABLE).value()==1,"Fishing power assumes native book enchantability 1");
                var tag=s.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.tags.EnchantmentTags.ON_RANDOM_LOOT);
                for(int power=26;power<=36;power++)for(var candidate:net.minecraft.world.item.enchantment.EnchantmentHelper.getAvailableEnchantmentResults(power,book,tag.stream())){
                    String id=candidate.enchantment().unwrapKey().orElseThrow().identifier().getPath();
                    var entry=rt.economy.entries.get("minecraft:enchanted_book__"+id+"_"+candidate.level());
                    if(entry!=null)check(!entry.product().canBuy(),"A fishable book level must not be sold: "+id+" "+candidate.level());
                }
                for(String id:java.util.List.of("oak_door","diamond_pickaxe","zombie_spawn_egg","iron_block")){
                    boolean rejected=false;try{rt.trades.buy(p,"minecraft:"+id,1);}catch(IllegalArgumentException expected){rejected=true;}
                    check(rejected,"Removed purchase must be rejected: "+id);
                }
            });
            long[] seedSale=server.computeOnServer(s->{var rt=SkyServer.get(s);var party=rt.parties.active(connection.getServerPlayer().getUUID());var entry=rt.economy.entries.get("minecraft:wheat_seeds");return new long[]{party.coins,rt.economy.buyPrice(entry),rt.economy.quote(entry.template()).normalSale().floorLongExact()};});
            server.runOnServer(s->SkyServer.get(s).trades.buy(connection.getServerPlayer(),"minecraft:wheat_seeds",16));
            server.waitFor(s->SkyServer.get(s).store.available(),400);
            server.runOnServer(s->{var rt=SkyServer.get(s);var party=rt.parties.active(connection.getServerPlayer().getUUID());check(party.coins==seedSale[0]-seedSale[1]*16,"The entire first batch must pay the premium");check(seedSale[1]>=10000&&seedSale[2]==1,"The premium must not inflate seed buyback");check(rt.economy.buyPrice(rt.economy.entries.get("minecraft:wheat_seeds"),party)==rt.economy.full(rt.economy.entries.get("minecraft:wheat_seeds"),rt.economy.entries.get("minecraft:wheat_seeds").template()).floorLongExact(),"First acquisition must permanently remove the seed premium");});
            context.runOnClient(mc->{
                var model=SkyIslandClient.CATALOG;
                check(!model.products.containsKey("minecraft:diamond_pickaxe"),"Deleted items must leave the client catalog");
                check(model.products.get("minecraft:oak_planks").sell()&&model.products.get("minecraft:oak_planks").buy(),"Basic planks must support buying and selling");
                for(var tab:model.shopTabs())check(!java.util.Set.of("minecraft:colored_blocks","minecraft:functional_blocks","minecraft:tools_and_utilities","minecraft:combat").contains(tab.id()),"Removed shop category must not remain: "+tab.id());
                var lines=new java.util.ArrayList<Component>();model.tooltip(new ItemStack(Items.IRON_BLOCK),lines);
                check(lines.stream().noneMatch(line->line.getString().startsWith("价值：")||line.getString().startsWith("常规收购：")),"Deleted items must have no economic tooltip");
                lines.clear();model.tooltip(new ItemStack(Items.WHEAT_SEEDS),lines);
                check(lines.stream().anyMatch(line->line.getString().startsWith("常规收购：1")),"Client seed buyback must use the ordinary basis");
            });
            context.getInput().lookAt(180,5);
            context.runOnClient(mc->{SkyIslandClient.CATALOG.cellSize=ClientCatalog.DEFAULT_CELL;SkyIslandClient.CATALOG.favorites.clear();SkyIslandClient.CATALOG.revision++;});
            context.getInput().pressKey(GLFW.GLFW_KEY_I);context.waitForScreen(ShopScreen.class);context.getInput().setCursorPos(10,10);context.waitTicks(5);
            // Server confirmation must not close the custom screen as a side effect of inventory normalization.
            server.runOnServer(s->SkyServer.get(s).trades.buy(connection.getServerPlayer(),"minecraft:cobblestone",1));
            server.waitFor(s->SkyServer.get(s).store.available(),400);context.waitTicks(5);
            check(context.computeOnClient(mc->mc.gui.screen() instanceof ShopScreen),"Purchases must leave the shop open");
            var favorites=context.computeOnClient(mc->java.util.Set.copyOf(SkyIslandClient.CATALOG.favorites));
            double[] itemCursor=context.computeOnClient(mc->{var w=mc.getWindow();int width=w.getGuiScaledWidth(),sidebar=width<500?106:132,start=sidebar+12,size=SkyIslandClient.CATALOG.cellSize,columns=Math.min(30,(width-start-12)/size),left=start+(width-start-12-columns*size)/2;return new double[]{(left+size/2.0)*w.getWidth()/width,126.0*w.getHeight()/w.getGuiScaledHeight()};});
            context.getInput().setCursorPos(itemCursor[0],itemCursor[1]);context.waitTicks(2);context.getInput().pressKey(GLFW.GLFW_KEY_A);context.waitTicks(2);
            check(context.computeOnClient(mc->!SkyIslandClient.CATALOG.favorites.equals(favorites)),"Hover A must toggle a favorite");
            context.getInput().pressKey(GLFW.GLFW_KEY_A);context.waitTicks(2);
            check(context.computeOnClient(mc->SkyIslandClient.CATALOG.favorites.equals(favorites)),"A second press must remove the same favorite");
            context.getInput().setCursorPos(10,10);
            context.takeScreenshot("shop-wide");
            double[] slider=context.computeOnClient(mc->{var w=mc.getWindow();return new double[]{(w.getGuiScaledWidth()-137+60.0)*w.getWidth()/w.getGuiScaledWidth(),54.0*w.getHeight()/w.getGuiScaledHeight()};});
            context.getInput().setCursorPos(slider[0],slider[1]);context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);context.waitTicks(2);
            check(context.computeOnClient(mc->SkyIslandClient.CATALOG.cellSize==68),"The size slider must update the cell size");
            context.getInput().setCursorPos(10,10);context.takeScreenshot("shop-large-cells");
            context.runOnClient(mc->{SkyIslandClient.CATALOG.cellSize=ClientCatalog.DEFAULT_CELL;});context.getInput().resizeWindow(1280,720);context.waitTicks(3);context.takeScreenshot("shop-small-window");
            context.runOnClient(mc->mc.gui.setScreen(null));
            // Native sale UI: the untouched inventory is visible before selecting and confirming.
            int materialSlot=server.computeOnServer(s->{var p=connection.getServerPlayer();for(int i=0;i<36;i++)if(p.getInventory().getItem(i).is(Items.COBBLESTONE)){p.getInventory().getItem(i).setCount(64);p.inventoryMenu.broadcastChanges();return i;}throw new AssertionError("Missing purchased cobblestone");});
            long saleWallet=server.computeOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();long before=rt.parties.active(p.getUUID()).coins;rt.trades.sell(p,false);check(p.getInventory().getItem(materialSlot).getCount()==64,"Opening sale must not consume anything");return before;});
            context.waitForScreen(SaleScreen.class);context.waitTicks(3);context.takeScreenshot("sale-inventory");
            double[] saleCursor=context.computeOnClient(mc->{var w=mc.getWindow();int gw=w.getGuiScaledWidth(),gh=w.getGuiScaledHeight(),cell=Math.max(20,Math.min(38,Math.min((gw-24)/9,(gh-190)/4)));return new double[]{((gw-cell*9)/2.0+materialSlot%9*cell+10)*w.getWidth()/gw,(35+materialSlot/9*cell+10.0)*w.getHeight()/gh};});
            context.getInput().setCursorPos(saleCursor[0],saleCursor[1]);context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitFor(mc->SkyIslandClient.CATALOG.sale.has("selection")&&SkyIslandClient.CATALOG.sale.get("selection").getAsString().equals(materialSlot+":64"),400);
            double[] decrease=context.computeOnClient(mc->{var w=mc.getWindow();int gw=w.getGuiScaledWidth(),gh=w.getGuiScaledHeight(),cell=Math.max(20,Math.min(38,Math.min((gw-24)/9,(gh-190)/4)));return new double[]{25.0*w.getWidth()/gw,(39+4*cell+9.0)*w.getHeight()/gh};});
            context.getInput().setCursorPos(decrease[0],decrease[1]);context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitFor(mc->SkyIslandClient.CATALOG.sale.has("selection")&&SkyIslandClient.CATALOG.sale.get("selection").getAsString().equals(materialSlot+":63"),400);
            long quotedSale=context.computeOnClient(mc->SkyIslandClient.CATALOG.sale.get("coins").getAsLong());
            context.getInput().setCursorPos(10,10);context.takeScreenshot("sale-priced-confirmation");
            double[] confirmSale=context.computeOnClient(mc->{var w=mc.getWindow();return new double[]{60.0*w.getWidth()/w.getGuiScaledWidth(),(w.getGuiScaledHeight()-17.0)*w.getHeight()/w.getGuiScaledHeight()};});
            context.getInput().setCursorPos(confirmSale[0],confirmSale[1]);context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            server.waitFor(s->{var rt=SkyServer.get(s);return rt.store.available()&&rt.parties.active(connection.getServerPlayer().getUUID()).coins==saleWallet+quotedSale;},400);
            server.runOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();check(p.getInventory().getItem(materialSlot).getCount()==1,"Partial sale must preserve the unselected quantity");check(rt.parties.active(p.getUUID()).soldProductIds.contains("minecraft:cobblestone"),"Sale must record concrete product history");rt.trades.preview(p,materialSlot+":1",false);});
            context.waitFor(mc->SkyIslandClient.CATALOG.sale.has("selection")&&SkyIslandClient.CATALOG.sale.get("selection").getAsString().equals(materialSlot+":1"),400);
            String staleQuote=context.computeOnClient(mc->SkyIslandClient.CATALOG.sale.get("token").getAsString());
            server.runOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();var party=rt.parties.active(p.getUUID());String group=rt.config.quotaUnits.getOrDefault("minecraft:cobblestone",new cn.yumis.skyisland.config.GameConfig.QuotaUnit("minecraft:cobblestone",Rational.ONE)).group();rt.change(p,state->state.parties.get(party.id).quota.put(group,"10000"),()->{});});
            server.waitFor(s->SkyServer.get(s).store.available(),400);
            server.runOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();long before=rt.parties.active(p.getUUID()).coins;rt.trades.confirmSale(p,staleQuote);check(rt.parties.active(p.getUUID()).coins==before&&p.getInventory().getItem(materialSlot).getCount()==1,"Stale quota quote must not silently sell or reprice");var party=rt.parties.active(p.getUUID());rt.change(p,state->state.parties.get(party.id).quota.clear(),()->{});});
            server.waitFor(s->SkyServer.get(s).store.available(),400);
            // A failed full-inventory reward must consume neither a task nor any material.
            server.runOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();var party=rt.parties.active(p.getUUID());var saved=rt.economy.capture(p.getInventory());var requirement=rt.config.object("tasks/quests.json").getAsJsonArray("quests").get(0).getAsJsonObject().getAsJsonArray("requirements").get(0).getAsJsonObject();
                try{for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));requirement.addProperty("quantity",1);boolean rejected=false;try{rt.progression.claimQuest(p,"first_cobblestone@"+party.overworldEpoch);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Full inventory must reject a reward that cannot fit");check(p.getInventory().getItem(0).getCount()==64&&!party.completedQuestIds.contains("first_cobblestone"),"Failed reward must be atomic");}
                finally{requirement.addProperty("quantity",64);rt.economy.restore(p.getInventory(),saved);}
                p.getInventory().setItem(materialSlot,new ItemStack(Items.COBBLESTONE,64));p.inventoryMenu.broadcastChanges();rt.syncAll();
            });
            context.waitFor(mc->SkyIslandClient.CATALOG.state.has("progression"),400);
            context.runOnClient(mc->mc.gui.setScreen(new TaskScreen(SkyIslandClient.CATALOG)));context.waitTicks(3);context.takeScreenshot("quest-first");
            double[] questSubmit=context.computeOnClient(mc->{var w=mc.getWindow();return new double[]{90.0*w.getWidth()/w.getGuiScaledWidth(),(w.getGuiScaledHeight()-23.0)*w.getHeight()/w.getGuiScaledHeight()};});
            context.getInput().setCursorPos(questSubmit[0],questSubmit[1]);context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);context.waitTicks(2);context.takeScreenshot("quest-confirmation");
            double[] taskConfirm=context.computeOnClient(mc->{var w=mc.getWindow();int gw=w.getGuiScaledWidth(),gh=w.getGuiScaledHeight(),cw=Math.min(360,gw-24);return new double[]{((gw-cw)/2.0+50)*w.getWidth()/gw,((gh-104)/2.0+84)*w.getHeight()/gh};});
            context.getInput().setCursorPos(taskConfirm[0],taskConfirm[1]);context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            server.waitFor(s->{var rt=SkyServer.get(s);return rt.store.available()&&rt.parties.active(connection.getServerPlayer().getUUID()).completedQuestIds.contains("first_cobblestone");},400);
            server.runOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();int gold=0;for(int i=0;i<36;i++){var stack=p.getInventory().getItem(i);if(stack.is(Items.GOLDEN_PICKAXE)){gold++;check(stack.getDamageValue()==0,"Reward tool must have full durability");var values=new java.util.HashMap<String,Integer>();for(var e:stack.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS).entrySet())values.put(e.getKey().unwrapKey().orElseThrow().identifier().toString(),e.getIntValue());check(values.get("minecraft:unbreaking")==4&&values.get("minecraft:efficiency")==5,"Task must deliver Unbreaking IV and Efficiency V");}}check(gold==1,"Exactly one task reward must be granted");boolean rejected=false;try{rt.progression.claimQuest(p,"first_cobblestone@"+rt.parties.active(p.getUUID()).overworldEpoch);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Completed quest must not be claimable twice");});
            server.waitFor(s->{var rt=SkyServer.get(s);var p=rt.parties.active(connection.getServerPlayer().getUUID());return rt.store.available()&&p.bounties.size()>=3&&p.bounties.getFirst().material!=null;},800);
            double[] bountyTab=context.computeOnClient(mc->{var w=mc.getWindow();return new double[]{132.0*w.getWidth()/w.getGuiScaledWidth(),43.0*w.getHeight()/w.getGuiScaledHeight()};});
            context.getInput().setCursorPos(bountyTab[0],bountyTab[1]);context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);context.waitTicks(3);context.getInput().setCursorPos(10,10);context.takeScreenshot("bounty-board");
            long[] slotPurchase=server.computeOnServer(s->{var rt=SkyServer.get(s);var party=rt.parties.active(connection.getServerPlayer().getUUID());return new long[]{party.coins,rt.progression.slots(party),party.bounties.getFirst().expiresAt};});
            server.runOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();var party=rt.parties.active(p.getUUID());rt.progression.buySlot(p,0,party.overworldEpoch);});
            server.waitFor(s->SkyServer.get(s).store.available(),400);
            server.runOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();var party=rt.parties.active(p.getUUID());check(party.coins==slotPurchase[0]-100000&&rt.progression.slots(party)==slotPurchase[1]+1,"Buying a slot must debit the selected tier");check(party.bounties.getFirst().expiresAt==slotPurchase[2],"Existing slot clocks must not reset when buying another");check(rt.progression.slotCost(party)==500000,"Next paid tier must be 500000");boolean rejected=false;try{rt.progression.buySlot(p,0,party.overworldEpoch);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"A second actor's stale paid-slot index must not buy the next tier");int level=party.highestIslandLevel,paid=party.paidBountySlots;party.highestIslandLevel=100;party.paidBountySlots=47;try{check(rt.progression.slots(party)==50,"Free and paid slots must cap at 50");rejected=false;try{rt.progression.buySlot(p,47,party.overworldEpoch);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"A full board must reject further payment");}finally{party.highestIslandLevel=level;party.paidBountySlots=paid;}});
            long[] frozen=server.computeOnServer(s->{var rt=SkyServer.get(s);var b=rt.parties.active(connection.getServerPlayer().getUUID()).bounties.getFirst();return new long[]{b.generation,b.quantity,b.rewardPrice};});
            server.runOnServer(s->SkyServer.get(s).state().effectiveTicks+=24000);
            server.waitFor(s->{var rt=SkyServer.get(s);return rt.store.available()&&rt.parties.active(connection.getServerPlayer().getUUID()).marketEpoch==rt.epoch();},400);
            String bountyClaim=server.computeOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();var party=rt.parties.active(p.getUUID());var b=party.bounties.getFirst();check(b.generation==frozen[0]&&b.quantity==frozen[1]&&b.rewardPrice==frozen[2],"Daily price changes must leave a seven-day bounty frozen");check(b.quantity>=1&&b.quantity<=1536,"Exchange demand must be capped without zero trades");int left=b.quantity;var entry=rt.economy.entries.get(b.material);while(left>0){int n=Math.min(left,64);p.getInventory().placeItemBackInInventory(entry.template().copyWithCount(n));left-=n;}return b.slot+":"+b.generation+":"+party.overworldEpoch;});
            server.runOnServer(s->SkyServer.get(s).progression.claimBounty(connection.getServerPlayer(),bountyClaim));
            server.waitFor(s->SkyServer.get(s).store.available(),400);
            server.runOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();var party=rt.parties.active(p.getUUID());check(party.bounties.getFirst().generation==frozen[0]+1,"Completing a bounty must immediately roll the same slot");boolean rejected=false;try{rt.progression.claimBounty(p,bountyClaim);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"The old bounty generation must not deliver twice");rt.refresh.request(p);});
            server.waitFor(s->{var rt=SkyServer.get(s);var p=rt.parties.active(connection.getServerPlayer().getUUID());return rt.store.available()&&p.refresh!=null&&p.refresh.cursor>0&&p.refresh.cursor<p.refresh.chunks.size();},1200);
            int pausedCursor=server.computeOnServer(s->{var old=SkyServer.get(s);try{old.close();SkyServer.start(s,old.config);}catch(java.io.IOException e){throw new RuntimeException(e);}var rt=SkyServer.get(s);rt.joined(connection.getServerPlayer());return rt.parties.active(connection.getServerPlayer().getUUID()).refresh.cursor;});
            context.waitTicks(30);
            BlockPos pausedChange=server.computeOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();var party=rt.parties.active(p.getUUID());check(party.refresh.cursor==pausedCursor,"A recovered scan must stay paused without a command");long key=party.refresh.scanned.iterator().next();var pos=new BlockPos(net.minecraft.world.level.ChunkPos.getX(key)*16+8,94,net.minecraft.world.level.ChunkPos.getZ(key)*16+8);rt.change(p,_->{},()->{});s.overworld().setBlock(pos,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);return pos;});
            server.waitFor(s->SkyServer.get(s).store.available(),600);
            server.runOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();var party=rt.parties.active(p.getUUID());String group=rt.config.blocks.get("minecraft:diamond_block").group();check(party.refresh.delta.getOrDefault(group,0L)==1,"Paused changes during a save must survive snapshot publication");rt.refresh.request(p);});
            server.waitFor(s->{var rt=SkyServer.get(s);return rt.store.available()&&rt.parties.active(connection.getServerPlayer().getUUID()).refresh==null;},6000);
            server.runOnServer(s->{var rt=SkyServer.get(s);var party=rt.parties.active(connection.getServerPlayer().getUUID());String group=rt.config.blocks.get("minecraft:diamond_block").group();check(party.blockCounts.getOrDefault(group,0L)==1,"A resumed scan must apply paused deltas exactly once");int highest=party.highestIslandLevel;s.overworld().setBlock(pausedChange,Blocks.AIR.defaultBlockState(),3);check(party.highestIslandLevel==highest,"Breaking blocks must not lower historical level");});
            context.runOnClient(mc->mc.gui.setScreen(null));
            server.runOnServer(s->{var p=connection.getServerPlayer();var worldLevel=s.overworld();
                var villager=net.minecraft.world.entity.EntityTypes.VILLAGER.create(worldLevel,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                var trader=net.minecraft.world.entity.EntityTypes.WANDERING_TRADER.create(worldLevel,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                var piglin=net.minecraft.world.entity.EntityTypes.PIGLIN.create(worldLevel,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                for(var npc:java.util.List.of(villager,trader,piglin))npc.setPos(p.getX(),p.getY(),p.getZ());
                var interaction=net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker();
                check(interaction.interact(p,worldLevel,net.minecraft.world.InteractionHand.MAIN_HAND,villager,null)==net.minecraft.world.InteractionResult.FAIL,"Villager trading entry must be blocked");
                check(interaction.interact(p,worldLevel,net.minecraft.world.InteractionHand.MAIN_HAND,trader,null)==net.minecraft.world.InteractionResult.FAIL,"Wandering trader entry must be blocked");
                check(interaction.interact(p,worldLevel,net.minecraft.world.InteractionHand.MAIN_HAND,piglin,null)==net.minecraft.world.InteractionResult.PASS,"Piglin interaction must remain available");
                for(var merchant:java.util.List.of(villager,trader)){
                    merchant.setTradingPlayer(p);
                    // Server-side AbstractVillager.overrideOffers is a no-op; modify its real offer list.
                    var offers=merchant.getOffers();offers.clear();
                    offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD,1),new ItemStack(Items.DIAMOND),16,0,0f));
                    var menu=new MerchantMenu(998,p.getInventory(),merchant);menu.getSlot(0).set(new ItemStack(Items.EMERALD));
                    check(menu.getSlot(2).getItem().is(Items.DIAMOND),"Native trade ingredients must produce a real result before testing the guard");
                    check(!menu.stillValid(p),"An existing merchant menu must be invalid");
                    check(menu.quickMoveStack(p,2).isEmpty()&&menu.getSlot(2).getItem().is(Items.DIAMOND)&&menu.getSlot(0).getItem().getCount()==1,"Shift extraction must preserve the blocked trade result and payment");
                }
            });
            // Use real recipe evaluation and native result-slot clicks, including the profession Mixins.
            server.runOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();
                var table=p.blockPosition().offset(-1,0,0);s.overworld().setBlock(table,Blocks.CRAFTING_TABLE.defaultBlockState(),3);
                rt.state().players.get(p.getUUID()).professionXp.put("blacksmith",56800L);
                JsonArray bands=rt.config.object("products/professions.json").getAsJsonArray("qualityChanceBands");
                for(JsonElement band:bands){var w=new JsonArray();w.add(0);w.add(10000);w.add(0);w.add(0);w.add(0);band.getAsJsonObject().add("weights",w);}
                long initial=rt.state().players.get(p.getUUID()).professionXp.get("blacksmith");
                ItemStack below=craftPickaxe(p,table);
                check(cn.yumis.skyisland.shop.ItemEconomy.grade(below)==0,"Diamond pickaxes below profession level 65 must remain ordinary");
                check(rt.state().players.get(p.getUUID()).professionXp.get("blacksmith")==initial+12,"One successful craft must award XP exactly once");
                rt.state().players.get(p.getUUID()).professionXp.put("blacksmith",58500L);
                ItemStack unlocked=craftPickaxe(p,table);
                check(cn.yumis.skyisland.shop.ItemEconomy.grade(unlocked)==1,"Level 65 must unlock the configured quality roll");
                check(unlocked.getMaxDamage()>Items.DIAMOND_PICKAXE.components().get(net.minecraft.core.component.DataComponents.MAX_DAMAGE),"Quality must increase durability");
            });
            // One rising edge sells once. Repeated neighbor callbacks under a constant signal must not sell again.
            BlockPos chest=server.computeOnServer(s->{var p=connection.getServerPlayer();return p.blockPosition().offset(1,0,0);});
            long before=server.computeOnServer(s->SkyServer.get(s).parties.active(connection.getServerPlayer().getUUID()).coins);
            server.runOnServer(s->{var rt=SkyServer.get(s);s.overworld().setBlock(chest,ModBlocks.SHIPPING_CHEST.defaultBlockState(),3);var box=(ShippingChestEntity)s.overworld().getBlockEntity(chest);box.serverTick();box.setItem(0,new ItemStack(Items.IRON_INGOT,10));box.setItem(1,new ItemStack(Items.IRON_BLOCK));s.overworld().setBlock(chest.above(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);box.powerChanged();});
            server.waitFor(s->{var box=(ShippingChestEntity)s.overworld().getBlockEntity(chest);return box.getItem(0).isEmpty()&&!box.locked;},400);
            long after=server.computeOnServer(s->SkyServer.get(s).parties.active(connection.getServerPlayer().getUUID()).coins);check(after>before,"Shipping chest must credit the party");
            server.runOnServer(s->{var box=(ShippingChestEntity)s.overworld().getBlockEntity(chest);check(box.getItem(1).is(Items.IRON_BLOCK),"Shipping must retain deleted-price items");box.setItem(0,new ItemStack(Items.IRON_INGOT,10));box.powerChanged();});context.waitTicks(20);
            server.runOnServer(s->{var box=(ShippingChestEntity)s.overworld().getBlockEntity(chest);check(box.getItem(0).getCount()==10,"Constant signal must not re-sell newly inserted materials");check(SkyServer.get(s).parties.active(connection.getServerPlayer().getUUID()).coins==after,"Constant signal must not credit twice");s.overworld().setBlock(chest.above(),Blocks.AIR.defaultBlockState(),3);box.powerChanged();s.overworld().setBlock(chest.above(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);box.powerChanged();});
            server.waitFor(s->((ShippingChestEntity)s.overworld().getBlockEntity(chest)).getItem(0).isEmpty(),400);
            // All market clocks are tick based, so /time commands cannot re-roll the daily shop.
            String marketBefore=server.computeOnServer(s->{var party=SkyServer.get(s).parties.active(connection.getServerPlayer().getUUID());return party.marketEpoch+":"+party.dailyStock;});
            server.runCommand("time set 1000000");context.waitTick();
            String marketAfter=server.computeOnServer(s->{var party=SkyServer.get(s).parties.active(connection.getServerPlayer().getUUID());return party.marketEpoch+":"+party.dailyStock;});check(marketBefore.equals(marketAfter),"Time jumps must not re-roll offers");
            // Both extra dimensions remain void; routing uses the party region rather than 8:1 coordinates.
            server.runOnServer(s->SkyServer.get(s).worlds.portal(s.overworld(),connection.getServerPlayer(),"nether"));
            server.waitFor(s->connection.getServerPlayer().level().dimension().equals(Level.NETHER),600);
            BlockPos netherMarker=server.computeOnServer(s->{var p=connection.getServerPlayer();var pos=p.blockPosition().offset(1,-1,0);s.getLevel(Level.NETHER).setBlock(pos,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);check(SkyServer.get(s).claim(p.blockPosition()).members.contains(p.getUUID()),"Nether exit must remain in the member's claim");check(p.level().getBlockState(p.blockPosition().below(30)).isAir(),"Nether must have no terrain below its safety platform");return pos;});
            server.runOnServer(s->SkyServer.get(s).worlds.portal(connection.getServerPlayer().level(),connection.getServerPlayer(),"end"));
            server.waitFor(s->connection.getServerPlayer().level().dimension().equals(Level.END),600);
            server.runOnServer(s->{var p=connection.getServerPlayer();check(s.getLevel(Level.END).getDragonFight()==null,"End dragon fight must not generate a central island");check(p.level().getBlockState(p.blockPosition().below(30)).isAir(),"End must have no natural terrain");SkyServer.get(s).worlds.portal(p.level(),p,"end");});
            server.waitFor(s->connection.getServerPlayer().level().dimension().equals(Level.OVERWORLD),600);
            server.waitFor(s->SkyServer.get(s).store.available(),400);
            long xpBefore=server.computeOnServer(s->SkyServer.get(s).state().players.get(connection.getServerPlayer().getUUID()).professionXp.get("blacksmith"));
            UUID animal=server.computeOnServer(s->{var p=connection.getServerPlayer();var chicken=net.minecraft.world.entity.EntityTypes.CHICKEN.create(s.overworld(),net.minecraft.world.entity.EntitySpawnReason.COMMAND);chicken.setNoAi(true);chicken.setPos(p.getX(),p.getY(),p.getZ()+1);s.overworld().addFreshEntity(chicken);return chicken.getUUID();});
            int itemsBefore=server.computeOnServer(s->{var p=connection.getServerPlayer();int count=0;for(int i=0;i<36;i++)count+=p.getInventory().getItem(i).getCount();return count;});
            server.runOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();String code=rt.resets.propose(p);rt.resets.confirm(p,code);});
            server.waitFor(s->SkyServer.get(s).parties.active(connection.getServerPlayer().getUUID()).resetInProgress,400);
            server.waitFor(s->{var rt=SkyServer.get(s);var party=rt.parties.active(connection.getServerPlayer().getUUID());return !party.resetInProgress&&party.starterReady&&!rt.refresh.isRunning(party.id)&&rt.store.available();},10000);
            server.runOnServer(s->{var rt=SkyServer.get(s);var p=connection.getServerPlayer();var party=rt.parties.active(p.getUUID());check(party.coins==0,"Reset must clear party coins");check(s.overworld().getBlockState(chest).isAir(),"Reset must remove the old shipping chest");check(s.overworld().getEntity(animal)==null,"Reset must remove old non-player entities");check(s.getLevel(Level.NETHER).getBlockState(netherMarker).is(Blocks.DIAMOND_BLOCK),"Reset must preserve Nether builds");check(rt.state().players.get(p.getUUID()).professionXp.get("blacksmith")==xpBefore,"Reset must preserve personal professions");int count=0;for(int i=0;i<36;i++)count+=p.getInventory().getItem(i).getCount();check(count==itemsBefore+2,"Reset must preserve inventory and grant only the two new starter buckets");});
            server.runOnServer(s->{var rt=SkyServer.get(s);var party=rt.parties.active(connection.getServerPlayer().getUUID());check(party.paidBountySlots==0&&party.completedQuestIds.isEmpty()&&party.soldProductIds.isEmpty()&&party.acquiredStarterIds.isEmpty(),"Reset must clear all newly introduced team progress");check(party.highestIslandLevel==BlockScorer.level(rt.points(party),java.util.Arrays.stream(rt.config.levelThresholds).boxed().toList()),"Reset must discard the old highest level");check(party.bounties.size()==rt.progression.slots(party),"Reset must rebuild only starting and freshly earned level slots");});
        }
    }
    private static ItemStack craftPickaxe(ServerPlayer player,BlockPos table) {
        CraftingMenu menu=new CraftingMenu(997,player.getInventory(),ContainerLevelAccess.create(player.level(),table));
        var inputs=menu.getInputGridSlots();
        for(int i=0;i<3;i++)inputs.get(i).set(new ItemStack(Items.DIAMOND));
        inputs.get(4).set(new ItemStack(Items.STICK));inputs.get(7).set(new ItemStack(Items.STICK));
        menu.slotsChanged(inputs.getFirst().container);
        check(menu.getResultSlot().getItem().is(Items.DIAMOND_PICKAXE),"Native crafting recipe must produce a pickaxe");
        check(menu.getCarried().isEmpty(),"Craft starts with an empty cursor");
        menu.clicked(0,0,ContainerInput.PICKUP,player);
        ItemStack crafted=menu.getCarried().copy();
        check(crafted.is(Items.DIAMOND_PICKAXE),"Result click must transfer the produced item");
        check(inputs.stream().allMatch(slot->slot.getItem().isEmpty()),"Successful craft must consume its ingredients");
        return crafted;
    }
}
