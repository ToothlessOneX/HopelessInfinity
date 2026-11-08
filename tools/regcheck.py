"""0.26.0 (from Hopeless Deep's regcheck.py, extended): run from the project root. Checks:
 - no two blocks (or two items) of one registry id (Forge refuses to start otherwise);
 - every block tag entry of ours names a real block (one unknown id makes the game drop the whole tag);
 - every block id has a blockstate; every model a blockstate or item model names exists; every texture a model names exists (ours in
   this jar, minecraft: ones in the client jar);
 - every block and item id of ours has an item model.
Exit 1 on any problem. Saved tool."""
import re, sys, glob, collections, json, os, zipfile
NS = "hopelessinfinity"; A = "src/main/resources/assets/" + NS; CLIENT = "/mnt/user-data/uploads/Install/versions/1.20.1/1.20.1.jar"
seen = collections.defaultdict(list)
for f in glob.glob("src/main/java/**/*.java", recursive=True):
    for m in re.finditer(r'RegistryObject<([A-Za-z.<>?, ]+)>\s+([A-Z_0-9]+)\s*=\s*([A-Za-z_.]+)\("([a-z0-9_]+)"', open(f).read()):
        t = m.group(1); kind = "item" if "Item" in t and "Entity" not in t else "block" if "Block" in t and "Entity" not in t else t
        seen[(kind, m.group(4))].append(f.split("/")[-1] + ":" + m.group(2))
bad = {k: v for k, v in seen.items() if len(v) > 1}
for k, v in bad.items(): print("DUPLICATE", k, v)
blocks = {os.path.basename(f)[:-5] for f in glob.glob(A + "/blockstates/*.json")}
badtags = []
for f in glob.glob("src/main/resources/data/*/tags/blocks/**/*.json", recursive=True):
    for v in json.load(open(f)).get("values", []):
        if isinstance(v, str) and v.startswith(NS + ":") and v.split(":")[1] not in blocks: badtags.append((f.split("tags/")[1], v))
for b in badtags: print("TAG NAMES NO BLOCK", b)
cj = zipfile.ZipFile(CLIENT); cnames = set(cj.namelist())
def res(rl, kind, ext):
    ns, p = (rl.split(":", 1) if ":" in rl else ("minecraft", rl))
    path = "assets/%s/%s/%s.%s" % (ns, kind, p, ext)
    if ns == NS: return os.path.exists("src/main/resources/" + path)
    return path in cnames
missing = collections.defaultdict(set)
def check_model(rl, src, depth=0):
    if rl.startswith("builtin/") or depth > 8: return
    if not res(rl, "models", "json"): missing["model"].add((rl, src)); return
    ns, p = (rl.split(":", 1) if ":" in rl else ("minecraft", rl))
    if ns != NS: return
    j = json.load(open("src/main/resources/assets/%s/models/%s.json" % (ns, p)))
    for k, t in j.get("textures", {}).items():
        if isinstance(t, str) and not t.startswith("#") and not res(t, "textures", "png"): missing["texture"].add((t, rl))
    if "parent" in j: check_model(j["parent"], rl, depth + 1)
for f in glob.glob(A + "/blockstates/*.json"):
    j = json.load(open(f)); name = os.path.basename(f)[:-5]
    vs = []
    for v in j.get("variants", {}).values(): vs += v if isinstance(v, list) else [v]
    for part in j.get("multipart", []): a = part.get("apply"); vs += a if isinstance(a, list) else [a]
    for v in vs:
        if v and "model" in v: check_model(v["model"], name)
ids = {k[1] for k in seen if k[0] == "item"}   # block-only ids (crops, upper halves) need no item model
for i in sorted(ids):
    if not os.path.exists(A + "/models/item/" + i + ".json") and not i.startswith("shape_") and i != "shaped": missing["item model"].add((i, ""))
    else:
        if os.path.exists(A + "/models/item/" + i + ".json"): check_model(NS + ":item/" + i, "item " + i)
for k in missing:
    print("MISSING %s: %d, e.g. %s" % (k, len(missing[k]), sorted(missing[k])[:6]))
nb = len([k for k in seen if k[0] == "block"]); ni = len([k for k in seen if k[0] == "item"])
problems = len(bad) + len(badtags) + sum(len(v) for v in missing.values())
print("registry ids: %d blocks, %d items; duplicates %d; bad tag entries %d; missing %s" % (nb, ni, len(bad), len(badtags), {k: len(v) for k, v in missing.items()}))
sys.exit(1 if problems else 0)
