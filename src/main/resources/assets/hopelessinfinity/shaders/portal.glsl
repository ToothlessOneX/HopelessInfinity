// Hopeless Infinity 0.27.2: THE GATE PORTAL (owner, 2026-11-04: "a real vortex style portal ... like we did with the water"; "that vortex look
// isn't owned by the franchise"). A liquid surface across the gate's aperture, all on the GPU: the CPU sends one grid and a few uniforms
// (client/PortalRender), this file moves the surface and lights it. Split at #ifdef VERTEX / FRAGMENT (FarTerrain's way). Physics:
//  - THE OPENING SURGE: an impulse throws a jet out of the surface. Its tip rises and falls ballistically, h(t) = v0 t - g t^2 / 2 (STATED:
//    projectile motion; the surface's restoring acceleration taken as g, CHOSEN), v0 = sqrt(2 g Hmax), Hmax = SURGE x the aperture radius
//    (CHOSEN 1.0); the jet's radius follows a fixed volume (STATED: mass conservation, pi w^2 h constant), so it thins as it stretches.
//  - THE SWIRL: a Lamb-Oseen vortex (STATED: v(r, t) = Gamma / (2 pi r) (1 - exp(-r^2 / (4 nu t)))): the liquid turns fastest in the core,
//    the core spreading as sqrt(4 nu t); the surface pattern is carried round by it.
//  - THE RIPPLES: ring waves from a point disturbance on deep water, gravity waves (STATED: the Cauchy-Poisson problem, Lamb, Hydrodynamics
//    s. 255): the crest phase is g t^2 / (4 r) (long waves run out first, short ones follow), the height falls as t^2 / r^3; started by the
//    jet's collapse at the centre and, smaller, by points of the field's own unrest (CHOSEN: 3 sources, every 4.7-7.3 s).
//  - THE LOOK: reflection by Schlick's Fresnel approximation (STATED: F0 = ((n - 1) / (n + 1))^2 = 0.020 for water, n = 1.333), the sky's
//    colour reflected; the field's own glow seen through it, bright where the curved surface focuses it (STATED: the thin-lens caustic
//    intensity 1 / (1 - d lap h) of a refracting height field) - the glow colour CHOSEN (the gate field's blue).
// ---- shared by both stages
const float G = 9.81, SURGE = 1.0, LMIN = 0.3;   // LMIN: the shortest wave drawn (m; CHOSEN: the grid's and the shading's resolution)
#ifdef VERTEX
in vec2 a_ab;                 // the grid, -1..1 across the aperture's square
uniform mat4 u_Mvp;
uniform vec3 u_Centre, u_U, u_V, u_N;   // the field's centre (camera-relative, m) and its axes (U across, V up, N out of the front)
uniform vec4 u_Size;          // x the aperture's radius (half side for a square) m, y 1 round / 0 square, z seconds since the gate opened, w seconds since it closed (-1 open)
out vec2 v_p; out vec3 v_w;
float H(vec2 p, float t, float R);
void main() { float R = u_Size.x; vec2 p = a_ab * R; float h = H(p, u_Size.z, R); v_p = p; vec3 w = u_Centre + p.x * u_U + p.y * u_V + h * u_N; v_w = w; gl_Position = u_Mvp * vec4(w, 1.0); }
#endif
#ifdef FRAGMENT
in vec2 v_p; in vec3 v_w;
uniform vec3 u_U, u_V, u_N;
uniform vec4 u_Size;
uniform vec4 u_Sky;           // rgb the sky's colour (the fog colour), w daylight 0..1
out vec4 fragColor;
float H(vec2 p, float t, float R);
float swirl(float r, float t, float R);
float vnoise(vec2 q) { vec2 i = floor(q), f = fract(q); f = f * f * (3.0 - 2.0 * f); float a = fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453), b = fract(sin(dot(i + vec2(1, 0), vec2(127.1, 311.7))) * 43758.5453),
    c = fract(sin(dot(i + vec2(0, 1), vec2(127.1, 311.7))) * 43758.5453), d = fract(sin(dot(i + vec2(1, 1), vec2(127.1, 311.7))) * 43758.5453); return mix(mix(a, b, f.x), mix(c, d, f.x), f.y); }
void main() {
    float R = u_Size.x, t = u_Size.z, r = length(v_p);
    float close = u_Size.w < 0.0 ? 1.0 : clamp(1.0 - u_Size.w / 0.6, 0.0, 1.0);   // on closing the surface draws in to the middle in 0.6 s (CHOSEN)
    float edge = u_Size.y > 0.5 ? r / R : max(abs(v_p.x), abs(v_p.y)) / R; if (edge > close) discard;
    // the surface's slope and curvature from the height field itself (central differences, 4 cm)
    float e = 0.04; float h0 = H(v_p, t, R), hx1 = H(v_p + vec2(e, 0), t, R), hx0 = H(v_p - vec2(e, 0), t, R), hy1 = H(v_p + vec2(0, e), t, R), hy0 = H(v_p - vec2(0, e), t, R);
    vec2 g = vec2(hx1 - hx0, hy1 - hy0) / (2.0 * e); float lap = (hx1 + hx0 + hy1 + hy0 - 4.0 * h0) / (e * e);
    vec3 n = normalize(u_N - g.x * u_U - g.y * u_V); vec3 v = normalize(-v_w); if (dot(n, v) < 0.0) n = -n;   // seen from behind: the back of the sheet
    float F0 = 0.0204, F = F0 + (1.0 - F0) * pow(1.0 - clamp(dot(n, v), 0.0, 1.0), 5.0);
    // the glow seen through the liquid: focused by the curved surface (caustics), the pattern carried round by the swirl
    float caus = clamp(1.0 / max(0.25, abs(1.0 - 0.35 * lap)), 0.0, 3.0);
    float th = atan(v_p.y, v_p.x) + swirl(r, t, R); vec2 q = vec2(cos(th), sin(th)) * r;
    float tex = 0.6 * vnoise(q * 1.7 + 0.15 * t) + 0.4 * vnoise(q * 4.3 - 0.11 * t);
    vec3 deep = vec3(0.04, 0.20, 0.62), bright = vec3(0.45, 0.80, 1.00);
    vec3 glow = mix(deep, bright, clamp(0.25 * caus + 0.45 * tex - 0.15, 0.0, 1.0)) * (0.85 + 0.6 * smoothstep(0.82, 1.0, edge / max(close, 1e-3)));   // a brighter meniscus at the rim (CHOSEN)
    glow *= 1.0 + 0.9 * clamp(h0 / (SURGE * R), 0.0, 1.0);   // the stretched jet is a thinner sheet: more of the glow comes through (CHOSEN)
    vec3 L = normalize(0.35 * u_U + 0.8 * u_V + 0.5 * u_N); float key = 0.62 + 0.38 * max(dot(n, L), 0.0);   // a key light from above and in front (CHOSEN) so the shape reads
    vec3 sky = u_Sky.rgb * (0.35 + 0.65 * u_Sky.w) + 0.08;
    vec3 c = mix(glow * key, sky, F);
    fragColor = vec4(c, mix(0.86, 0.98, F));
}
#endif
#ifdef VERTEX
float swirl(float r, float t, float R) { return 0.0; }
#endif
// ---- shared by both stages (constants at the top)
// Lamb-Oseen: the angle a fluid ring at r has turned by time t (the core radius sqrt(4 nu t)); Gamma and nu scaled to the aperture (CHOSEN:
// the rim turns at about 0.25 m/s, the core reaches the rim in about 12 s)
#ifdef FRAGMENT
float swirl(float r, float t, float R) { float Gam = 2.0 * 3.14159 * R * 0.25, nu = R * R / 48.0, rr = max(r, 0.05 * R); return Gam * t / (2.0 * 3.14159 * rr * rr) * (1.0 - exp(-rr * rr / (4.0 * nu * max(t, 0.05)))); }
#endif
float ring(vec2 p, vec2 at, float tau, float A, float R) {   // Cauchy-Poisson ring waves from a point at `at`, tau s after the disturbance
    if (tau <= 0.0) return 0.0; float r = max(length(p - at), 0.12 * R), ph = G * tau * tau / (4.0 * r), k = G * tau * tau / (4.0 * r * r), kmax = 6.28318 / LMIN;   // k: the local wavenumber d(phase)/dr
    float amp = A * min(1.0, (G * tau * tau / (4.0 * R)) * pow(0.12 * R / r, 3.0) * 6.0) * exp(-tau / 4.0);   // t^2 / r^3 (Lamb), damped (CHOSEN 4 s)
    return amp * sin(ph) * smoothstep(0.0, 0.6, ph / 6.28318) * exp(-(k / kmax) * (k / kmax)); }   // waves shorter than LMIN fade (the surface cannot draw them)
float H(vec2 p, float t, float R) {
    float r = length(p), v0 = sqrt(2.0 * G * SURGE * R), Ts = 2.0 * v0 / G, h = 0.0;
    // the surge: a jet of fixed volume, tip ballistic
    if (t < Ts) { float hc = max(0.0, v0 * t - 0.5 * G * t * t), w0 = 0.42 * R, w = w0 * clamp(sqrt(0.30 * SURGE * R / max(hc, 0.05)), 0.30, 1.0); h += hc * exp(-(r * r) / (w * w)); }
    // the collapse at the centre makes the ring waves; the field's unrest keeps the surface alive
    h += ring(p, vec2(0.0), t - Ts, 0.10 * R, R);
    for (int i = 0; i < 3; i++) { float per = 4.7 + 1.3 * float(i), ph = mod(t - Ts - 0.9 * float(i), per); vec2 at = 0.55 * R * vec2(cos(2.1 * float(i) + floor((t - Ts) / per)), sin(2.1 * float(i) + 1.7 * floor((t - Ts) / per)));
        if (t > Ts) h += ring(p, at, ph, 0.025 * R, R); }
    return h; }
