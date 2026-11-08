#!/usr/bin/env python3
"""portal_view.py <resources> out.gif [radius_m] [seconds] [fps]: the gate portal (shaders/portal.glsl) rendered by real OpenGL (Mesa's
llvmpipe, EGL, no window) from the SAME shader file the game compiles, with the same uniforms and the same grid as client/PortalRender:
the opening surge, the swirl and the ring waves over time. A flat ring drawn round it for scale (not the game's ring blocks). Saved tool."""
import sys, math, numpy as np, moderngl
from PIL import Image
RES = sys.argv[1]; OUT = sys.argv[2]; R = float(sys.argv[3]) if len(sys.argv) > 3 else 7.5; SECS = float(sys.argv[4]) if len(sys.argv) > 4 else 9.0; FPS = int(sys.argv[5]) if len(sys.argv) > 5 else 10
src = open(RES + '/assets/hopelessinfinity/shaders/portal.glsl').read()
W, H = 640, 400
ctx = moderngl.create_standalone_context(backend='egl', require=330)
prog = ctx.program(vertex_shader="#version 150\n#define VERTEX\n" + src, fragment_shader="#version 150\n#define FRAGMENT\n" + src)
N = 128   # the grid PortalRender uses for a great gate
g = np.linspace(-1, 1, N + 1, dtype='f4'); ab = np.array([[x, y] for y in g for x in g], 'f4')
idx = []; [idx.extend([j * (N + 1) + i, j * (N + 1) + i + 1, (j + 1) * (N + 1) + i, j * (N + 1) + i + 1, (j + 1) * (N + 1) + i + 1, (j + 1) * (N + 1) + i]) for j in range(N) for i in range(N)]
vao = ctx.vertex_array(prog, [(ctx.buffer(ab.tobytes()), '2f', 'a_ab')], ctx.buffer(np.array(idx, 'i4').tobytes()))
ringp = ctx.program(vertex_shader="#version 150\nin vec3 p; uniform mat4 m; out float s; void main(){ s = p.z; gl_Position = m * vec4(p.xy, 0.0, 1.0); }",
                    fragment_shader="#version 150\nin float s; out vec4 c; void main(){ c = vec4(vec3(0.16, 0.18, 0.2) * (0.8 + 0.4 * s), 1.0); }")
rv = []
for k in range(128):
    a0, a1 = 2 * math.pi * k / 128, 2 * math.pi * (k + 1) / 128
    for (ra, rb) in [(R, R + 2.0)]:
        q = [(ra * math.cos(a0), ra * math.sin(a0)), (rb * math.cos(a0), rb * math.sin(a0)), (rb * math.cos(a1), rb * math.sin(a1)), (ra * math.cos(a1), ra * math.sin(a1))]
        sh = 0.5 + 0.5 * math.sin(a0 + 0.6)
        for t in [0, 1, 2, 0, 2, 3]: rv.append((q[t][0], q[t][1], sh))
rvao = ctx.vertex_array(ringp, [(ctx.buffer(np.array(rv, 'f4').tobytes()), '3f', 'p')])
fbo = ctx.framebuffer(color_attachments=[ctx.texture((W, H), 4)], depth_attachment=ctx.depth_renderbuffer((W, H)))
def persp(fov, asp, n, f):
    t = 1 / math.tan(math.radians(fov) / 2); return np.array([[t / asp, 0, 0, 0], [0, t, 0, 0], [0, 0, (f + n) / (n - f), 2 * f * n / (n - f)], [0, 0, -1, 0]], 'f4')
def look(eye, at):
    f = np.array(at, 'f4') - eye; f /= np.linalg.norm(f); s = np.cross(f, [0, 1, 0]); s /= np.linalg.norm(s); u = np.cross(s, f)
    return np.array([[s[0], s[1], s[2], 0], [u[0], u[1], u[2], 0], [-f[0], -f[1], -f[2], 0], [0, 0, 0, 1]], 'f4')   # rotation only: positions are camera-relative (as in game)
eye = np.array([-2.3 * R, 0.45 * R, 2.0 * R], "f4"); centre = np.array([0, 0, 0], 'f4')
mvp = persp(60, W / H, 0.1, 500) @ look(eye, centre)
frames = []
for i in range(int(SECS * FPS)):
    t = i / FPS
    fbo.use(); ctx.clear(0.62, 0.74, 0.86, 1.0); ctx.enable(moderngl.DEPTH_TEST)
    m = mvp @ np.array([[1, 0, 0, -eye[0]], [0, 1, 0, -eye[1]], [0, 0, 1, -eye[2]], [0, 0, 0, 1]], 'f4')
    ringp['m'].write(m.T.copy().tobytes()); rvao.render()
    ctx.enable(moderngl.BLEND); ctx.blend_func = moderngl.SRC_ALPHA, moderngl.ONE_MINUS_SRC_ALPHA
    prog['u_Mvp'].write(mvp.T.copy().tobytes()); prog['u_Centre'].value = tuple(centre - eye)
    prog['u_U'].value = (1, 0, 0); prog['u_V'].value = (0, 1, 0)
    for nm, v in [('u_N', (0, 0, 1)), ('u_Size', (R, 1.0, t, -1.0)), ('u_Sky', (0.62, 0.74, 0.86, 1.0))]:
        if nm in prog: prog[nm].value = v
    vao.render(); ctx.disable(moderngl.BLEND)
    im = Image.frombytes('RGBA', (W, H), fbo.color_attachments[0].read()).transpose(Image.FLIP_TOP_BOTTOM).convert('RGB')
    frames.append(im)
frames[0].save(OUT, save_all=True, append_images=frames[1:], duration=int(1000 / FPS), loop=0)
print('wrote', OUT, len(frames), 'frames;', ctx.info['GL_RENDERER'])
