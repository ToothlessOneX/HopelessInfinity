// Hopeless Infinity 0.27.3: THE VIDEO SCREEN (client/ScreenRender; planet/Frontage, planet/AdArt). The advertisement's slides (one picture,
// stacked top to bottom) shown in turn on an LED wall: each picture pixel is one LED, a round emitter with dark gaps between (STATED: how
// LED video walls are built); far off, where one screen pixel covers more than one LED, the dots blend into their mean (no shimmer). The
// pitch is the picture's (32 m / 512 = 62.5 mm, CHOSEN for the block scale; outdoor walls run about 6-20 mm). Slides SLIDE_S s each, a
// cross-fade between (CHOSEN). Bright: an LED wall is its own light (unlit by the world).
#ifdef VERTEX
in vec2 a_uv;
uniform mat4 u_Mvp;
uniform vec3 u_Origin, u_U, u_V;   // the picture's bottom-left corner (camera-relative) and its across / up edges (full length, m)
out vec2 v_uv;
void main() { v_uv = a_uv; gl_Position = u_Mvp * vec4(u_Origin + a_uv.x * u_U + a_uv.y * u_V, 1.0); }
#endif
#ifdef FRAGMENT
in vec2 v_uv;
uniform sampler2D u_Pic;
uniform vec4 u_Info;   // x seconds (the world's clock), y slides, z picture width px, w picture height px (one slide)
out vec4 fragColor;
uniform float u_Mode;   // 0.28.4: 0 the HQ screen, 1 a digital billboard (8 s a message, changed at once, no sweep: FHWA 2007 guidance and state rules)
const float SLIDE_S0 = 7.0, FADE_S0 = 0.8;
vec3 slide(float k, vec2 uv) { return texture(u_Pic, vec2(uv.x, (k + 1.0 - uv.y) / u_Info.y)).rgb; }
void main() {
    float SLIDE_S = u_Mode > 0.5 ? 8.0 : SLIDE_S0; float t = u_Info.x, n = u_Info.y, k = mod(floor(t / SLIDE_S), n), f = u_Mode > 0.5 ? 0.0 : smoothstep(SLIDE_S - FADE_S0, SLIDE_S, mod(t, SLIDE_S));
    vec2 px = v_uv * u_Info.zw, cell = fract(px), uv = (floor(px) + 0.5) / u_Info.zw;   // the LED this point belongs to
    vec3 c = mix(slide(k, uv), slide(mod(k + 1.0, n), uv), f);
    float d = length(cell - 0.5), led = 1.0 - smoothstep(0.30, 0.46, d), fw = max(fwidth(px.x), fwidth(px.y));
    float m = mix(led, 0.4577, smoothstep(0.35, 0.9, fw));   // far: the mean of the dot pattern (0.4577, measured over the cell: 2000 x 2000 samples)
    float sweep = 0.12 * exp(-pow((v_uv.x - mod(t * 0.18, 1.6) + 0.3) * 6.0, 2.0)) * step(k, 0.5) * (1.0 - f) * (1.0 - step(0.5, u_Mode));   // a light sweep across the first slide (CHOSEN)
    fragColor = vec4(min(vec3(1.0), (c + sweep) * m / 0.4577), 1.0);   // far off the wall shows the picture's own brightness; up close the dots run brighter
}
#endif
