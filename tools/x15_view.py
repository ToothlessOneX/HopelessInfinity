#!/usr/bin/env python3
"""x15_view.py quads.json out.png: renders vehicle/X15Mesh.build() (dumped by tools/X15Dump) - the same quads, colours and vertex normals
the game's renderer draws - from the side, above, three-quarters front and rear, and close on the cockpit, with a 1.8 m Minecraft player
(the 0.6 x 1.8 m box) beside it for scale. Lambert light per vertex (as the game's entity shading). Saved tool."""
import sys, json, math, numpy as np
from PIL import Image, ImageDraw
Q = json.load(open(sys.argv[1])); OUT = sys.argv[2]
def boxq(x0, x1, y0, y1, z0, z1, c):
    P = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    F = [((0, 3, 2, 1), (0, 0, -1)), ((4, 5, 6, 7), (0, 0, 1)), ((0, 4, 7, 3), (-1, 0, 0)), ((1, 2, 6, 5), (1, 0, 0)), ((3, 7, 6, 2), (0, 1, 0)), ((0, 1, 5, 4), (0, -1, 0))]
    return [{'c': c, 'p': [list(P[i]) + list(n) for i in f]} for f, n in F]
PLAYER = boxq(-0.3, 0.3, -1.0, 0.8, 7.74 - 6.3 - 0.3, 7.74 - 6.3 + 0.3, 0x3A6EA5)   # a player standing ahead of the wing, 3.9 m out to the right
for q in PLAYER:
    for v in q['p']: v[0] += 3.9
def render(R, W, H, scale, cx=0, cy=0, title='', extra=(), at=None):
    if at is not None: c = R @ np.array(at); cx, cy = c[0], c[1]
    SS = 2; W2, H2 = W * SS, H * SS; img = np.zeros((H2, W2, 3), np.float32); img[:] = (196, 205, 214); zb = np.full((H2, W2), 1e9, np.float32)
    L = np.array([0.35, 0.8, 0.45]); L /= np.linalg.norm(L)
    for q in list(Q) + list(extra):
        P = np.array(q['p'], np.float64); pos, nrm = P[:, :3], P[:, 3:]; col = np.array([(q['c'] >> 16) & 255, (q['c'] >> 8) & 255, q['c'] & 255], np.float32)
        lum = 0.40 + 0.60 * np.abs(nrm @ L) * 0 + 0.60 * np.clip(nrm @ L, 0, 1)
        v = (R @ pos.T).T; s = np.stack([W2 / 2 + (v[:, 0] - cx) * scale * SS, H2 / 2 - (v[:, 1] - cy) * scale * SS, -v[:, 2]], 1)
        for tri in ((0, 1, 2), (0, 2, 3)):
            t = s[list(tri)]; lt = lum[list(tri)]
            x0, x1 = int(max(0, t[:, 0].min())), int(min(W2 - 1, t[:, 0].max() + 1)); y0, y1 = int(max(0, t[:, 1].min())), int(min(H2 - 1, t[:, 1].max() + 1))
            if x1 < x0 or y1 < y0: continue
            xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5); (ax, ay, az), (bx, by, bz), (qx, qy, qz) = t
            d = (by - qy) * (ax - qx) + (qx - bx) * (ay - qy)
            if abs(d) < 1e-9: continue
            w0 = ((by - qy) * (xs - qx) + (qx - bx) * (ys - qy)) / d; w1 = ((qy - ay) * (xs - qx) + (ax - qx) * (ys - qy)) / d; w2 = 1 - w0 - w1
            m = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4); z = w0 * az + w1 * bz + w2 * qz - 1e-4; sub = zb[y0:y1 + 1, x0:x1 + 1]; m &= z < sub
            l = w0 * lt[0] + w1 * lt[1] + w2 * lt[2]
            sub[m] = z[m]; img[y0:y1 + 1, x0:x1 + 1][m] = np.minimum(255, col[None, :] * l[m][:, None] + 10)
    im = Image.fromarray(img.astype(np.uint8)).resize((W, H), Image.LANCZOS); d = ImageDraw.Draw(im); d.text((8, 6), title, fill=(20, 20, 20)); return im
def rot(yaw, pitch):
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    return np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
W, H = 1100, 420
side = render(np.array([[0, 0, -1], [0, 1, 0], [1, 0, 0]]), W, H, 62, 0.2, 0.4, 'right side (nose left); the blue box is a 1.8 m Minecraft player', PLAYER)
top = render(np.array([[0, 0, -1], [-1, 0, 0], [0, 1, 0]]), W, H, 62, 0.2, 0, 'from above (nose left): 15.47 m long, 6.82 m span')
q1 = render(rot(math.radians(-145), math.radians(18)), W, H, 58, 0, 0.3, 'three-quarters front', PLAYER)
q2 = render(rot(math.radians(-35), math.radians(15)), W, H, 58, 0, 0.3, 'three-quarters rear (left side)')
ck = render(rot(math.radians(-125), math.radians(16)), W, H, 190, title='cockpit and nose, close', at=(0, 0.6, 7.74 - 3.2))
sheet = Image.new('RGB', (W, H * 5)); [sheet.paste(im, (0, i * H)) for i, im in enumerate((side, top, q1, q2, ck))]; sheet.save(OUT); print('saved', OUT)
