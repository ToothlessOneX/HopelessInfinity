package com.hopeless.infinity.game;

import com.hopeless.infinity.planet.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.28.5 (ported from Hopeless Deep 0.38.0, on Infinity's towns: game/Places): the radio. Four stations: NEWS (the nearest town's news -
 * councils, outages - one line every 10 s), DISPATCH (the outages round you), WEATHER (the temperature and biome where you stand, from
 * the planet's own climate: planet/Column) and MUSIC (a record played to you when you tune in). Use the radio to tune to the next one;
 * it plays while you hold it.
 */
public final class Radio {
    private Radio() {}
    static final String[] STATION = {"WCSN 88.1 News", "Dispatch scanner", "Planet Weather 102.5", "KCSM 97.3 Music"};
    static final SoundEvent[] MUSIC = {SoundEvents.MUSIC_DISC_CAT, SoundEvents.MUSIC_DISC_BLOCKS, SoundEvents.MUSIC_DISC_CHIRP, SoundEvents.MUSIC_DISC_FAR, SoundEvents.MUSIC_DISC_MALL,
        SoundEvents.MUSIC_DISC_MELLOHI, SoundEvents.MUSIC_DISC_STAL, SoundEvents.MUSIC_DISC_STRAD, SoundEvents.MUSIC_DISC_WAIT, SoundEvents.MUSIC_DISC_WARD};
    public static volatile long tunes, lines;
    public static void tune(ServerLevel l, ServerPlayer p, ItemStack radio) { int st = (radio.getOrCreateTag().getInt("station") + 1) % STATION.length; radio.getTag().putInt("station", st); radio.getTag().putInt("line", 0); tunes++;
        p.displayClientMessage(Component.literal("Tuned to " + STATION[st]), true); Awards.award(p, "radio"); if (st == 3) l.playSound(null, p.blockPosition(), MUSIC[l.random.nextInt(MUSIC.length)], SoundSource.RECORDS, 1f, 1f); }
    /** Every 10 s: the next line of the station for each player holding a radio. */
    public static void tick(ServerLevel l) {
        for (ServerPlayer p : l.getServer().getPlayerList().getPlayers()) { if (p.level() != l) continue;
            ItemStack r = p.getMainHandItem().is(com.hopeless.infinity.town.PlayReg.RADIO.get()) ? p.getMainHandItem() : p.getOffhandItem().is(com.hopeless.infinity.town.PlayReg.RADIO.get()) ? p.getOffhandItem() : ItemStack.EMPTY;
            if (r.isEmpty()) continue; int st = r.getOrCreateTag().getInt("station"); if (st == 3) continue; List<String> ls = lines(l, p, st); if (ls.isEmpty()) continue;
            int i = r.getTag().getInt("line") % ls.size(); r.getTag().putInt("line", i + 1); lines++; p.displayClientMessage(Component.literal("[" + STATION[st] + "] " + ls.get(i)), true); } }
    static List<String> lines(ServerLevel l, ServerPlayer p, int st) {
        List<String> o = new ArrayList<>(); Places.Place s = Places.at(l, p.getBlockX(), p.getBlockZ()); if (s == null) { List<Places.Place> n = Places.near(l, p.getBlockX(), p.getBlockZ(), 3000); if (!n.isEmpty()) s = n.get(0); }
        switch (st) {
            case 0 -> { if (s != null) for (String t : TownNews.recent(s.id(), 8)) o.add(s.name() + ": " + t); if (o.isEmpty()) o.add("No news from here tonight."); }
            case 1 -> { for (var f : Econ.get(l).faults.values()) o.add((f.getBoolean("power") ? "Power out" : "Water main break") + " - " + Places.far(p.getX(), p.getZ(), f.getInt("cx"), f.getInt("cz"))); if (o.isEmpty()) o.add("All quiet on the scanner."); }
            case 2 -> { PlanetWorld w = PlanetWorld.current(); int f = com.hopeless.infinity.HopelessInfinity.face(l); if (w != null && f >= 0) { Column c = Column.of(w, f, p.getBlockX(), p.getBlockZ()); o.add(String.format("%.0f F (%.0f C) at ground level, %s.", c.tempC * 9 / 5 + 32, c.tempC, c.biome.name().toLowerCase().replace('_', ' '))); } }
            default -> {} }
        return o; }
    public static String probe() { return "radio: tunes " + tunes + " lines " + lines; }
}
