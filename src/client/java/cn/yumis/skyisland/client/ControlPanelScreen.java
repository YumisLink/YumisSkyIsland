package cn.yumis.skyisland.client;

import cn.yumis.skyisland.core.Money;
import cn.yumis.skyisland.network.ShopPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Custom transparent shop with native-size text and two-line product names. */
public final class ControlPanelScreen extends Screen {
    private static final int TOP = 106, FOOTER = 40, SLIDER_WIDTH = 120;
    private final ClientCatalog model;
    private final TaskPanel tasks;
    private final ResidentPanel residents;
    public void showResident(String id){selectSection(4);residents.select(id);}
    private SalePanel salePanel;
    private PriceEditorPanel priceEditor;
    private boolean showAllConfigured;
    public void priceUpdated(com.google.gson.JsonObject data){if(priceEditor!=null)priceEditor.updated(data);}
    public void closePriceEditor(){priceEditor=null;model.priceBusy=false;model.configMessage="";}
    private final StoreView[] stores={new StoreView(),new StoreView()};
    private static final class StoreView {String category="all",search="";int scroll,categoryScroll;}
    private final ConfirmDialog confirmation=new ConfirmDialog();
    public ClientCatalog model(){return model;}
    public net.minecraft.client.gui.Font uiFont(){return font;}
    int contentLeft(){return sidebar()+12;}
    int contentWidth(){return width-contentLeft()-12;}
    public int section(){return mode;}
    public void selectSection(int next){
        if(next<0||next>4||next==mode)return;
        if(mode<2){var saved=stores[mode];saved.category=category;saved.search=search;saved.scroll=scroll;saved.categoryScroll=categoryScroll;}
        if(model.saleDesired)cancelSale(true);
        mode=next;detail=null;searchFocused=false;closePriceEditor();
        if(next<2){var saved=stores[next];category=saved.category;search=saved.search;scroll=saved.scroll;categoryScroll=saved.categoryScroll;}
        lastRevision=-1;
    }
    public void showSale(){if(mode>1)selectSection(0);model.saleDesired=true;detail=null;salePanel=new SalePanel(this);}
    public void cancelSale(boolean notify){model.saleDesired=false;model.sale=new com.google.gson.JsonObject();salePanel=null;if(notify)send("sale_cancel","",0);}
    public void saleUpdated(){if(salePanel!=null)salePanel.updated();}
    public void saleFailed(){if(salePanel!=null)salePanel.failed();}
    public void taskSaved(com.google.gson.JsonObject data){tasks.saved(data);}
    void confirm(String label,Runnable action){confirmation.open(label,action);}
    public void textLine(GuiGraphicsExtractor g,String label,int x,int y,int color,int maximum){text(g,label,x,y,color,maximum);}
    public int wrappedText(GuiGraphicsExtractor g,String label,int x,int y,int color,int maximum){var lines=font.split(Component.literal(label),Math.max(30,maximum));for(int i=0;i<lines.size();i++)g.text(font,lines.get(i),x,y+i*12,color,false);return lines.size()*12;}
    public void actionButton(GuiGraphicsExtractor g,String label,int x,int y,int w,boolean active){g.fill(x,y,x+w,y+22,active?UiTheme.BUTTON:UiTheme.DISABLED);text(g,label,x+5,y+7,active?0xFFE9EBEE:0xFF777F88,w-10);}
    public static boolean hit(double x,double y,int a,int b,int w,int h){return inside(x,y,a,b,w,h);}
    @Override public void tick(){if(!model.editing()&&(showAllConfigured||priceEditor!=null)){showAllConfigured=false;closePriceEditor();invalidate();}if(model.saleDesired&&salePanel!=null)salePanel.tick();if(mode==2)tasks.tick();}

    private List<ClientCatalog.Product> visible = List.of();
    private int mode, scroll, categoryScroll, lastRevision = -1, columns, left, rowHeight;
    private String category = "all", search = "";
    private boolean searchFocused, dragSlider;
    private ClientCatalog.Product hovered, detail;
    private int quantity = 1;

    public ControlPanelScreen(ClientCatalog model) { super(Component.literal("控制面板")); this.model = model; this.tasks=new TaskPanel(this);this.residents=new ResidentPanel(this); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float delta) { }
    int sidebar() { return width < 500 ? 106 : 132; }
    private int sliderX() { return width - 137; }
    private int tabWidth(){return Math.max(30,Math.min(86,((width<500?width-20:sliderX()-20)/5)-4));}
    private int iconSize() { return Math.min(48, Math.max(16, (model.cellSize - 8) / 16 * 16)); }
    private List<ClientCatalog.Category> categories() {
        var result = new ArrayList<ClientCatalog.Category>();
        result.add(new ClientCatalog.Category("all", "全部分类", net.minecraft.world.item.ItemStack.EMPTY));
        for(var c:model.tabs)if(model.products.values().stream().anyMatch(p -> shopEligible(p,false)&&model.category(p).equals(c.id())))result.add(c);
        result.add(new ClientCatalog.Category("favorites","收藏",net.minecraft.world.item.ItemStack.EMPTY));
        return result;
    }
    private boolean shopEligible(ClientCatalog.Product p,boolean favoriteView){
        if(p.id().startsWith("yumis_sky_island:biome_ball_")&&mode!=0)return false;
        if(mode==1&&model.editing()&&showAllConfigured)return true;
        if(!p.buy()||!model.showLockedItems&&p.level()>model.level())return false;
        if(mode==0)return p.rarity().equals("common")||p.id().startsWith("yumis_sky_island:biome_ball_");
        return model.state.has("stock")&&model.state.getAsJsonObject("stock").has(p.id())||favoriteView&&model.specialFavorites.contains(p.id());
    }
    private void layout() {
        model.cellSize = Math.clamp(model.cellSize, ClientCatalog.MIN_CELL, ClientCatalog.MAX_CELL);
        int start = sidebar() + 12;
        columns = Math.max(1, Math.min(30, (width - start - 12) / model.cellSize));
        left = start + Math.max(0, (width - start - 12 - columns * model.cellSize) / 2);
        rowHeight = iconSize() + 4 * font.lineHeight + 30;
        if (lastRevision != model.revision) {
            var items = new ArrayList<ClientCatalog.Product>();
            for (var p : model.products.values()) {
                if(!shopEligible(p,category.equals("favorites")))continue;
                if(category.equals("favorites")){if(!model.favorites(mode).contains(p.id()))continue;}
                else if(!category.equals("all")&&!model.category(p).equals(category))continue;
                if (!search.isBlank() && !p.name().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)) && !p.id().contains(search.toLowerCase(Locale.ROOT))) continue;
                items.add(p);
            }
            items.sort(Comparator.comparing((ClientCatalog.Product p) -> !model.favorites(mode).contains(p.id())).thenComparingInt(ClientCatalog.Product::level).thenComparing(ClientCatalog.Product::name));
            visible = items; lastRevision = model.revision;
        }
        int rows = Math.max(1, (height - FOOTER - TOP) / rowHeight), totalRows = (visible.size() + columns - 1) / columns;
        scroll = Math.clamp(scroll, 0, Math.max(0, totalRows - rows));
    }
    private void invalidate() { lastRevision = -1; scroll = 0; }
    private static boolean inside(double x, double y, int l, int t, int w, int h) { return x >= l && x < l + w && y >= t && y < t + h; }
    private String ellipsis(String value, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(value) <= maxWidth) return value;
        return font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("…"))) + "…";
    }
    private void text(GuiGraphicsExtractor g, String value, int x, int y, int color, int maxWidth) {
        g.text(font, ellipsis(value, maxWidth), x, y, color, false);
    }
    private void button(GuiGraphicsExtractor g, String label, int x, int y, int w, int h, boolean selected, boolean hot) {
        g.fill(x, y, x + w, y + h, selected ? UiTheme.SELECTED : hot ? UiTheme.HOVER : UiTheme.CARD);
        if (selected) g.fill(x, y + h - 1, x + w, y + h, 0xFF71DCC9);
        text(g, label, x + 6, y + (h - font.lineHeight) / 2, 0xFFE9EBEE, w - 12);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if(mode<2)layout(); hovered = null;
        g.fill(0, 0, width, height, 0xB0000000); g.fill(6, 6, width - 6, 32, UiTheme.CARD);
        text(g, "YUMIS · 控制面板", 14, 14, 0xFFF3F5F7, width / 2 - 16);
        String wallet = Money.format(model.wallet());
        text(g, wallet, width - Math.min(width / 2 - 16, font.width(wallet)) - 14, 14, 0xFFFFDC78, width / 2 - 16);
        String[] modes = {width<400?"常驻":"常驻商店", model.hidden() ? (width<400?"隐藏":"隐藏商店") : (width<400?"每日":"每日商店"), "任务", "悬赏", "居民"};
        int tabWidth = tabWidth();
        for (int i = 0; i < 5; i++) button(g, modes[i], 8 + i * (tabWidth + 4), 38, tabWidth, 24, mode == i, inside(mx, my, 8 + i * (tabWidth + 4), 38, tabWidth, 24));
        if (model.state.has("ticksLeft")) {
            long seconds = Math.max(0, model.state.get("ticksLeft").getAsLong()) / 20;
            long day = model.state.has("epoch") ? model.state.get("epoch").getAsLong() + 1 : 1;
            int x = 16 + 5 * (tabWidth + 4);
            text(g, "第 " + day + " 日 · 刷新 " + String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60), x, 46, 0xFFAFC4C1, sliderX() - x - 10);
        }
        if(mode<2&&width>=500){text(g, "格子 " + model.cellSize, sliderX(), 35, 0xFFC4C7CD, SLIDER_WIDTH);
        g.fill(sliderX(), 53, sliderX() + SLIDER_WIDTH, 56, UiTheme.PANEL);
        int knob = sliderX() + (model.cellSize - ClientCatalog.MIN_CELL) * SLIDER_WIDTH / (ClientCatalog.MAX_CELL - ClientCatalog.MIN_CELL);
        g.fill(knob - 2, 49, knob + 3, 61, 0xFF79D9C2);}
        if(mode>=2){if(mode==2)tasks.render(g,mx,my,delta);else if(mode==3)tasks.renderBounties(g,mx,my,delta);else residents.render(g,mx,my);drawMessages(g);drawConfirmation(g,mx,my);return;}
        if(model.saleDesired){if(salePanel==null)salePanel=new SalePanel(this);salePanel.render(g,mx,my,delta);drawConfirmation(g,mx,my);return;}
        if(priceEditor!=null){priceEditor.render(g,mx,my,delta);drawConfirmation(g,mx,my);return;}
        int sx = sidebar() + 12;
        g.fill(sx, 72, width - 128, 96, searchFocused ? UiTheme.CARD : UiTheme.CARD);
        text(g, search.isEmpty() && !searchFocused ? "搜索物品名 / ID" : search + (searchFocused ? "_" : ""), sx + 6, 79, 0xFFDDDEE0, width - sx - 140);
        boolean allButton=model.editing()&&mode==1;
        button(g,allButton?(showAllConfigured?"☑ 查看所有":"☐ 查看所有"):model.showLockedItems?"☑ 显示未解锁":"☐ 显示未解锁",width-120,72,108,24,allButton?showAllConfigured:model.showLockedItems,inside(mx,my,width-120,72,108,24));
        g.fill(6, 64, sidebar() + 6, height - 80, UiTheme.CARD);
        var categories = categories(); int available = Math.max(1, (height - 160) / 26);
        categoryScroll = Math.clamp(categoryScroll, 0, Math.max(0, categories.size() - available));
        for (int i = categoryScroll; i < Math.min(categories.size(), categoryScroll + available); i++) {
            var c = categories.get(i); int y = 72 + (i - categoryScroll) * 26;
            boolean active = category.equals(c.id());
            g.fill(10, y, sidebar() + 2, y + 24, active ? UiTheme.CARD : inside(mx, my, 10, y, sidebar() - 8, 24) ? UiTheme.CARD : 0x45181818);
            if (!c.icon().isEmpty()) g.item(c.icon(), 13, y + 4);
            text(g, c.name(), c.icon().isEmpty() ? 16 : 34, y + 8, active ? 0xFFB3F2E5 : 0xFFD3D5D7, sidebar() - (c.icon().isEmpty() ? 20 : 38));
        }
        text(g, "岛等级 " + model.level(), 14, height - 71, 0xFF8DDAC8, sidebar() - 20);
        if(model.state.has("currentLevelScoreScaled"))text(g,"本级 "+score(model.state.get("currentLevelScoreScaled").getAsString())+" / "+score(model.state.get("nextLevelScoreScaled").getAsString()),14,height-57,0xFFB9D7D0,sidebar()-20);
        int rows = Math.max(1, (height - FOOTER - TOP) / rowHeight), first = scroll * columns, last = Math.min(visible.size(), first + rows * columns);
        g.enableScissor(left, TOP, width - 8, height - FOOTER);
        for (int index = first; index < last; index++) {
            var p = visible.get(index); int local = index - first;
            int x = left + local % columns * model.cellSize, y = TOP + local / columns * rowHeight, w = model.cellSize - 2, h = rowHeight - 4;
            boolean hot = inside(mx, my, x, y, w, h), locked = !model.canBuy(p,mode,1);
            g.fill(x, y, x + w, y + h, hot ? UiTheme.HOVER : 0x731E1E1E);
            if (model.favorites(mode).contains(p.id())) g.fill(x, y, x + w, y + 2, 0xFFEBC36C);
            if (hot) hovered = p;
            int icon = iconSize();
            g.pose().pushMatrix(); g.pose().translate(x + (w - icon) / 2f, y + 5); g.pose().scale(icon / 16f, icon / 16f); g.item(p.stack(), 0, 0); g.pose().popMatrix();
            String firstLine = font.plainSubstrByWidth(p.name(), w - 6), rest = p.name().substring(firstLine.length());
            int nameColor = cn.yumis.skyisland.core.RarityColors.color(p.rarity());
            text(g, firstLine, x + 3, y + icon + 10, nameColor, w - 6);
            if (!rest.isEmpty()) text(g, rest, x + 3, y + icon + 12 + font.lineHeight, nameColor, w - 6);
            if(mode==1)text(g,"库存 "+model.stockText(p),x+3,y+h-2*font.lineHeight-8,model.stock(p)!=0?0xFFADB8C2:0xFF777F88,w-6);
            int bps = model.bps(p), color = locked ? 0xFF777B81 : bps < 10000 ? 0xFF68DF93 : bps > 10000 ? 0xFFFF7A76 : 0xFFF4D67A;
            text(g, p.buy()?compact(model.price(p,mode), w - 6):p.sell()?"仅收购":"停止供货", x + 3, y + h - font.lineHeight - 5, color, w - 6);
        }
        g.disableScissor();
        if (visible.isEmpty()) text(g, model.products.isEmpty() ? "正在同步商品资料…" : "此分类暂无商品；每日物资按队伍等级随机抽取", left + 8, TOP + 18, 0xFFA0A7AC, width - left - 24);
        g.fill(6, height - 35, width - 6, height - 5, UiTheme.CARD);
        button(g, "卖出背包材料", sidebar() + 12, height - 31, 120, 24, false, inside(mx, my, sidebar() + 12, height - 31, 120, 24));
        text(g, model.editing()?"编辑模式 · 点击商品修改购入基价":"A 收藏 · 左键详情 · 右键买1 · 滚轮翻页", sidebar() + 144, height - 23, 0xFFADB4BA, width - sidebar() - 162);
        if (detail != null) drawDetail(g, mx, my); else if (hovered != null) drawTooltip(g, mx, my, hovered);
        drawMessages(g);drawConfirmation(g,mx,my);
    }
    private String compact(long value, int maxWidth) {
        long unit = value >= 100_000_000 ? 100_000_000 : value >= 10_000 ? 10_000 : 1;
        if (unit == 1) return Long.toString(value);
        String suffix = unit == 100_000_000 ? "亿" : "万";
        for (int decimals = 2; decimals >= 0; decimals--) {
            String label = String.format(Locale.ROOT, "%." + decimals + "f", value / (double) unit) + suffix;
            if (font.width(label) <= maxWidth) return label;
        }
        return Long.toString(value / unit) + suffix;
    }
    private static String score(String scaled){
        java.math.BigDecimal amount=new java.math.BigDecimal(new java.math.BigInteger(scaled)).movePointLeft(4);
        java.math.BigDecimal yi=java.math.BigDecimal.valueOf(100_000_000),wan=java.math.BigDecimal.valueOf(10_000);
        if(amount.compareTo(yi)>=0)return amount.divide(yi,1,java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString()+"亿";
        if(amount.compareTo(wan)>=0)return amount.divide(wan,1,java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString()+"万";
        return amount.stripTrailingZeros().toPlainString();
    }
    private static String rarity(String value) { return switch (value) { case "rare" -> "稀有"; case "epic" -> "史诗"; case "legendary" -> "传说"; default -> "普通"; }; }
    private void drawTooltip(GuiGraphicsExtractor g, int mx, int my, ClientCatalog.Product p) {
        List<Component> lines = new ArrayList<>(); lines.add(Component.literal(p.name()));
        lines.add(Component.literal("种类稀有度：" + rarity(p.rarity()) + " · 解锁等级 " + p.level()));
        if (mode==1) lines.add(Component.literal("本队今日剩余库存：" + model.stockText(p)));
        int purchaseLine=lines.size();
        lines.add(Component.literal("购买：" + Money.format(model.price(p,mode))));
        lines.add(Component.literal("行情：" + String.format(Locale.ROOT, "%+.2f%%", (model.bps(p) - 10000) / 100.0)));
        model.tooltip(p.stack(), lines);
        int tw = Math.min(360, width - 20);
        var wrapped = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        var colors=new ArrayList<Integer>();
        for(int index=0;index<lines.size();index++)for(var line:font.split(lines.get(index),tw-16)){
            wrapped.add(line);colors.add(index==0?0xFFF4E7BA:index==purchaseLine?model.canBuy(p,mode,1)?0xFF9BE2C7:0xFFFFB0A0:0xFFCDD4D9);
        }
        int th = wrapped.size() * 12 + 14, x = Math.clamp(mx + 10, 8, Math.max(8, width - tw - 8)), y = Math.clamp(my + 12, 8, Math.max(8, height - th - 8));
        g.fill(x, y, x + tw, y + th, UiTheme.PANEL); g.fill(x, y, x + 2, y + th, 0xFF70CEB8);
        for (int i = 0; i < wrapped.size(); i++) g.text(font, wrapped.get(i), x + 8, y + 8 + i * 12, colors.get(i), false);
    }
    private void drawDetail(GuiGraphicsExtractor g, int mx, int my) {
        int w = Math.min(420, width - 24), h = Math.min(232, height - 20), x = (width - w) / 2, y = (height - h) / 2;
        g.fill(0, 0, width, height, 0x95000000); g.fill(x, y, x + w, y + h, UiTheme.PANEL); g.fill(x, y, x + w, y + 2, 0xFF77D6BF);
        g.pose().pushMatrix(); g.pose().translate(x + 14, y + 18); g.pose().scale(3f, 3f); g.item(detail.stack(), 0, 0); g.pose().popMatrix();
        text(g, detail.name(), x + 76, y + 20, cn.yumis.skyisland.core.RarityColors.color(detail.rarity()), w - 90);
        text(g, rarity(detail.rarity()) + " · 岛等级 " + detail.level(), x + 76, y + 40, 0xFFB2BBC7, w - 90);
        text(g, "每件 " + Money.format(model.price(detail,mode)), x + 14, y + 78, 0xFF9BE2C7, w - 28);
        if (mode==1) text(g, "队伍共享库存：" + model.stockText(detail), x + 14, y + 97, 0xFFBBC7D3, w - 28);
        int[] amounts = {1, 16, 64};
        for (int i = 0; i < 3; i++) button(g, "×" + amounts[i], x + 14 + i * 72, y + h - 98, 66, 24, quantity == amounts[i], inside(mx, my, x + 14 + i * 72, y + h - 98, 66, 24));
        boolean can = model.canBuy(detail,mode,quantity);
        text(g, "合计 " + total(detail,quantity), x + 14, y + h - 59, 0xFFFFD67B, w - 28);
        button(g, can ? "购买" : "等级或库存不足", x + 14, y + h - 34, w - 102, 24, false, inside(mx, my, x + 14, y + h - 34, w - 102, 24));
        button(g, "返回", x + w - 78, y + h - 34, 64, 24, false, inside(mx, my, x + w - 78, y + h - 34, 64, 24));
    }
    private String total(ClientCatalog.Product product,int quantity){try{return Money.format(Math.multiplyExact(model.price(product,mode),quantity));}catch(ArithmeticException e){return "金额超出上限";}}
    private void drawMessages(GuiGraphicsExtractor g){if(!model.lastError.isEmpty())text(g,model.lastError,contentLeft(),height-48,0xFFFF8888,contentWidth());}
    private void drawConfirmation(GuiGraphicsExtractor g,int mx,int my){
        if(model.state.has("wallet")){
            String balance=Money.format(model.wallet());int x=width-font.width(balance)-12,y=height-28;
            g.fill(x-6,y-4,width-6,y+13,0x98000000);g.text(font,balance,x,y,0xFFFFDD74,true);model.coinGains.render(g,y);
        }
        model.events.render(g);
        confirmation.render(g,this);
    }
    private void send(String action, String product, int count) { if (ClientPlayNetworking.canSend(ShopPayloads.Request.TYPE)) ClientPlayNetworking.send(new ShopPayloads.Request(action, product, count)); }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double x = e.x(), y = e.y();
        if(confirmation.click(this,x,y))return true;
        if(mode<2)layout();
        if (detail != null) {
            int w = Math.min(420, width - 24), h = Math.min(232, height - 20), dx = (width - w) / 2, dy = (height - h) / 2; int[] amounts = {1, 16, 64};
            for (int i = 0; i < 3; i++) if (inside(x, y, dx + 14 + i * 72, dy + h - 98, 66, 24)) { quantity = amounts[i]; return true; }
            if (inside(x, y, dx + w - 78, dy + h - 34, 64, 24)) { detail = null; return true; }
            if (inside(x, y, dx + 14, dy + h - 34, w - 102, 24)) { if(model.canBuy(detail,mode,quantity))model.purchase(detail,mode,quantity); return true; }
            return true;
        }
        int tabWidth = tabWidth();
        for(int i=0;i<5;i++)if(inside(x,y,8+i*(tabWidth+4),38,tabWidth,24)){selectSection(i);return true;}
        if(mode==4)return residents.clicked(e);
        if(mode==2)return tasks.clicked(e,doubleClick);
        if(mode==3)return tasks.bountyClicked(e);
        if(model.saleDesired&&salePanel!=null)return salePanel.mouseClicked(e,doubleClick);
        if(priceEditor!=null)return priceEditor.click(e,doubleClick);
        if(inside(x,y,width-120,72,108,24)){if(model.editing()&&mode==1)showAllConfigured=!showAllConfigured;else{model.showLockedItems=!model.showLockedItems;model.save();}invalidate();return true;}
        if (width>=500&&inside(x, y, sliderX() - 4, 47, SLIDER_WIDTH + 8, 18)) { dragSlider = true; slider(x); return true; }
        searchFocused = inside(x, y, sidebar() + 12, 72, width - sidebar() - 140, 24); if (searchFocused) return true;
        var categories = categories(); int ci = (int) ((y - 72) / 26) + categoryScroll;
        if (inside(x, y, 10, 72, sidebar() - 8, Math.max(1, (height - 160) / 26) * 26) && ci >= 0 && ci < categories.size()) { category = categories.get(ci).id(); invalidate(); return true; }
        if (inside(x, y, sidebar() + 12, height - 31, 120, 24)) { showSale(); send("sell_inventory", "", 0); return true; }
        if (hovered != null) { if(model.editing()){priceEditor=new PriceEditorPanel(this,hovered.id());searchFocused=false;}else if (e.button() == 1) model.purchase(hovered,mode,(e.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 64 : 1); else { detail = hovered; quantity = 1; } return true; }
        return super.mouseClicked(e, doubleClick);
    }
    private void slider(double x) { model.cellSize = Math.clamp(ClientCatalog.MIN_CELL + (int) Math.round((x - sliderX()) * (ClientCatalog.MAX_CELL - ClientCatalog.MIN_CELL) / SLIDER_WIDTH), ClientCatalog.MIN_CELL, ClientCatalog.MAX_CELL); scroll = 0; }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { if (dragSlider) { slider(e.x()); return true; } return super.mouseDragged(e, dx, dy); }
    @Override public boolean mouseReleased(MouseButtonEvent e) { if (dragSlider) { dragSlider = false; model.save(); return true; } return super.mouseReleased(e); }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) { if(confirmation.active())return true;if(mode==4)return residents.scrolled(sy);if(mode==2)return tasks.scrolled(x,y,sy);if(mode==3)return tasks.bountyScrolled(sy);if(model.saleDesired&&salePanel!=null)return salePanel.mouseScrolled(x,y,sx,sy);if(priceEditor!=null){priceEditor.scroll(sy);return true;} if (detail != null) return true; if (x < sidebar() + 8) categoryScroll -= Math.signum(sy); else scroll -= Math.signum(sy) * 3; layout(); return true; }
    @Override public boolean keyPressed(KeyEvent e) {
        if(confirmation.key(e.key()))return true;
        if(mode==4&&residents.key(e))return true;
        if(mode==2&&tasks.keyPressed(e))return true;
        if(priceEditor!=null){if(e.key()==GLFW.GLFW_KEY_ESCAPE)closePriceEditor();else priceEditor.key(e);return true;}
        if (e.key() == GLFW.GLFW_KEY_ESCAPE) {if(model.saleDesired){cancelSale(true);return true;} if (detail != null) { detail = null; return true; } if (searchFocused) { searchFocused = false; return true; } onClose(); return true; }
        if (searchFocused) { if (e.key() == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) { search = search.substring(0, search.offsetByCodePoints(search.length(), -1)); invalidate(); } return true; }
        if (e.key() == GLFW.GLFW_KEY_A && hovered != null) { model.favorite(hovered.id(),mode); invalidate(); return true; }
        return super.keyPressed(e);
    }
    @Override public boolean charTyped(CharacterEvent e) {if(mode==4&&residents.typed(e))return true;if(priceEditor!=null)return priceEditor.typed(e);if(mode==2&&tasks.charTyped(e))return true; if (searchFocused && e.isAllowedChatCharacter() && search.length() < 80) { search += e.codepointAsString(); invalidate(); return true; } return super.charTyped(e); }
    @Override public boolean preeditUpdated(PreeditEvent e){if(priceEditor!=null)return priceEditor.preedit(e);if(mode==2&&tasks.preedit(e))return true;return super.preeditUpdated(e);}
    @Override public void onClose(){
        if(tasks.hasDraft()){confirm("关闭控制面板将放弃未保存的任务编辑。",()->{tasks.discard();closeNow();});return;}
        closeNow();
    }
    private void closeNow(){if(model.saleDesired)cancelSale(true);model.save();super.onClose();}

}
