package com.hopeless.infinity.planet;

/**
 * The cube-sphere planet at real size (1 block = 1 m): each of the six square faces (F x F blocks, F = pi R / 2) is its own dimension,
 * centred on that dimension's origin, so even the largest rocky planet (face half-width 6.2 million blocks) fits inside the lifted
 * position packing (+/- 8.39 million, see mixin.BlockPosMixin). Faces 0-3 run round the equator eastward (+Y, -X, -Y, +X in planet space, planet axis
 * +Z), face 4 is the north pole, face 5 the south pole. In each face MC +x points east and MC +z points south, so every face is an
 * ordinary Minecraft surface with up = +y. Walking off a face edge folds you onto the neighbour face (transfer()): same point on the
 * planet, heading turned the way the edge turns it. Terrain is sampled at the sphere point of each block (Nowell's cube-to-sphere map),
 * so both sides of an edge are the same ground.
 */
public final class CubeSphere {
    public static final double[][] N = {{0,1,0},{-1,0,0},{0,-1,0},{1,0,0},{0,0,1},{0,0,-1}};
    public static final double[][] A = {{-1,0,0},{0,-1,0},{1,0,0},{0,1,0},{-1,0,0},{-1,0,0}};   // MC +x (east) in planet space
    public static final double[][] B = {{0,0,-1},{0,0,-1},{0,0,-1},{0,0,-1},{0,1,0},{0,-1,0}};   // MC +z (south) in planet space
    public static final String[] NAME = {"equator 0", "equator 90E", "equator 180", "equator 90W", "north pole", "south pole"};
    public final int F; public final double R;

    public CubeSphere(long blockRadius) { R = blockRadius; F = (int) Math.round(Math.PI * blockRadius / 2); }
    public int faceX0(int f) { return -F / 2; }
    public int faceZ0() { return -F / 2; }
    /** Inside the face square (every face dimension has the same square round its origin)? */
    public boolean inside(double x, double z) { return x >= faceX0(0) && x < faceX0(0) + F && z >= faceZ0() && z < faceZ0() + F; }
    public double u(int f, double x) { return 2 * (x - faceX0(f)) / F - 1; }
    public double v(double z) { return 2 * (z - faceZ0()) / F - 1; }
    public double x(int f, double u) { return faceX0(f) + (u + 1) * F / 2; }
    public double z(double v) { return faceZ0() + (v + 1) * F / 2; }
    /** Cube point (on the face plane) for face coordinates. */
    public static double[] cube(int f, double u, double v) { return new double[]{N[f][0] + u * A[f][0] + v * B[f][0], N[f][1] + u * A[f][1] + v * B[f][1], N[f][2] + u * A[f][2] + v * B[f][2]}; }
    /** Unit sphere point for a cube point (Nowell 2005, "Mapping a cube to a sphere": evens out cell areas better than normalising). */
    public static double[] sphere(double[] c) {
        double x = c[0], y = c[1], z = c[2], x2 = x * x, y2 = y * y, z2 = z * z;
        return new double[]{x * Math.sqrt(1 - y2 / 2 - z2 / 2 + y2 * z2 / 3), y * Math.sqrt(1 - z2 / 2 - x2 / 2 + z2 * x2 / 3), z * Math.sqrt(1 - x2 / 2 - y2 / 2 + x2 * y2 / 3)}; }
    /** Sphere point of a block centre (x, z already +0.5) on face f, or null outside the face. */
    public double[] spherePoint(int f, double x, double z) { return inside(x, z) ? sphere(cube(f, u(f, x), v(z))) : null; }
    /** Latitude in degrees (planet axis +Z). */
    public static double lat(double[] s) { return Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, s[2])))); }
    public static double lon(double[] s) { return Math.toDegrees(Math.atan2(-s[0], s[1])); }

    /** Result of a fold over a face edge: new world x, z and the turn of the heading in degrees (add to yaw). */
    public record Fold(int face, double x, double z, double yawTurn, double[] rot) {}
    /**
     * Folds a position that has left face f (x, z outside its square) onto the neighbour face. The overshoot past the edge is laid
     * down on the neighbour as the same distance from the shared edge, and directions turn with the edge; rot is the 2x2 matrix that
     * turns a horizontal (dx, dz) vector from face f into the neighbour face.
     */
    public Fold transfer(int f, double x, double z) {
        double u = u(f, x), v = v(z), cu = Math.max(-1, Math.min(1, u)), cv = Math.max(-1, Math.min(1, v));
        double ou = (u - cu) * F / 2, ov = (v - cv) * F / 2;   // overshoot in blocks along +x / +z
        double[] e = cube(f, cu, cv);
        double[] exit = Math.abs(ou) >= Math.abs(ov) ? scale(A[f], Math.signum(ou)) : scale(B[f], Math.signum(ov));
        int g = faceOf(exit);
        // a face-f direction d: its part along 'exit' turns into -N[f] on face g, the part along the edge stays
        double[] ax = map(A[f], exit, f), bz = map(B[f], exit, f);
        double[] rot = {dot(ax, A[g]), dot(bz, A[g]), dot(ax, B[g]), dot(bz, B[g])};   // [dx'; dz'] = rot * [dx; dz]
        double over = Math.max(Math.abs(ou), Math.abs(ov));
        double[] p = add(e, scale(N[f], -over * 2 / F));   // on face g, 'over' blocks in from the shared edge (e keeps the place along it)
        double nx = x(g, dot(p, A[g])), nz = z(dot(p, B[g]));
        double turn = yaw(rot, 0);
        return new Fold(g, nx, nz, turn, rot);
    }
    /** A Minecraft yaw (degrees; facing dx = -sin, dz = cos) carried over the fold. */
    public static float yaw(double[] rot, double yawDeg) {
        double r = Math.toRadians(yawDeg), dx = -Math.sin(r), dz = Math.cos(r), nx = rot[0] * dx + rot[1] * dz, nz = rot[2] * dx + rot[3] * dz;
        return (float) Math.toDegrees(Math.atan2(-nx, nz)); }
    static double[] map(double[] d, double[] exit, int f) { double k = dot(d, exit); return add(sub(d, scale(exit, k)), scale(N[f], -k)); }
    /** 0.12.0: the inverse of spherePoint: the face and block x, z (centre convention as spherePoint) of a direction s (any length). Gauss-Newton on
     *  Nowell's map from the gnomonic first guess (6 steps reach 1e-12). {face, x, z}. */
    public double[] locate(double[] s0) {
        double l = Math.sqrt(dot(s0, s0)); double[] s = {s0[0] / l, s0[1] / l, s0[2] / l}; int f = 0; double best = -2;
        for (int g = 0; g < 6; g++) { double d = dot(N[g], s); if (d > best) { best = d; f = g; } }
        double u = dot(s, A[f]) / best, v = dot(s, B[f]) / best, h = 1e-7;
        for (int it = 0; it < 8; it++) {
            double[] p = sphere(cube(f, u, v)), pu = sphere(cube(f, u + h, v)), pv = sphere(cube(f, u, v + h)), r = sub(p, s);
            double[] ju = scale(sub(pu, p), 1 / h), jv = scale(sub(pv, p), 1 / h);
            double a = dot(ju, ju), b = dot(ju, jv), c = dot(jv, jv), gu = dot(ju, r), gv = dot(jv, r), det = a * c - b * b; if (Math.abs(det) < 1e-30) break;
            double du = (c * gu - b * gv) / det, dv = (a * gv - b * gu) / det; u -= du; v -= dv; if (Math.abs(du) + Math.abs(dv) < 1e-14) break; }
        u = Math.max(-1, Math.min(1, u)); v = Math.max(-1, Math.min(1, v));
        return new double[]{f, Math.min(x(f, u), faceX0(f) + F - 1e-6), Math.min(z(v), faceZ0() + F - 1e-6)}; }
    /** 0.12.0: the block frame at a face point, in planet space: {up, ex (block +x), ez (block +z), {metres per block x, z}}, right-handed as FarMesh's. */
    public double[][] frame(int f, double x, double z) {
        double[] s0 = spherePoint(f, x, z), sx = spherePoint(f, Math.min(x + 1, faceX0(f) + F - 1e-6), z), sz = spherePoint(f, x, Math.min(z + 1, faceZ0() + F - 1e-6));
        if (s0 == null) return new double[][]{N[f], A[f], B[f]};
        boolean bx = x + 1 >= faceX0(f) + F, bz = z + 1 >= faceZ0() + F; if (bx) { sx = s0; s0 = spherePoint(f, x - 1, z); } if (bz) { sz = spherePoint(f, x, z); }
        double[] up = spherePoint(f, x, z), ex = sub(sx, s0); ex = sub(ex, scale(up, dot(ex, up))); double el = Math.sqrt(dot(ex, ex)); ex = scale(ex, 1 / el);
        double[] ez = {ex[1] * up[2] - ex[2] * up[1], ex[2] * up[0] - ex[0] * up[2], ex[0] * up[1] - ex[1] * up[0]};
        double[] zz = bz ? sub(sz, spherePoint(f, x, z - 1)) : sub(sz, up); if (dot(ez, zz) < 0) ez = scale(ez, -1);
        double lx = Math.sqrt(dot(sub(sx, s0), sub(sx, s0))) * R, lz = Math.sqrt(dot(sub(sz, bz ? spherePoint(f, x, z - 1) : up), sub(sz, bz ? spherePoint(f, x, z - 1) : up))) * R;
        return new double[][]{up, ex, ez, {lx, lz}}; }   // [3]: the metres in one block along x and along z here (a block is not exactly a metre on a cube face)
    static int faceOf(double[] n) { for (int g = 0; g < 6; g++) if (dot(N[g], n) > 0.5) return g; throw new IllegalStateException(); }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double[] scale(double[] a, double k) { return new double[]{a[0] * k, a[1] * k, a[2] * k}; }
    static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
}
