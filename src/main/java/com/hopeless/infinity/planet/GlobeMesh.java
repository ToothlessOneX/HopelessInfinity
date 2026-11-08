package com.hopeless.infinity.planet;

/**
 * 0.14.0 THE WHOLE PLANET from far away (stage 3, "render everything at distance"): FarMesh reaches 2,100 km round the camera, the
 * horizon from orbit is further (sqrt(2 R h): 2,400 km from 400 km up, the whole disc from far out). This is the planet as one mesh: the six
 * cube faces at G x G cells each (11,300 km faces: 118 km cells at G 96), heights and colours from the same Terrain and FarMesh.colour the
 * near ground uses, positions in planet space (metres = blocks, centre at 0; the same radius rule as FarMesh: R + the chunks' block height,
 * the sea at sea level). Built once a world on a worker. Pure Java (tools/GlobeTest).
 */
public final class GlobeMesh {
    public static final int G = 96;
    public final float[] pos, nrm, col; public final int[] idx; public final int verts; public final long ms;
    GlobeMesh(float[] p, float[] n, float[] c, int[] i, long ms) { pos = p; nrm = n; col = c; idx = i; verts = p.length / 3; this.ms = ms; }
    /** 0.18.0 cloud tops seen from space: Venus's sulphuric-acid deck nearly featureless pale yellow in visible light (Mariner 10, MESSENGER
     *  flyby images); mini-Neptunes' hazy hydrogen-methane blue-green like Uranus and Neptune (Voyager 2; CHOSEN, none has been imaged); faint
     *  bands along the spin (amount CHOSEN). */
    static float[] band(double[] s, float r, float g, float b, float amp) { float k = (float) (1 + amp * Math.sin(s[2] * 23 + 1.3) + amp * 0.5 * Math.sin(s[2] * 61)); return new float[]{r * k, g * k, b * k}; }
    public static GlobeMesh build(PlanetWorld w) {
        long t0 = System.nanoTime(); int N = G + 1, per = N * N; float[] p = new float[6 * per * 3], n = new float[6 * per * 3], c = new float[6 * per * 3]; double R = w.cube.R;
        for (int f = 0; f < 6; f++) for (int j = 0; j < N; j++) for (int i = 0; i < N; i++) {
            double u = -1 + 2.0 * i / G, v = -1 + 2.0 * j / G; double[] s = CubeSphere.sphere(CubeSphere.cube(f, u, v)); boolean cloud = w.atm.kind == com.hopeless.infinity.galaxy.Atmosphere.Kind.VENUS_LIKE || w.surface == PlanetWorld.Surface.NONE; double[] hw = cloud ? new double[]{0, Double.NaN, 0} : w.terrain.heightAndWater(s, R); double h = hw[0];   // 0.18.0 seen from space a thick-aired world is its clouds
            double yb = h < 0 && w.surface == PlanetWorld.Surface.EARTH ? Terrain.SEA + 0.888 : (w.surface != PlanetWorld.Surface.EARTH ? MoonTerrain.yd(h) : Terrain.toYd(h)) + 1, r = R + (yb - Terrain.SEA); int k = (f * per + i + N * j) * 3;
            for (int a = 0; a < 3; a++) p[k + a] = (float) (s[a] * r);
            float[] cc = !cloud ? FarMesh.colour(w, s, hw) : w.surface == PlanetWorld.Surface.NONE ? band(s, 0.42f, 0.62f, 0.80f, 0.04f) : band(s, 0.90f, 0.84f, 0.66f, 0.02f); c[k] = cc[0]; c[k + 1] = cc[1]; c[k + 2] = cc[2]; }
        for (int f = 0; f < 6; f++) for (int j = 0; j < N; j++) for (int i = 0; i < N; i++) { int k = (f * per + i + N * j) * 3;
            int a1 = (f * per + Math.min(G, i + 1) + N * j) * 3, a0 = (f * per + Math.max(0, i - 1) + N * j) * 3, b1 = (f * per + i + N * Math.min(G, j + 1)) * 3, b0 = (f * per + i + N * Math.max(0, j - 1)) * 3;
            double[] tx = {p[a1] - p[a0], p[a1 + 1] - p[a0 + 1], p[a1 + 2] - p[a0 + 2]}, tz = {p[b1] - p[b0], p[b1 + 1] - p[b0 + 1], p[b1 + 2] - p[b0 + 2]};
            double[] nn = {tx[1] * tz[2] - tx[2] * tz[1], tx[2] * tz[0] - tx[0] * tz[2], tx[0] * tz[1] - tx[1] * tz[0]}; double out = nn[0] * p[k] + nn[1] * p[k + 1] + nn[2] * p[k + 2]; if (out < 0) { nn[0] = -nn[0]; nn[1] = -nn[1]; nn[2] = -nn[2]; }
            double l = Math.sqrt(nn[0] * nn[0] + nn[1] * nn[1] + nn[2] * nn[2]); for (int a = 0; a < 3; a++) n[k + a] = (float) (nn[a] / l); }
        int[] ix = new int[6 * G * G * 6]; int t = 0;
        for (int f = 0; f < 6; f++) for (int j = 0; j < G; j++) for (int i = 0; i < G; i++) { int v00 = f * per + i + N * j, v10 = v00 + 1, v01 = v00 + N, v11 = v01 + 1; ix[t++] = v00; ix[t++] = v01; ix[t++] = v10; ix[t++] = v10; ix[t++] = v01; ix[t++] = v11; }
        return new GlobeMesh(p, n, c, ix, (System.nanoTime() - t0) / 1_000_000); }
}
