"""Map the Aseprite crate contact sheet's 130 variants to 26.2 item models."""

import csv
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "art/biome_treasure_boxes/manifest.tsv"
ITEMS = ROOT / "src/main/resources/assets/yumis_sky_island/items"
MODELS = ROOT / "src/main/resources/assets/yumis_sky_island/models/item/biome_treasure_box"
TEXTURES = ROOT / "src/main/resources/assets/yumis_sky_island/textures/item/biome_treasure_box"


def model(variant: str) -> dict:
    return {"type": "minecraft:model", "model": f"yumis_sky_island:item/biome_treasure_box/{variant}"}


def main() -> None:
    with MANIFEST.open(encoding="utf-8", newline="") as file:
        rows = list(csv.DictReader(file, delimiter="\t"))
    variants = [row["variant"] for row in rows]
    if len(variants) != 130 or len(set(variants)) != 130:
        raise ValueError("Expected exactly 130 distinct Aseprite crate variants")
    MODELS.mkdir(parents=True, exist_ok=True)
    for variant in variants:
        if not (TEXTURES / f"{variant}.png").is_file():
            raise FileNotFoundError(variant)
        definition = {"parent": "minecraft:item/generated", "textures": {"layer0": f"yumis_sky_island:item/biome_treasure_box/{variant}"}}
        (MODELS / f"{variant}.json").write_text(json.dumps(definition, indent=2) + "\n", encoding="utf-8")
    selector = {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": [{"when": variant, "model": model(variant)} for variant in variants],
            "fallback": model("plains_water"),
        }
    }
    (ITEMS / "biome_treasure_box.json").write_text(json.dumps(selector, indent=2) + "\n", encoding="utf-8")
    print(f"Mapped {len(variants)} Aseprite crate PNGs to custom-model-data variants")


if __name__ == "__main__":
    main()
