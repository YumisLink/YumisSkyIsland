-- Draw with Aseprite MCP; editable source and matching transparent 16x16 texture.
local spr=Sprite(16,16,ColorMode.RGB)
spr.layers[1].name="纠缠星辉"
local rows={
"................",".......gg.......","......gyyg......","......gWWg......",
"...gggBWWRggg...","..gBBBcWWmRRRg..",".gBBcccWWmmmRRg.",".gBccCCWWMMmmRg.",
"..gCCWWWwWMMMg..","...gCWWwwWWMg...","...gWWwppwWWg...","....gwwppwwg....",
".....gppppg.....","......gppg......",".......gg.......","................"}
local colors={g={93,64,116},y={255,213,132},W={255,248,232},w={202,211,255},p={151,135,223},B={65,130,210},c={97,184,230},C={168,233,239},R={189,100,162},m={226,144,196},M={255,195,218}}
for y=0,15 do
  assert(#rows[y+1]==16,"invalid pixel row")
  for x=0,15 do local c=colors[rows[y+1]:sub(x+1,x+1)];if c then spr.cels[1].image:drawPixel(x,y,app.pixelColor.rgba(c[1],c[2],c[3],255)) end end
end
spr:saveAs("C:/GameApp/YumisSkyIsland/art/lottery/lottery_ticket.aseprite")
spr:saveCopyAs("C:/GameApp/YumisSkyIsland/src/main/resources/assets/yumis_sky_island/textures/item/lottery_ticket.png")
print("Saved editable 16x16 lottery ticket and transparent PNG")
