import com.hopeless.infinity.planet.*; import com.hopeless.infinity.sky.*;
/** SkyDump seed outDir [x z]: the uniforms the game's sky shader gets (SkyRenderer.draw) for a set of moments at the spawn (or x z on face 0),
 *  from the mod's own SkyModel / SkyShade, plus the baked star field (stars.bin, RGB float x 1e6, as uploaded) and a CPU reference of
 *  sk_scatter for 64 directions (tools/sky_parity.js). For tools/sky_render.js. Saved tool. */
public class SkyDump { public static void main(String[] a) throws Exception {
  long seed = Long.parseLong(a[0]); java.io.File out = new java.io.File(a[1]); out.mkdirs(); PlanetWorld w = new PlanetWorld(seed); int[] sp = w.spawn();
  double x = a.length > 3 ? Double.parseDouble(a[2]) : sp[0], z = a.length > 3 ? Double.parseDouble(a[3]) : sp[2]; SkyModel m = new SkyModel(w);
  float[] t = m.bake(); java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(t.length * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN); for (float f : t) bb.putFloat(f * 1e6f);
  java.nio.file.Files.write(new java.io.File(out, "stars.bin").toPath(), bb.array()); System.out.println(m.bakeNote);
  // moments: {name, dayTime ticks}; tod from Minecraft's DimensionType.timeOfDay (the same formula as the game)
  String[][] mo0 = {{"noon", "6000"}, {"afternoon", "10500"}, {"sunset", "12300"}, {"dusk", "13200"}, {"night_full_moon", "18000"}, {"night_new_moon", "114000"}, {"dawn", "23300"}};
  String[][] mo = mo0; if (a.length > 4 && a[4].equals("cycle")) { mo = new String[48][]; for (int i = 0; i < 48; i++) mo[i] = new String[]{String.format("c%02d", i), Long.toString(24000L * 2 + i * 500)}; }   // two days on: a day in 48 steps
  double[] air = SkyShade.air(m); StringBuilder sb = new StringBuilder("{\"air\":[" + air[0] + "," + air[1] + "," + air[2] + "," + air[3] + "],\"limb\":0.6,");
  double y = SkyShade.lum(m.sunRgb[0], m.sunRgb[1], m.sunRgb[2]); sb.append(String.format("\"sunRgb\":[%f,%f,%f],\"angR\":%.8f,\"alt\":%.1f,\"moments\":[", m.sunRgb[0] / y, m.sunRgb[1] / y, m.sunRgb[2] / y, m.angR, 3.0));
  for (int i = 0; i < mo.length; i++) { long dt = Long.parseLong(mo[i][1]); double d0 = (dt / 24000.0) - Math.floor(dt / 24000.0) - 0.25; d0 = d0 - Math.floor(d0); double e = 0.5 - Math.cos(d0 * Math.PI) / 2; double tod = (d0 * 2 + e) / 3;
    SkyModel.At at = m.at(0, x, z, dt / 24000.0, tod); double ex = SkyShade.exposure(m, at.sun, 3);
    sb.append(String.format("{\"name\":\"%s\",\"sun\":[%f,%f,%f],\"exp\":%.6g,\"pole\":[%f,%f,%f,%f],\"noon\":[%f,%f,%f],\"west\":[%f,%f,%f],\"moon\":[", mo[i][0], at.sun[0], at.sun[1], at.sun[2], ex, at.pole[0], at.pole[1], at.pole[2], at.lst, at.noon[0], at.noon[1], at.noon[2], at.west[0], at.west[1], at.west[2]));
    for (int k = 0; k < 4; k++) { if (k < at.moonDir.length) sb.append(String.format("%f,%f,%f,%.8f", at.moonDir[k][0], at.moonDir[k][1], at.moonDir[k][2], m.moons.get(k).angR())); else sb.append("0,0,0,0"); sb.append(k < 3 ? "," : ""); }
    sb.append("],\"planet\":["); for (int k = 0; k < 12; k++) { if (k < at.otherDir.length) sb.append(String.format("%f,%f,%f,%.6g", at.otherDir[k][0], at.otherDir[k][1], at.otherDir[k][2], at.otherFlux[k])); else sb.append("0,0,0,0"); sb.append(k < 11 ? "," : ""); }
    sb.append(String.format("],\"sunElevDeg\":%.2f,\"moonElevDeg\":%.2f}", Math.toDegrees(Math.asin(at.sun[1])), at.moonDir.length > 0 ? Math.toDegrees(Math.asin(at.moonDir[0][1])) : -99)).append(i < mo.length - 1 ? "," : ""); }
  // parity reference: sunset sun, 64 directions
  SkyModel.At at = m.at(0, x, z, 12300 / 24000.0, 0.27); sb.append("],\"parity\":{\"sun\":[" + at.sun[0] + "," + at.sun[1] + "," + at.sun[2] + "],\"pts\":["); java.util.Random r = new java.util.Random(4);
  for (int k = 0; k < 64; k++) { double ph = r.nextDouble() * 2 * Math.PI, el = Math.asin(r.nextDouble() * 1.2 - 0.2); double[] v = {Math.cos(el) * Math.cos(ph), Math.sin(el), Math.cos(el) * Math.sin(ph)}; double[] sc = SkyShade.scatter(v, at.sun, 3, air);
    sb.append(String.format("[%f,%f,%f,%.8g,%.8g,%.8g,%.6f,%.6f,%.6f]", v[0], v[1], v[2], sc[0], sc[1], sc[2], sc[3], sc[4], sc[5])).append(k < 63 ? "," : ""); }
  sb.append("]}}"); java.nio.file.Files.writeString(new java.io.File(out, "sky.json").toPath(), sb.toString()); System.out.println("written");
}}
