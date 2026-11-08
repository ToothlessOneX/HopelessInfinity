package com.hopeless.infinity.planet;

import java.util.ArrayList;
import java.util.List;

/** What one block column of a planet face is made of: the plan the chunk generator lays down and the preview renderer draws. */
public final class Column {
    public enum Mat { AIR, BEDROCK, DEEPSLATE, STONE, DIRT, GRASS, PODZOL, SAND, SANDSTONE, RED_SAND, TERRACOTTA, GRAVEL, CLAY, MUD, SNOW_BLOCK, SNOW_LAYER, ICE, PACKED_ICE, WATER, CALCITE, REGOLITH, MARE_REGOLITH, ANORTHOSITE, BASALT, BASALT_ROCK, LAVA, MAGMA_CRUST, BLACKSTONE,
        ROAD_CONCRETE, ASPHALT, LINE_WHITE, LINE_YELLOW, DIRT_PATH, DECK, RAIL, LINING, TIE, RAIL_TOP }   // 0.29.0 TIE, RAIL_TOP: railway ties and the rails on them   // 0.23.0 roads (Roads)   // 0.18.0 Venus's kind of ground: basalt plains (Venera landers), molten seas
    public final boolean voidCol; public final int face, surfaceY, fillerDepth; public final double heightM; public double groundTop; /* groundTop: the true ground height (top of the top block plus the slope fraction above it) */ public final Terrain.Bio biome; public final Mat top, filler, under; public final boolean snowLayer, ice; public final double tempC; /** 0.5.0: the sea floor plan (Seabed), NONE until seabed() is applied */ public final Seabed.Kind seabed;
    Column(boolean v, int face, int sy, double hM, Terrain.Bio b, Mat top, Mat filler, int fd, Mat under, boolean snow, boolean ice, double t) { this(v, face, sy, hM, b, top, filler, fd, under, snow, ice, t, Seabed.Kind.NONE); }
    Column(boolean v, int face, int sy, double hM, Terrain.Bio b, Mat top, Mat filler, int fd, Mat under, boolean snow, boolean ice, double t, Seabed.Kind k) {
        voidCol = v; this.face = face; surfaceY = sy; heightM = hM; groundTop = (b.dry() ? MoonTerrain.yd(hM) : Terrain.toYd(hM)) + 1; biome = b; this.top = top; this.filler = filler; fillerDepth = fd; this.under = under; snowLayer = snow; this.ice = ice; tempC = t; seabed = k; }
    static final Column VOID = new Column(true, -1, Integer.MIN_VALUE, 0, Terrain.Bio.THE_VOID, Mat.AIR, Mat.AIR, 0, Mat.AIR, false, false, 0);

    /** The column at block x, z of face f (outside the face square: void). */
    public static Column of(PlanetWorld w, int f, int x, int z) {
        if (!w.cube.inside(x + 0.5, z + 0.5)) return VOID;
        double[] s = CubeSphere.sphere(CubeSphere.cube(f, w.cube.u(f, x + 0.5), w.cube.v(z + 0.5)));
        double[] hw = w.terrain.heightAndWater(s, w.cube.R); double h = hw[0]; Terrain.Bio b = w.terrain.biome(s, h); int wk = (int) hw[2];   // 0.21.0 rivers and lakes
        if (wk == 1 && h < hw[1]) b = w.terrain.temperatureC(s, Math.max(0, h)) < 0 ? Terrain.Bio.FROZEN_RIVER : Terrain.Bio.RIVER; int y = b.dry() ? (int) Math.floor(MoonTerrain.yd(h)) : Terrain.toY(h); double t = w.terrain.temperatureC(s, Math.max(0, h));
        boolean under = y < Terrain.SEA; Mat top, fill; int fd = 4;
        switch (b) {
            case DESERT, BEACH -> { top = Mat.SAND; fill = Mat.SANDSTONE; }
            case SNOWY_BEACH -> { top = Mat.SAND; fill = Mat.SAND; }
            case BADLANDS -> { top = Mat.RED_SAND; fill = Mat.TERRACOTTA; fd = 12; }
            case STONY_PEAKS, JAGGED_PEAKS, STONY_SHORE -> { top = Mat.STONE; fill = Mat.STONE; }
            case FROZEN_PEAKS, SNOWY_SLOPES -> { top = Mat.SNOW_BLOCK; fill = Mat.STONE; fd = 2; }
            case ICE_SPIKES -> { top = Mat.SNOW_BLOCK; fill = Mat.DIRT; }
            case OLD_GROWTH_SPRUCE_TAIGA -> { top = Mat.PODZOL; fill = Mat.DIRT; }
            case MANGROVE_SWAMP -> { top = Mat.MUD; fill = Mat.MUD; }   // 0.20.0 mangroves root in tidal mud
            case VOLCANIC -> { top = Mat.BASALT_ROCK; fill = Mat.BASALT; fd = 8; }   // young lava flows: basalt
            default -> { top = Mat.GRASS; fill = Mat.DIRT; }
        }
        if (b == Terrain.Bio.LUNAR) { boolean mare = w.terrain instanceof MoonTerrain mt && mt.mare(s);   // 0.15.0 the moon: regolith (andesite grey) over anorthosite (diorite) highlands, or dark mare basalt (smooth basalt); dry at every height; both have terrain slopes
            return new Column(false, f, y, h, b, mare ? Mat.MARE_REGOLITH : Mat.REGOLITH, mare ? Mat.BASALT : Mat.ANORTHOSITE, mare ? 6 : 8, Mat.STONE, false, false, t); }
        if (b == Terrain.Bio.VENUSIAN || b == Terrain.Bio.MAGMA) { VenusTerrain vt = (VenusTerrain) w.terrain; boolean tess = vt.tessera(s);   // 0.18.0 basalt plains (Venera 13/14: basalt), deformed tessera highlands (deepslate), lava seas on molten worlds
            Column c; if (vt.lava(h)) c = new Column(false, f, y, h, b, Mat.MAGMA_CRUST, Mat.BASALT, 3, Mat.STONE, false, false, t);
            else c = new Column(false, f, y, h, b, tess ? Mat.DEEPSLATE : Mat.BASALT_ROCK, Mat.BASALT, 5, Mat.STONE, false, false, t);
            if (Double.isFinite(vt.lavaLevelM)) c.lavaY = (int) Math.floor(MoonTerrain.yd(Math.min(vt.lavaLevelM, 5.6e3))); return c; }
        int lavaY = Integer.MIN_VALUE, waterY = Integer.MIN_VALUE;
        if (!Double.isNaN(hw[1]) && hw[1] > h) { (wk == 1 ? RIVER_COLS : LAKE_COLS).increment(); waterY = Terrain.toY(hw[1]); if (waterY <= y && hw[1] - h >= 0.25) { y = waterY - 1; under = y < Terrain.SEA; WET_STEP.increment(); }   /* 0.29.8 water a quarter metre deep or more shows a block of it: the bed steps down under the water's block (measured: 11.6 % of channel centres had no water block, the level and the bed in the same block) */ top = h < hw[1] - 3 ? Mat.GRAVEL : Mat.SAND; fill = Mat.SAND; fd = 3; }   // river and lake beds: gravel in the deep, sand at the edges
        else if (wk == 3) { DRY_COLS.increment(); top = Mat.CALCITE; fill = Mat.SAND; fd = 2; }   // a salt flat (evaporite crust: calcite stands in for salt)
        if (w.surface == PlanetWorld.Surface.EARTH && Terrain.volcanicOn && h > 0 && w.terrain.volcanic(s) > 0) { double[] k = {0, 0}; w.terrain.volcano(s, w.cube.R, k);   // 0.20.0 cones of scoria (blackstone), active craters (magma crust round a lava lake)
            if (k[0] == 1) { top = Mat.BLACKSTONE; fill = Mat.BLACKSTONE; fd = 6; }
            else if (k[0] == 3) { top = Mat.MAGMA_CRUST; fill = Mat.BASALT; fd = 4; lavaY = Terrain.toY(k[1]); }
            else if (k[0] == 2 && b != Terrain.Bio.FROZEN_PEAKS && b != Terrain.Bio.SNOWY_SLOPES && b != Terrain.Bio.JAGGED_PEAKS) { top = b == Terrain.Bio.VOLCANIC ? Mat.BASALT_ROCK : top; } }
        if (under) { double d = -h; top = d < 8 ? Mat.SAND : d < 200 ? (((x * 31 + z * 17) & 7) == 0 ? Mat.CLAY : Mat.SAND) : Mat.GRAVEL; fill = d < 200 ? Mat.SAND : Mat.GRAVEL; fd = 3; }   // shelf sand to 200 m, then gravel
        boolean wetTop = waterY > y, snow = !under && !wetTop && t < 0 && top != Mat.SNOW_BLOCK && top != Mat.STONE && top != Mat.MAGMA_CRUST, ice = (under || wetTop) && t < -2;
        Column out = new Column(false, f, y, h, b, top, fill, fd, Mat.STONE, snow, ice, t); out.lavaY = lavaY; out.waterY = waterY;
        if ((Roads.on || Towns.on) && w.body == 0 && !under && !Roads.building()) out = roads(w, f, x, z, s, out, !Double.isNaN(hw[1]) && hw[1] > h || wk == 3);
        if (Metro.on && w.body == 0 && !under && !Roads.building()) out = metro(w, f, x, z, s, out);
        if (Quarry.on && w.body == 0 && !under && !Roads.building()) { Quarry.Site q = Quarry.site(w); if (q != null && q.face() == f && Math.abs(x - q.x()) <= Quarry.A + 1 && Math.abs(z - q.z()) <= Quarry.B + Quarry.STAIR_OUT + 1) { int fy = Quarry.floor(q, x, z);   // 0.28.5 the origin quarry's pit (planet/Quarry)
                if (fy != Integer.MIN_VALUE && fy - 1 < out.surfaceY) { Column o = new Column(false, f, fy - 1, out.heightM, out.biome, Mat.STONE, Mat.STONE, 1, Mat.STONE, false, false, out.tempC); o.groundTop = fy; Quarry.COLS.increment(); out = o; } } }
        return out;
    }
    /** 0.27.0 a megacity's graded pads (planet/Metro): a tower lot or the gate plaza is cut and filled to its pad, paved in concrete. */
    public static final java.util.concurrent.atomic.LongAdder METRO_COLS = new java.util.concurrent.atomic.LongAdder();
    static Column metro(PlanetWorld w, int f, int x, int z, double[] s, Column c) { Metro m = w.metro(); if (m == null) return c; int[] a = m.at(f, x, z, s); if (a == null) return c;
        int gy = a[0] - 1; boolean park = a[1] == 5; Column o = new Column(false, f, gy, c.heightM, c.biome, park ? Mat.GRASS : Mat.ROAD_CONCRETE, park ? Mat.DIRT : Mat.GRAVEL, park ? 3 : 2, Mat.STONE, false, false, c.tempC);   /* 0.28.0 a park: lawn on soil */ o.groundTop = gy + 1; METRO_COLS.increment(); return o; }
    /** 0.23.0 the roads at this column (planet/Roads): a road surface (cut and fill to the road's height, side slopes 1 up in 2 out: CHOSEN),
     *  a bridge deck with rails and piers (every 30 m: CHOSEN), or a tunnel (16.5 ft = 5 m clear: 6 blocks of air, a concrete lining). */
    static Column roads(PlanetWorld w, int f, int x, int z, double[] s, Column c, boolean wet) {
        Roads rd = Roads.on ? w.roads() : null; Towns tw = Towns.on ? w.towns() : null; Towns.Hit th = tw == null ? null : tw.at(f, x + 0.5, z + 0.5, s); if (rd == null && th == null) return c;
        List<Roads.Hit> hs = rd == null ? List.of() : rd.hits(s); Roads.Hit g = null, deck = null; boolean nearI = false;
        // 0.25.2 in the slope band round a plat the ground is graded first, so the section roads that run on through the band lie on it
        if (th != null && th.kind == Towns.Kind.SLOPE) { double gz = Towns.groundFor(th, c.heightM); int gy = Double.isNaN(gz) ? c.surfaceY : (int) Math.floor(Terrain.toYd(gz));
            if (gy != c.surfaceY) { Column o = new Column(false, f, gy, c.heightM, c.biome, c.top, c.filler, c.fillerDepth, Mat.STONE, c.snowLayer, false, c.tempC); o.groundTop = gy + 1; if (gy < c.waterY) o.waterY = c.waterY; c = o; } }
        // 0.24.0 in a town the street grid carries the traffic: highways and collectors on the ground end at the plat (bridges and Interstates stay)
        if (th != null && !hs.isEmpty()) { List<Roads.Hit> keep = new ArrayList<>(hs.size()); for (Roads.Hit h : hs) if (h.kind == 1 || h.cls == Roads.Cls.INTERSTATE || h.cls == Roads.Cls.RAMP || (h.cls == Roads.Cls.RAIL && th.kind == Towns.Kind.SLOPE)) keep.add(h);   /* 0.29.1 the railway crosses the slope band to its station */ hs = keep; }
        // 0.28.8 and past their entry (a hole inside the plat, the plat's far side): the highway's ground part ends where its link begins
        if (!hs.isEmpty() && tw != null && !tw.CUT.isEmpty()) { List<Roads.Hit> keep = null; for (int i = 0; i < hs.size(); i++) { Roads.Hit h = hs.get(i); boolean drop = h.kind == 0 && h.cls != Roads.Cls.INTERSTATE && h.cls != Roads.Cls.RAMP && h.edge >= 0 && tw.cut(h, rd.route(h.edge).along[rd.route(h.edge).n() - 1]);
            if (drop && keep == null) { keep = new ArrayList<>(hs.subList(0, i)); } else if (!drop && keep != null) keep.add(h); } if (keep != null) hs = keep; }
        // 0.23.1 which road a column shows: a ramp running on its parent road (its lane, its dotted line) over the parent; any other road over a
        // free ramp (the crossroad at a ramp terminal); else the nearest
        Roads.Hit auxG = null, roadG = null, rampG = null, auxD = null; for (Roads.Hit h : hs) { if (h.cls == Roads.Cls.INTERSTATE && Math.abs(h.off) < h.half() + 8) nearI = true;
            boolean paints = Roads.surface(h) != null, aux = h.cls == Roads.Cls.RAMP && !Float.isNaN(h.clip);
            if (h.kind == 1) { if (aux && paints && auxD == null) auxD = h; if (Math.abs(h.off) < h.half() + 1 && (deck == null || h.z > deck.z + 0.5)) deck = h; }
            else { if (aux && paints && auxG == null) auxG = h; else if (paints && h.cls != Roads.Cls.RAMP && roadG == null) roadG = h; else if (paints && rampG == null) rampG = h; if (g == null) g = h; } }
        if (auxD != null && deck != null && Math.abs(auxD.z - deck.z) < 1) deck = auxD;
        g = auxG != null ? auxG : roadG != null ? roadG : rampG != null ? rampG : g;
        // 0.29.6 a city's freight yard beside the main track: the yard's rail hit rules its columns (measured: section roads took 1 in 30 of them)
        double[] yd = null; for (Roads.Hit h : hs) if (h.cls == Roads.Cls.RAIL && h.kind == 0) { double[] y = rd.yard(h); if (y != null) { yd = y; g = h; break; } }
        if (yd == null && rd != null && (th == null || th.kind == Towns.Kind.SLOPE) && (g == null || Math.abs(g.off) > g.half() + 30) && !nearI && deck == null) { Metro mt = Metro.on ? w.metroIfMade() : null; Roads.Hit sec = mt != null && mt.inCity(f, x, z, s) ? null : rd.section(f, x, z, s, c.heightM, wet); if (sec != null) g = sec; }   /* 0.27.8 no section roads in a megacity */
        // a road at nearly the deck's height under it is the same structure (a ramp beside its bridge): the deck alone; a road well under it keeps it
        if (g != null && deck != null && g.kind != 1 && Math.abs(deck.z - g.z) < 3) g = null;
        if (g != null && th != null && Math.abs(g.off) >= g.half()) g = null;   // only a road's own width, not its side slopes, over a town
        if (g == null && deck == null) { if (th == null) return c; Mat m = Towns.surface(th);
            // 0.24.1 the graded ground (cut and fill to the town's surface; 2:1 slopes in the band round it)
            double gz = Towns.groundFor(th, c.heightM); int gy = Double.isNaN(gz) ? c.surfaceY : (int) Math.floor(Terrain.toYd(gz)); if (m == null && gy == c.surfaceY) return c;
            Column o = new Column(false, f, gy, c.heightM, c.biome, m != null ? m : c.top, m != null ? Mat.GRAVEL : c.filler, m != null ? 2 : c.fillerDepth, Mat.STONE, m == null && c.snowLayer, false, c.tempC); o.groundTop = gy + 1; if (gy < c.waterY) o.waterY = c.waterY; Towns.GRADED.increment(); return o; }
        Column o = c; List<int[]> ovY = new ArrayList<>(); List<Mat> ovM = new ArrayList<>();
        if (yd != null) { boolean[] rl = new boolean[1]; Mat ym = Roads.yardTop(g, yd, rl); if (ym != null) { int ry = (int) Math.floor(Terrain.toYd(g.z));
            o = new Column(false, f, ry, c.heightM, c.biome, ym, Mat.GRAVEL, 2, Mat.STONE, false, false, c.tempC); o.groundTop = ry + 1; if (ry < c.waterY) o.waterY = c.waterY; if (rl[0]) { ovY.add(new int[]{ry + 1, ry + 1}); ovM.add(Mat.RAIL_TOP); } YARD_COLS.increment(); g = null; }
            else if (g.off > 0) { double dist = g.off - (Math.min(Roads.yardOuter((int) yd[2]), Math.min(yd[0], yd[1] - yd[0]) / Roads.YARD_FROG) + 3.5), zr = Terrain.toYd(g.z) + 1, gt = c.groundTop, ng = zr > gt ? Math.max(gt, zr - dist / 2) : Math.min(gt, zr + dist / 2);   /* the yard's side slope, 1 in 2 */
                if (Math.abs(ng - gt) >= 0.25) { int ny = (int) Math.floor(ng - 1e-9) - 1; o = new Column(false, f, ny, c.heightM, c.biome, c.top, c.filler, c.fillerDepth, c.under, c.snowLayer, c.ice, c.tempC); o.groundTop = ng; o.lavaY = c.lavaY; o.waterY = c.waterY; } g = null; } }
        if (g != null) { double a = Math.abs(g.off), half = g.half(); int ry = g.cls == Roads.Cls.LOCAL ? c.surfaceY : (int) Math.floor(Terrain.toYd(g.z));
            if (a < half) { Mat m = Roads.surface(g); if (m == null) m = g.kind == 2 ? Mat.ROAD_CONCRETE : Mat.GRASS;   // the Interstate's median: grass (concrete in a tunnel)
                boolean railTop = g.cls == Roads.Cls.RAIL && a >= 0.5 && a < 1.5;   // 0.29.0 the two rails, a block each side of the middle tie (standard gauge 4 ft 8.5 in drawn 2 m centre to centre: CHOSEN)
                if (g.kind == 2) { ovY.add(new int[]{ry, ry}); ovM.add(m); if (railTop) { ovY.add(new int[]{ry + 1, ry + 1}); ovM.add(Mat.RAIL_TOP); } ovY.add(new int[]{ry + 1, ry + 6}); ovM.add(Mat.AIR); if (ry + 7 <= c.surfaceY) { ovY.add(new int[]{ry + 7, ry + 7}); ovM.add(Mat.LINING); } Roads.TUNNEL_COLS.increment(); }
                else { o = new Column(false, f, ry, c.heightM, c.biome, m, Mat.GRAVEL, 2, Mat.STONE, false, false, c.tempC); o.groundTop = ry + 1; if (ry < c.waterY) o.waterY = c.waterY; if (railTop) { ovY.add(new int[]{ry + 1, ry + 1}); ovM.add(Mat.RAIL_TOP); }
                    (g.cls == Roads.Cls.LOCAL ? Roads.SECTION_COLS : Roads.COLS).increment(); } }
            else if (g.kind == 2 && a < half + 1) { ovY.add(new int[]{ry, ry + 7}); ovM.add(Mat.LINING); }   // the tunnel's walls
            else if (g.kind == 0 && g.cls != Roads.Cls.LOCAL) { double dist = a - half, zr = Terrain.toYd(g.z) + 1, gt = c.groundTop, ng = zr > gt ? Math.max(gt, zr - dist / 2) : Math.min(gt, zr + dist / 2);
                if (Math.abs(ng - gt) >= 0.25) { int ny = (int) Math.floor(ng - 1e-9) - 1; o = new Column(false, f, ny, c.heightM, c.biome, c.top, c.filler, c.fillerDepth, c.under, c.snowLayer, c.ice, c.tempC); o.groundTop = ng; o.lavaY = c.lavaY; o.waterY = c.waterY; } } }
        if (deck != null) { double a = Math.abs(deck.off), half = deck.half(); int dy = (int) Math.floor(Terrain.toYd(deck.z));
            if (a < half) { Mat m = Roads.surface(deck); ovY.add(new int[]{dy, dy}); ovM.add(m == null ? Mat.DECK : m); Roads.BRIDGE_COLS.increment(); if (deck.cls == Roads.Cls.RAIL && a >= 0.5 && a < 1.5) { ovY.add(new int[]{dy + 1, dy + 1}); ovM.add(Mat.RAIL_TOP); }
                if (Math.floorMod((long) Math.floor(deck.along), 30) < 2 && a < half - 1 && !(g != null && Math.abs(g.off) < g.half() + 2)) {   /* no pier on a road under the bridge or its 2 m clear strip */ int from = Math.min(o.surfaceY, Math.max(o.surfaceY, c.surfaceY)) + 1; if (from < dy) { ovY.add(new int[]{from, dy - 1}); ovM.add(Mat.DECK); } } }
            else { ovY.add(new int[]{dy, dy}); ovM.add(Mat.DECK); ovY.add(new int[]{dy + 1, dy + 1}); ovM.add(Mat.RAIL); } }
        if (!ovM.isEmpty()) { if (o == c) { o = new Column(false, f, c.surfaceY, c.heightM, c.biome, c.top, c.filler, c.fillerDepth, c.under, c.snowLayer && deck == null, c.ice, c.tempC); o.groundTop = c.groundTop; o.lavaY = c.lavaY; o.waterY = c.waterY; }
            o.ovY = new int[ovM.size() * 2]; o.ovM = ovM.toArray(new Mat[0]); for (int i = 0; i < ovM.size(); i++) { o.ovY[2 * i] = ovY.get(i)[0]; o.ovY[2 * i + 1] = ovY.get(i)[1]; } }
        return o; }
    /** 0.23.0 the road's layers over the plain column: {y from, y to} pairs and their materials (checked first). */
    public int[] ovY; public Mat[] ovM;
    public static final java.util.concurrent.atomic.LongAdder WET_STEP = new java.util.concurrent.atomic.LongAdder(), YARD_COLS = new java.util.concurrent.atomic.LongAdder();
    /** The highest block this column sets (the ground, the sea, a bridge deck's rail). */
    public int topY() { int t = dry() ? surfaceY : Math.max(surfaceY + 1, Terrain.SEA); t = Math.max(t, waterY); if (ovY != null) for (int i = 1; i < ovY.length; i += 2) t = Math.max(t, ovY[i]); return t; }
    /** 0.5.0: this column with the sea floor plan applied (planet/Seabed, from Hopeless Seabed): its kind from the water depth (real metres),
     *  the ground's slope (cosSlope: the up part of the ground normal) and the water's mean temperature; the ground's top and filler to match.
     *  Living things (seagrass, kelp, coral, marsh grass, ice-shore boulders) are placed by the chunk generator's decoration step. */
    public Column seabed(double cosSlope) {
        if (voidCol) return this; Seabed.Kind k = Seabed.classify(-heightM, cosSlope, tempC); if (k == Seabed.Kind.NONE) return this;
        Mat t, f; int fd = 3; boolean wet = surfaceY < Terrain.SEA;
        switch (k) {
            case SEAGRASS, SAND -> { t = Mat.SAND; f = Mat.SAND; }
            case BEACH -> { t = Mat.SAND; f = Mat.SAND; fd = 4; }
            case SHINGLE, ICE_SHORE -> { t = Mat.GRAVEL; f = Mat.GRAVEL; }
            case SALT_MARSH -> { t = wet ? Mat.MUD : Mat.GRASS; f = Mat.MUD; }   // high marsh: short cordgrass on marsh mud
            case LOW_MARSH -> { t = Mat.MUD; f = Mat.MUD; }
            case KELP, SUBTIDAL_ROCK -> { t = Mat.STONE; f = Mat.STONE; }
            case CORAL_CREST, CORAL_SLOPE, CORAL_DEEP, DEEP_OOZE -> { t = Mat.CALCITE; f = Mat.CALCITE; }   // reef framework and calcareous ooze: calcium carbonate
            default -> { return this; }
        }
        return new Column(false, face, surfaceY, heightM, biome, t, f, fd, under, snowLayer && !wet, ice, tempC, k); }
    /** The column with its sea floor plan, the slope from the true ground 1 m away (4 more height samples: for single columns; the chunk
     *  generator uses its own grid). */
    public static Column planned(PlanetWorld w, int f, int x, int z) {
        Column c = of(w, f, x, z); if (c.voidCol || c.dry() || c.heightM > Seabed.BEACH_TOP + 1) return c;
        return c.seabed(Seabed.cosSlope(of(w, f, x - 1, z).heightM, of(w, f, x + 1, z).heightM, of(w, f, x, z - 1).heightM, of(w, f, x, z + 1).heightM)); }
    /** The material at height yy: top, filler, stone, deepslate below -64 (Minecraft's own split is at 0; here sea level is 0), bedrock at the floor; water up to sea level. */
    public Mat at(int yy, int minY) {
        if (voidCol) return Mat.AIR;
        if (yy <= minY) return Mat.BEDROCK;
        if (ovM != null) for (int i = 0; i < ovM.length; i++) if (yy >= ovY[2 * i] && yy <= ovY[2 * i + 1]) return ovM[i];
        if (yy > surfaceY) { if (yy <= lavaY) return Mat.LAVA; if (yy <= waterY) return yy == waterY && ice ? Mat.ICE : Mat.WATER; if (biome.dry()) return Mat.AIR; if (yy == Terrain.SEA && ice) return Mat.ICE; if (yy <= Terrain.SEA) return Mat.WATER; if (yy == surfaceY + 1 && snowLayer) return Mat.SNOW_LAYER; return Mat.AIR; }
        if (yy == surfaceY) return top;
        if (yy > surfaceY - fillerDepth) return filler;
        return yy < -64 ? Mat.DEEPSLATE : Mat.STONE;
    }
    /** 0.15.0: no water at any height (an airless body). */
    public boolean dry() { return biome.dry(); }
    /** 0.18.0 the top of a molten world's lava sea (block y; none: MIN_VALUE). */
    public int lavaY = Integer.MIN_VALUE;
    /** 0.21.0 the top of a river's or lake's water over this column (block y; none: MIN_VALUE). */
    public int waterY = Integer.MIN_VALUE;
    /** Probes: columns made under a river, under a lake, on a salt flat. */
    public static final java.util.concurrent.atomic.LongAdder RIVER_COLS = new java.util.concurrent.atomic.LongAdder(), LAKE_COLS = new java.util.concurrent.atomic.LongAdder(), DRY_COLS = new java.util.concurrent.atomic.LongAdder();
    /** Lowest y that is not plain stone/deepslate/bedrock (everything below is filled in whole sections). */
    public int plainBelow() { if (voidCol) return Integer.MAX_VALUE; int p = surfaceY - fillerDepth + 1; if (ovY != null) for (int i = 0; i < ovY.length; i += 2) p = Math.min(p, ovY[i]); return p; }
}
