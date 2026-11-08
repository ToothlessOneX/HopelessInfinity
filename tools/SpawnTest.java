import com.hopeless.infinity.planet.*;
/** SpawnTest seed...: the world spawn each seed gets (0.4.1 coast search), measured from the mod's own Column plans: time, kind, biome,
 *  height above the sea, temperature, nearest water 3 m deep, and the large-scale ground 20 km inland and seaward. Saved tool. */
public class SpawnTest {
    public static void main(String[] a) { com.hopeless.infinity.planet.Roads.on = false; com.hopeless.infinity.planet.Towns.on = false; com.hopeless.infinity.planet.Metro.on = false;   /* 0.27.0 megacities are MetroTest's */   /* 0.23.0: roads are RoadsTest's; this test reads the terrain */
        int pass = 0;
        for (String sd : a) { long seed = Long.parseLong(sd); PlanetWorld w = new PlanetWorld(seed);
            long t0 = System.nanoTime(); int[] s = w.spawn(); long ms = (System.nanoTime() - t0) / 1000000;
            Column c = Column.of(w, 0, s[0], s[2]); int near = 999;
            for (int r = 1; r < 400 && near == 999; r++) for (int k = 0; k < 64; k++) { double t = 2 * Math.PI * k / 64; Column q = Column.of(w, 0, s[0] + (int) Math.round(r * Math.cos(t)), s[2] + (int) Math.round(r * Math.sin(t))); if (q.surfaceY <= Terrain.SEA - 3) { near = r; break; } }
            int deep = 0; for (int k = 0; k < 64; k++) { double t = 2 * Math.PI * k / 64; Column q = Column.of(w, 0, s[0] + (int) Math.round(300 * Math.cos(t)), s[2] + (int) Math.round(300 * Math.sin(t))); if (q.surfaceY <= Terrain.SEA - 10) deep++; }
            boolean ok = w.spawnKind.equals("coast") && c.surfaceY >= 1 && c.surfaceY <= 6 && near <= 320 && c.tempC >= 2; /* 0.7.0: 3 m of water within 320 blocks (a shaped beach) */ if (ok) pass++;
            System.out.printf("seed %d: %s in %d ms (coasts tried %d) at %d %d %d | %s, ground y %d, %.1f C | water 3 m deep %d blocks away | %d of 64 directions 10 m deep at 300 blocks | %s%n",
                seed, w.spawnKind, ms, w.coastTries, s[0], s[1], s[2], c.biome.key, c.surfaceY, c.tempC, near, deep, ok ? "PASS" : "FAIL");
        }
        System.out.println("SpawnTest: " + pass + " of " + a.length + " pass");
    }
}
