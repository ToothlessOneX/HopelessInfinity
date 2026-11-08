// sky_parity.js sky.glsl sky.json: sk_scatter (GPU, sky.glsl) against SkyShade.scatter (Java, the exposure and fog colour) for SkyDump's 64
// directions at sunset. Prints the worst relative difference in radiance and transmittance. Saved tool.
const { chromium } = require('playwright'); const fs = require('fs');
(async () => {
  const body = fs.readFileSync(process.argv[2], 'utf8'), d = JSON.parse(fs.readFileSync(process.argv[3], 'utf8'));
  const b = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist'] }); const p = await b.newPage();
  const r = await p.evaluate(([body, d]) => { const gl = document.createElement('canvas').getContext('webgl2'); gl.getExtension('EXT_color_buffer_float');
    const vs = `#version 300 es\nprecision highp float; precision highp int;\n${body}\nuniform vec3 u_v[64]; out vec3 L; out vec3 T; void main(){ vec3 tr; float g; L = sk_scatter(u_v[gl_VertexID], u_Sun.xyz, tr, g); T = tr; gl_Position = vec4((float(gl_VertexID) + 0.5) / 32.0 - 1.0, 0.0, 0.0, 1.0); gl_PointSize = 1.0; }`;
    const fs1 = `#version 300 es\nprecision highp float; in vec3 L; in vec3 T; layout(location = 0) out vec4 o0; layout(location = 1) out vec4 o1; void main(){ o0 = vec4(L, 1.0); o1 = vec4(T, 1.0); }`;
    function sh(t, s) { const x = gl.createShader(t); gl.shaderSource(x, s); gl.compileShader(x); if (!gl.getShaderParameter(x, gl.COMPILE_STATUS)) throw gl.getShaderInfoLog(x); return x; }
    const pr = gl.createProgram(); gl.attachShader(pr, sh(gl.VERTEX_SHADER, vs)); gl.attachShader(pr, sh(gl.FRAGMENT_SHADER, fs1)); gl.linkProgram(pr); gl.useProgram(pr);
    const U = n => gl.getUniformLocation(pr, n); gl.uniform4f(U('u_Air'), ...d.air); gl.uniform4f(U('u_View'), d.alt, 1, 0, 0); gl.uniform4f(U('u_Sun'), ...d.parity.sun, d.angR);
    const v = new Float32Array(192); d.parity.pts.forEach((q, i) => v.set(q.slice(0, 3), 3 * i)); gl.uniform3fv(U('u_v'), v);
    const mk = () => { const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t); gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA32F, 64, 1, 0, gl.RGBA, gl.FLOAT, null); return t; };
    const t0 = mk(), t1 = mk(), fb = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, fb); gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, t0, 0); gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT1, gl.TEXTURE_2D, t1, 0);
    gl.drawBuffers([gl.COLOR_ATTACHMENT0, gl.COLOR_ATTACHMENT1]); gl.viewport(0, 0, 64, 1); gl.drawArrays(gl.POINTS, 0, 64);
    const a = new Float32Array(256), c = new Float32Array(256); gl.readBuffer(gl.COLOR_ATTACHMENT0); gl.readPixels(0, 0, 64, 1, gl.RGBA, gl.FLOAT, a); gl.readBuffer(gl.COLOR_ATTACHMENT1); gl.readPixels(0, 0, 64, 1, gl.RGBA, gl.FLOAT, c);
    let wl = 0, wt = 0; d.parity.pts.forEach((q, i) => { for (let k = 0; k < 3; k++) { const ref = q[3 + k]; wl = Math.max(wl, Math.abs(a[4 * i + k] - ref) / Math.max(1e-6, Math.abs(ref))); wt = Math.max(wt, Math.abs(c[4 * i + k] - q[6 + k])); } });
    return `sky scatter, 64 directions at sunset: worst radiance difference ${(100 * wl).toFixed(3)} %, worst transmittance difference ${wt.toExponential(2)}`; }, [body, d]);
  console.log(r); await b.close();
})();
