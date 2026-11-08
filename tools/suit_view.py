#!/usr/bin/env python3
"""suit_view.py (saved tool, Infinity 0.11.0): renders the four spacesuits as the game draws them - Minecraft's humanoid model (HumanoidModel
part pivots and boxes), the armor layers with the game's inflation (outer layer +1.0, inner +0.5 model pixels), the vanilla UV box layout
(with mirrored left limbs), and the backpack boxes of client/SuitClient.pack - from the textures in the mod's resources. No guessing at
the look: the same PNGs and boxes the game uses. Run: python3 tools/suit_view.py <resources dir> <out.png>"""
import sys, math, numpy as np
from PIL import Image

R = sys.argv[1]; OUT = sys.argv[2]
A = R + '/assets/hopelessinfinity/textures'
PACK = {'emu': (9, 12, 4, 0), 'xemu': (10, 13, 5, 0), 'a7l': (8, 11, 4, 1), 'orlan': (9, 13, 5, 0)}   # = SuitClient.PACK

def tex(p): return np.asarray(Image.open(p).convert('RGBA')).astype(np.float32)

def box_quads(x0, y0, z0, w, h, d, u, v, infl, mirror, pivot, rotx=0.0):
    """Model space (y down, front -z). Returns (corners[4] world, uv[4]) per face. World: X = x, Y = -y, Z = -z (front +Z)."""
    xa, ya, za = x0 - infl, y0 - infl, z0 - infl; xb, yb, zb = x0 + w + infl, y0 + h + infl, z0 + d + infl
    R = {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}
    if mirror: R['right'], R['left'] = R['left'], R['right']
    def uvq(r, flip):   # corners: (u0,v0) top-left, (u1,v0), (u1,v1), (u0,v1)
        a, b, c, e = r[0], r[1], r[0] + r[2], r[1] + r[3]
        if flip: a, c = c, a
        return [(a, b), (c, b), (c, e), (a, e)]
    # geometry corners matching the uv corners (top-left, top-right, bottom-right, bottom-left as seen from outside)
    F = {'front': [(xa, ya, za), (xb, ya, za), (xb, yb, za), (xa, yb, za)],
         'back': [(xb, ya, zb), (xa, ya, zb), (xa, yb, zb), (xb, yb, zb)],
         'right': [(xa, ya, zb), (xa, ya, za), (xa, yb, za), (xa, yb, zb)],
         'left': [(xb, ya, za), (xb, ya, zb), (xb, yb, zb), (xb, yb, za)],
         'top': [(xa, ya, zb), (xb, ya, zb), (xb, ya, za), (xa, ya, za)],
         'bottom': [(xa, yb, za), (xb, yb, za), (xb, yb, zb), (xa, yb, zb)]}
    out = []
    for k, cs in F.items():   # mirror (left limbs): the same box, each face's texture flipped left-right and the two side regions swapped
        cy, sy = math.cos(rotx), math.sin(rotx); w3 = []
        for (x, y, z) in cs: y2, z2 = y * cy - z * sy, y * sy + z * cy; w3.append((x + pivot[0], -(y2 + pivot[1]), -(z2 + pivot[2])))
        out.append((w3, uvq(R[k], mirror), k))
    return out

def render(kind, yaw, W=420, H=560, SS=3):
    l1, l2, pk = tex(f'{A}/models/armor/{kind}_layer_1.png'), tex(f'{A}/models/armor/{kind}_layer_2.png'), tex(f'{A}/models/pack_{kind}.png')
    body = np.zeros((32, 64, 4), np.float32); body[..., :3] = (120, 112, 104); body[..., 3] = 255   # the wearer under the suit (plain)
    Q = []   # (corners, uv, texture)
    def part(T, x0, y0, z0, w, h, d, u, v, infl, mirror, piv):
        for c, uv, k in box_quads(x0, y0, z0, w, h, d, u, v, infl, mirror, piv): Q.append((c, uv, T))
    for T, inf in ((body, 0.0),):
        part(T, -4, -8, -4, 8, 8, 8, 0, 0, inf, False, (0, 0, 0)); part(T, -4, 0, -2, 8, 12, 4, 16, 16, inf, False, (0, 0, 0))
        part(T, -3, -2, -2, 4, 12, 4, 40, 16, inf, False, (-5, 2, 0)); part(T, -1, -2, -2, 4, 12, 4, 40, 16, inf, True, (5, 2, 0))
        part(T, -2, 0, -2, 4, 12, 4, 0, 16, inf, False, (-1.9, 12, 0)); part(T, -2, 0, -2, 4, 12, 4, 0, 16, inf, True, (1.9, 12, 0))
    # inner layer (leggings) +0.5, outer layer (helmet, chest, boots) +1.0
    part(l2, -4, 0, -2, 8, 12, 4, 16, 16, 0.5, False, (0, 0, 0))
    part(l2, -2, 0, -2, 4, 12, 4, 0, 16, 0.5, False, (-1.9, 12, 0)); part(l2, -2, 0, -2, 4, 12, 4, 0, 16, 0.5, True, (1.9, 12, 0))
    part(l1, -4, -8, -4, 8, 8, 8, 0, 0, 1.0, False, (0, 0, 0)); part(l1, -4, -8, -4, 8, 8, 8, 32, 0, 1.5, False, (0, 0, 0))
    part(l1, -4, 0, -2, 8, 12, 4, 16, 16, 1.0, False, (0, 0, 0))
    part(l1, -3, -2, -2, 4, 12, 4, 40, 16, 1.0, False, (-5, 2, 0)); part(l1, -1, -2, -2, 4, 12, 4, 40, 16, 1.0, True, (5, 2, 0))
    part(l1, -2, 0, -2, 4, 12, 4, 0, 16, 1.0, False, (-1.9, 12, 0)); part(l1, -2, 0, -2, 4, 12, 4, 0, 16, 1.0, True, (1.9, 12, 0))
    w, h, d, ops = PACK[kind]
    part(pk, -w / 2, -0.5, 3, w, h, d, 0, 0, 0, False, (0, 0, 0))
    if ops: part(pk, -w / 2, -3.5, 3, w, 3, d, 32, 0, 0, False, (0, 0, 0))
    part(pk, -w / 2 - 2, h - 4.5, 3 + d / 2 - 1, 2, 3, 2, 32, 12, 0, False, (0, 0, 0)); part(pk, w / 2, h - 4.5, 3 + d / 2 - 1, 2, 3, 2, 32, 12, 0, False, (0, 0, 0))
    # camera
    Wp, Hp = W * SS, H * SS; img = np.zeros((Hp, Wp, 3), np.float32); img[:] = (34, 38, 46); zb = np.full((Hp, Wp), 1e9, np.float32)
    cy, sy = math.cos(yaw), math.sin(yaw); camd = 75.0; f = Hp * 1.55; L = np.array([0.4, 0.7, 0.6]); L /= np.linalg.norm(L)
    def proj(p):
        x, y, z = p; y += 4   # centre the figure (model y -8..24 -> world 8..-24)
        xr, zr = x * cy + z * sy, -x * sy + z * cy; dz = camd - zr
        return np.array([Wp / 2 + f * xr / dz, Hp / 2 - f * y / dz, dz]), np.array([xr, y, zr])
    for cs, uv, T in Q:
        P = [proj(c) for c in cs]; S = np.array([p[0] for p in P]); V = np.array([p[1] for p in P])
        n = np.cross(V[1] - V[0], V[3] - V[0]); nl = np.linalg.norm(n)
        if nl < 1e-9: continue
        n /= nl
        if np.dot(n, np.array([0, 0, camd]) - V[0]) >= 0: continue   # back face
        lum = 0.55 + 0.45 * max(0.0, float(np.dot(-n, L)))
        UV = np.array(uv, np.float32)
        for tri in ((0, 1, 2), (0, 2, 3)):
            s = S[list(tri)]; t = UV[list(tri)]
            x0, x1 = int(max(0, np.floor(s[:, 0].min()))), int(min(Wp - 1, np.ceil(s[:, 0].max()))); y0, y1 = int(max(0, np.floor(s[:, 1].min()))), int(min(Hp - 1, np.ceil(s[:, 1].max())))
            if x1 < x0 or y1 < y0: continue
            xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            (ax, ay), (bx, by), (cx, cy2) = s[0, :2], s[1, :2], s[2, :2]; den = (by - cy2) * (ax - cx) + (cx - bx) * (ay - cy2)
            if abs(den) < 1e-9: continue
            w0 = ((by - cy2) * (xs - cx) + (cx - bx) * (ys - cy2)) / den; w1 = ((cy2 - ay) * (xs - cx) + (ax - cx) * (ys - cy2)) / den; w2 = 1 - w0 - w1
            m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not m.any(): continue
            iz = w0 / s[0, 2] + w1 / s[1, 2] + w2 / s[2, 2]; z = 1 / iz
            u = (w0 * t[0, 0] / s[0, 2] + w1 * t[1, 0] / s[1, 2] + w2 * t[2, 0] / s[2, 2]) * z; v = (w0 * t[0, 1] / s[0, 2] + w1 * t[1, 1] / s[1, 2] + w2 * t[2, 1] / s[2, 2]) * z
            ui = np.clip(np.floor(u - 1e-4).astype(int), 0, T.shape[1] - 1); vi = np.clip(np.floor(v - 1e-4).astype(int), 0, T.shape[0] - 1)
            texel = T[vi, ui]; m &= texel[..., 3] > 0.5
            sub = zb[y0:y1 + 1, x0:x1 + 1]; m &= z < sub + 1e-3   # LEQUAL with draw order, as the game
            sub[m] = z[m]; img[y0:y1 + 1, x0:x1 + 1][m] = texel[..., :3][m] * lum
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((W, H), Image.LANCZOS)

kinds = ['emu', 'xemu', 'a7l', 'orlan']; names = {'emu': 'EMU', 'xemu': 'xEMU', 'a7l': 'Apollo A7L', 'orlan': 'Orlan-MKS'}
W, H = 300, 400; sheet = Image.new('RGB', (W * 4, H * 2 + 30), (20, 22, 26))
from PIL import ImageDraw
dr = ImageDraw.Draw(sheet)
for i, k in enumerate(kinds):
    sheet.paste(render(k, math.radians(-30), W, H), (i * W, 30)); sheet.paste(render(k, math.radians(180 + 35), W, H), (i * W, 30 + H))
    dr.text((i * W + 10, 8), names[k] + "  (front / back)", fill=(230, 230, 230))
sheet.save(OUT); print('saved', OUT)
