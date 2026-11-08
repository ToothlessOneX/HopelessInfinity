#!/usr/bin/env python3
"""screen_view.py <resources> out.gif [seconds] [fps] [start_s] [billboard]: (0.28.4 "billboard": a 15 x 4 m digital billboard on its pole, mode 1, 8 s a message) the HQ's video screen (shaders/adscreen.glsl) rendered by real OpenGL
(Mesa, EGL, no window) from the SAME shader file and the SAME picture the game uses (planet/AdArt, written by tools/AdArtDump), on a
32 x 18 m quad seen from the plaza at an angle, with a close-up inset of the LED dots. Saved tool."""
import sys, os, math, subprocess, numpy as np, moderngl
from PIL import Image
RES = sys.argv[1]; OUT = sys.argv[2]; SECS = float(sys.argv[3]) if len(sys.argv) > 3 else 28; FPS = int(sys.argv[4]) if len(sys.argv) > 4 else 4; T0 = float(sys.argv[5]) if len(sys.argv) > 5 else 0
BB = len(sys.argv) > 6 and sys.argv[6] == 'billboard'; PW, PH, PIX = (15.0, 4.0, (960.0, 256.0)) if BB else (32.0, 18.0, (512.0, 288.0)); NS = 3.0 if BB else 4.0
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__))); CP = "build/classes:" + open('/home/claude/mctool/cp.txt').read().strip()
pic = '/tmp/adart_view.png'
subprocess.run(['javac', '-proc:none', '-nowarn', '-cp', CP, '-d', '/tmp/adart_tool', 'tools/AdArtDump.java'], cwd=ROOT, check=True, capture_output=True)
subprocess.run(['java', '-Djava.awt.headless=true', '-cp', '/tmp/adart_tool:' + CP, 'AdArtDump', pic] + (['billboard'] if BB else []), cwd=ROOT, check=True, capture_output=True)
src = open(RES + '/assets/hopelessinfinity/shaders/adscreen.glsl').read(); W, H = 640, 400
ctx = moderngl.create_standalone_context(backend='egl', require=330)
prog = ctx.program(vertex_shader="#version 150\n#define VERTEX\n" + src, fragment_shader="#version 150\n#define FRAGMENT\n" + src)
im = Image.open(pic).convert('RGBA'); tex = ctx.texture(im.size, 4, im.tobytes()); tex.filter = (moderngl.NEAREST, moderngl.NEAREST)   # top row first, as ScreenRender uploads
vao = ctx.vertex_array(prog, [(ctx.buffer(np.array([0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1], 'f4').tobytes()), '2f', 'a_uv')])
fbo = ctx.framebuffer(color_attachments=[ctx.texture((W, H), 4)], depth_attachment=ctx.depth_renderbuffer((W, H)))
def persp(fov, asp, n, f): t = 1 / math.tan(math.radians(fov) / 2); return np.array([[t / asp, 0, 0, 0], [0, t, 0, 0], [0, 0, (f + n) / (n - f), 2 * f * n / (n - f)], [0, 0, -1, 0]], 'f4')
def look(eye, at):
    f = np.array(at, 'f4') - eye; f /= np.linalg.norm(f); s = np.cross(f, [0, 1, 0]); s /= np.linalg.norm(s); u = np.cross(s, f)
    return np.array([[s[0], s[1], s[2], 0], [u[0], u[1], u[2], 0], [-f[0], -f[1], -f[2], 0], [0, 0, 0, 1]], 'f4')
def draw(eye, at, t, fov):
    mvp = persp(fov, W / H, 0.1, 2000) @ look(np.array(eye, 'f4'), at); fbo.use(); ctx.clear(0.5, 0.62, 0.75, 1.0); ctx.enable(moderngl.DEPTH_TEST)
    prog['u_Mvp'].write(mvp.T.copy().tobytes()); org = np.array([16.0, 0, 0], 'f4') - eye   # the screen: x from 16 to -16 (seen from +z: left at +x... here the viewer is at +z looking -z, so left is -x)
    prog['u_Origin'].value = tuple(np.array([-PW / 2, 0, 0], 'f4') - np.array(eye, 'f4')); prog['u_U'].value = (PW, 0, 0); prog['u_V'].value = (0, PH, 0)
    if 'u_Mode' in prog: prog['u_Mode'].value = 1.0 if BB else 0.0
    tex.use(0); prog['u_Pic'].value = 0; prog['u_Info'].value = (t, NS, PIX[0], PIX[1]); vao.render()
    return Image.frombytes('RGBA', (W, H), fbo.color_attachments[0].read()).transpose(Image.FLIP_TOP_BOTTOM).convert('RGB')
frames = []
for i in range(max(1, int(SECS * FPS))):
    t = T0 + i / FPS; big = draw([-14.0, 4.0, 34.0], [0, 9, 0], t, 55) if not BB else draw([-6.0, 1.5, 16.0], [0, 2, 0], t, 50)
    near = (draw([-2.0, 9.5, 1.2], [-2.0, 9.5, 0], t, 40) if not BB else draw([-2.0, 2.0, 1.0], [-2.0, 2.0, 0], t, 40)).resize((220, 138))   # 1.2 m from the wall: the LED dots
    big.paste(near, (W - 228, H - 146)); frames.append(big)
frames[0].save(OUT, save_all=True, append_images=frames[1:], duration=int(1000 / FPS), loop=0)
print('wrote', OUT, len(frames), 'frames;', ctx.info['GL_RENDERER'])
