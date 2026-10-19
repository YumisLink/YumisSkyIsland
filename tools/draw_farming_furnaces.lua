-- Run through Aseprite MCP. Each native 16px tile/icon retains editable material and highlight layers.
local root=app.params['project-root'] or 'C:/GameApp/YumisSkyIsland'
local out=root..'/src/main/resources/assets/yumis_sky_island/textures/'
local function color(hex) return app.pixelColor.rgba(tonumber(hex:sub(1,2),16),tonumber(hex:sub(3,4),16),tonumber(hex:sub(5,6),16),255) end
local function rect(im,x1,y1,x2,y2,c) for y=y1,y2 do for x=x1,x2 do im:drawPixel(x,y,color(c)) end end end
local function line(im,x1,y1,x2,y2,c)
  local dx,dy=math.abs(x2-x1),math.abs(y2-y1);local sx=x1<x2 and 1 or -1;local sy=y1<y2 and 1 or -1;local e=dx-dy
  while true do im:drawPixel(x1,y1,color(c));if x1==x2 and y1==y2 then break end;local e2=e*2;if e2>-dy then e=e-dy;x1=x1+sx end;if e2<dx then e=e+dx;y1=y1+sy end end
end
local palettes={
 {'copper','4d302a','86503b','bd7951','ecb47e','fff0bc'},
 {'iron','313c49','596b7b','8da6b4','cee0df','f8fff0'},
 {'gold','62402a','9c6a32','d8a945','ffe489','fff8cf'},
 {'emerald','153e39','236b51','3da775','86e7a1','ddffe0'},
 {'diamond','163c53','246b85','42b2c3','9bf0e9','e6fffc'},
 {'netherite','24202d','443747','66515a','a18686','e1c8ac'},
 {'ender','231f3c','44345f','765297','b38ad6','e5d2ff'}
}
local tileSheet=Image(16*4*6,16*7*6,ColorMode.RGB)
rect(tileSheet,0,0,tileSheet.width-1,tileSheet.height-1,'171b22')
local function preview(im,ox,oy,scale)
  for y=0,15 do for x=0,15 do local c=im:getPixel(x,y);if app.pixelColor.rgbaA(c)>0 then for sy=0,scale-1 do for sx=0,scale-1 do tileSheet:drawPixel(ox+x*scale+sx,oy+y*scale+sy,c) end end end end end
end
for index,p in ipairs(palettes) do
  for faceIndex,face in ipairs({'side','top','front','front_on'}) do
    local sprite=Sprite(16,16,ColorMode.RGB);sprite.layers[1].name='Metal chassis'
    local base,detail,hi=Image(16,16,ColorMode.RGB),Image(16,16,ColorMode.RGB),Image(16,16,ColorMode.RGB)
    rect(base,0,0,15,15,p[2]);rect(base,1,1,14,14,p[3]);rect(base,2,2,13,13,p[4])
    line(hi,1,1,14,1,p[5]);line(hi,1,2,1,14,p[5]);line(detail,14,2,14,14,p[2]);line(detail,2,14,13,14,p[2])
    for _,r in ipairs({{2,2},{13,2},{2,13},{13,13}}) do rect(detail,r[1],r[2],r[1],r[2],p[2]);hi:drawPixel(r[1],r[2]-1,color(p[6])) end
    if face=='side' then
      rect(detail,4,4,11,11,p[3]);line(hi,4,4,11,4,p[5]);line(hi,4,4,4,10,p[5]);line(detail,5,11,11,11,p[2])
      for y=6,9 do line(detail,6,y,10,y,p[2]);y=y+1 end
    elseif face=='top' then
      rect(detail,4,4,11,11,p[2]);rect(detail,5,5,10,10,p[3]);line(hi,5,5,10,5,p[5]);line(detail,6,7,9,7,p[2]);line(detail,6,9,9,9,p[2])
    else
      rect(detail,4,3,11,6,p[2]);rect(detail,5,4,10,5,'1c222b');line(hi,4,2,11,2,p[5]);line(detail,6,4,9,4,'3c4656')
      rect(detail,3,8,12,12,p[2]);rect(detail,4,9,11,11,'171b25');line(hi,4,7,11,7,p[5]);line(hi,4,12,11,12,p[3])
      if face=='front_on' then
        local fire=p[1]=='ender' and {'415a54','48a795','99f5ca','e6ffe0'} or {'ae3828','e97429','ffbd4d','fff2a1'}
        rect(detail,4,10,11,11,fire[1]);rect(detail,5,10,10,11,fire[2]);line(detail,5,11,10,11,fire[3]);rect(detail,6,9,7,10,fire[3]);rect(detail,9,9,10,10,fire[2]);hi:drawPixel(6,10,color(fire[4]));hi:drawPixel(9,11,color(fire[4]))
      else line(detail,5,10,10,10,'39434b') end
    end
    sprite:newCel(sprite.layers[1],sprite.frames[1],base)
    local layer=sprite:newLayer();layer.name='Panels and furnace mouth';sprite:newCel(layer,sprite.frames[1],detail)
    layer=sprite:newLayer();layer.name='Highlights and glow';sprite:newCel(layer,sprite.frames[1],hi)
    local id=p[1]..'_furnace_'..face
    sprite:saveAs(root..'/art/furnaces/'..id..'.aseprite');sprite:saveCopyAs(out..'block/'..id..'.png')
    local merged=Image(sprite.spec);merged:drawSprite(sprite,1);preview(merged,(faceIndex-1)*96,(index-1)*96,6)
    print(id)
  end
end
local overview=Sprite(tileSheet.width,tileSheet.height,ColorMode.RGB);overview:newCel(overview.layers[1],overview.frames[1],tileSheet);overview:saveCopyAs(root..'/art/furnaces/preview.png')
local icons={
 {'tool_belt',function(im,h)
   rect(im,2,5,13,10,'48332e');rect(im,2,6,13,9,'97603c');rect(im,3,6,5,10,'bd8754');rect(im,10,6,12,10,'b67b4b');rect(im,6,5,9,10,'daa64b');rect(im,7,6,8,9,'493a31');line(h,3,5,5,5,'f2cf8c');line(h,6,5,9,5,'fff2b7');line(im,11,2,11,5,'b2c5d0');rect(im,10,2,12,3,'829cab');rect(im,3,3,4,5,'b9cad0')
 end},
 {'toolbox',function(im,h)
   rect(im,5,2,10,4,'303b49');line(h,6,2,9,2,'d8e6df');rect(im,2,5,13,12,'4b3435');rect(im,3,5,12,7,'c44e42');rect(im,3,8,12,11,'8d3a3c');line(h,3,5,12,5,'f68b65');line(im,2,8,13,8,'25303b');rect(im,7,7,8,9,'dcaa44');line(h,7,7,8,7,'fff0b8');line(h,3,9,3,10,'e06a59')
 end},
 {'extender_grip',function(im,h)
   line(im,3,13,10,6,'795133');line(im,4,13,11,6,'d1a363');line(h,4,12,10,6,'f5d894');line(im,9,5,11,3,'8199a8');line(im,11,3,13,3,'b9cdd5');line(im,13,3,13,6,'8199a8');line(h,12,2,14,2,'edf7eb');line(im,8,6,8,3,'8199a8');line(im,8,3,6,3,'b9cdd5');line(h,6,2,8,2,'edf7eb');rect(im,2,11,4,13,'4c3631');line(h,2,11,3,11,'cf9461')
 end},
 {'excavator',function(im,h)
   line(im,3,13,9,7,'65432d');line(im,4,13,10,7,'b3824f');line(h,4,12,9,7,'edc88a');rect(im,6,4,11,8,'576b78');rect(im,7,3,12,6,'8ba9b8');line(h,7,3,11,3,'e8f4ee');line(h,7,4,7,6,'c7e2e5');line(im,8,8,12,5,'3c4d5e');line(im,10,8,13,5,'576b78');line(im,12,7,14,5,'8ba9b8');rect(im,2,11,3,13,'473531')
 end},
 {'portable_cement_mixer',function(im,h)
   rect(im,5,3,11,8,'4f6675');rect(im,4,4,12,7,'7796a2');line(h,5,3,10,3,'d4e3df');line(h,4,4,4,6,'b6cdd2');rect(im,3,5,5,7,'26343f');line(im,6,4,10,4,'b0c7ce');line(im,6,9,4,12,'a67b3e');line(im,10,9,12,12,'a67b3e');line(im,4,11,12,11,'d6ac58');rect(im,3,12,5,13,'29333f');rect(im,11,12,13,13,'29333f');h:drawPixel(4,12,color('a6bcc6'));h:drawPixel(12,12,color('a6bcc6'));line(im,12,4,13,3,'d6ac58')
 end},
 {'architect_backpack',function(im,h)
   rect(im,5,1,10,3,'3c3947');line(h,6,1,9,1,'d5b988');rect(im,3,4,12,13,'654730');rect(im,4,4,11,12,'a47846');rect(im,4,4,11,6,'cba36a');line(h,4,4,11,4,'ffe5aa');rect(im,6,8,10,12,'7a553a');line(h,6,8,10,8,'dfbc7b');rect(im,7,6,8,8,'e4b74d');h:drawPixel(7,6,color('fff4bc'));rect(im,2,6,3,10,'687b94');rect(im,12,5,13,10,'3f5970');line(h,12,5,13,5,'b8deeb');line(im,2,4,3,3,'7faec0');line(h,2,3,2,1,'def5f2');line(im,12,3,13,1,'8eabd0');line(h,13,1,14,1,'e3f8f5')
 end}
}
local iconOverview=Sprite(6*96,96,ColorMode.RGB);local iconSheet=Image(iconOverview.spec);rect(iconSheet,0,0,iconSheet.width-1,95,'171b22')
for index,entry in ipairs(icons) do
  local sprite=Sprite(16,16,ColorMode.RGB);sprite.layers[1].name='Contour'
  local im,h,outline=Image(16,16,ColorMode.RGB),Image(16,16,ColorMode.RGB),Image(16,16,ColorMode.RGB);entry[2](im,h)
  for y=0,15 do for x=0,15 do if im:getPixel(x,y)~=0 or h:getPixel(x,y)~=0 then for _,off in ipairs({{-1,0},{1,0},{0,-1},{0,1}}) do local xx,yy=x+off[1],y+off[2];if xx>=0 and xx<16 and yy>=0 and yy<16 and im:getPixel(xx,yy)==0 and h:getPixel(xx,yy)==0 then outline:drawPixel(xx,yy,color('202331')) end end end end end
  sprite:newCel(sprite.layers[1],sprite.frames[1],outline);local layer=sprite:newLayer();layer.name='Tool materials';sprite:newCel(layer,sprite.frames[1],im);layer=sprite:newLayer();layer.name='Metal highlights';sprite:newCel(layer,sprite.frames[1],h)
  sprite:saveAs(root..'/art/icons/building/'..entry[1]..'.aseprite');sprite:saveCopyAs(out..'item/accessory/'..entry[1]..'.png')
  local merged=Image(sprite.spec);merged:drawSprite(sprite,1)
  for y=0,15 do for x=0,15 do local c=merged:getPixel(x,y);if app.pixelColor.rgbaA(c)>0 then for yy=0,5 do for xx=0,5 do iconSheet:drawPixel((index-1)*96+x*6+xx,y*6+yy,c) end end end end end
  print(entry[1])
end
app.activeSprite=iconOverview
iconOverview:newCel(iconOverview.layers[1],iconOverview.frames[1],iconSheet);iconOverview:saveCopyAs(root..'/art/icons/building/preview.png')
print('DONE: 28 furnace tiles and 6 accessory icons, all with editable Aseprite sources')
