-- Native 16px artwork. Run with Aseprite --batch --script-param project-root=... --script this-file.
-- All contours, colour clusters and highlights remain separate editable layers.
local root=assert(app.params["project-root"],"project-root is required")
local out=root.."/src/main/resources/assets/yumis_sky_island/"
app.fs.makeAllDirectories(root.."/art/icons/accessory-expansion")
app.fs.makeAllDirectories(out.."textures/item/accessory")
app.fs.makeAllDirectories(out.."textures/block")
local C={outline="202331",dark="3c4656",stone="7b8892",silver="b1c9d4",white="eff6e9",gold="dcaa44",light="ffe5a0",brown="775133",red="c84d5d",pink="ff9d9b",blue="3b8bba",cyan="78e6df",purple="9660c9",lavender="d4acfa",green="63ba79",cream="e2d9bd"}
local function rgba(hex) return app.pixelColor.rgba(tonumber(hex:sub(1,2),16),tonumber(hex:sub(3,4),16),tonumber(hex:sub(5,6),16),255) end
local pixels={}
local function p(x,y,color) if x>=1 and x<=14 and y>=1 and y<=14 then pixels[y*16+x]=C[color] or color end end
local function rect(x1,y1,x2,y2,color) for y=y1,y2 do for x=x1,x2 do p(x,y,color) end end end
local function line(x1,y1,x2,y2,color)
  local dx,dy=math.abs(x2-x1),math.abs(y2-y1);local sx=x1<x2 and 1 or -1;local sy=y1<y2 and 1 or -1;local e=dx-dy
  while true do p(x1,y1,color);if x1==x2 and y1==y2 then break end;local e2=e*2;if e2>-dy then e=e-dy;x1=x1+sx end;if e2<dx then e=e+dx;y1=y1+sy end end
end
local function ellipse(cx,cy,rx,ry,color,hollow)
  for y=1,14 do for x=1,14 do local d=((x-cx)/rx)^2+((y-cy)/ry)^2;if d<=1 and (not hollow or d>=0.48) then p(x,y,color) end end end
end
local function heart(color,light)
  local rows={".##.##.","#######","#######",".#####.","..###..","...#..."}
  for y,row in ipairs(rows) do for x=1,#row do if row:sub(x,x)=="#" then p(x+3,y+4,color) end end end
  line(5,6,6,6,light);p(5,7,light)
end
local function bow(color,hi,kind)
  heart(color,hi);line(10,2,12,4,hi);line(12,4,13,8,color);line(13,8,11,12,color);line(11,12,8,14,color);line(10,2,8,14,"brown")
  line(3,11,12,5,hi);p(12,6,color);p(11,5,color)
  if kind=="split" then line(7,5,6,7,"dark");line(6,7,8,8,"dark");line(8,8,7,10,"dark");p(7,7,"lavender") end
end
local icons={
 {"bow_heart_stone",function()bow("stone","white")end},
 {"vulnerability_mark",function()ellipse(8,8,5,5,"red",true);line(8,1,8,5,"pink");line(8,11,8,14,"red");line(1,8,5,8,"pink");line(11,8,14,8,"red");rect(7,7,9,9,"cream");p(8,8,"red")end},
 {"arrow_saver",function()line(5,2,7,7,"silver");line(9,1,9,7,"white");line(12,3,10,8,"silver");p(4,2,"white");p(5,1,"white");p(10,1,"stone");p(12,2,"white");rect(4,7,11,12,"brown");rect(5,8,10,12,"green");line(6,13,9,13,"dark");line(4,7,11,7,"gold");line(5,8,5,11,"cream");p(9,10,"gold")end},
 {"arrow_splash",function()line(3,12,11,4,"silver");line(4,12,11,5,"white");line(8,3,12,3,"cyan");line(12,3,12,7,"cyan");p(13,2,"white");line(3,8,4,4,"blue");line(4,4,7,3,"cyan");line(8,13,12,12,"blue");line(12,12,13,9,"cyan");p(2,3,"white");p(13,13,"white")end},
 {"counterkill",function()line(4,12,11,3,"silver");line(5,12,12,3,"white");line(4,10,8,13,"gold");line(4,12,3,14,"brown");line(3,14,4,14,"gold");line(3,5,3,3,"red");line(3,3,7,2,"red");line(7,2,9,4,"red");p(8,4,"pink");line(10,11,13,11,"red");line(13,11,13,8,"pink")end},
 {"bow_heart_iron",function()bow("silver","white");p(6,9,"stone");p(8,6,"white")end},
 {"bow_heart_split",function()bow("purple","lavender","split");line(2,9,5,6,"cyan");p(2,8,"cyan")end},
 {"bow_heart_gold",function()bow("gold","light");rect(6,7,7,8,"cyan");p(6,7,"white");p(3,3,"light");p(2,4,"gold")end},
 {"slaughter_aura",function()ellipse(8,9,6,4,"red",true);ellipse(8,9,4,2,"pink",true);rect(6,4,10,8,"cream");p(5,5,"cream");p(11,5,"cream");rect(6,5,7,6,"dark");rect(9,5,10,6,"dark");p(8,7,"red");rect(7,8,9,9,"stone");line(5,2,4,5,"red");line(12,2,12,5,"pink");p(3,10,"gold")end},
 {"shadow_rush",function()line(4,3,11,3,"dark");rect(7,4,12,6,"purple");rect(6,5,12,7,"dark");line(7,8,10,10,"purple");line(10,10,8,13,"purple");line(7,8,5,10,"lavender");line(5,10,3,10,"purple");line(8,13,11,13,"lavender");p(11,5,"cyan");line(2,5,5,5,"lavender");line(1,8,4,8,"purple");line(2,12,5,12,"dark")end},
 {"first_aid",function()rect(6,3,10,4,"silver");rect(4,5,12,12,"red");rect(4,6,11,11,"pink");rect(5,6,11,12,"red");rect(7,7,9,11,"white");rect(6,8,10,10,"white");line(4,5,12,5,"silver");p(4,12,"dark");p(12,12,"dark");p(5,6,"pink")end},
 {"dive_bomb",function()ellipse(8,10,4,4,"dark");ellipse(7,9,3,3,"stone");rect(7,5,9,6,"gold");line(8,5,10,3,"brown");p(10,2,"gold");p(11,3,"light");p(6,8,"white");p(5,9,"silver");line(2,2,4,5,"cyan");line(1,4,3,7,"blue");line(13,3,12,6,"cyan");p(6,12,"red");p(10,11,"red")end},
 {"collector",function()rect(5,7,11,12,"brown");rect(4,8,12,10,"gold");line(5,12,10,13,"brown");rect(5,8,10,11,"gold");line(5,8,5,10,"light");ellipse(9,5,3,2,"gold");line(7,4,10,4,"light");p(9,5,"brown");ellipse(5,3,2,1,"light");p(12,2,"cyan");p(13,1,"white")end},
 {"anklet",function()ellipse(8,9,5,3,"silver",true);line(4,7,10,7,"white");line(5,10,11,10,"stone");rect(7,9,9,11,"gold");p(8,10,"cyan");line(11,7,12,4,"gold");p(12,3,"light");p(4,9,"white");p(8,13,"silver")end},
 {"shark_necklace",function()line(3,2,2,6,"silver");line(2,6,5,10,"silver");line(12,2,13,6,"stone");line(13,6,10,10,"silver");line(3,2,5,1,"white");line(10,1,12,2,"white");rect(6,8,9,9,"gold");for y=10,13 do for x=6,9-(y-10) do p(x,y,"cream") end end;p(7,10,"white");p(7,11,"white");p(6,13,"silver");p(8,8,"cyan")end}
}
local preview=Sprite(640,384,ColorMode.RGB);preview.layers[1].name="8x pixel overview";local sheet=Image(640,384,ColorMode.RGB)
for y=0,383 do for x=0,639 do sheet:drawPixel(x,y,rgba("171b22")) end end
for index,entry in ipairs(icons) do
  pixels={};entry[2]();local sprite=Sprite(16,16,ColorMode.RGB);sprite.layers[1].name="Contour"
  local contour=Image(16,16,ColorMode.RGB);local colors=Image(16,16,ColorMode.RGB);local highlights=Image(16,16,ColorMode.RGB)
  for key,color in pairs(pixels) do local x=key%16;local y=math.floor(key/16);for _,off in ipairs({{-1,0},{1,0},{0,-1},{0,1}}) do local xx=x+off[1];local yy=y+off[2];if xx>=1 and xx<=14 and yy>=1 and yy<=14 and not pixels[yy*16+xx] then contour:drawPixel(xx,yy,rgba(C.outline)) end end
    local hi=color==C.white or color==C.light or color==C.cyan or color==C.lavender;local img=hi and highlights or colors;img:drawPixel(x,y,rgba(color))
  end
  sprite:newCel(sprite.layers[1],sprite.frames[1],contour)
  local layer=sprite:newLayer();layer.name="Materials";sprite:newCel(layer,sprite.frames[1],colors)
  layer=sprite:newLayer();layer.name="Highlights";sprite:newCel(layer,sprite.frames[1],highlights)
  sprite:saveAs(root.."/art/icons/accessory-expansion/"..entry[1]..".aseprite")
  sprite:saveCopyAs(out.."textures/item/accessory/"..entry[1]..".png")
  for y=0,15 do for x=0,15 do local color=highlights:getPixel(x,y);if color==0 then color=colors:getPixel(x,y) end;if color==0 then color=contour:getPixel(x,y) end;if color~=0 then
    for yy=0,7 do for xx=0,7 do sheet:drawPixel(((index-1)%5)*128+x*8+xx,math.floor((index-1)/5)*128+y*8+yy,color) end end
  end end end
  sprite:close();print(entry[1])
end
preview:newCel(preview.layers[1],preview.frames[1],sheet);preview:saveCopyAs(root.."/art/icons/accessory-expansion/preview.png");preview:close()
-- The block uses ordinary baked cube faces; no block-entity renderer or animation is needed.
for _,face in ipairs({"side","front","top"}) do
  local sprite=Sprite(16,16,ColorMode.RGB);sprite.layers[1].name="Machine casing";local base=Image(16,16,ColorMode.RGB)
  for y=0,15 do for x=0,15 do local color=(x==0 or y==0 or x==15 or y==15) and "242733" or (x==1 or y==1) and "707785" or "3c4350";base:drawPixel(x,y,rgba(color)) end end
  sprite:newCel(sprite.layers[1],sprite.frames[1],base);local details=Image(16,16,ColorMode.RGB)
  for _,point in ipairs({{2,2},{13,2},{2,13},{13,13}}) do details:drawPixel(point[1],point[2],rgba(C.gold)) end
  if face=="front" then
    for y=4,11 do for x=4,11 do local d=math.abs(x-7.5)+math.abs(y-7.5);if d<=4 then details:drawPixel(x,y,rgba(d<=2 and C.cyan or C.blue)) end end end
    for y=6,9 do details:drawPixel(2,y,rgba(C.blue));details:drawPixel(13,y,rgba(C.gold)) end
  else for y=5,10,2 do for x=4,11 do details:drawPixel(x,y,rgba(face=="top" and C.gold or C.dark)) end end end
  local layer=sprite:newLayer();layer.name="Ports, core and fasteners";sprite:newCel(layer,sprite.frames[1],details)
  sprite:saveAs(root.."/art/icons/accessory-expansion/sorter-"..face..".aseprite");sprite:saveCopyAs(out.."textures/block/sorter_"..face..".png");sprite:close()
end
print("15 icons and 3 machine faces, all editable native Aseprite pixels")
