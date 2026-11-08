package com.hopeless.infinity.life;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** 0.26.0: the air reading the ported thermometer and gas meter show (Hopeless Deep's Air, without Deep's cave gases). Temperature: the
 *  planet's own climate at that column (planet/Column.tempC from world/PlanetGenerator's face); elsewhere 20 C and "unknown". */
public final class Air {
    private Air() {}
    public static volatile long reads, planetReads;
    public static final class Reading { public double o2 = 20.9, ch4, co2 = 0.04, co, temp = 20; public int mine = -1; public boolean working = true; public String where = "unknown"; }
    public static Reading read(ServerLevel l, BlockPos p) { reads++; Reading r = new Reading();
        try { if (l.getChunkSource().getGenerator() instanceof com.hopeless.infinity.world.PlanetGenerator g) { com.hopeless.infinity.planet.PlanetWorld w = com.hopeless.infinity.planet.PlanetWorld.current();
                if (w != null && g.body > 0) w = w.body(g.body); if (w != null) { r.temp = com.hopeless.infinity.planet.Column.of(w, g.face, p.getX(), p.getZ()).tempC; r.where = "planet surface"; planetReads++; } } }
        catch (Throwable t) { }
        return r; }
}
