package com.hopeless.infinity.game;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** 0.26.0 (ported from Hopeless Deep 0.38.0): achievements as advancements (data/hopelessinfinity/advancements/city/*.json, criterion
 *  "done", granted from code). */
public final class Awards {
    private Awards() {}
    public static volatile long granted, missing;   // probes
    public static void award(ServerPlayer p, String id) {
        if (p.getServer() == null) return;
        Advancement a = p.getServer().getAdvancements().getAdvancement(new ResourceLocation("hopelessinfinity", "city/" + id));
        if (a == null) { missing++; return; }
        if (!p.getAdvancements().getOrStartProgress(a).isDone()) { p.getAdvancements().award(a, "done"); granted++; } }
    public static String probe() { return "achievements granted " + granted + (missing > 0 ? " missing " + missing : ""); }
}
