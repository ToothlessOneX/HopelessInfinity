"""Resources for the slope and shaped blocks (ported from Hopeless Deep 0.26.0). The client models are made in code (slope/client/SlopeModels);
these files are the fallbacks the game loads first, the names, the drops (a slope gives back its material) and the tool tags."""
import json, os, re
B = "/home/claude/infinity/src/main/resources"; R = B + "/assets/hopelessinfinity"; D = B + "/data"
J = open("/home/claude/infinity/src/main/java/com/hopeless/infinity/slope/Slopes.java").read()
nat = re.findall(r'"([a-z_]+)"', J[J.index('for (String b : new String[]{"stone"'):J.index('MAT.put(b, "slope_" + b);')])
SH = open("/home/claude/infinity/src/main/java/com/hopeless/infinity/slope/ShapeLib.java").read()
shapes = re.findall(r'add\("([a-z0-9_]+)", "([^"]+)"', SH)
for d in ("blockstates", "models/item", "lang"): os.makedirs(f"{R}/{d}", exist_ok=True)
os.makedirs(f"{D}/hopelessinfinity/loot_tables/blocks", exist_ok=True); os.makedirs(f"{D}/minecraft/tags/blocks/mineable", exist_ok=True)
lang = {}
soft = re.compile(r"slope_(grass_block|dirt|coarse_dirt|podzol|moss_block|sand|red_sand|gravel|clay|mud|snow_block|mycelium|rooted_dirt|packed_mud)$")
pick, shovel = [], []
drop = {"grass_block": "dirt", "podzol": "dirt", "mycelium": "dirt", "stone": "cobblestone", "deepslate": "cobbled_deepslate", "snow_block": "snowball", "packed_ice": "air", "clay": "clay_ball"}
for mat in nat:
    sl = "slope_" + mat; model = {"grass_block": "minecraft:block/grass_block", "basalt": "minecraft:block/basalt", "snow_block": "minecraft:block/snow_block"}.get(mat, f"minecraft:block/{mat}")
    json.dump({"variants": {"": {"model": model}}}, open(f"{R}/blockstates/{sl}.json", "w"), indent=1)
    json.dump({"parent": model}, open(f"{R}/models/item/{sl}.json", "w"), indent=1)
    lang[f"block.hopelessinfinity.{sl}"] = mat.replace("_block", "").replace("_", " ").title() + " Slope"
    it = drop.get(mat, mat)
    pool = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"minecraft:{it}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}
    json.dump({"type": "minecraft:block", "pools": [] if it == "air" else [pool]}, open(f"{D}/hopelessinfinity/loot_tables/blocks/{sl}.json", "w"), indent=1)
    (shovel if soft.match(sl) else pick).append(f"hopelessinfinity:{sl}")
json.dump({"variants": {"": {"model": "minecraft:block/stone"}}}, open(f"{R}/blockstates/shaped.json", "w"), indent=1)
lang["block.hopelessinfinity.shaped"] = "Shaped Block"
for sid, name in shapes:
    json.dump({"parent": "minecraft:block/stone"}, open(f"{R}/models/item/shape_{sid}.json", "w"), indent=1)
    lang[f"item.hopelessinfinity.shape_{sid}"] = name
pick.append("hopelessinfinity:shaped")
json.dump(lang, open(f"{R}/lang/en_us.json", "w"), indent=1, ensure_ascii=False)
json.dump({"replace": False, "values": pick}, open(f"{D}/minecraft/tags/blocks/mineable/pickaxe.json", "w"), indent=1)
json.dump({"replace": False, "values": shovel}, open(f"{D}/minecraft/tags/blocks/mineable/shovel.json", "w"), indent=1)
print("terrain slopes", len(nat), "shapes", len(shapes), "pickaxe", len(pick), "shovel", len(shovel))
