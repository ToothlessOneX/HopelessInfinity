#!/usr/bin/env python3
"""suit_assets.py (saved tool, Infinity 0.11.0): makes the four spacesuits' game assets from the reference-photo notes:
armor layers (vanilla 64x32 humanoid armor layout), backpack textures (SuitClient.pack boxes), item icons, the oxygen station's block
textures, item/block models, blockstate, recipes, loot table, pickaxe tag, and lang lines. Run: python3 tools/suit_assets.py <resources dir>.
Looks (photos viewed 2026-11-02, Wikimedia Commons):
 EMU  - white; red leg stripes (commander); grey display-and-control module on the chest; gold visor; helmet lights on the sides.
 xEMU - white legs and lower arms; navy upper torso and arm bands; red shoulders and trim; big round helmet, gold visor.
 A7L  - white Beta cloth; blue and red umbilical connectors on the chest; US flag on the left shoulder; bubble helmet, gold visor;
        blue-grey glove fingers; white lunar boots with grey ribbed soles; the backpack with the purge system on top.
 Orlan- off-white; orange-red piping; red and blue stripes on the sleeves and waist (MKS); chest control panel; Russian flag on the
        left arm; helmet lights on top; the rigid backpack that is the entry door, with its handle.
"""
import sys, os, json, random
from PIL import Image

R = sys.argv[1] if len(sys.argv) > 1 else 'src/main/resources'
A = os.path.join(R, 'assets/hopelessinfinity'); D = os.path.join(R, 'data')
def mk(p): os.makedirs(os.path.dirname(p), exist_ok=True); return p
def hexc(h, a=255): h = h.lstrip('#'); return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)
def shade(c, k): return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3],)

# box faces in the vanilla UV layout: top, bottom, right, front, left, back -> (x, y, w, h)
def faces(u, v, w, h, d):
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}
class Tex:
    def __init__(s, w=64, h=32): s.im = Image.new('RGBA', (w, h), (0, 0, 0, 0)); s.px = s.im.load(); s.rng = random.Random(1)
    def fill(s, r, c, noise=0.04):
        x0, y0, w, h = r
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w): s.px[x, y] = shade(c, 1 + (s.rng.random() - 0.5) * 2 * noise)
    def put(s, r, dx, dy, c):
        x0, y0, w, h = r
        if 0 <= dx < w and 0 <= dy < h: s.px[x0 + dx, y0 + dy] = c
    def row(s, r, dy, c, x0=0, x1=None):
        for dx in range(x0, r[2] if x1 is None else x1): s.put(r, dx, dy, c)
    def col(s, r, dx, c, y0=0, y1=None):
        for dy in range(y0, r[3] if y1 is None else y1): s.put(r, dx, dy, c)
    def box(s, u, v, w, h, d, c, noise=0.04):
        f = faces(u, v, w, h, d)
        for k, r in f.items(): s.fill(r, shade(c, {'top': 1.06, 'bottom': 0.8, 'right': 0.92, 'front': 1.0, 'left': 0.92, 'back': 0.95}[k]), noise)
        return f
    def save(s, p): s.im.save(mk(p))

GOLD, GOLD_HI = hexc('#C9A13B'), hexc('#F0D27A')
SUITS = {
 'emu':   dict(base=hexc('#ECECE8'), glove=hexc('#B9BDC4'), sole=hexc('#8A8D92')),
 'xemu':  dict(base=hexc('#EEEEEA'), navy=hexc('#1E2C4F'), red=hexc('#B3263A'), glove=hexc('#C9CCD2'), sole=hexc('#5A5E66')),
 'a7l':   dict(base=hexc('#EFEDE4'), glove=hexc('#5C6F88'), sole=hexc('#7E8187')),
 'orlan': dict(base=hexc('#F0EEE6'), pipe=hexc('#D9552B'), red=hexc('#C8302C'), blue=hexc('#2F4FA0'), glove=hexc('#E6E4DC'), sole=hexc('#6E6A62')),
}
def seams(t, r, c):  # quilted seams: a darker line every 4 rows (the suits' fabric layers show as panels)
    for dy in range(3, r[3], 4): t.row(r, dy, shade(c, 0.86))

def layer1(k, s):
    t = Tex(); b = s['base']
    # HEAD 8x8x8 at (0,0): the helmet shell, the visor on the front
    hd = t.box(0, 0, 8, 8, 8, b); fr = hd['front']
    big = k in ('xemu', 'a7l')
    y0, y1 = (1, 7) if big else (2, 6)
    for dy in range(y0, y1):
        for dx in range(1, 7): t.put(fr, dx, dy, shade(GOLD, 1.0 - 0.07 * (dy - y0)))   # the gold visor, darker downwards (reflects the sky above)
    t.put(fr, 2, y0, GOLD_HI); t.put(fr, 3, y0, GOLD_HI); t.put(fr, 2, y0 + 1, GOLD_HI)   # the sun's glint
    for side in ('right', 'left'):   # visor edge round the sides (the bubble shows)
        for dy in range(1 if big else 2, 7 if big else 6): t.put(hd[side], 7 if side == 'right' else 0, dy, shade(GOLD, 0.8))
    if k == 'emu':   # helmet lights (EHIP) on both sides
        for side in ('right', 'left'):
            for dy in (1, 2): t.put(hd[side], 3, dy, hexc('#5B5F66')); t.put(hd[side], 4, dy, hexc('#5B5F66'))
            t.put(hd[side], 3, 1, hexc('#FFF2B0'))
    if k == 'orlan':  # lights on top-front
        t.put(fr, 1, 0, hexc('#FFF2B0')); t.put(fr, 6, 0, hexc('#FFF2B0')); t.row(hd['top'], 6, s['pipe'])
    if k == 'xemu':
        t.row(hd['top'], 7, s['red'], 2, 6)
    # BODY 8x12x4 at (16,16)
    bd = t.box(16, 16, 8, 12, 4, b); seams(t, bd['front'], b); seams(t, bd['back'], b)
    f = bd['front']
    if k == 'emu':    # display and control module
        for dy in range(2, 6):
            for dx in range(2, 6): t.put(f, dx, dy, hexc('#6C7078'))
        t.put(f, 3, 3, hexc('#E8E04A')); t.put(f, 4, 3, hexc('#D23B2E')); t.put(f, 3, 4, hexc('#2E2E30')); t.put(f, 4, 4, hexc('#2E2E30'))
    if k == 'xemu':   # navy upper torso, red shoulders
        for r in (bd['front'], bd['back'], bd['right'], bd['left']):
            for dy in range(0, 7): t.row(r, dy, shade(s['navy'], 1 + (dy % 4 == 3) * -0.1))
        t.row(bd['top'], 0, s['red']); t.row(bd['top'], 1, s['red']); t.row(bd['top'], 2, s['red']); t.row(bd['top'], 3, s['red'])
        t.row(f, 7, s['red'])
    if k == 'a7l':    # umbilical connectors: blue, blue, red
        for dx, c in ((1, hexc('#2D5DB3')), (3, hexc('#2D5DB3')), (5, hexc('#C23A2E'))):
            t.put(f, dx, 6, c); t.put(f, dx + 1, 6, c); t.put(f, dx, 7, shade(c, 0.8)); t.put(f, dx + 1, 7, shade(c, 0.8))
        t.row(f, 9, hexc('#9EA2A8'), 1, 7)
    if k == 'orlan':  # chest control panel, orange piping
        for dy in range(4, 8):
            for dx in range(2, 6): t.put(f, dx, dy, hexc('#7A7C80'))
        t.put(f, 2, 5, hexc('#E8E04A')); t.put(f, 5, 5, hexc('#40B060'))
        for r in (bd['front'], bd['back']): t.col(r, 0, s['pipe']); t.col(r, 7, s['pipe'])
        t.row(f, 11, s['red']); t.row(f, 10, s['blue'])
    # ARMS 4x12x4 at (40,16): sleeve, wrist ring, glove
    ar = t.box(40, 16, 4, 12, 4, b)
    for r in ar.values():
        if r[3] == 12:
            seams(t, r, b); t.row(r, 8, hexc('#9EA2A8'))
            for dy in (9, 10, 11): t.row(r, dy, shade(s['glove'], 1 - 0.06 * (dy - 9)))
    if k == 'a7l':
        t.put(ar['front'], 1, 11, shade(s['glove'], 0.75)); t.put(ar['front'], 2, 11, shade(s['glove'], 0.75))
    if k == 'xemu':
        for r in ar.values():
            if r[3] == 12:
                for dy in range(0, 4): t.row(r, dy, s['navy'])
                t.row(r, 0, s['red']); t.row(r, 5, s['navy'])
        t.row(ar['top'], 0, s['red']); t.row(ar['top'], 1, s['red'])
    if k == 'orlan':
        for r in ar.values():
            if r[3] == 12: t.row(r, 6, s['red']); t.row(r, 7, s['blue'])
    # flags on the outer upper arm (the left arm mirrors this texture, so the flag shows on both: real suits carry one; CHOSEN, the model shares it)
    fl = ar['right']
    if k in ('emu', 'a7l', 'xemu'):
        for dy in range(1, 4):
            for dx in range(0, 4): t.put(fl, dx, dy, hexc('#B22234') if dy % 2 else hexc('#FFFFFF'))
        t.put(fl, 0, 1, hexc('#3C3B6E')); t.put(fl, 1, 1, hexc('#3C3B6E')); t.put(fl, 0, 2, hexc('#3C3B6E')); t.put(fl, 1, 2, hexc('#3C3B6E'))
    if k == 'orlan':
        for dx in range(4): t.put(fl, dx, 1, hexc('#FFFFFF')); t.put(fl, dx, 2, hexc('#0039A6')); t.put(fl, dx, 3, hexc('#D52B1E'))
    # BOOTS: the leg box (0,16) 4x12x4, lower part only
    lg = faces(0, 16, 4, 12, 4)
    for kf, r in lg.items():
        if r[3] == 12:
            for dy in range(7, 12): t.row(r, dy, shade(b, 0.97))
            t.row(r, 7, shade(b, 0.85)); t.row(r, 11, s['sole'])
            if k == 'a7l': t.row(r, 10, shade(s['sole'], 1.15))
    t.fill(lg['bottom'], s['sole'])
    return t

def layer2(k, s):
    t = Tex(); b = s['base']
    # waist (body box, lower half) and legs
    bd = faces(16, 16, 8, 12, 4)
    for kf, r in bd.items():
        if r[3] == 12:
            for dy in range(6, 12): t.row(r, dy, shade(b, 0.98))
            t.row(r, 6, hexc('#9EA2A8'))   # the body seal ring between upper and lower torso
            if k == 'orlan': t.row(r, 9, s['red']); t.row(r, 10, s['blue'])
    lg = faces(0, 16, 4, 12, 4)
    for kf, r in lg.items():
        t.fill(r, shade(b, {'top': 1.05, 'bottom': 0.8}.get(kf, 0.95)))
        if r[3] == 12:
            seams(t, r, b); t.row(r, 5, shade(b, 0.8))   # knee joint
            if k == 'emu': t.row(r, 1, hexc('#B8232F')); t.row(r, 2, hexc('#B8232F')); t.row(r, 7, hexc('#B8232F'))
            if k == 'xemu': t.row(r, 0, s['red'])
            if k == 'orlan': t.col(r, 0, s['pipe'])
    return t

PACK = {'emu': (9, 12, 4, 0), 'xemu': (10, 13, 5, 0), 'a7l': (8, 11, 4, 1), 'orlan': (9, 13, 5, 0)}   # = SuitClient.PACK
def pack(k, s):
    t = Tex(); b = s['base']; w, h, d, ops = PACK[k]
    p = t.box(0, 0, w, h, d, b)
    bk = p['back']   # the face away from the wearer
    for dy in range(0, h, 4): t.row(bk, dy, shade(b, 0.85))
    t.col(bk, 0, shade(b, 0.82)); t.col(bk, w - 1, shade(b, 0.82))
    if k == 'emu':   # secondary oxygen pack: the lower third
        for dy in range(h - 4, h): t.row(bk, dy, hexc('#D4D6D8'))
        t.row(bk, h - 5, hexc('#7F848B'))
    if k == 'xemu':
        t.row(bk, 1, s['navy']); t.row(bk, 2, s['navy']); t.row(bk, h - 2, s['red'])
    if k == 'a7l':
        for dy in range(3, h - 2): t.put(bk, 1, dy, hexc('#9EA2A8')); t.put(bk, w - 2, dy, hexc('#9EA2A8'))
        o = t.box(32, 0, w, 3, d, shade(b, 0.97)); t.row(o['back'], 2, hexc('#9EA2A8'))
    if k == 'orlan':  # the door: piping round it, the handle in the middle, the red umbilical
        t.row(bk, 0, s['pipe']); t.row(bk, h - 1, s['pipe']); t.col(bk, 0, s['pipe']); t.col(bk, w - 1, s['pipe'])
        for dx in range(2, w - 2): t.put(bk, dx, h // 2, hexc('#5E6166'))
        t.put(bk, 2, h // 2 - 1, hexc('#5E6166')); t.put(bk, w - 3, h // 2 - 1, hexc('#5E6166'))
        t.col(p['left'], 1, s['red'], 2, 8)
    t.box(32, 12, 2, 3, 2, hexc('#9AA0A8'), 0.02)
    t.fill(faces(32, 12, 2, 3, 2)['bottom'], hexc('#2A2A2C'), 0)
    return t

def icon(k, s, piece):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); px = im.load(); b = s['base']; out = shade(b, 0.55)
    acc = s.get('navy') or s.get('pipe') or (hexc('#B8232F') if k == 'emu' else hexc('#2D5DB3'))
    def P(x, y, c): px[x, y] = c
    if piece == 'helmet':
        for y in range(2, 14):
            for x in range(2, 14):
                if (x - 7.5) ** 2 + (y - 8) ** 2 <= 34: P(x, y, b)
        for y in range(5, 11):
            for x in range(4, 12):
                if (x - 7.5) ** 2 / 16 + (y - 7.8) ** 2 / 9 <= 1: P(x, y, GOLD if (x + y) % 4 else GOLD_HI)
        for x in range(4, 12): P(x, 13, hexc('#9EA2A8'))
    elif piece == 'upper':
        for y in range(2, 14):
            for x in range(2, 14):
                if (y < 6 and 2 <= x <= 13) or (4 <= x <= 11): P(x, y, b)
        for y in range(3, 8):
            for x in range(5, 11): P(x, y, hexc('#6C7078') if k in ('emu', 'orlan') else (acc if k == 'xemu' else b))
        for x in range(2, 14): P(x, 2, acc if k != 'emu' else b)
        for y in range(6, 14): P(2, y, b) if y < 10 else None
        P(6, 10, hexc('#2D5DB3')); P(9, 10, hexc('#C23A2E'))
    elif piece == 'lower':
        for y in range(2, 15):
            for x in range(3, 13):
                if y < 6 or x <= 6 or x >= 9: P(x, y, b)
        for x in range(3, 13): P(x, 2, hexc('#9EA2A8'))
        if k == 'emu':
            for x in list(range(3, 7)) + list(range(9, 13)): P(x, 8, hexc('#B8232F'))
        if k == 'orlan':
            for x in range(3, 13): P(x, 4, s['red']); P(x, 5, s['blue'])
        if k == 'xemu':
            for x in list(range(3, 7)) + list(range(9, 13)): P(x, 7, s['red'])
    else:
        for y in range(6, 14):
            for x in list(range(1, 7)) + list(range(9, 15)):
                if y >= 10 or x in (3, 4, 5, 6, 9, 10, 11, 12): P(x, y, b)
        for x in list(range(1, 7)) + list(range(9, 15)): P(x, 13, s['sole'])
    # outline
    a = im.copy().load()
    for y in range(16):
        for x in range(16):
            if a[x, y][3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and a[x + dx, y + dy][3] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))): px[x, y] = out
    return im

def station():
    side = Tex(16, 16); side.fill((0, 0, 16, 16), hexc('#8C9096'), 0.05)
    for i in range(16): side.px[i, 0] = side.px[i, 15] = side.px[0, i] = side.px[15, i] = hexc('#5D6066')
    for y in range(3, 9):
        for x in range(3, 9): side.px[x, y] = hexc('#E8F0F2') if (x - 5.5) ** 2 + (y - 5.5) ** 2 <= 7 else side.px[x, y]
    side.px[5, 4] = side.px[6, 5] = hexc('#2F8F4A'); side.px[5, 5] = hexc('#202020')
    for x in (10, 11, 12): side.px[x, 4] = hexc('#3FBF5F')
    for y in range(11, 14):
        for x in range(3, 13): side.px[x, y] = hexc('#4A4D52') if x % 2 else hexc('#6A6D72')
    top = Tex(16, 16); top.fill((0, 0, 16, 16), hexc('#7E8288'), 0.05)
    for y in range(2, 14, 2):
        for x in range(2, 14): top.px[x, y] = hexc('#4A4D52')
    for x in range(5, 11): top.px[x, 8] = hexc('#2F6FB0')
    return side, top

NAMES = {'emu': 'EMU', 'xemu': 'xEMU', 'a7l': 'A7L', 'orlan': 'Orlan-MKS'}
PIECE_NAME = {'helmet': 'Helmet', 'upper': 'Upper Torso, Life Support and Jetpack', 'lower': 'Lower Torso', 'boots': 'Boots'}
DYE = {'emu': 'minecraft:red_dye', 'xemu': 'minecraft:blue_dye', 'a7l': 'minecraft:light_blue_dye', 'orlan': 'minecraft:orange_dye'}
RECIPE = {'helmet': (['IDI', 'GVG'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass', 'V': 'minecraft:gold_ingot'}),
          'upper': (['WDW', 'CRC', 'WIW'], {'W': 'minecraft:white_wool', 'C': 'minecraft:copper_ingot', 'R': 'minecraft:redstone', 'I': 'minecraft:iron_ingot'}),
          'lower': (['WDW', 'W W', 'W W'], {'W': 'minecraft:white_wool'}),
          'boots': (['WDW', 'I I'], {'W': 'minecraft:white_wool', 'I': 'minecraft:iron_ingot'})}

def main():
    lang = {}
    for k, s in SUITS.items():
        layer1(k, s).save(f'{A}/textures/models/armor/{k}_layer_1.png'); layer2(k, s).save(f'{A}/textures/models/armor/{k}_layer_2.png')
        pack(k, s).save(f'{A}/textures/models/pack_{k}.png')
        for piece in ('helmet', 'upper', 'lower', 'boots'):
            n = f'{k}_{piece}'; icon(k, s, piece).save(mk(f'{A}/textures/item/{n}.png'))
            json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'hopelessinfinity:item/{n}'}}, open(mk(f'{A}/models/item/{n}.json'), 'w'), indent=1)
            pat, keys = RECIPE[piece]; kk = {c: {'item': i} for c, i in keys.items()}; kk['D'] = {'item': DYE[k]}
            json.dump({'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': pat, 'key': kk, 'result': {'item': f'hopelessinfinity:{n}'}}, open(mk(f'{D}/hopelessinfinity/recipes/{n}.json'), 'w'), indent=1)
            lang[f'item.hopelessinfinity.{n}'] = f'{NAMES[k]} {PIECE_NAME[piece]}'
    sd, tp = station(); sd.save(f'{A}/textures/block/oxygen_station_side.png'); tp.save(f'{A}/textures/block/oxygen_station_top.png')
    json.dump({'parent': 'minecraft:block/cube_bottom_top', 'textures': {'side': 'hopelessinfinity:block/oxygen_station_side', 'top': 'hopelessinfinity:block/oxygen_station_top', 'bottom': 'hopelessinfinity:block/oxygen_station_top'}}, open(mk(f'{A}/models/block/oxygen_station.json'), 'w'), indent=1)
    json.dump({'parent': 'hopelessinfinity:block/oxygen_station'}, open(mk(f'{A}/models/item/oxygen_station.json'), 'w'), indent=1)
    json.dump({'variants': {'': {'model': 'hopelessinfinity:block/oxygen_station'}}}, open(mk(f'{A}/blockstates/oxygen_station.json'), 'w'), indent=1)
    json.dump({'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['ICI', 'GRG', 'ICI'], 'key': {'I': {'item': 'minecraft:iron_ingot'}, 'C': {'item': 'minecraft:copper_ingot'}, 'G': {'item': 'minecraft:glass'}, 'R': {'item': 'minecraft:redstone'}}, 'result': {'item': 'hopelessinfinity:oxygen_station'}}, open(mk(f'{D}/hopelessinfinity/recipes/oxygen_station.json'), 'w'), indent=1)
    json.dump({'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'hopelessinfinity:oxygen_station'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}, open(mk(f'{D}/hopelessinfinity/loot_tables/blocks/oxygen_station.json'), 'w'), indent=1)
    json.dump({'replace': False, 'values': ['hopelessinfinity:oxygen_station']}, open(mk(f'{D}/minecraft/tags/blocks/mineable/pickaxe.json'), 'w'), indent=1)
    lang['block.hopelessinfinity.oxygen_station'] = 'Oxygen Station'
    lp = f'{A}/lang/en_us.json'; L = json.load(open(lp)) if os.path.exists(lp) else {}; L.update(lang); json.dump(L, open(lp, 'w'), indent=1, ensure_ascii=False)
    print('suit assets written:', len(lang), 'lang lines')
main()
