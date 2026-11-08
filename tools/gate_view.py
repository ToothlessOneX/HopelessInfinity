#!/usr/bin/env python3
"""gate_view.py <resources> out.png: renders a whole gate as the game builds it - the 15 frame blocks and the controller (world/GateShape's
ring), the field's thin panes (models/block/gate_field.json: 16 x 16 x 4 pixels, translucent, first animation frame) in the 3 x 3 middle -
from the mod's own textures, with a 1.8 m player box beside it for scale. Saved tool."""
import sys, math, numpy as np
from PIL import Image, ImageDraw
R = sys.argv[1]; OUT = sys.argv[2]; T = R + '/assets/hopelessinfinity/textures/block/'
def tex(n, frame0=False):
    a = np.asarray(Image.open(T + n).convert('RGBA')).astype(np.float32); return a[:16] if frame0 else a
FR, CT, FI = tex('gate_frame.png'), tex('gate_controller.png'), tex('gate_field.png', True)
CTRL = (2, 2)   # 0.14.1: the controller in the right side of the ring; the bottom row buried
GRASS = np.tile(np.array([92, 140, 60, 255], np.float32), (16, 16, 1))
quads = []   # (corners world, uv corners, texture, translucent)
def cube(x0, y0, z0, x1, y1, z1, t, tr=False, uvz=(0, 16)):
    c = lambda x, y, z: (x, y, z)
    F = [([c(x0, y0, z0), c(x0, y1, z0), c(x1, y1, z0), c(x1, y0, z0)]), ([c(x1, y0, z1), c(x1, y1, z1), c(x0, y1, z1), c(x0, y0, z1)]),
         ([c(x0, y0, z1), c(x0, y1, z1), c(x0, y1, z0), c(x0, y0, z0)]), ([c(x1, y0, z0), c(x1, y1, z0), c(x1, y1, z1), c(x1, y0, z1)]),
         ([c(x0, y1, z0), c(x0, y1, z1), c(x1, y1, z1), c(x1, y1, z0)]), ([c(x0, y0, z1), c(x0, y0, z0), c(x1, y0, z0), c(x1, y0, z1)])]
    for f in F: quads.append((np.array(f, float), [(0, 16), (0, 0), (16, 0), (16, 16)], t, tr))
for a in range(-2, 3):
    for y in range(0, 5):
        edge = abs(a) == 2 or y == 0 or y == 4
        if edge: cube(a, y, 0, a + 1, y + 1, 1, CT if (a == CTRL[0] and y == CTRL[1]) else FR)
        elif True: cube(a, y, 6 / 16, a + 1, y + 1, 10 / 16, FI, True)
cube(-5, -1, -3, 6, 1, 4, GRASS)   # the ground, one block above the ring's bottom row
cube(3.6, 1, -1.2, 4.2, 2.8, -0.6, np.tile(np.array([58, 110, 165, 255], np.float32), (16, 16, 1)))   # a 0.6 x 1.8 m player
def render(yaw, pitch, W=900, H=700, scale=95, title=''):
    SS = 2; W2, H2 = W * SS, H * SS; img = np.zeros((H2, W2, 3), np.float32); img[:] = (150, 180, 215); img[int(H2 * 0.62):] = (96, 126, 70); zb = np.full((H2, W2), 1e9, np.float32)
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch); Rm = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    L = np.array([0.4, 0.8, 0.45]); L /= np.linalg.norm(L)
    order = sorted(quads, key=lambda q: q[3])   # opaque first, then the field
    trans = [q for q in order if q[3]]; trans.sort(key=lambda q: -(Rm @ q[0].mean(0))[2])
    for corners, uv, t, tr in [q for q in order if not q[3]] + trans:
        cen = corners - np.array([0.5, 2.5, 0.5]); v = (Rm @ cen.T).T; s = np.stack([W2 / 2 + v[:, 0] * scale * SS, H2 / 2 - v[:, 1] * scale * SS, -v[:, 2]], 1)
        n = np.cross(corners[1] - corners[0], corners[3] - corners[0]); n /= np.linalg.norm(n); lum = 0.55 + 0.45 * abs(float(n @ L)) if tr else 0.5 + 0.5 * max(0.0, float(n @ L))
        UV = np.array(uv, float)
        for tri in ((0, 1, 2), (0, 2, 3)):
            q = s[list(tri)]; u3 = UV[list(tri)]
            x0, x1 = int(max(0, q[:, 0].min())), int(min(W2 - 1, q[:, 0].max() + 1)); y0, y1 = int(max(0, q[:, 1].min())), int(min(H2 - 1, q[:, 1].max() + 1))
            if x1 < x0 or y1 < y0: continue
            xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5); (ax, ay, az), (bx, by, bz), (qx, qy, qz) = q
            d = (by - qy) * (ax - qx) + (qx - bx) * (ay - qy)
            if abs(d) < 1e-9: continue
            w0 = ((by - qy) * (xs - qx) + (qx - bx) * (ys - qy)) / d; w1 = ((qy - ay) * (xs - qx) + (ax - qx) * (ys - qy)) / d; w2 = 1 - w0 - w1
            m = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4); z = w0 * az + w1 * bz + w2 * qz; sub = zb[y0:y1 + 1, x0:x1 + 1]; m &= z < sub + 1e-4
            uu = np.clip((w0 * u3[0, 0] + w1 * u3[1, 0] + w2 * u3[2, 0]).astype(int), 0, 15); vv = np.clip((w0 * u3[0, 1] + w1 * u3[1, 1] + w2 * u3[2, 1]).astype(int), 0, 15)
            tx = t[15 - vv, uu]; reg = img[y0:y1 + 1, x0:x1 + 1]
            if tr: a = tx[..., 3:4] / 255.0; reg[m] = (reg * (1 - a) + tx[..., :3] * lum * a)[m]
            else: sub[m] = z[m]; reg[m] = (tx[..., :3] * lum)[m]
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((W, H), Image.LANCZOS); ImageDraw.Draw(im).text((8, 6), title, fill=(10, 10, 10)); return im
a = render(math.radians(-25), math.radians(12), title='gate, open: bottom row buried, controller in the right side; blue box = 1.8 m player')
b = render(math.radians(-80), math.radians(8), title='side: you walk straight in at ground level')
sheet = Image.new('RGB', (900, 1400)); sheet.paste(a, (0, 0)); sheet.paste(b, (0, 700)); sheet.save(OUT); print('saved', OUT)
