package com.hopeless.infinity.planet;

import java.util.*;

/** 0.28.1 ENCLOSED GLASS SKY BRIDGES between megacity towers (owner, 2026-11-04: "enclosed glass sky bridges between some, like Dubai's
 *  mega towers"). Grounded in the built ones (STATED): the Petronas Towers' bridge, 58 m long, two storeys at floors 41-42, about 170 m up
 *  (Thornton Tomasetti), so at 0.38 of the 452 m towers; Raffles City Chongqing's Crystal, about 300 m long, about 250 m up, on four towers.
 *  The rule here: two of the tallest half of the megatall towers (600 m or more) on the same row or column of the plan (their axes in line) with facades 300 m apart
 *  or less (the Crystal's span) are joined, each tower to one other, nearest first; the bridge's lower storey lands on the shorter tower's sky
 *  lobby nearest 0.38 of its height (Petronas' proportion; sky lobbies are where people change lifts, so the bridge has a use), and on
 *  the same height in the taller one (whose pad must differ by a whole number of floors). Two storeys (Petronas), 12 m wide (CHOSEN: a
 *  6 m walkway each storey side by side with services), glass walls and roof on a concrete deck, steel trusses under it. Each end cuts
 *  through the facade into the floor behind it (2 m in: CHOSEN), so the bridge opens onto the tower's floors. */
public final class SkyBridges {
    public static final int MAX_SPAN = 300, HALF_W = 6, STOREYS = 2, PER_TOWER = 1, INTO = 2; public static final double AT = 0.38, TOP_SHARE = 0.5;
    /** A bridge: towers a and b (their lots), along u (true) or v, deck y (absolute block y of the deck), the faces' positions along the axis (plan metres, from a toward b), the across position. */
    public record Bridge(Metro.Lot a, Metro.Lot b, boolean alongU, int deckY, double s0, double s1, double across, int floorA, int floorB) {}
    public static final java.util.concurrent.atomic.LongAdder BLOCKS = new java.util.concurrent.atomic.LongAdder();

    /** The bridges of plan p (pads from Metro.pad). */
    static List<Bridge> plan(Metro m, Metro.Plan p) { List<Bridge> out = new ArrayList<>(); List<Metro.Lot> mega = new ArrayList<>(); for (Metro.Lot l : p.towers) if (l.height >= Parks.MEGATALL_M) mega.add(l); mega.sort((x, y) -> y.height - x.height); mega = new ArrayList<>(mega.subList(0, (int) Math.ceil(mega.size() * TOP_SHARE)));   // 0.28.1 "between some": the tallest half of the megatalls, one bridge each (measured: every megatall with 2 gave 88 bridges in Stonewood)
        List<double[]> cand = new ArrayList<>(); for (int i = 0; i < mega.size(); i++) for (int j = i + 1; j < mega.size(); j++) { Metro.Lot a = mega.get(i), b = mega.get(j);
            boolean u = Math.abs(a.v - b.v) < 0.5, v = Math.abs(a.u - b.u) < 0.5; if (!u && !v) continue; double d = u ? Math.abs(b.u - a.u) : Math.abs(b.v - a.v); cand.add(new double[]{d, i, j, u ? 1 : 0}); }
        cand.sort(Comparator.comparingDouble(c -> c[0])); int[] used = new int[mega.size()];
        for (double[] c : cand) { int i = (int) c[1], j = (int) c[2]; if (used[i] >= PER_TOWER || used[j] >= PER_TOWER) continue; Metro.Lot a = mega.get(i), b = mega.get(j); boolean alongU = c[3] == 1;
            if (alongU ? a.u > b.u : a.v > b.v) { Metro.Lot t = a; a = b; b = t; }
            Metro.Lot lo = a.height <= b.height ? a : b, hi = lo == a ? b : a; Tower tl = lo.tower(), th = hi.tower(); int padL = m.pad(p, lo), padH = m.pad(p, hi); if (Math.floorMod(padL - padH, Tower.FLOOR) != 0) continue;
            int fl = -1; double want = AT * tl.floors; for (int f = Tower.PODIUM_FLOORS; f < tl.floors - STOREYS; f++) if (tl.use(f) == Tower.Use.SKY_LOBBY && (fl < 0 || Math.abs(f - want) < Math.abs(fl - want))) fl = f;
            if (fl < 0) continue; int deck = padL - 1 + fl * Tower.FLOOR, fh = (deck - (padH - 1)) / Tower.FLOOR; if (fh < 0 || fh + STOREYS >= th.floors) continue;
            Tower ta = a.tower(), tb = b.tower(); int fa = a == lo ? fl : fh, fb = b == lo ? fl : fh;
            int ea = face(ta, fa, alongU ? 1 : 0, 1), eb = face(tb, fb, alongU ? 1 : 0, -1);   // a's face toward b (+), b's face toward a (-)
            double s0 = (alongU ? a.u : a.v) + ea, s1 = (alongU ? b.u : b.v) - eb; if (s1 - s0 > MAX_SPAN || s1 - s0 < 4) continue;
            // the bridge's own blocks may not pass through a third tower
            boolean clear = true; for (Metro.Lot l : p.towers) { if (l == a || l == b) continue; double al = alongU ? l.u : l.v, ac = alongU ? l.v : l.u, ab = alongU ? a.v : a.u; int r = l.tower().reach(); if (al + r > s0 && al - r < s1 && Math.abs(ac - ab) < r + HALF_W) { clear = false; break; } }
            if (!clear) continue; used[i]++; used[j]++; out.add(new Bridge(a, b, alongU, deck, s0 - INTO, s1 + INTO, alongU ? a.v : a.u, fa, fb)); }
        return out; }
    /** How far from its axis tower t's facade is on floor f, along u (axis 1) or v (axis 0), in direction sg. */
    static int face(Tower t, int f, int axis, int sg) { int y = f * Tower.FLOOR + 1, d = 0; for (int k = 0; k < t.reach() + 2; k++) { String b = axis == 1 ? t.at(sg * k, y, 0) : t.at(0, y, sg * k); if (b == null) break; d = k; } return d; }
    /** The bridge's block at plan offset (s along the axis, c across from its centre line) and dy over its deck; null outside it. */
    public static String at(Bridge br, double s, double c, int dy) { if (s < br.s0 || s > br.s1 || Math.abs(c) > HALF_W || dy < -2 || dy > STOREYS * Tower.FLOOR) return null; int ac = (int) Math.round(Math.abs(c));
        boolean end = s < br.s0 + INTO + 0.5 || s > br.s1 - INTO - 0.5;   // inside the towers: the opening, floor and ceiling only
        if (dy < 0) return end ? null : ac == HALF_W || ac == 0 ? (dy == -1 ? "iron_block" : "iron_bars") : dy == -2 && Math.floorMod((int) Math.round(s), 6) == 0 ? "iron_bars" : null;   // trusses: two chords and a centre chord, verticals every 6 m
        if (dy == 0 || dy == Tower.FLOOR) return ac == HALF_W && !end ? "light_blue_stained_glass" : dy == 0 ? "gray_concrete" : "smooth_quartz";   // decks (the middle one is the upper storey's floor)
        if (dy == STOREYS * Tower.FLOOR) return end ? null : "light_blue_stained_glass";   // the glass roof
        if (ac == HALF_W) return end ? null : (Math.floorMod((int) Math.round(s), Tower.MULLION) == 0 ? "light_gray_concrete" : "light_blue_stained_glass");
        if (dy % Tower.FLOOR == Tower.FLOOR - 1 && ac % 6 == 3 && Math.floorMod((int) Math.round(s), 6) == 3) return "sea_lantern";   // lights on a 6 m grid, as in the towers
        return "air"; }
    private SkyBridges() {}
}
