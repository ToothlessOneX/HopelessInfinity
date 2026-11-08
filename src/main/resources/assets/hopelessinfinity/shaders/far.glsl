// Hopeless Infinity 0.9.0: the planet at distance (planet/FarMesh.java builds the mesh; client/FarTerrain.java draws it). Split at the
// line "//---FRAGMENT---". Lighting: the real star (sky/Ephemeris, the same direction the sky shader gets) on the ground's own colour,
// a sky ambient, and the air between: fog to the sky's horizon colour (Minecraft's fog colour, which our sky sets) over the distance.
#ifdef VERTEX
in vec3 a_pos; in vec3 a_nrm; in vec3 a_col;
uniform mat4 u_Mvp;
out vec3 v_nrm; out vec3 v_col; out float v_dist; out float v_dy;
void main() { v_nrm = a_nrm; v_col = a_col; v_dist = length(a_pos); v_dy = a_pos.y; gl_Position = u_Mvp * vec4(a_pos, 1.0); }
#endif
#ifdef FRAGMENT
in vec3 v_nrm; in vec3 v_col; in float v_dist; in float v_dy;
uniform vec4 u_Air;   // 0.9.1: x the camera's height over the sea (m), y the haze's scale height (m: aerosols, the sky's Mie scale height); 0.14.0 z 1 in space
uniform vec4 u_Sun;   // xyz direction to the star (camera frame), w daylight 0..1 (Minecraft's sky darkening, as the chunks get)
uniform vec4 u_Fog;   // rgb fog colour, w distance (blocks) at which the air hides half the ground
out vec4 fragColor;
void main() {
    vec3 n = normalize(v_nrm); float sun = max(dot(n, normalize(u_Sun.xyz)), 0.0);
    float amb = mix(0.35, 0.02, u_Air.z);   // 0.14.0: in space no sky light fills the night side
    vec3 c = v_col * (amb + (1.0 - amb) * sun) * (0.15 + 0.85 * u_Sun.w);
    // 0.9.1: the haze thins with height (density e^(-h / H)): the optical depth along the straight path from the camera (hc) to the ground (hp)
    // is k L H (e^(-hp/H) - e^(-hc/H)) / (hc - hp), k the sea-level extinction (ln 2 / half distance); looking down from high up the ground stays clear
    float H = max(100.0, u_Air.y), hc = max(0.0, u_Air.x), hp = max(0.0, u_Air.x + v_dy), dh = hc - hp;
    float mean = abs(dh) < 1.0 ? exp(-hc / H) : H * (exp(-hp / H) - exp(-hc / H)) / dh;
    float f = 1.0 - exp(-0.6931 * v_dist / max(1.0, u_Fog.w) * mean);
    fragColor = vec4(mix(c, u_Fog.rgb, f), 1.0);
}
#endif
