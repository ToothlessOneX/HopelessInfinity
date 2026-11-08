package com.hopeless.infinity.client;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.Net;
import com.hopeless.infinity.planet.Terrain;
import com.hopeless.infinity.world.Space;
import com.hopeless.infinity.world.Suits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 0.12.0 space on the client: the server's state of where the player really is (world/Space), eased between ticks; the view point the
 * sky and the far terrain draw from (the point below and the height); the thrust keys (jump: the rocket belt while it has propellant, then
 * the MMU up; sneak: MMU down; the movement keys: the MMU along the look); the readout. Probes: states, inputs, errors.
 */
public final class SpaceClient {
    private SpaceClient() {}
    /** The view point: face, block x, z of the point below, and the height as a block y. */
    public record View(int body, int face, double x, double z, double camY) {}   // 0.16.0 body: 0 the planet, 1 its moon
    static volatile Net.SpaceState prev, cur; static volatile long curAt; public static volatile long states, inputs, errors; static float[] lastIn = new float[4];

    /** 0.17.0 time warp keys (Kerbal Space Program's: . up, , down, / stop). */
    public static final KeyMapping WARP_UP = new KeyMapping("key.hopelessinfinity.warp_up", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PERIOD, "key.categories.hopelessinfinity"),
        WARP_DOWN = new KeyMapping("key.hopelessinfinity.warp_down", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_COMMA, "key.categories.hopelessinfinity"),
        WARP_STOP = new KeyMapping("key.hopelessinfinity.warp_stop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SLASH, "key.categories.hopelessinfinity");
    static volatile int wAsked, wEff; static volatile String wWhy = ""; public static volatile long warpStates, warpKeys;
    static String bodyName(int id) { var w = ClientHooks.world; var b = w == null ? null : w.body(id); return b == null ? "body " + id : com.hopeless.infinity.planet.Bodies.name(b.ref); }
    public static void warp(int asked, int eff, String why) { wAsked = asked; wEff = eff; wWhy = why; warpStates++; }
    public static void init(IEventBus bus) {
        bus.addListener((RegisterKeyMappingsEvent e) -> { e.register(WARP_UP); e.register(WARP_DOWN); e.register(WARP_STOP); });
        MinecraftForge.EVENT_BUS.addListener(SpaceClient::tick);
        bus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "space", (gui, g, pt, w, h) -> {
            Net.SpaceState s = cur; if (s == null || Minecraft.getInstance().options.hideGui || !inSpace()) return;
            double r = Math.sqrt(s.px() * s.px() + s.py() * s.py() + s.pz() * s.pz()), climb = (s.px() * s.vx() + s.py() * s.vy() + s.pz() * s.vz()) / r, sp = Math.sqrt(s.vx() * s.vx() + s.vy() * s.vy() + s.vz() * s.vz());
            LocalPlayer p = Minecraft.getInstance().player; String mmu = p != null && Suits.fullSet(p) != null ? String.format("  MMU %.1f m/s", Suits.n2(p.getItemBySlot(EquipmentSlot.CHEST))) : "";
            g.drawString(Minecraft.getInstance().font, String.format("SPACE  height %.2f km  speed %.1f m/s  climb %.1f m/s  over the %s, face %d%s", s.alt() / 1000, sp, climb, bodyName(s.face() >> 3), s.face() & 7, mmu), w / 2 - 91, h - 59, 0xE0F0FF);
            int a = wAsked, f = wEff; String wl = String.format("WARP %,d x", com.hopeless.infinity.world.Warp.LEVELS[f]) + (a > f ? String.format("  (asked %,d x: held, %s)", com.hopeless.infinity.world.Warp.LEVELS[a], wWhy.isEmpty() ? "coasting only" : wWhy) : "") + "   [ . up   , down   / stop ]";
            g.drawString(Minecraft.getInstance().font, wl, w / 2 - 91, h - 70, f > 0 ? 0xFFD060 : 0xA0B0C0); }));
    }
    public static void state(Net.SpaceState s) { if (!s.in()) { prev = cur = null; return; } prev = cur; cur = s; curAt = System.nanoTime(); states++; }
    public static boolean inSpace() { var l = Minecraft.getInstance().level; return l != null && l.dimension() == Space.KEY && cur != null; }
    /** The view point now, eased from the last two server states (not across a face change). Null off space. */
    public static View view() {
        Net.SpaceState a = prev, b = cur; if (b == null || !inSpace()) return null;
        double f = Math.min(1, (System.nanoTime() - curAt) / 5e7);
        if (a == null || a.face() != b.face()) return new View(b.face() >> 3, b.face() & 7, b.x(), b.z(), Terrain.SEA + b.alt());
        return new View(b.face() >> 3, b.face() & 7, a.x() + (b.x() - a.x()) * f, a.z() + (b.z() - a.z()) * f, Terrain.SEA + a.alt() + (b.alt() - a.alt()) * f); }

    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return; Minecraft mc = Minecraft.getInstance(); LocalPlayer p = mc.player; if (p == null || !inSpace()) return;
        try { p.setDeltaMovement(Vec3.ZERO); p.fallDistance = 0;
            boolean free = mc.screen == null; var k = Suits.fullSet(p) == null ? null : p.getItemBySlot(EquipmentSlot.CHEST);
            boolean jump = free && mc.options.keyJump.isDown(), sneak = free && mc.options.keyShift.isDown(), belt = jump && k != null && Suits.belt(k) > 0;
            float fw = free ? p.input.forwardImpulse : 0, st = free ? p.input.leftImpulse : 0;
            double yr = Math.toRadians(p.getYRot()), pr = Math.toRadians(p.getXRot());
            double[] look = {-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)}, left = {Math.cos(yr), 0, Math.sin(yr)};
            double[] t = new double[3]; for (int i = 0; i < 3; i++) t[i] = look[i] * fw + left[i] * st; if (jump && !belt) t[1] += 1; if (sneak) t[1] -= 1;
            float[] in = {(float) t[0], (float) t[1], (float) t[2], belt ? 1 : 0};
            while (WARP_UP.consumeClick()) { Net.CH.sendToServer(new Net.WarpAsk(1)); warpKeys++; } while (WARP_DOWN.consumeClick()) { Net.CH.sendToServer(new Net.WarpAsk(-1)); warpKeys++; } while (WARP_STOP.consumeClick()) { Net.CH.sendToServer(new Net.WarpAsk(0)); warpKeys++; }
            if (!java.util.Arrays.equals(in, lastIn)) { Net.CH.sendToServer(new Net.SpaceInput(in[0], in[1], in[2], belt)); lastIn = in; inputs++; }
        } catch (Throwable t) { errors++; if (errors < 3) HopelessInfinity.LOG.error("[Hopeless Infinity] space (client) failed", t); } }
    public static String probe() { Net.SpaceState s = cur; return String.format("space (client): states %d, inputs %d, warp keys %d, warp states %d (asked %d x, running %d x%s), errors %d%s", states, inputs, warpKeys, warpStates, com.hopeless.infinity.world.Warp.LEVELS[wAsked], com.hopeless.infinity.world.Warp.LEVELS[wEff], wWhy.isEmpty() ? "" : ", " + wWhy, errors, s == null ? "" : String.format(", height %.0f m over the %s, face %d", s.alt(), (s.face() >> 3) == 1 ? "moon" : "planet", s.face() & 7)); }
}
