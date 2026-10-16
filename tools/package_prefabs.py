"""Review admission evidence and package this server's external, attributed library.

This is a private server asset bundle, not assets for the public mod JAR.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import tomllib
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--library",type=Path,default=ROOT/"run/prefabs/yumis_sky_island")
    parser.add_argument("--approve-exterior",nargs="*",default=[])
    parser.add_argument("--admit-production",action="store_true")
    parser.add_argument("--package",action="store_true")
    args=parser.parse_args();library=args.library.resolve()
    if not library.is_relative_to(ROOT):raise ValueError("仅处理本项目内的建筑库")
    manifest=library/"catalog.toml";text=manifest.read_text("utf-8")
    data=tomllib.loads(text)["templates"]
    evidence={r["id"]:r for r in json.loads((library/"native-validation.json").read_text("utf-8"))}
    admitted=[]
    for key,row in data.items():
        proof=evidence.get(key,{})
        exterior=key in args.approve_exterior and row["category"]!="生电"
        broken_circuits=any(count and before.split(" -> ")[0] in ("minecraft:redstone_wire","minecraft:repeater","minecraft:comparator") for before,count in proof.get("changes",{}).items())
        production=args.admit_production and proof.get("productionPassed") is True and proof.get("cannonPlacement") is True and not broken_circuits
        if not exterior and not production:continue
        digest=hashlib.sha256((library/row["file"]).read_bytes()).hexdigest()
        if digest!=proof.get("sha256"):raise ValueError(key+"的验收记录与当前文件不符")
        if exterior and proof["sameBlockAfter30Ticks"]/proof["blocks"]<.97:raise ValueError(key+"原版更新后变化过多，需要复核")
        pattern=re.compile(r"(?ms)^\[templates\."+re.escape(key)+r"]\n(.*?)(?=^\[templates\.|\Z)")
        def update(match):
            body=match.group(1)
            body=re.sub(r'^status\s*=.*$', 'status = "verified"',body,flags=re.M)
            # Architecture uses vanilla state rotation; technical layouts retain only tested directions.
            body=re.sub(r'^rotations\s*=.*$', 'rotations = [0, 1, 2, 3]' if exterior else 'rotations = [0]',body,flags=re.M)
            body=body.replace("；26.2实机验收前不上架","").replace("需26.2验收", "已通过本服26.2验收")
            return "[templates."+key+"]\n"+body
        text=pattern.sub(update,text);admitted.append(key)
    manifest.write_text(text,"utf-8",newline="\n")
    final=tomllib.loads(text)["templates"]
    rows=["# 本服建筑目录", "", "仅供本服自用，第三方作品不进入公共模组JAR。保留原作者与许可。", "", "| 建筑 | 类型 | 状态 | 作者 | 来源 |", "|---|---|---|---|---|"]
    for key,row in final.items():
        state="可购买" if row["status"]=="verified" else "候选，未上架"
        rows.append("| "+" | ".join([row["name"],row["category"],state,row["author"],"[作品页面]("+row["sourceUrl"]+")"])+" |")
    (library/"BUILDINGS.md").write_text("\n".join(rows)+"\n","utf-8")
    print(json.dumps({"admitted":admitted,"available":sum(r["status"]=="verified" for r in final.values()),"candidates":len(final)},ensure_ascii=True))
    if args.package:
        output=ROOT/"build/libs/YumisSkyIsland-server-prefabs.zip";output.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(output,"w",zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(library.rglob("*")):
                if path.is_file() and not path.name.startswith("test_"):
                    archive.write(path,"prefabs/yumis_sky_island/"+path.relative_to(library).as_posix())
            archive.write(ROOT/"docs/PREFAB_BUILDING_DELIVERY.md","预制建筑使用说明.md")
            proof=ROOT/"docs/PREFAB_VALIDATION.json"
            if proof.exists():archive.write(proof,"PREFAB_VALIDATION.json")
            for screenshot in (ROOT/"docs/screenshots/prefab").glob("*.png"):
                archive.write(screenshot,"screenshots/prefab/"+screenshot.name)
        print(str(output));print("SHA256 "+hashlib.sha256(output.read_bytes()).hexdigest())

if __name__=="__main__":main()
