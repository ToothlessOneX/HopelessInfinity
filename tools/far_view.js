// far_view.js far.glsl dataDir out.png [yawDeg] [pitchDeg]: draws FarView's mesh with the game's far.glsl (WebGL2) from the camera, 70 deg
// field of view, the background the fog colour (the sky is not drawn here). Saved tool.
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
(async () => {
  const [glsl, dir, out] = process.argv.slice(2); const yaw = +(process.argv[5] || 0), pitch = +(process.argv[6] || -8), moon = process.argv[7] === 'moon', deck = process.argv[7] === 'deck';   // 0.18.0 deck: a cloud-deck world (SkyRenderer.deckRgb at a high star, through SkyShade.display)   // 0.15.0 moon: no air (no haze, black sky), a low sun as the game's u_Sun from the moon's sky
  const src = fs.readFileSync(glsl, 'utf8'), meta = JSON.parse(fs.readFileSync(path.join(dir, 'far.json'), 'utf8')), bin = fs.readFileSync(path.join(dir, 'far.bin')).toString('base64');
  const b = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist'] }); const p = await b.newPage();
  const url = await p.evaluate(([src, meta, bin, yaw, pitch, moon, deck]) => {
    const raw = Uint8Array.from(atob(bin), c => c.charCodeAt(0)).buffer; const W = 1280, H = 640, c = document.createElement('canvas'); c.width = W; c.height = H; const gl = c.getContext('webgl2', { preserveDrawingBuffer: true, antialias: true });
    const es = s => s.replace(/#version 150/, '#version 300 es\nprecision highp float;');
    function sh(t, s) { const x = gl.createShader(t); gl.shaderSource(x, s); gl.compileShader(x); if (!gl.getShaderParameter(x, gl.COMPILE_STATUS)) throw gl.getShaderInfoLog(x); return x; }
    const pr = gl.createProgram(); gl.attachShader(pr, sh(gl.VERTEX_SHADER, es('#version 150\n#define VERTEX\n' + src))); gl.attachShader(pr, sh(gl.FRAGMENT_SHADER, es('#version 150\n#define FRAGMENT\n' + src)));
    gl.bindAttribLocation(pr, 0, 'a_pos'); gl.bindAttribLocation(pr, 1, 'a_nrm'); gl.bindAttribLocation(pr, 2, 'a_col'); gl.linkProgram(pr); if (!gl.getProgramParameter(pr, gl.LINK_STATUS)) throw gl.getProgramInfoLog(pr); gl.useProgram(pr);
    const vb = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, vb); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(raw, 0, meta.verts * 9), gl.STATIC_DRAW);
    for (let a = 0; a < 3; a++) { gl.enableVertexAttribArray(a); gl.vertexAttribPointer(a, 3, gl.FLOAT, false, 36, a * 12); }
    const ib = gl.createBuffer(); gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, ib); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, new Uint32Array(raw, meta.verts * 36, meta.idx), gl.STATIC_DRAW);
    const fov = 70 * Math.PI / 180, n = 8, f = 3000000, t = 1 / Math.tan(fov / 2), A = W / H; const P = [t / A, 0, 0, 0, 0, t, 0, 0, 0, 0, -(f + n) / (f - n), -1, 0, 0, -2 * f * n / (f - n), 0];
    const cy = Math.cos(yaw * Math.PI / 180), sy = Math.sin(yaw * Math.PI / 180), cp = Math.cos(pitch * Math.PI / 180), sp = Math.sin(pitch * Math.PI / 180);
    const F = [-sy * cp, sp, cy * cp], Rt = [-cy, 0, -sy], U = [Rt[1] * F[2] - Rt[2] * F[1], Rt[2] * F[0] - Rt[0] * F[2], Rt[0] * F[1] - Rt[1] * F[0]];   // Minecraft yaw: 0 = +z (south)
    const V = [Rt[0], U[0], -F[0], 0, Rt[1], U[1], -F[1], 0, Rt[2], U[2], -F[2], 0, 0, 0, 0, 1];
    const M = new Array(16).fill(0); for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) for (let k = 0; k < 4; k++) M[j * 4 + i] += P[k * 4 + i] * V[j * 4 + k];
    gl.uniformMatrix4fv(gl.getUniformLocation(pr, 'u_Mvp'), false, M); if (moon) gl.uniform4f(gl.getUniformLocation(pr, 'u_Sun'), 0.80, 0.26, 0.54, 1); else gl.uniform4f(gl.getUniformLocation(pr, 'u_Sun'), 0.4, 0.75, 0.3, 1); gl.uniform4f(gl.getUniformLocation(pr, 'u_Air'), Math.max(0, meta.y), 1200 * 9.81 / 11.53, moon ? 1 : 0, 0); const aces = x => Math.min(1, Math.max(0, (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14))); const dk = [1.45, 0.90, 0.46].map(t => Math.pow(aces((t * 0.025 * 0.5 / Math.PI + 2e-5) * 0.18 / (0.025 * 0.5 / Math.PI)), 1 / 2.2)); const fog = moon ? [0, 0, 0] : deck ? dk : [0.62, 0.74, 0.88]; gl.uniform4f(gl.getUniformLocation(pr, 'u_Fog'), ...fog, moon ? 1e12 : 0.6931 * 60000 / 3.912);
    gl.viewport(0, 0, W, H); gl.clearColor(...fog, 1); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT); gl.enable(gl.DEPTH_TEST); gl.drawElements(gl.TRIANGLES, meta.idx, gl.UNSIGNED_INT, 0);
    const o = document.createElement('canvas'); o.width = W; o.height = H + 30; const x = o.getContext('2d'); x.fillStyle = '#0e121a'; x.fillRect(0, 0, W, H + 30); x.drawImage(c, 0, 30);
    x.fillStyle = '#fff'; x.font = '13px sans-serif'; x.fillText(`Hopeless Infinity 0.9.0 far terrain (FarMesh + far.glsl): camera ${meta.x} ${meta.z} at y ${meta.y}, yaw ${yaw}, pitch ${pitch}; mesh to ${meta.reachKm} km; sky not drawn (fog colour behind)`, 8, 20);
    return o.toDataURL('image/png'); }, [src, meta, bin, yaw, pitch, moon, deck]);
  fs.writeFileSync(out, Buffer.from(url.split(',')[1], 'base64')); console.log('written ' + out); await b.close();
})();
