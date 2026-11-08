package com.hopeless.infinity.planet;

import java.util.*;

/** 0.28.5 the lots of a town's plat as the town game sees them (game/Places): each lot's block, row and number, its use, its centre in
 *  block coordinates, its key and seed. Kept here, off the game's classes, so the tests measure it without the game loaded. */
public final class PlatLots {
    private PlatLots() {}
    public record Lot(Towns.Plan plan, long key, Towns.Use use, int bi, int bj, int row, int lot, int x, int z, long seed) {}
    /** Lot (i, j, row, lot) of plan p: its centre from the plat's layout (Towns.seg's inverse). */
    public static Lot lot(Towns.Plan p, int i, int j, int row, int lot, Towns.Use use) { boolean store = row == 0 && p.blocks.get(Towns.key(i, j)) == Towns.Use.STORE; double lw = store ? Towns.LOT_BIZ : Towns.LOT_RES;
        double u = side(i, Towns.STREET_W / 2, Towns.BLOCK_U, Towns.STREET_W, (lot + 0.5) * lw), v = side(j, Towns.MAIN_W / 2, Towns.BLOCK_V, Towns.STREET_W, row == 0 ? Towns.LOT_D / 2 : Towns.LOT_D + Towns.ALLEY_W + Towns.LOT_D / 2);
        double x = p.alongX ? p.cx + u : p.cx + v, z = p.alongX ? p.cz + v : p.cz + u; long k = Towns.lotKey(i, j, row, lot);
        return new Lot(p, p.site.id * 1_000_003L ^ k, use, i, j, row, lot, (int) Math.floor(x), (int) Math.floor(z), Towns.lotSeed(p.site.id, i, j, row, lot)); }
    /** A plan coordinate from a block index and the offset into it from its inner edge. */
    static double side(int idx, double half, double B, double S, double off) { int k = idx >= 0 ? idx : -1 - idx; double sg = idx >= 0 ? 1 : -1; return sg * (half + k * (B + S) + off); }
    /** Every lot of a plat (house and store lots, the civic ones; not the school's or the grain elevator's whole blocks). */
    public static List<Lot> lots(Towns.Plan p) { List<Lot> out = new ArrayList<>();
        for (var en : p.blocks.entrySet()) { long bk = en.getKey(); int i = (int) (bk >> 32), j = (int) (long) bk; Towns.Use bu = en.getValue(); if (bu == Towns.Use.SCHOOL || bu == Towns.Use.GRAIN_ELEVATOR) continue;
            for (int row = 0; row < 2; row++) { boolean store = bu == Towns.Use.STORE && row == 0; int n = store ? 12 : 6; for (int lot = 0; lot < n; lot++) { Towns.Use lu = p.lots.get(Towns.lotKey(i, j, row, lot)); out.add(lot(p, i, j, row, lot, lu != null ? lu : store ? Towns.Use.STORE : Towns.Use.HOUSE)); } } }
        return out; }
}
