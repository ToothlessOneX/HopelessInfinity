import com.hopeless.infinity.planet.*; import java.util.*;
/** BiomesTest: 0.19.1 the home planet's biome shares (Terrain.biome, Whittaker 1975 by temperature and precipitation) against Earth's. Saved tool. */
public class BiomesTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) { PlanetWorld w = new PlanetWorld(a.length > 0 ? Long.parseLong(a[0]) : 1); int N = 40000; double ga = Math.PI * (3 - Math.sqrt(5)); Map<String, Integer> bio = new TreeMap<>(); int land = 0;
    for (int i = 0; i < N; i++) { double y = 1 - 2 * (i + 0.5) / N, r = Math.sqrt(1 - y * y), th = ga * i; double[] s = {r * Math.cos(th), y, r * Math.sin(th)}; double h = w.terrain.heightM(s, w.cube.R); if (h <= 0) continue; land++; bio.merge(w.terrain.biome(s, h).key, 1, Integer::sum); }
    final int L = land; System.out.printf("land %.1f %% of the planet%n", 100.0 * land / N);
    bio.entrySet().stream().sorted((x, z) -> z.getValue() - x.getValue()).forEach(e -> System.out.printf("  %-26s %5.1f %% of the land%n", e.getKey(), 100.0 * e.getValue() / L));
    double desert = (bio.getOrDefault("desert", 0) + bio.getOrDefault("badlands", 0)) / (double) land, flower = bio.getOrDefault("flower_forest", 0) / (double) land, grass = (bio.getOrDefault("plains", 0) + bio.getOrDefault("savanna", 0)) / (double) land;
    int warm = 0, warmDes = 0; for (int i = 0; i < N; i++) { double y = 1 - 2 * (i + 0.5) / N, r = Math.sqrt(1 - y * y), th = ga * i; double[] s = {r * Math.cos(th), y, r * Math.sin(th)}; double h = w.terrain.elevationM(s); if (h <= 0 || w.terrain.temperatureC(s, h) < 5) continue; warm++; var b = w.terrain.biome(s, w.terrain.heightM(s, w.cube.R)); if (b == Terrain.Bio.DESERT || b == Terrain.Bio.BADLANDS) warmDes++; }
    System.out.printf("warm land (5 C or more) %.1f %% of the land (Earth ~70 %%)%n", 100.0 * warm / land);
    ok(Math.abs(warmDes / (double) warm - 0.28) < 0.05, String.format("deserts and badlands %.1f %% of the warm land, %.1f %% of all land (Earth: hyper-arid + arid 19.6 %% of all land, UNEP 1997 = 28 %% of its ~70 %% warm land)", 100.0 * warmDes / warm, 100 * desert));
    ok(flower < 0.04, String.format("flower forest %.1f %% of the land (a patchy few)", 100 * flower));
    ok(grass > 0.08, String.format("grassland and savanna %.1f %% of the land", 100 * grass));
    for (String k : new String[]{"desert", "jungle", "taiga", "forest", "plains", "snowy_plains"}) ok(bio.containsKey(k), "has " + k);
    System.out.println("BiomesTest: " + fail + " fail"); if (fail > 0) System.exit(1); } }
