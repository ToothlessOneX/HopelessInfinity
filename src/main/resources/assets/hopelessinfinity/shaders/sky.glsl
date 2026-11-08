// Hopeless Infinity 0.6.0: the planet's own sky (sky/SkyModel.java has the sources; sky/SkyShade.java is this file's Java twin for the
// exposure and the fog colour, checked number for number by tools/sky_parity.js). All brightness in units of the host star's irradiance.
uniform mat4 u_InvViewProj;   // clip -> view direction (camera rotation only)
uniform vec4 u_Sun;           // xyz direction to the star (world: y up), w angular radius (rad)
uniform vec4 u_SunRgb;        // rgb the star's colour (luminance 1), w limb darkening u
uniform vec4 u_Air;           // x planet radius (m), y Rayleigh scale height, z Mie scale height, w top of the air (m above ground)
uniform vec4 u_View;          // x camera height above sea level (m), y exposure, z rain 0..1, w star texture scale (1e-6)
uniform vec4 u_Pole;          // xyz the celestial pole (world), w local sidereal time (rad)
uniform vec4 u_Noon;          // xyz the celestial equator on the meridian
uniform vec4 u_West;          // xyz west
uniform sampler2D u_Stars;    // stars + milky way, right ascension x declination, radiance x 1e6
uniform vec4 u_Moon[8];       // 0.8.0 the bodies drawn as disks (moons, near planets): xyz direction, w angular radius (0: none)
uniform vec4 u_MoonL[8];      // x light reaching it (eclipses), y geometric albedo, z kind (1 rocky, 2 giant, 3 moon), w seed
uniform vec4 u_MoonS[8];      // xyz the direction from the body to the star (its lit side)
uniform vec4 u_Ecl;           // x the share of the star's disk not hidden (solar eclipse)
uniform vec4 u_Planet[12];    // xyz direction, w flux (E)
uniform sampler2D u_Home;     // 0.15.0 the home planet's albedo map (sky/HomeMap), for its disk seen from a moon
uniform vec4 u_HomeX; uniform vec4 u_HomeY; uniform vec4 u_HomeZ;   // its body axes in the local frame (w of X: 1 when the map is in)
uniform vec4 u_Deck;          // 0.18.0 a cloud deck overhead (thick CO2 air, Venus): w 1 on; rgb its radiance (the whole sky one dim orange glow: no star disk, no stars)
const float PI = 3.14159265, MIE_G = 0.8, OZ_C = 25000.0, OZ_H = 15000.0, K_MS = 0.3;
const vec3 BETA_R = vec3(5.802e-6, 13.558e-6, 33.1e-6), OZONE = vec3(0.650e-6, 1.881e-6, 0.085e-6); const float MIE_S = 3.996e-6, MIE_E = 4.40e-6;
// distance along a ray (origin o, unit d) to a sphere of radius r round the planet's centre; x near, y far (y < 0: none)
vec2 sk_sphere(vec3 o, vec3 d, float r) { float b = dot(o, d), c = dot(o, o) - r * r, h = b * b - c; if (h < 0.0) return vec2(-1.0); h = sqrt(h); return vec2(-b - h, -b + h); }
vec3 sk_density(float h) { return vec3(exp(-h / u_Air.y), exp(-h / u_Air.z), max(0.0, 1.0 - abs(h - OZ_C * u_Air.y / 8000.0) / (OZ_H * u_Air.y / 8000.0))); }
vec3 sk_extinction(vec3 od) { return BETA_R * od.x + vec3(MIE_E) * od.y + OZONE * od.z; }
// single scattering along view direction v with the star at s; out: radiance (E/sr) and the view ray's transmittance
vec3 sk_scatter(vec3 v, vec3 s, out vec3 trans, out float ground) {
    float R = u_Air.x, top = R + u_Air.w; vec3 o = vec3(0.0, R + max(1.0, u_View.x), 0.0); trans = vec3(1.0); ground = 0.0; if (u_Air.w <= 0.0) return vec3(0.0);   // 0.15.0 no air
    vec2 ta = sk_sphere(o, v, top), tg = sk_sphere(o, v, R); ground = 0.0; trans = vec3(1.0); if (ta.y <= 0.0) return vec3(0.0);
    float t0 = max(0.0, ta.x), t1 = ta.y; if (tg.x > 0.0) { t1 = tg.x; ground = 1.0; }
    const int N = 16, M = 6; float ds = (t1 - t0) / float(N), mu = dot(v, s);
    float pr = 3.0 / (16.0 * PI) * (1.0 + mu * mu), pm = (1.0 - MIE_G * MIE_G) / (4.0 * PI * pow(1.0 + MIE_G * MIE_G - 2.0 * MIE_G * mu, 1.5));
    vec3 od = vec3(0.0), L = vec3(0.0);
    for (int i = 0; i < N; i++) { vec3 p = o + v * (t0 + (float(i) + 0.5) * ds); float h = length(p) - R; vec3 dn = sk_density(h) * ds; od += dn * 0.5;
        float b = dot(p, s), dmin = b > 0.0 ? R + 1e9 : sqrt(max(0.0, dot(p, p) - b * b)), lit = smoothstep(R - 2.0 * u_Air.y, R, dmin);   // the planet's shadow, soft over 2 H_R (the light scattered by the lit air above stands in for multiple scattering)
        if (lit > 0.0) { float tl = sk_sphere(p, s, top).y, dl = tl / float(M); vec3 ol = vec3(0.0);
            for (int j = 0; j < M; j++) { vec3 q = p + s * ((float(j) + 0.5) * dl); ol += sk_density(length(q) - R) * dl; }
            vec3 T = exp(-sk_extinction(od + ol)); L += lit * T * (BETA_R * dn.x * pr + vec3(MIE_S) * dn.y * pm); }
        // second and higher orders, isotropic: the light of the sunlit sky reaching this air, which also fills the planet's shadow at twilight (a broad
        // penumbra over 6 H_R below to 2 H_R above the shadow line); K_MS of the single-scattered light's strength (CHOSEN)
        float lit2 = smoothstep(R - 6.0 * u_Air.y, R + 2.0 * u_Air.y, dmin); L += lit2 * K_MS * exp(-sk_extinction(od)) * (BETA_R * dn.x + vec3(MIE_S) * dn.y) / (4.0 * PI);
        od += dn * 0.5; }
    trans = exp(-sk_extinction(od)); return L;
}
float sk_hash(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float sk_noise(vec3 x) { vec3 i = floor(x), f = fract(x); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(sk_hash(i), sk_hash(i + vec3(1, 0, 0)), f.x), mix(sk_hash(i + vec3(0, 1, 0)), sk_hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(sk_hash(i + vec3(0, 0, 1)), sk_hash(i + vec3(1, 0, 1)), f.x), mix(sk_hash(i + vec3(0, 1, 1)), sk_hash(i + vec3(1, 1, 1)), f.x), f.y), f.z); }
vec3 sk_aces(vec3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }   // Narkowicz 2015 ACES fit
// the colour of the sky in direction v (world, unit), linear and exposed, before the display curve
vec3 sk_radiance(vec3 v) {
    if (u_Deck.w > 0.5) return u_Deck.rgb * (v.y > -0.05 ? 1.0 : 0.6);   // 0.18.0 under the deck: a nearly even glow (Venera 9-14 surface light: diffuse, orange)
    vec3 s = u_Sun.xyz, T; float gnd; vec3 L = sk_scatter(v, s, T, gnd) * u_Ecl.x;
    if (gnd > 0.5) { vec3 h = normalize(vec3(v.x, 0.012 - 0.3 * v.y, v.z)); vec3 T2; float g2; return sk_scatter(h, s, T2, g2) * u_Ecl.x; }   // 0.8.0: under the horizon (past the drawn terrain) the sky just above it, mirrored and flattened: no dark band, no seam at the horizon
    vec3 bg = vec3(0.0); float cov = 0.0;
    // 0.8.0: the moons and near planets as lit spheres at their real size, phase from their own sun direction (in front of the star and the stars;
    // the nearest disk on the ray wins, the list is sorted nearest first)
    for (int i = 0; i < 8; i++) { vec4 m = u_Moon[i]; if (m.w <= 0.0) continue; float d = dot(v, m.xyz); if (d < cos(m.w)) continue;
        vec3 x = (v - m.xyz * d) / sin(m.w); float r2 = dot(x, x); if (r2 >= 1.0) continue; vec3 n = x - m.xyz * sqrt(1.0 - r2);
        vec4 ml = u_MoonL[i]; float k = ml.z, sd = ml.w; float alb; vec3 col = vec3(1.0);
        if (k > 3.5) {   // 0.15.0 the home planet from its moon: its real surface (albedo map), turned with its spin, and its air's blue light (Rayleigh, CHOSEN 0.04 of the sun)
            vec3 b = vec3(dot(n, u_HomeX.xyz), dot(n, u_HomeY.xyz), dot(n, u_HomeZ.xyz)); float la = asin(clamp(b.z, -1.0, 1.0)), lo = atan(-b.x, b.y);
            col = u_HomeX.w > 0.5 ? texture(u_Home, vec2(lo / (2.0 * PI) + 0.5, 0.5 - la / PI)).rgb + vec3(0.012, 0.022, 0.045) : vec3(ml.y); alb = 1.0;
            float full = 0.367 * ml.x / PI, keep = min(1.0, 0.5 / (u_View.y * full)); bg += col * alb * ml.x * max(0.0, dot(n, u_MoonS[i].xyz)) / PI * keep; cov = 1.0; break; }
        if (k > 1.5 && k < 2.5) { float lat = dot(n, u_Pole.xyz); alb = ml.y * 1.5 * (0.8 + 0.25 * sin(lat * 18.0 + sd) + 0.1 * sk_noise(n * 9.0 + sd)); }   // a giant's cloud bands along its rotation (GAME look)
        else alb = ml.y * 1.5 * (0.75 + 0.5 * sk_noise(n * 6.0 + sd) * (0.7 + 0.3 * sk_noise(n * 23.0 + sd)));   // Lambert albedo 1.5 x geometric, maria and craters
        // the eye looking at a bright disk in a dark sky: its lit side kept at about middle grey (CHOSEN; the 'looney 11' rule of moon photography: the
        // moon wants the daylight exposure, not the night sky's), the night sky round it keeps the dark-adapted exposure
        float full = ml.y * 1.5 * ml.x / PI, keep = full > 0.0 ? min(1.0, 0.5 / (u_View.y * full)) : 1.0;
        bg += vec3(alb * ml.x * max(0.0, dot(n, u_MoonS[i].xyz)) / PI) * keep; cov = 1.0; break; }
    if (cov < 0.5) {
        float ds = dot(v, s); if (ds > cos(u_Sun.w)) { float r = sqrt(max(0.0, 1.0 - ds * ds)) / sin(u_Sun.w), mu = sqrt(max(0.0, 1.0 - r * r));
            bg += u_SunRgb.rgb * (1.0 - u_SunRgb.w * (1.0 - mu)) / ((1.0 - u_SunRgb.w / 3.0) * PI * u_Sun.w * u_Sun.w); }
        float dec = asin(clamp(dot(v, u_Pole.xyz), -1.0, 1.0)), h = atan(dot(v, u_West.xyz), dot(v, u_Noon.xyz)), ra = u_Pole.w - h;
        bg += texture(u_Stars, vec2(fract(ra / (2.0 * PI)), 0.5 - dec / PI)).rgb * u_View.w;
        for (int i = 0; i < 12; i++) { vec4 p = u_Planet[i]; if (p.w <= 0.0) continue; float a = acos(clamp(dot(v, p.xyz), -1.0, 1.0)); const float SG = 0.0007; bg += vec3(p.w * exp(-0.5 * a * a / (SG * SG)) / (2.0 * PI * SG * SG)); }
    }
    return L + T * bg;
}
vec3 sk_display(vec3 L) { float rain = u_View.z; L *= 1.0 - 0.55 * rain; float y = dot(L, vec3(0.2126, 0.7152, 0.0722)); L = mix(L, vec3(y), 0.6 * rain); return pow(sk_aces(L * u_View.y), vec3(1.0 / 2.2)); }
