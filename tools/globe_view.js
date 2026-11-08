// far_view.js globe.glsl dataDir out.png [yawDeg] [pitchDeg]: draws GlobeView's mesh with the game's globe.glsl and the game's matrices, 70 deg
// field of view, the background the fog colour (the sky is not drawn here). Saved tool.
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
(async () => {
  const [glsl, dir, out] = process.argv.slice(2); const yaw = +(process.argv[5] || 0), pitch = +(process.argv[6] || -8);
  const src = fs.readFileSync(glsl, 'utf8'), meta = JSON.parse(fs.readFileSync(path.join(dir, 'globe.json'), 'utf8')), bin = fs.readFileSync(path.join(dir, 'globe.bin')).toString('base64');
  const b = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist'] }); const p = await b.newPage();
  const url = await p.evaluate(([src, meta, bin, yaw, pitch]) => {
    const raw = Uint8Array.from(atob(bin), c => c.charCodeAt(0)).buffer; const W = 1280, H = 640, c = document.createElement('canvas'); c.width = W; c.height = H; const gl = c.getContext('webgl2', { preserveDrawingBuffer: true, antialias: true });
    const es = s => s.replace(/#version 150/, '#version 300 es\nprecision highp float;');
    function sh(t, s) { const x = gl.createShader(t); gl.shaderSource(x, s); gl.compileShader(x); if (!gl.getShaderParameter(x, gl.COMPILE_STATUS)) throw gl.getShaderInfoLog(x); return x; }
    const pr = gl.createProgram(); gl.attachShader(pr, sh(gl.VERTEX_SHADER, es('#version 150\n#define VERTEX\n' + src))); gl.attachShader(pr, sh(gl.FRAGMENT_SHADER, es('#version 150\n#define FRAGMENT\n' + src)));
    gl.bindAttribLocation(pr, 0, 'a_pos'); gl.bindAttribLocation(pr, 1, 'a_nrm'); gl.bindAttribLocation(pr, 2, 'a_col'); gl.linkProgram(pr); if (!gl.getProgramParameter(pr, gl.LINK_STATUS)) throw gl.getProgramInfoLog(pr); gl.useProgram(pr);
    const vb = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, vb); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(raw, 0, meta.verts * 9), gl.STATIC_DRAW);
    for (let a = 0; a < 3; a++) { gl.enableVertexAttribArray(a); gl.vertexAttribPointer(a, 3, gl.FLOAT, false, 36, a * 12); }
    const ib = gl.createBuffer(); gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, ib); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, new Uint32Array(raw, meta.verts * 36, meta.idx), gl.STATIC_DRAW);
    const fov = 70 * Math.PI / 180, n = Math.max(100, meta.alt * 0.25), f = meta.alt + 2.2 * meta.R, t = 1 / Math.tan(fov / 2), A = W / H; const P = [t / A, 0, 0, 0, 0, t, 0, 0, 0, 0, -(f + n) / (f - n), -1, 0, 0, -2 * f * n / (f - n), 0];
    const cy = Math.cos(yaw * Math.PI / 180), sy = Math.sin(yaw * Math.PI / 180), cp = Math.cos(pitch * Math.PI / 180), sp = Math.sin(pitch * Math.PI / 180);
    const F = [-sy * cp, sp, cy * cp], Rt = [-cy, 0, -sy], U = [Rt[1] * F[2] - Rt[2] * F[1], Rt[2] * F[0] - Rt[0] * F[2], Rt[0] * F[1] - Rt[1] * F[0]];   // Minecraft yaw: 0 = +z (south)
    const V = [Rt[0], U[0], -F[0], 0, Rt[1], U[1], -F[1], 0, Rt[2], U[2], -F[2], 0, 0, 0, 0, 1];
    const M = new Array(16).fill(0); for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) for (let k = 0; k < 4; k++) M[j * 4 + i] += P[k * 4 + i] * V[j * 4 + k];
    gl.uniformMatrix4fv(gl.getUniformLocation(pr, 'u_Mvp'), false, M); gl.uniformMatrix4fv(gl.getUniformLocation(pr, 'u_Model'), false, meta.model); gl.uniform4f(gl.getUniformLocation(pr, 'u_Sun'), ...meta.sun, 1); gl.uniform4f(gl.getUniformLocation(pr, 'u_Air'), meta.alt, 1200 * 9.81 / meta.g, 1, 0); const fog = [0.0, 0.0, 0.0]; gl.uniform4f(gl.getUniformLocation(pr, 'u_Fog'), ...fog, meta.body >= 1 ? 1e30 : 0.6931 * 60000 / 3.912);   // as FarTerrain.globe (0.16.0: no haze round the moon)
    gl.viewport(0, 0, W, H); gl.clearColor(...fog, 1); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT); gl.enable(gl.DEPTH_TEST); gl.drawElements(gl.TRIANGLES, meta.idx, gl.UNSIGNED_INT, 0);
    const o = document.createElement('canvas'); o.width = W; o.height = H + 30; const x = o.getContext('2d'); x.fillStyle = '#0e121a'; x.fillRect(0, 0, W, H + 30); x.drawImage(c, 0, 30);
    x.fillStyle = '#fff'; x.font = '13px sans-serif'; x.fillText(`Hopeless Infinity globe (GlobeMesh + globe.glsl): ${(meta.alt/1000).toFixed(0)} km up, yaw ${yaw}, pitch ${pitch}; the real star ${meta.sunAlt} deg up at the point below; sky not drawn (black)`, 8, 20);
    return o.toDataURL('image/png'); }, [src, meta, bin, yaw, pitch]);
  fs.writeFileSync(out, Buffer.from(url.split(',')[1], 'base64')); console.log('written ' + out); await b.close();
})();
