package com.hopeless.infinity.game;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Random;

/**
 * 0.28.5 (ported from Hopeless Deep 0.38.0, on Infinity's towns: game/Places): power and water outages. While a player is in a town, each
 * game minute there is a 1 in 150 chance of a fault (about one a game day in eight): a power fault at the town's substation, or a water
 * main break at its water tower. A power fault takes the town off the grid (GridNet.live asks noPower: lamps, relays, batteries react);
 * a water fault stops sinks, taps and hydrants. Repair: a wrench on the substation's transformer or a water tank in the town - paid $120,
 * or the repair job's pay (Jobs). A utility crew fixes what nobody repairs in two game days.
 */
public final class Outage {
    private Outage() {}
    public static volatile long powerFaults, waterFaults, repaired, crewFixed, waterAsks, powerAsks, wrenchAsks;
    public static boolean ON = true;
    public static boolean noPower(Level l, int x, int z) { powerAsks++; return l instanceof ServerLevel sl && faulted(sl, x, z, "power"); }
    public static boolean noWater(ServerLevel l, int x, int z) { waterAsks++; return faulted(l, x, z, "water"); }
    static boolean faulted(ServerLevel l, int x, int z, String what) { Econ e = Econ.get(l); if (e.faults.isEmpty()) return false;
        for (CompoundTag f : e.faults.values()) if (f.getBoolean(what) && Math.hypot(x - f.getInt("cx"), z - f.getInt("cz")) < f.getInt("r")) return true; return false; }
    public static void force(ServerLevel l, ServerPlayer p) { FORCE = p.getUUID(); minute(l); FORCE = null; }
    static java.util.UUID FORCE;
    /** Each game minute: new faults in towns with players; the crew for old ones. */
    public static void minute(ServerLevel l) {
        if (!ON) return; Econ e = Econ.get(l); long now = l.getGameTime(); Random r = new Random(now * 7919 + (FORCE != null ? System.nanoTime() : 0));
        for (ServerPlayer p : l.getServer().getPlayerList().getPlayers()) { if (p.level() != l) continue; if (FORCE != null && !FORCE.equals(p.getUUID())) continue;
            Places.Place s = Places.at(l, p.getBlockX(), p.getBlockZ()); if (s == null || s.plan() == null) continue;
            CompoundTag f = e.faults.get(s.id()); if (f != null || (FORCE == null && r.nextInt(150) != 0)) continue;
            boolean power = r.nextBoolean(); com.hopeless.infinity.planet.Towns.Use want = power ? com.hopeless.infinity.planet.Towns.Use.SUBSTATION : com.hopeless.infinity.planet.Towns.Use.WATER_TOWER; Places.Lot at = null;
            for (Places.Lot lt : Places.lots(s)) if (lt.use() == want) { at = lt; break; }
            f = new CompoundTag(); f.putBoolean(power ? "power" : "water", true); f.putLong("since", now); f.putInt("cx", (int) s.cx()); f.putInt("cz", (int) s.cz()); f.putInt("r", (int) s.radius() + 50);
            e.faults.put(s.id(), f); e.setDirty(); if (power) powerFaults++; else waterFaults++;
            TownNews.add(l, s.id(), (power ? "Power outage in " : "Water main break in ") + s.name() + ". Repair at " + (at != null ? Places.addr(at) : "the town centre") + " - a wrench on the " + (power ? "transformer" : "water tank") + "."); }
        for (var it = e.faults.entrySet().iterator(); it.hasNext(); ) { var en = it.next(); if (now - en.getValue().getLong("since") > 48000) { it.remove(); crewFixed++; e.setDirty(); } } }
    /** A wrench used on a block: true when it repaired a fault. */
    public static boolean wrench(ServerLevel l, ServerPlayer p, BlockPos pos) { wrenchAsks++;
        Econ e = Econ.get(l); if (e.faults.isEmpty()) return false; String id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(l.getBlockState(pos).getBlock()).getPath();
        boolean tr = id.equals("transformer"), pump = id.equals("pump") || id.equals("water_tank"); if (!tr && !pump) return false;
        for (Map.Entry<Long, CompoundTag> en : e.faults.entrySet()) { CompoundTag f = en.getValue();
            if (Math.hypot(pos.getX() - f.getInt("cx"), pos.getZ() - f.getInt("cz")) > f.getInt("r")) continue; if (!(tr && f.getBoolean("power")) && !(pump && f.getBoolean("water"))) continue;
            e.faults.remove(en.getKey()); e.setDirty(); repaired++;
            CompoundTag j = e.job.get(p.getUUID()); if (j != null && Jobs.type(j) == Jobs.T.REPAIR && j.getLong("town") == en.getKey()) Jobs.finish(l, p, j); else e.pay(p, 120, "for the repair");
            Awards.award(p, tr ? "lineman" : "plumber"); l.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.4f);
            p.sendSystemMessage(Component.literal("[Utility] " + (tr ? "Power" : "Water") + " is back on.")); return true; }
        return false; }
    public static String probe() { return "outages: power " + powerFaults + " water " + waterFaults + " repaired " + repaired + " by the crew " + crewFixed + "; asks water " + waterAsks + " power " + powerAsks + " wrench " + wrenchAsks; }
}
