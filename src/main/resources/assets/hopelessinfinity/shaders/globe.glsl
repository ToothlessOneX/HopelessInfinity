// Hopeless Infinity 0.14.0: the whole planet from far away (planet/GlobeMesh; drawn by client/FarTerrain in space). Positions arrive in
// planet space; u_Model turns them into the camera's frame (the block frame of the point below, the camera at its origin). Shading as
// far.glsl: the real star on the ground's colour, the night side dark (no sky ambient in space), the air's haze thinning with height.
#ifdef VERTEX
in vec3 a_pos; in vec3 a_nrm; in vec3 a_col;
uniform mat4 u_Mvp; uniform mat4 u_Model;
out vec3 v_nrm; out vec3 v_col; out float v_dist; out float v_dy;
void main() { vec3 rel = (u_Model * vec4(a_pos, 1.0)).xyz; v_nrm = mat3(u_Model) * a_nrm; v_col = a_col; v_dist = length(rel); v_dy = rel.y; gl_Position = u_Mvp * vec4(rel, 1.0); }
#endif
#ifdef FRAGMENT
in vec3 v_nrm; in vec3 v_col; in float v_dist; in float v_dy;
uniform vec4 u_Air; uniform vec4 u_Sun; uniform vec4 u_Fog;
out vec4 fragColor;
void main() {
    vec3 n = normalize(v_nrm); float sun = max(dot(n, normalize(u_Sun.xyz)), 0.0);
    vec3 c = v_col * (0.02 + 0.98 * sun);
    float H = max(100.0, u_Air.y), hc = max(0.0, u_Air.x), hp = max(0.0, u_Air.x + v_dy), dh = hc - hp;
    float mean = abs(dh) < 1.0 ? exp(-hc / H) : H * (exp(-hp / H) - exp(-hc / H)) / dh;
    float f = 1.0 - exp(-0.6931 * v_dist / max(1.0, u_Fog.w) * mean);
    fragColor = vec4(mix(c, u_Fog.rgb * sun, f), 1.0);
}
#endif
