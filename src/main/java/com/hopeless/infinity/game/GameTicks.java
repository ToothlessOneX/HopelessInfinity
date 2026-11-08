package com.hopeless.infinity.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** 0.28.5 the town game's clock (Deep ran these from its own tick): every second the jobs (arrivals, expiry), every 10 s the radios,
 *  every game minute (1,200 ticks) the outages, and each new game day the rents, taxes and elections - on every level of the planet. */
public final class GameTicks {
    private GameTicks() {}
    public static volatile long seconds, days;
    public static void tick(MinecraftServer s) { long t = s.overworld().getGameTime();
        for (ServerLevel l : s.getAllLevels()) { if (com.hopeless.infinity.HopelessInfinity.face(l) < 0) continue;
            if (t % 20 == 0) Jobs.tick(l); if (t % 200 == 0) Radio.tick(l); if (t % 1200 == 0) Outage.minute(l); }
        if (t % 20 == 0) { seconds++; ServerLevel o = s.overworld(); Econ e = Econ.get(o); long day = Econ.day(o); if (e.lastDay < 0) { e.lastDay = day; e.setDirty(); }
            if (day > e.lastDay) { e.lastDay = day; e.setDirty(); days++; for (ServerLevel l : s.getAllLevels()) { if (com.hopeless.infinity.HopelessInfinity.face(l) < 0) continue; Realty.daily(l, day); Council.daily(l, day); break; } } } }
    public static String probe() { return "town game clock: " + seconds + " s, " + days + " days turned; " + Jobs.probe() + "; " + Realty.probe() + "; " + Council.probe() + "; " + Outage.probe() + "; " + Radio.probe() + "; " + Atlas.probe(); }
}
