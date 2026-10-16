"""Check current material values and common block decompositions against fixed prefab fees.

This checks direct dismantling, not every possible future crafting/production chain.
"""
import gzip
import json
import tomllib
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
LIBRARY=ROOT/"run/prefabs/yumis_sky_island"

def main():
    products=tomllib.loads((ROOT/"config/yumis_sky_island/shop/products.toml").read_text("utf-8"))["products"]
    shop=tomllib.loads((ROOT/"config/yumis_sky_island/shop/shops.toml").read_text("utf-8"))
    market=shop["market"]["maxPriceBps"]
    catalog=tomllib.loads((LIBRARY/"catalog.toml").read_text("utf-8"))["templates"]
    result=[]
    decomposed={"iron":"iron_ingot","gold":"gold_ingot","diamond":"diamond","emerald":"emerald","netherite":"netherite_ingot","lapis":"lapis_lazuli","redstone":"redstone"}
    def value(item):return max(shop["market"]["minimumCurrentBaseValueCoins"],products.get(item,{}).get("baseValueCoins",100))
    for key,row in catalog.items():
        data=json.loads(gzip.decompress((LIBRARY/row["file"]).read_bytes()));total=0
        for block in data["blocks"]:
            state=data["palette"][block[3]];item=state.split("[")[0];price=value(item)
            for name,material in decomposed.items():
                if item=="minecraft:"+name+"_block":price=max(price,9*value("minecraft:"+material))
            total+=price*(2 if "type=double" in state else 1)
            if len(block)>6:
                for ingredient,count in block[6]:total+=value(ingredient)*count
        upper=(total*market+9999)//10000
        result.append(dict(id=key,sha256=row["sha256"],materialValueAtMaxMarket=upper,buildFee=row["buildFee"],covered=upper<=row["buildFee"]))
    (LIBRARY/"price-audit.json").write_text(json.dumps(result,indent=2),"utf-8")
    failures=[r["id"] for r in result if not r["covered"]]
    print(json.dumps(dict(templates=len(result),maxMarketBps=market,feesBelowMaterialValue=failures)))
    if failures:raise SystemExit(1)

if __name__=="__main__":main()
