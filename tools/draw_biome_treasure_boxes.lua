-- Original 16x16 biome fishing crates, drawn and exported by Aseprite.
-- Inspiration: Terraria's readable, metal-banded biome-crate silhouette;
-- all pixels, motifs and palettes below are newly plotted for this mod.
-- Execute with Aseprite MCP: dofile("C:/GameApp/YumisSkyIsland/tools/draw_biome_treasure_boxes.lua")
local root = "C:/GameApp/YumisSkyIsland"
local biomes, seen = {}, {}
for line in io.lines(root.."/config/yumis_sky_island/shop/item_levels.toml") do
  local id = line:match('^"yumis_sky_island:biome_ball_([%w_]+)"%s*=')
  if id and not seen[id] then biomes[#biomes+1]=id; seen[id]=true end
end
table.sort(biomes)
assert(#biomes==65,"Expected 65 biome IDs, got "..#biomes)
local biomeRanks={}
for i,id in ipairs(biomes) do biomeRanks[id]=i-1 end

local nether={nether_wastes=true,soul_sand_valley=true,crimson_forest=true,warped_forest=true,basalt_deltas=true}
local ending={the_end=true,end_highlands=true,end_midlands=true,small_end_islands=true,end_barrens=true}
local variants={}
for _,biome in ipairs(biomes) do
  variants[#variants+1]={biome=biome,medium="water",blood=false,name=biome.."_water"}
  if nether[biome] then variants[#variants+1]={biome=biome,medium="lava",blood=false,name=biome.."_lava"} end
  if ending[biome] then variants[#variants+1]={biome=biome,medium="void",blood=false,name=biome.."_void"} end
  if not nether[biome] and not ending[biome] then variants[#variants+1]={biome=biome,medium="water",blood=true,name=biome.."_water_blood"} end
end
table.sort(variants,function(a,b)return a.name<b.name end)
assert(#variants==130,"Expected 130 box variants, got "..#variants)

local palettes={
  meadow={"202d2a","477047","719747","a6c468","d8da8a","315441","d4b66a","f5e3aa"},
  forest={"202c27","4a6642","6f8e4e","9db46b","cdd89a","345b37","caa268","f8dc9a"},
  birch={"2a3434","6c7770","a9b7a4","dce3c8","f5f2d8","486d50","d9c195","fff4d9"},
  dark={"171f2a","344150","526475","788990","bac0b2","38505b","a77d60","e1c59a"},
  cherry={"342733","765066","b37b8d","e5abb2","ffe0c8","755067","c79774","ffe6b4"},
  jungle={"172d27","315e3c","48874b","7dbc65","bbde84","336754","ba9459","f1d58a"},
  desert={"453228","987044","c69a60","e2c184","f9e6b0","8c6c45","e0b46b","fff0bf"},
  badlands={"3b272a","925047","ba6c4b","d79361","f2c58a","803f3a","d8a45b","ffe0a3"},
  savanna={"383029","806541","b18b50","d5b46d","f0d89a","7e6743","ddad60","f6e6af"},
  ocean={"172b3b","285d79","397f9b","5fb4bd","a4dfd7","32647c","b6a878","e8e5b7"},
  deep_ocean={"142338","254768","33668b","558fa9","96c6cf","2b4c72","8c9fb5","d6e6e3"},
  beach={"354038","7e9b8b","a9c5a2","d8d9aa","f4e7be","558895","d9bb7e","fff1cd"},
  swamp={"252e29","4b6041","728250","9c9f64","d0c88e","495d49","b59b68","e4d49e"},
  ice={"253b53","4d7991","77a9bb","a9d7d9","e4f7ec","5e9cb8","b9d7dd","ffffff"},
  mountain={"283440","566777","8193a1","b3bec1","e4e1d1","5e8596","c2b494","f4edce"},
  cave={"292a37","535064","797287","aca6a3","d9d0ba","696078","bd9a70","efdbad"},
  deep_dark={"111c26","1f3c4b","2b6071","3e91a0","83d9d4","154b56","52b7b3","c0fcde"},
  mushroom={"38293f","694966","986b83","ca91a0","efc4b7","7c4e78","dc938a","ffe4c7"},
  nether={"2b232c","62343b","8e4a43","c66b45","f9aa60","7b3438","e49150","ffd49a"},
  crimson={"2e1c29","712b38","ad3f48","da6660","f49b7b","842c42","e58c68","ffd0a0"},
  warped={"162e38","28616c","328e8d","5cbbb0","a1e2c8","2a6374","91d3b6","d7f5d7"},
  ender={"27233b","484061","73648e","a193b5","ddd0dd","5e4d83","d7c585","fff1b8"},
}
local motifs={
  leaf={"..#..",".###.","#####","..#..","..#.."},
  flower={"#.#.#",".###.","#####",".###.","#.#.#"},
  tree={"..#..",".###.","#####","..#..",".###."},
  wave={".....","##...","..###","###..","....."},
  coral={"#...#",".#.#.","..#..",".###.","..#.."},
  dune={".....","....#","..###","#####","....."},
  cactus={"..#..","#.#.#","#####","..#..","..#.."},
  snow={"#.#.#",".###.","#####",".###.","#.#.#"},
  mountain={"..#..",".#.#.","#...#","#####","....."},
  mushroom={".###.","#####","..#..","..#..",".###."},
  cave={"#...#",".#.#.","..#..",".#.#.","#...#"},
  eye={".###.","#...#","#.#.#","#...#",".###."},
  fire={"..#..",".#.#.",".###.","#####",".###."},
  star={"#...#",".#.#.","..#..",".#.#.","#...#"},
  moss={"#..#.",".##.#","#.#.#",".###.","..#.."},
}
local function style(id,medium)
  if medium=="lava" then return "nether","fire" end
  if medium=="void" then return "ender","star" end
  if id:find("crimson") then return "crimson","fire" end
  if id:find("warped") then return "warped","star" end
  if nether[id] then return "nether","fire" end
  if ending[id] then return "ender","star" end
  if id=="deep_dark" then return "deep_dark","eye" end
  if id:find("mushroom") then return "mushroom","mushroom" end
  if id:find("cherry") then return "cherry","flower" end
  if id:find("pale") or id:find("dark_forest") then return "dark","tree" end
  if id:find("birch") then return "birch","tree" end
  if id:find("jungle") or id:find("bamboo") or id:find("lush") then return "jungle","leaf" end
  if id:find("badlands") then return "badlands","dune" end
  if id:find("desert") then return "desert","cactus" end
  if id:find("savanna") then return "savanna","tree" end
  if id:find("mangrove") or id:find("swamp") then return "swamp","moss" end
  if id:find("frozen") or id:find("snow") or id:find("ice") or id:find("grove") then return "ice","snow" end
  if id:find("ocean") or id:find("river") then return id:find("deep") and "deep_ocean" or "ocean","wave" end
  if id:find("beach") or id:find("shore") then return "beach","coral" end
  if id:find("peaks") or id:find("windswept") or id:find("hills") then return "mountain","mountain" end
  if id:find("caves") then return "cave","cave" end
  if id:find("forest") or id:find("taiga") then return "forest","tree" end
  if id:find("sunflower") or id:find("flower") then return "meadow","flower" end
  return "meadow","leaf"
end
local function rgba(hex)
  return app.pixelColor.rgba(tonumber(hex:sub(1,2),16),tonumber(hex:sub(3,4),16),tonumber(hex:sub(5,6),16),255)
end
local function pixel(img,x,y,color)
  if x>=0 and y>=0 and x<16 and y<16 then img:drawPixel(x,y,rgba(color)) end
end
local function rect(img,x1,y1,x2,y2,color)
  for y=y1,y2 do for x=x1,x2 do pixel(img,x,y,color) end end
end
local function draw(v)
  local group,motif=style(v.biome,v.medium)
  local p=palettes[group]
  local spr=Sprite(16,16,ColorMode.RGB)
  local layers={spr.layers[1],spr:newLayer(),spr:newLayer(),spr:newLayer()}
  layers[1].name="01 Ink and silhouette"
  layers[2].name="02 Biome material"
  layers[3].name="03 Metalwork and emblem"
  layers[4].name="04 Blood moon seal"
  local imgs={Image(16,16,ColorMode.RGB),Image(16,16,ColorMode.RGB),Image(16,16,ColorMode.RGB),Image(16,16,ColorMode.RGB)}
  local ink,wood,trim,event=table.unpack(imgs)
  rect(ink,4,1,11,1,p[1]);rect(ink,2,2,13,3,p[1]);rect(ink,1,4,14,13,p[1]);rect(ink,2,14,13,14,p[1])
  rect(wood,4,2,11,2,p[4]);rect(wood,3,3,12,3,p[3]);rect(wood,2,5,13,12,p[2])
  rect(wood,2,5,13,5,p[3]);rect(wood,2,8,13,8,p[3]);rect(wood,2,11,13,11,p[3])
  rect(wood,3,6,12,6,p[4]);rect(wood,3,9,12,9,p[4]);rect(wood,3,12,12,12,p[1])
  rect(trim,2,4,13,4,p[6]);rect(trim,3,4,11,4,p[7]);rect(trim,2,13,13,13,p[6])
  rect(trim,3,5,4,12,p[6]);rect(trim,11,5,12,12,p[6])
  rect(trim,3,6,3,7,p[7]);rect(trim,11,6,11,7,p[7])
  rect(trim,4,5,4,5,p[8]);rect(trim,12,5,12,5,p[8]);rect(trim,4,11,4,11,p[8]);rect(trim,12,11,12,11,p[8])
  rect(trim,5,7,10,12,p[1]);rect(trim,6,7,9,11,p[2])
  local pattern=motifs[motif]
  for y,row in ipairs(pattern) do for x=1,#row do
    if row:sub(x,x)=="#" then pixel(trim,x+4,y+6,p[8]) end
  end end
  local hash=0
  for i=1,#v.biome do hash=(hash*33+v.biome:byte(i))%65521 end
  pixel(trim,5+hash%5,3,p[8]);pixel(trim,5+math.floor(hash/5)%5,12,p[7])
  -- Seven engraved lid studs encode the biome's stable catalog rank, so even
  -- closely related oceans and peaks remain visually distinct at item scale.
  local rank=biomeRanks[v.biome]
  for bit=0,6 do
    local lit=math.floor(rank/2^bit)%2==1
    pixel(trim,4+bit,3,lit and p[8] or p[1])
  end
  if v.medium=="lava" then
    pixel(event,6,1,"fc9b47");pixel(event,7,1,"ffe098");pixel(event,8,1,"f36231")
    pixel(event,6,5,"f36a32");pixel(event,9,5,"ffb34f")
  elseif v.medium=="void" then
    pixel(event,6,1,"a888e5");pixel(event,8,1,"f0daff");pixel(event,10,2,"956ad4")
    pixel(event,2,7,"bd9ff0");pixel(event,13,9,"bd9ff0")
  end
  if v.blood then
    rect(event,5,1,10,1,"84273c")
    pixel(event,6,1,"e45c62");pixel(event,8,1,"ffb389");pixel(event,10,1,"cf3c51")
    pixel(event,2,5,"b32c45");pixel(event,2,6,"e45458");pixel(event,2,7,"8f2943")
    pixel(event,13,5,"b32c45");pixel(event,13,6,"e45458");pixel(event,13,7,"8f2943")
    pixel(event,4,13,"b32c45");pixel(event,12,13,"b32c45")
  end
  for i=1,4 do spr:newCel(layers[i],spr.frames[1],imgs[i],Point(0,0)) end
  return spr,imgs,group
end

local preview=Sprite(400,520,ColorMode.RGB)
preview.layers[1].name="130 crate variants, 2x nearest preview"
local sheet=preview.cels[1].image
local previewBg=rgba("202a35")
for y=0,519 do for x=0,399 do sheet:drawPixel(x,y,previewBg) end end
local manifest=assert(io.open(root.."/art/biome_treasure_boxes/manifest.tsv","w"))
manifest:write("variant\tbiome\tmedium\tblood_moon\tpalette\n")
for i,v in ipairs(variants) do
  local spr,imgs,group=draw(v)
  local base=root.."/art/biome_treasure_boxes/"..v.name
  spr:saveAs(base..".aseprite")
  spr:saveCopyAs(root.."/src/main/resources/assets/yumis_sky_island/textures/item/biome_treasure_box/"..v.name..".png")
  manifest:write(v.name,"\t",v.biome,"\t",v.medium,"\t",tostring(v.blood),"\t",group,"\n")
  local ox,oy=((i-1)%10)*40+4,math.floor((i-1)/10)*40+4
  for y=0,15 do for x=0,15 do
    local color=app.pixelColor.rgba(0,0,0,0)
    for j=1,4 do local candidate=imgs[j]:getPixel(x,y);if app.pixelColor.rgbaA(candidate)>0 then color=candidate end end
    if app.pixelColor.rgbaA(color)>0 then
      for yy=0,1 do for xx=0,1 do sheet:drawPixel(ox+x*2+xx,oy+y*2+yy,color) end end
    end
  end end
  spr:close()
end
manifest:close()
preview:saveCopyAs(root.."/art/biome_treasure_boxes/preview.png")
preview:close()
print("Aseprite drew and exported "..#variants.." editable 16x16 biome crates, 130 PNGs and a contact sheet.")
