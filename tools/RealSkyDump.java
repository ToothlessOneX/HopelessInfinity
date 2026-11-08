import com.hopeless.infinity.planet.*; import com.hopeless.infinity.sky.*; import com.hopeless.infinity.world.Clock;
/** RealSkyDump seed outDir [x z]: the 0.8.0 real sky's uniforms (SkyRenderer.draw, from sky/Ephemeris and the server Clock's dayTime) at
 *  moments of one real day at the spawn: noon, afternoon, sunset, dusk, midnight, dawn (found from the star's real altitude), plus the
 *  moon's highest moment at night, and the baked star field, for tools/sky_render.js. Also prints Minecraft's dayTime the Clock sets at
 *  each moment. Saved tool. */
public class RealSkyDump { public static void main(String[] a) throws Exception {
  long seed = Long.parseLong(a[0]); java.io.File out = new java.io.File(a[1]); out.mkdirs(); PlanetWorld w = new PlanetWorld(seed); int[] sp = w.spawn();
  double x = a.length > 3 ? Double.parseDouble(a[2]) : sp[0], z = a.length > 3 ? Double.parseDouble(a[3]) : sp[2]; SkyModel m = new SkyModel(w); Ephemeris e = new Ephemeris(w);
  float[] t = m.bake(); java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(t.length * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN); for (float f : t) bb.putFloat(f * 1e6f);
  java.nio.file.Files.write(new java.io.File(out, "stars.bin").toPath(), bb.array()); System.out.println(m.bakeNote);
  double day = e.solarDay(), t0 = 3600; double noon = 0, best = -9; for (double s = t0; s < t0 + day; s += 30) { double y = e.at(0, x, z, s).sunAlt; if (y > best) { best = y; noon = s; } }
  double set = noon; while (e.at(0, x, z, set).sunAlt > 0) set += 30; double rise = noon; while (e.at(0, x, z, rise).sunAlt > 0) rise -= 30;
  double dusk = set; while (e.at(0, x, z, dusk).sunAlt > Math.toRadians(-8) && dusk < set + day / 3) dusk += 30;
  double mid = noon + day / 2, moonT = mid, mb = -9; for (double s = set; s < rise + day; s += 120) { Ephemeris.At q = e.at(0, x, z, s); if (q.sunAlt < Math.toRadians(-12)) for (var b : q.bodies) if (b.kind() == 3 && b.dir()[1] > mb) { mb = b.dir()[1]; moonT = s; } }
  // a few nights on the moon may be fuller: search 30 days for the brightest moon at night, highest
  double bestF = -1; for (double s = t0; s < t0 + 30 * day; s += 600) { Ephemeris.At q = e.at(0, x, z, s); if (q.sunAlt > Math.toRadians(-12)) continue; for (var b : q.bodies) if (b.kind() == 3 && b.dir()[1] > 0.3) { double lit = (1 - (b.dir()[0] * b.sunDir()[0] + b.dir()[1] * b.sunDir()[1] + b.dir()[2] * b.sunDir()[2])) / 2; if (lit * b.dir()[1] > bestF) { bestF = lit * b.dir()[1]; moonT = s; } } }
  Object[][] mo = {{"noon", noon}, {"afternoon", (noon + set) / 2}, {"sunset", set - 300}, {"dusk", dusk}, {"midnight", mid}, {"dawn", rise + day - 600}, {"moon_night", moonT}};
  double[] air = SkyShade.air(m); StringBuilder sb = new StringBuilder("{\"air\":[" + air[0] + "," + air[1] + "," + air[2] + "," + air[3] + "],\"limb\":0.6,");
  double yl = SkyShade.lum(m.sunRgb[0], m.sunRgb[1], m.sunRgb[2]); sb.append(String.format("\"sunRgb\":[%f,%f,%f],\"angR\":%.8f,\"alt\":%.1f,\"moments\":[", m.sunRgb[0] / yl, m.sunRgb[1] / yl, m.sunRgb[2] / yl, 0.0, 3.0));
  long prevDT = 0;
  for (int i = 0; i < mo.length; i++) { double tt = (Double) mo[i][1]; Ephemeris.At at = e.at(0, x, z, tt); double ex = SkyShade.exposure(m, at.sun, 3); double[][] ax = at.starAxes();
    long dt = Clock.dayTime(e, 0, x, z, tt, prevDT); prevDT = dt;
    java.util.List<Ephemeris.Body> disks = new java.util.ArrayList<>(), pts = new java.util.ArrayList<>(); for (var b : at.bodies) (b.angR() > 3e-4 ? disks : pts).add(b); disks.sort((p, q) -> Double.compare(p.distKm(), q.distKm()));
    sb.append(String.format("{\"name\":\"%s\",\"angR\":%.8f,\"ecl\":%.4f,\"sun\":[%f,%f,%f],\"exp\":%.6g,\"pole\":[%f,%f,%f,0],\"noon\":[%f,%f,%f],\"west\":[%f,%f,%f],\"moon\":[", mo[i][0], at.sunAngR, at.sunVisible, at.sun[0], at.sun[1], at.sun[2], ex, ax[2][0], ax[2][1], ax[2][2], ax[0][0], ax[0][1], ax[0][2], ax[1][0], ax[1][1], ax[1][2]));
    StringBuilder l = new StringBuilder(), s2 = new StringBuilder();
    for (int k = 0; k < 8; k++) { if (k < disks.size()) { var b = disks.get(k); sb.append(String.format("%f,%f,%f,%.8f", b.dir()[0], b.dir()[1], b.dir()[2], b.angR())); l.append(String.format("%f,%f,%d,%d", b.light(), b.albedo(), b.kind(), b.seed() % 97)); s2.append(String.format("%f,%f,%f,0", b.sunDir()[0], b.sunDir()[1], b.sunDir()[2])); }
      else { sb.append("0,0,0,0"); l.append("0,0,0,0"); s2.append("0,0,0,0"); } String c = k < 7 ? "," : ""; sb.append(c); l.append(c); s2.append(c); }
    sb.append("],\"moonL\":[").append(l).append("],\"moonS\":[").append(s2).append("],\"planet\":[");
    for (int k = 0; k < 12; k++) { if (k < pts.size()) { var b = pts.get(k); sb.append(String.format("%f,%f,%f,%.6g", b.dir()[0], b.dir()[1], b.dir()[2], b.flux())); } else sb.append("0,0,0,0"); sb.append(k < 11 ? "," : ""); }
    double moonEl = -99; for (var b : at.bodies) if (b.kind() == 3) moonEl = Math.toDegrees(Math.asin(b.dir()[1]));
    sb.append(String.format("],\"sunElevDeg\":%.2f,\"moonElevDeg\":%.2f,\"dayTime\":%d}", Math.toDegrees(at.sunAlt), moonEl, dt)).append(i < mo.length - 1 ? "," : "");
    System.out.printf("%-10s t %8.0f s: star altitude %6.2f deg, %d disks, Minecraft dayTime %6d (in day %5d)%n", mo[i][0], tt, Math.toDegrees(at.sunAlt), disks.size(), dt, Math.floorMod(dt, 24000L)); }
  sb.append("],\"parity\":{\"sun\":[0,1,0],\"pts\":[]}}"); java.nio.file.Files.writeString(new java.io.File(out, "sky.json").toPath(), sb.toString()); System.out.println("written");
}}
