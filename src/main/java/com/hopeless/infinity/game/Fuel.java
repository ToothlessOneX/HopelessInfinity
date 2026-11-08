package com.hopeless.infinity.game;

import com.hopeless.infinity.town.TownReg;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.38.0: fuel costs money and stations run dry. A bucket from a pump is 5 gal at $3.60 a gallon ($18, US average regular in 2026
 * is about $3.30-3.80), taken from the bank; each pump holds 8 buckets (TownReg.FuelPump FUEL) and fills only when a tanker comes
 * (Jobs FUEL). Vehicles burn fuel by their own figures (VehicleMath, VehicleKind mpg).
 */
public final class Fuel {
    private Fuel() {}
    public static final int BUCKET = 18;
    public static volatile long sold, refilled;   // probes
    /** Called by a pump before it fills a bucket: true when the player paid (or plays in creative). */
    public static boolean pay(ServerLevel l, Player p) { if (p.getAbilities().instabuild) return true; boolean ok = Econ.get(l).charge(p, BUCKET, "for 5 gal of fuel"); if (ok) sold++; return ok; }
    /** A tanker filled the station at w: every pump within 32 blocks to full. Returns the number of pumps. */
    public static int refill(ServerLevel l, BlockPos w) {
        int n = 0; for (BlockPos q : BlockPos.betweenClosed(w.offset(-32, -6, -32), w.offset(32, 6, 32))) { BlockState s = l.getBlockState(q);
            if (s.getBlock() instanceof TownReg.FuelPump) { l.setBlock(q, s.setValue(TownReg.FuelPump.FUEL, 8), 3); n++; } }
        refilled += n; return n; }
    public static String probe() { return "fuel: buckets sold " + sold + " pumps refilled " + refilled; }
}
