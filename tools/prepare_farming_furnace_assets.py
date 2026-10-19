"""Targeted furnace resources, building accessory icons and current-development config sync."""
from pathlib import Path
import json
import sys
import tomllib

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'src/main/resources/assets/yumis_sky_island'
DATA = ROOT / 'src/main/resources/data'
sys.path.insert(0, str(ROOT / '.tools/python-libs'))
import tomlkit

TIERS = [('copper', '铜', 'copper_ingot'), ('iron', '铁', 'iron_ingot'),
         ('gold', '金', 'gold_ingot'), ('emerald', '绿宝石', 'emerald'),
         ('diamond', '钻石', 'diamond'), ('netherite', '下界合金', None), ('ender', '末影', None)]
ACCESSORIES = {'tool_belt': '工具腰带', 'toolbox': '工具盒', 'extender_grip': '延长抓钩',
               'excavator': '挖掘器', 'portable_cement_mixer': '便携式水泥搅拌器',
               'architect_backpack': '建筑师发明背包'}
for directory in ['art/furnaces', 'art/icons/building',
                  'src/main/resources/assets/yumis_sky_island/textures/block',
                  'src/main/resources/assets/yumis_sky_island/textures/item/accessory']:
    (ROOT / directory).mkdir(parents=True, exist_ok=True)

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(value, ensure_ascii=False, indent=2) + '\n'
    if not path.exists() or path.read_text(encoding='utf-8') != text:
        path.write_text(text, encoding='utf-8')

for index, (tier, name, mineral) in enumerate(TIERS):
    block = f'{tier}_furnace'
    for lit in [False, True]:
        model = block + ('_on' if lit else '')
        write(ASSETS / f'models/block/{model}.json', {
            'parent': 'minecraft:block/orientable',
            'textures': {face: f'yumis_sky_island:block/{block}_{texture}' for face, texture in
                         [('side', 'side'), ('top', 'top'), ('front', 'front_on' if lit else 'front')]}})
    write(ASSETS / f'blockstates/{block}.json', {'variants': {
        f'facing={face},lit={str(lit).lower()}': dict(model=f'yumis_sky_island:block/{block}' + ('_on' if lit else ''),
                                                    **({'y': angle} if angle else {}))
        for face, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)] for lit in [False, True]}})
    write(ASSETS / f'items/{block}.json', {'model': {'type': 'minecraft:model', 'model': f'yumis_sky_island:block/{block}'}})
    write(DATA / f'yumis_sky_island/loot_table/blocks/{block}.json', {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'yumis_sky_island:{block}',
                               'functions': [{'function': 'minecraft:copy_components', 'source': 'block_entity', 'include': ['minecraft:custom_name']}]}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    if mineral:
        lower = 'minecraft:furnace' if index == 0 else f'yumis_sky_island:{TIERS[index - 1][0]}_furnace'
        recipe = {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['MMM', 'FMF', 'MMM'],
                  'key': {'M': f'minecraft:{mineral}', 'F': lower}, 'result': {'id': f'yumis_sky_island:{block}', 'count': 1}}
    elif tier == 'netherite':
        recipe = {'type': 'minecraft:smithing_transform', 'template': 'minecraft:netherite_upgrade_smithing_template',
                  'base': 'yumis_sky_island:diamond_furnace', 'addition': 'minecraft:netherite_ingot',
                  'result': {'id': 'yumis_sky_island:netherite_furnace'}}
    else:
        recipe = {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['EDE', 'FEF', 'EDE'],
                  'key': {'E': 'minecraft:ender_eye', 'D': 'minecraft:dragon_breath', 'F': 'yumis_sky_island:netherite_furnace'},
                  'result': {'id': 'yumis_sky_island:ender_furnace', 'count': 1}}
    write(DATA / f'yumis_sky_island/recipe/{block}.json', recipe)
    trigger_item = 'minecraft:furnace' if index == 0 else f'yumis_sky_island:{TIERS[index - 1][0]}_furnace'
    write(DATA / f'yumis_sky_island/advancement/recipes/misc/{block}.json', {
        'parent': 'minecraft:recipes/root', 'criteria': {
            'has_lower': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': trigger_item}]}},
            'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': f'yumis_sky_island:{block}'}}},
        'requirements': [['has_lower', 'has_the_recipe']], 'rewards': {'recipes': [f'yumis_sky_island:{block}']}})

pickaxe = DATA / 'minecraft/tags/block/mineable/pickaxe.json'
tag = json.loads(pickaxe.read_text(encoding='utf-8')) if pickaxe.exists() else {'replace': False, 'values': []}
for tier, _, _ in TIERS:
    name = f'yumis_sky_island:{tier}_furnace'
    if name not in tag['values']:
        tag['values'].append(name)
write(pickaxe, tag)
for accessory in ACCESSORIES:
    write(ASSETS / f'items/accessory_{accessory}.json', {'model': {'type': 'minecraft:model', 'model': f'yumis_sky_island:item/accessory/{accessory}'}})
    write(ASSETS / f'models/item/accessory/{accessory}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'yumis_sky_island:item/accessory/{accessory}'}})
for locale in ['zh_cn', 'en_us']:
    file = ASSETS / f'lang/{locale}.json'
    lang = json.loads(file.read_text(encoding='utf-8'))
    for tier, name, _ in TIERS:
        lang[f'block.yumis_sky_island.{tier}_furnace'] = name + '熔炉' if locale == 'zh_cn' else tier.title() + ' Furnace'
    for accessory, name in ACCESSORIES.items():
        lang[f'item.yumis_sky_island.accessory_{accessory}'] = name if locale == 'zh_cn' else accessory.replace('_', ' ').title()
    write(file, lang)

# Add only new metadata; do not regenerate the older catalog or administrator values.
rarity_file = ROOT / 'config/yumis_sky_island/shop/rarities.toml'
rarities = tomlkit.parse(rarity_file.read_text(encoding='utf-8-sig'))
rarities['itemRarities']['yumis_sky_island:accessory_architect_backpack'] = 'epic'
rarity_file.write_text(tomlkit.dumps(rarities), encoding='utf-8')

source = ROOT / 'config/yumis_sky_island'
shops_source = tomlkit.parse((source / 'shop/shops.toml').read_text(encoding='utf-8-sig'))
for folder in ['run/config/yumis_sky_island', 'build/run/clientGameTest/config/yumis_sky_island']:
    target = ROOT / folder
    if not target.exists():
        continue
    backup = ROOT / '.tools/farming-furnaces-before' / folder
    for relative in ['shop/shops.toml', 'gameplay/furnaces.toml', 'products/quality.toml', 'shop/rarities.toml']:
        dest = target / relative
        original = dest.read_bytes() if dest.exists() else None
        if relative == 'shop/shops.toml' and dest.exists():
            current = tomlkit.parse(dest.read_text(encoding='utf-8-sig'))
            current['cropSales'] = shops_source['cropSales']
            updated = tomlkit.dumps(current).encode('utf-8')
        elif relative == 'shop/rarities.toml' and dest.exists():
            current = tomlkit.parse(dest.read_text(encoding='utf-8-sig'))
            current['itemRarities']['yumis_sky_island:accessory_architect_backpack'] = 'epic'
            updated = tomlkit.dumps(current).encode('utf-8')
        else:
            updated = (source / relative).read_bytes()
        if original != updated:
            if original is not None and not (backup / relative).exists():
                (backup / relative).parent.mkdir(parents=True, exist_ok=True)
                (backup / relative).write_bytes(original)
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(updated)
    assert tomllib.loads((target / 'shop/shops.toml').read_text(encoding='utf-8-sig'))['cropSales'] == shops_source['cropSales'].unwrap()
print('Prepared 7 furnace recipes/models, 6 building accessory models, language entries and targeted development TOML.')
