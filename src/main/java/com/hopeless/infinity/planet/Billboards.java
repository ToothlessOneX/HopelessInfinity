package com.hopeless.infinity.planet;

import java.util.*;

/** 0.28.4 BILLBOARDS along the Interstates and the US highways (arterials), Cobble & Sons' and every other: digital billboards (the LED
 *  shader of the HQ screens, client/ScreenRender, at billboard size). The rules are the Highway Beautification Act's (23 U.S.C. 131) as a
 *  state carries them out (STATED, South Carolina's federal-state agreement): only in commercial or industrial areas (here: within a
 *  place's urban radius plus 2 km, of places of 2,500 or more: CHOSEN as the "unzoned commercial area" of the agreement); within 660 ft
 *  of the road (here 15 m past the pavement); no two on one side of an Interstate or freeway closer than 500 ft (152.4 m), 300 ft
 *  (91.4 m) on other primary highways; none within 500 ft of an interchange (here 600 m of any crossing on the road: CHOSEN, to clear the
 *  ramps); a face at most 1,200 sq ft, 30 ft high, 60 ft long: here the standard bulletin, 14 x 48 ft (STATED, OAAA: 672 sq ft), 4 x 15
 *  blocks, on a steel monopole, its face 6 m up (CHOSEN). A changeable message sign shows each message 8 s at least and changes at once
 *  (STATED: FHWA guidance of 2007 and the state rules after it: dwell 4-10 s, here 8 s; no animation).
 *  The stations: every 500 ft (300 ft) along the road on its right side, the left side's half way between, kept where the rules allow. */
public final class Billboards {
    public static final double SP_INTERSTATE = 152.4, SP_PRIMARY = 91.44, OFF_M = 15, NEAR_X_M = 600, ZONE_M = 2000; public static final int FACE_W = 15, FACE_H = 4, FACE_Y0 = 6; public static final long ZONE_POP = 2500;
    public static final java.util.concurrent.atomic.LongAdder PLACED = new java.util.concurrent.atomic.LongAdder(), REFUSED_ZONE = new java.util.concurrent.atomic.LongAdder(), REFUSED_X = new java.util.concurrent.atomic.LongAdder(), REFUSED_TOWN = new java.util.concurrent.atomic.LongAdder();
    /** A billboard in face block coordinates: the controller (lowest corner of the face) x, y, z; facing (north/south: the face runs along +x; east/west: along +z). */
    public record Board(int x, int y, int z, String facing, int edge, double along) {}
    static final Map<Long, Optional<Board>> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** The billboards whose blocks reach the chunk [x0, x1) x [z0, z1) on face f. */
    public static List<Board> near(PlanetWorld w, int f, int x0, int z0, int x1, int z1, double[] sc) { Roads rd = w.roadsIfMade(); if (rd == null || Roads.building() || !Roads.on) return List.of();
        List<Board> out = new ArrayList<>(); Set<Integer> done = new HashSet<>();
        for (Roads.Hit h : rd.hits(sc)) { if (h.edge < 0 || h.kind != 0 || (h.cls != Roads.Cls.INTERSTATE && h.cls != Roads.Cls.ARTERIAL) || !done.add(h.edge)) continue; Roads.Route r = rd.route(h.edge); double sp = h.cls == Roads.Cls.INTERSTATE ? SP_INTERSTATE : SP_PRIMARY, L = r.along[r.n() - 1];
            for (int side = -1; side <= 1; side += 2) for (long k = (long) Math.floor((h.along - 80) / sp); k <= (long) Math.ceil((h.along + 80) / sp); k++) { double s = k * sp + (side < 0 ? sp / 2 : 0); if (s < 0 || s > L) continue;
                long key = ((long) h.edge << 24) ^ (k << 1) ^ (side > 0 ? 1 : 0); final double fs = s; final int fside = side; Optional<Board> b = CACHE.computeIfAbsent(key, q -> Optional.ofNullable(make(w, rd, r, fs, fside)));
                if (b.isPresent()) { Board bd = b.get(); boolean alongX = bd.facing.equals("north") || bd.facing.equals("south"); int bx1 = bd.x + (alongX ? FACE_W : 2), bz1 = bd.z + (alongX ? 2 : FACE_W);
                    if (bx1 >= x0 && bd.x - 2 < x1 && bz1 >= z0 && bd.z - 2 < z1 && w.cube.locate(sc)[0] == f) out.add(bd); } } }
        return out; }
    /** The billboard at station s on side `side` of route r, or null where the rules refuse it. */
    static Board make(PlanetWorld w, Roads rd, Roads.Route r, double s, int side) {
        for (int ci : r.crossAt) if (Math.abs(r.along[ci] - s) < NEAR_X_M) { REFUSED_X.increment(); return null; }
        double half = Roads.HALF[r.e.cls.ordinal()], off = side * (half + OFF_M); double[] p = r.al.point(s, off), p2 = r.al.point(Math.min(s + 10, r.al.length()), off);
        boolean zone = false; for (Sites.Site st : w.sites().nearest(p, null, 6)) if (st.pop >= ZONE_POP && Rivers.arc(p, st.s) * w.cube.R < st.urbanRadiusM() + ZONE_M) { zone = true; break; }
        if (!zone) { REFUSED_ZONE.increment(); return null; }
        double[] a = w.cube.locate(p), b = w.cube.locate(p2); if ((int) a[0] != (int) b[0]) return null;
        // not on a town's plat or in a city's built blocks (their own signs and screens are there; a billboard stands on open roadside land)
        Towns tw = Towns.on ? w.towns() : null; Metro mt = Metro.on ? w.metroIfMade() : null; if ((tw != null && tw.at((int) a[0], a[1], a[2], p) != null) || (mt != null && mt.inCity((int) a[0], (int) Math.floor(a[1]), (int) Math.floor(a[2]), p))) { REFUSED_TOWN.increment(); return null; } double dx = b[1] - a[1], dz = b[2] - a[2];
        // the face looks back across the road: the road's left (side +1 stands on the right) is the way it faces
        double nx = side > 0 ? dz : -dz, nz = side > 0 ? -dx : dx; String facing = Math.abs(nx) > Math.abs(nz) ? (nx > 0 ? "east" : "west") : (nz > 0 ? "south" : "north");
        double[] hw = w.terrain.heightAndWater(p, w.cube.R); if (!Double.isNaN(hw[1]) && hw[1] > hw[0]) return null;   // not in water
        int y = (int) Math.floor(Terrain.toYd(hw[0])) + 1; boolean alongX = facing.equals("north") || facing.equals("south"); int x = (int) Math.floor(a[1]) - (alongX ? FACE_W / 2 : 0), z = (int) Math.floor(a[2]) - (alongX ? 0 : FACE_W / 2);
        PLACED.increment(); return new Board(x, y, z, facing, r.e.id, s); }
    /** The billboard's blocks: (x, y, z, block string) into the sink, inside the chunk only. */
    public static void draw(Board b, int x0, int z0, int x1, int z1, Metro.Sink out) { boolean alongX = b.facing.equals("north") || b.facing.equals("south");
        int back = b.facing.equals("north") ? 1 : b.facing.equals("south") ? -1 : b.facing.equals("west") ? 1 : -1;   // the pole stands behind the face
        for (int i = 0; i < FACE_W; i++) for (int j = 0; j < FACE_H; j++) { int x = b.x + (alongX ? i : 0), z = b.z + (alongX ? 0 : i); if (x < x0 || x >= x1 || z < z0 || z >= z1) continue;
            out.put(x, b.y + FACE_Y0 + j, z, i == 0 && j == 0 ? "hopelessinfinity:billboard[facing=" + b.facing + "]" : "black_concrete"); }
        for (int i = 0; i < FACE_W; i++) {   // the catwalk in front, under the face
            int fx = b.x + (alongX ? i : (b.facing.equals("east") ? 1 : -1)), fz = b.z + (alongX ? (b.facing.equals("south") ? 1 : -1) : i); if (fx >= x0 && fx < x1 && fz >= z0 && fz < z1) out.put(fx, b.y + FACE_Y0 - 1, fz, "iron_bars"); }
        int px = b.x + (alongX ? FACE_W / 2 : back), pz = b.z + (alongX ? back : FACE_W / 2);
        if (px >= x0 && px < x1 && pz >= z0 && pz < z1) for (int y = 0; y < FACE_Y0 + FACE_H; y++) out.put(px, b.y + y, pz, "iron_block"); }   // the monopole, set in the ground
    private Billboards() {}
}
