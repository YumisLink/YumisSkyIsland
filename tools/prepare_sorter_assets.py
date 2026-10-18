"""Targeted resource definitions; never regenerates the old accessory catalog."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parent.parent
ASSETS=ROOT/'src/main/resources/assets/yumis_sky_island'
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
IDS='bow_heart_stone vulnerability_mark arrow_saver arrow_splash counterkill bow_heart_iron bow_heart_split bow_heart_gold slaughter_aura shadow_rush first_aid dive_bomb collector anklet shark_necklace'.split()
for name in IDS:
    write(ASSETS/f'items/accessory_{name}.json',{'model':{'type':'minecraft:model','model':f'yumis_sky_island:item/accessory/{name}'}})
    write(ASSETS/f'models/item/accessory/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'yumis_sky_island:item/accessory/{name}'}})
write(ASSETS/'items/sorter.json',{'model':{'type':'minecraft:model','model':'yumis_sky_island:block/sorter'}})
write(ASSETS/'models/block/sorter.json',{'parent':'minecraft:block/orientable','textures':{k:f'yumis_sky_island:block/sorter_{v}' for k,v in {'side':'side','front':'front','top':'top'}.items()}})
write(ASSETS/'blockstates/sorter.json',{'variants':{f'facing={face}':dict(model='yumis_sky_island:block/sorter',**({'y':angle} if angle else {})) for face,angle in [('north',0),('east',90),('south',180),('west',270)]}})
DATA=ROOT/'src/main/resources/data'
write(DATA/'yumis_sky_island/recipe/sorter.json',{'type':'minecraft:crafting_shaped','category':'redstone','pattern':['GDG','RCR','HPH'],'key':{'G':'minecraft:gold_block','D':'minecraft:diamond','R':'minecraft:redstone','C':'minecraft:chest','H':'minecraft:hopper','P':'minecraft:comparator'},'result':{'id':'yumis_sky_island:sorter','count':1}})
write(DATA/'yumis_sky_island/loot_table/blocks/sorter.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'yumis_sky_island:sorter'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for name in ['mineable/pickaxe']:
    path=DATA/f'minecraft/tags/block/{name}.json'
    value=json.loads(path.read_text(encoding='utf-8')) if path.exists() else {'replace':False,'values':[]}
    if 'yumis_sky_island:sorter' not in value['values']:value['values'].append('yumis_sky_island:sorter')
    write(path,value)
