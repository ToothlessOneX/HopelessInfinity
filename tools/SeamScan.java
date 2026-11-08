import com.hopeless.infinity.planet.*;
import java.awt.image.BufferedImage;
/** SeamScan seed tiles outDir: looks for seams in the terrain's own height field (Terrain.heightM, continuous) on inland tiles of face 0:
 *  a seam is a jump between two neighbouring columns far larger than the slope on either side (|dh_i - (dh_i-1 + dh_i+1)/2| > 1.5 m, both
 *  axes). Tiles: 256 x 256 blocks at random dry places (height over 20 m) over the whole face. Prints each tile's seam count and the
 *  worst ones with the height parts there; writes a shaded relief PNG of the worst tile with the seams in red. Saved tool. */
public class SeamScan { public static void main(String[] a) throws Exception {
  long seed = Long.parseLong(a[0]); int tiles = Integer.parseInt(a[1]); java.io.File out = new java.io.File(a[2]); out.mkdirs();
  PlanetWorld w = new PlanetWorld(seed); int F = w.cube.F, N = 256; java.util.Random r = new java.util.Random(11); double R = w.cube.R;
  int worstN = -1; int[] worstAt = null; double[] worstH = null; boolean[] worstS = null; int total = 0, done = 0; StringBuilder ex = new StringBuilder();
  while (done < tiles) { int x0 = (int) ((r.nextDouble() - 0.5) * F * 0.9), z0 = (int) ((r.nextDouble() - 0.5) * F * 0.9);
    double hc = w.terrain.heightM(w.cube.spherePoint(0, x0 + 0.5, z0 + 0.5), R); if (hc < 20) continue; done++;
    double[] h = new double[N * N]; for (int j = 0; j < N; j++) for (int i = 0; i < N; i++) h[i + N * j] = w.terrain.heightM(w.cube.spherePoint(0, x0 + i + 0.5, z0 + j + 0.5), R);
    boolean[] s = new boolean[N * N]; int n = 0;
    for (int j = 1; j < N - 2; j++) for (int i = 1; i < N - 2; i++) { int c = i + N * j;
      double dx0 = h[c] - h[c - 1], dx1 = h[c + 1] - h[c], dx2 = h[c + 2] - h[c + 1], ex1 = Math.abs(dx1 - (dx0 + dx2) / 2);
      double dz0 = h[c] - h[c - N], dz1 = h[c + N] - h[c], dz2 = h[c + 2 * N] - h[c + N], ez1 = Math.abs(dz1 - (dz0 + dz2) / 2);
      if (ex1 > 1.5 || ez1 > 1.5) { s[c] = true; n++; if (ex.length() < 3000) ex.append(String.format("  %d %d: h %.2f -> x+1 %.2f / z+1 %.2f (jump off the trend %.2f / %.2f m)%n", x0 + i, z0 + j, h[c], h[c + 1], h[c + N], ex1, ez1)); } }
    total += n; System.out.printf("tile at %d %d (height %.0f m): %d seam columns%n", x0, z0, hc, n);
    if (n > worstN) { worstN = n; worstAt = new int[]{x0, z0}; worstH = h; worstS = s; } }
  System.out.printf("SeamScan: %d tiles, %d seam columns in all%n%s", tiles, total, ex);
  BufferedImage img = new BufferedImage(N * 2, N * 2, BufferedImage.TYPE_INT_RGB); double lo = 1e9, hi = -1e9; for (double v : worstH) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
  for (int j = 1; j < N - 1; j++) for (int i = 1; i < N - 1; i++) { int c = i + N * j; double sx = worstH[c + 1] - worstH[c - 1], sz = worstH[c + N] - worstH[c - N]; double lit = Math.max(0, Math.min(1, 0.6 - 0.25 * (sx * 0.7 + sz * 0.7))); double t = (worstH[c] - lo) / Math.max(1, hi - lo);
    int g = (int) (255 * lit), rgb = worstS[c] ? 0xFF2020 : ((int) (g * (0.5 + 0.5 * t)) << 16) | (g << 8) | (int) (g * 0.8); for (int q = 0; q < 4; q++) img.setRGB(i * 2 + (q & 1), j * 2 + (q >> 1), rgb); }
  javax.imageio.ImageIO.write(img, "png", new java.io.File(out, "seams.png")); System.out.printf("worst tile %d %d (%d seam columns) -> seams.png%n", worstAt[0], worstAt[1], worstN);
}}
