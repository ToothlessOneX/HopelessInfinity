#!/usr/bin/env python3
"""moon_patch.py patch.bin n out.png: a block-scale relief of the home moon's ground (tools/MoonPatch: the generator's own columns), the blocks'
colours (light grey concrete powder, tuff) lit by a low sun from the east, plus a perspective-free oblique view. Saved tool."""
import sys, numpy as np
from PIL import Image, ImageDraw
raw = open(sys.argv[1], 'rb').read(); n = int(sys.argv[2]); OUT = sys.argv[3]
a = np.frombuffer(raw, dtype=np.dtype([('y', '<f4'), ('m', 'u1')])); y = a['y'].reshape(n, n).astype(np.float64); m = a['m'].reshape(n, n)
REG, MARE = 20, 21   # Column.Mat ordinals: REGOLITH, MARE_REGOLITH
col = np.zeros((n, n, 3)); col[m == REG] = (136, 136, 137); col[m == MARE] = (72, 72, 78)   # andesite, smooth basalt
gy, gx = np.gradient(np.floor(y)); nrm = np.dstack([-gx, np.ones_like(y), -gy]); nrm /= np.linalg.norm(nrm, axis=2, keepdims=True)
sun = np.array([0.85, 0.30, 0.43]); sun /= np.linalg.norm(sun); lam = np.clip(nrm @ sun, 0, 1)
img = np.clip(col * (0.03 + 0.97 * lam[..., None] / sun[1] * 0.45), 0, 255).astype(np.uint8)
im = Image.fromarray(img).resize((900, 900), Image.NEAREST); d = ImageDraw.Draw(im)
d.rectangle((0, 0, 900, 22), fill=(0, 0, 0)); d.text((6, 5), f'home moon, landing site, {n} x {n} blocks from above; ground y {y.min():.0f}..{y.max():.0f}; sun 17 deg up from the east', fill=(255, 255, 255))
im.save(OUT); print('saved', OUT)
