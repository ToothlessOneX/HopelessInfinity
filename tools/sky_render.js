// sky_render.js sky.glsl dumpDir outDir: renders the game's own sky shader (sky.glsl, unchanged) in WebGL2 with SkyDump's uniforms and star
// field: per moment a 90 deg view toward the sun's side of the sky and an all-sky fisheye (zenith in the middle, south down). The only part
// that differs from the game is how a pixel's view ray is made (here from yaw/pitch/fov or the fisheye). Saved tool.
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
(async () => {
  const [glsl, dir, out, only] = process.argv.slice(2); fs.mkdirSync(out, { recursive: true });
  const body = fs.readFileSync(glsl, 'utf8'), d = JSON.parse(fs.readFileSync(path.join(dir, 'sky.json'), 'utf8')), bin = fs.readFileSync(path.join(dir, 'stars.bin'));
  const b = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist'] }); const p = await b.newPage();
  await p.route('http://sky.local/stars.bin', r => r.fulfill({ body: bin, contentType: 'application/octet-stream' }));
  const homeBin = fs.existsSync(path.join(dir, 'home.bin')) ? fs.readFileSync(path.join(dir, 'home.bin')) : Buffer.alloc(0); await p.route('http://sky.local/home.bin', r => r.fulfill({ body: homeBin, contentType: 'application/octet-stream' })); /* 0.15.0 the home planet's map (MoonSkyDump) */ await p.route('http://sky.local/', r => r.fulfill({ body: '<html><body></body></html>', contentType: 'text/html' }));
  await p.goto('http://sky.local/');
  const res = await p.evaluate(async ([body, d, only]) => {
    const buf = new Float32Array(await (await fetch('http://sky.local/stars.bin')).arrayBuffer());
    const W = 960, H = 540, c = document.createElement('canvas'); c.width = W; c.height = H; const gl = c.getContext('webgl2', { preserveDrawingBuffer: true }); gl.getExtension('EXT_color_buffer_float'); gl.getExtension('OES_texture_float_linear');
    const vs = `#version 300 es\nout vec2 sk_ndc; void main(){ vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2)) * 2.0 - 1.0; sk_ndc = p; gl_Position = vec4(p, 0.0, 1.0); }`;
    const fsrc = `#version 300 es\nprecision highp float; precision highp int;\nin vec2 sk_ndc; out vec4 fragColor;\n${body}\nuniform vec4 u_Cam; uniform vec3 u_R, u_U, u_F;\nvoid main(){ vec3 v;
      if (u_Cam.x > 0.5) { vec2 q = sk_ndc * vec2(${W / H}, 1.0); float r = length(q); if (r > 1.0) { fragColor = vec4(0.05, 0.05, 0.06, 1.0); return; } float el = (1.0 - r) * 1.5707963, az = atan(q.x, -q.y); v = vec3(sin(az) * cos(el), sin(el), cos(az) * cos(el)); }
      else { v = normalize(u_F + sk_ndc.x * u_Cam.y * ${W / H} * u_R + sk_ndc.y * u_Cam.y * u_U); }
      fragColor = vec4(sk_display(sk_radiance(v)), 1.0); }`;
    function sh(t, s) { const x = gl.createShader(t); gl.shaderSource(x, s); gl.compileShader(x); if (!gl.getShaderParameter(x, gl.COMPILE_STATUS)) throw gl.getShaderInfoLog(x); return x; }
    const pr = gl.createProgram(); gl.attachShader(pr, sh(gl.VERTEX_SHADER, vs)); gl.attachShader(pr, sh(gl.FRAGMENT_SHADER, fsrc)); gl.linkProgram(pr); if (!gl.getProgramParameter(pr, gl.LINK_STATUS)) throw gl.getProgramInfoLog(pr); gl.useProgram(pr);
    const tex = gl.createTexture(); gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, tex); gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGB32F, 2048, 1024, 0, gl.RGB, gl.FLOAT, buf);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.REPEAT); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    const hb = new Float32Array(await (await fetch('http://sky.local/home.bin')).arrayBuffer()); const homeIn = hb.length === 512 * 256 * 3; const ht = gl.createTexture(); gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, ht);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGB32F, homeIn ? 512 : 1, homeIn ? 256 : 1, 0, gl.RGB, gl.FLOAT, homeIn ? hb : new Float32Array(3)); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.REPEAT); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE); gl.activeTexture(gl.TEXTURE0);
    const U = n => gl.getUniformLocation(pr, n); gl.uniform1i(U('u_Stars'), 0); gl.uniform1i(U('u_Home'), 1); gl.uniform4f(U('u_Air'), ...d.air); gl.uniform4f(U('u_SunRgb'), ...d.sunRgb, d.limb);
    const shots = [];
    for (const m of d.moments) {
      gl.uniform4f(U('u_Sun'), ...m.sun, m.angR !== undefined ? m.angR : d.angR); gl.uniform4f(U('u_Ecl'), m.ecl !== undefined ? m.ecl : 1, 0, 0, 0); if (m.moonL) { gl.uniform4fv(U('u_MoonL'), m.moonL); gl.uniform4fv(U('u_MoonS'), m.moonS); } gl.uniform4f(U('u_View'), d.alt, m.exp, 0, 1e-6); gl.uniform4f(U('u_Pole'), ...m.pole); gl.uniform4f(U('u_Noon'), ...m.noon, 0); gl.uniform4f(U('u_West'), ...m.west, 0);
      gl.uniform4fv(U('u_Moon'), m.moon); gl.uniform4fv(U('u_Planet'), m.planet);
      const hx = m.home || [1, 0, 0, 0, 1, 0, 0, 0, 1]; gl.uniform4f(U('u_HomeX'), hx[0], hx[1], hx[2], homeIn && m.home ? 1 : 0); gl.uniform4f(U('u_HomeY'), hx[3], hx[4], hx[5], 0); gl.uniform4f(U('u_HomeZ'), hx[6], hx[7], hx[8], 0);
      for (const mode of (only ? [only] : ['view', 'allsky', 'zoom'])) {
        // view: toward the sun (or the moon at night), at its height (8 to 69 deg up); fov 90
        const tgt = (mode === 'zoom' && m.moon[1] > 0) ? m.moon : m.sun[1] > -0.05 ? m.sun : (m.moon[3] > 0 && m.moon[1] > 0 ? m.moon : m.sun); let fx = tgt[0], fz = tgt[2]; const l = Math.hypot(fx, fz) || 1; fx /= l; fz /= l; const pit = mode === 'zoom' ? Math.asin(Math.max(-1, Math.min(1, tgt[1]))) : Math.max(0.14, Math.min(1.2, Math.asin(Math.max(-1, Math.min(1, tgt[1]))) ));
        const F = [fx * Math.cos(pit), Math.sin(pit), fz * Math.cos(pit)], R = [-fz, 0, fx], Uv = [R[1] * F[2] - R[2] * F[1], R[2] * F[0] - R[0] * F[2], R[0] * F[1] - R[1] * F[0]];
        gl.uniform4f(U('u_Cam'), mode === 'allsky' ? 1 : 0, mode === 'zoom' ? Math.tan(Math.PI / 180 * 0.75) : Math.tan(Math.PI / 4), 0, 0); gl.uniform3f(U('u_R'), ...R); gl.uniform3f(U('u_U'), ...Uv); gl.uniform3f(U('u_F'), ...F);
        gl.viewport(0, 0, W, H); gl.drawArrays(gl.TRIANGLES, 0, 3); shots.push([m.name + '_' + mode, c.toDataURL('image/png')]); } }
    return shots; }, [body, d, only]);
  for (const [n, u] of res) fs.writeFileSync(path.join(out, n + '.png'), Buffer.from(u.split(',')[1], 'base64'));
  console.log('rendered ' + res.length); await b.close();
})();
