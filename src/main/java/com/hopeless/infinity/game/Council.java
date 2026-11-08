package com.hopeless.infinity.game;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * 0.28.5 (ported from Hopeless Deep 0.38.0, on Infinity's towns: game/Places): each town's council and its property tax. An election
 * every 28 days (US municipal councils mostly sit 2-4 years; a game month here), the polls open for the 4 days before it at the ballot
 * box in the town hall (planet/Buildings puts it there). Three candidates, each running on a property tax of 0.5, 1 or 2 % of a home's
 * value a week (Realty). Townspeople vote too (a share by the candidate's appeal, from the town's seed and the day); a player's ballot
 * counts as 50 of them. The winner's rate is the law the day after; the town news reports it. (Deep's speed and curfew laws are left
 * out: Infinity has no townspeople or vehicles yet.)
 */
public final class Council {
    private Council() {}
    public static final int TERM = 28, POLLS = 4, PLAYER_VOTE = 50;
    public static volatile long elections, ballots;
    static final double[] TAX = {0.005, 0.01, 0.02};
    static final String[] FIRST = {"Ruth", "Earl", "Darlene", "Wade", "Marcy", "Lonnie", "Gail", "Dwight", "Peggy", "Clyde", "Rhonda", "Vern", "Opal", "Floyd", "Nadine", "Hal"},
        LAST = {"Pruitt", "Stamper", "Hensley", "Crabtree", "Dials", "Kitts", "Riffe", "Salyers", "Jarrell", "Bowling", "Caudill", "Tackett", "Rowe", "Fannin", "Lykins", "Cobble"};
    static CompoundTag of(ServerLevel l, long town) { Econ e = Econ.get(l); CompoundTag c = e.councils.get(town);
        if (c == null) { c = new CompoundTag(); long day = Econ.day(l); c.putLong("next", day + TERM - Math.floorMod(day + town, TERM)); c.putInt("tax", 1); c.putString("mayor", name(town, 0)); cands(c, town, 0); e.councils.put(town, c); e.setDirty(); }
        return c; }
    static String name(long town, int k) { Random r = new Random(town * 977 + k); return FIRST[r.nextInt(FIRST.length)] + " " + LAST[r.nextInt(LAST.length)]; }
    static void cands(CompoundTag c, long town, int round) { ListTag l = new ListTag(); Random r = new Random(town * 31 + round);
        for (int i = 0; i < 3; i++) { CompoundTag k = new CompoundTag(); k.putString("name", name(town, round * 3 + i + 1)); k.putInt("tax", r.nextInt(3)); k.putInt("votes", 0); l.add(k); }
        c.put("cands", l); c.put("voted", new ListTag()); c.putInt("round", round); }
    static String platform(CompoundTag k) { return String.format("property tax %.1f %% a week", 100 * TAX[k.getInt("tax")]); }
    public static double taxRate(ServerLevel l, long town) { return TAX[of(l, town).getInt("tax")]; }
    static final Map<UUID, Integer> CURSOR = new HashMap<>();
    public static void use(ServerLevel l, ServerPlayer p, BlockPos box, boolean sneak) {
        Places.Place s = Places.at(l, box.getX(), box.getZ()); if (s == null) { p.sendSystemMessage(Component.literal("[Council] This ballot box stands in no town.")); return; }
        CompoundTag c = of(l, s.id()); long day = Econ.day(l), next = c.getLong("next"); ListTag ks = c.getList("cands", Tag.TAG_COMPOUND);
        if (next - day > POLLS) { p.sendSystemMessage(Component.literal(String.format("[Council of %s] Mayor %s. Law now: %s. Polls open on day %d (today %d).", s.name(), c.getString("mayor"), platform(c), next - POLLS, day))); return; }
        ListTag voted = c.getList("voted", Tag.TAG_STRING); String me = p.getUUID().toString();
        for (Tag t : voted) if (t.getAsString().equals(me)) { p.sendSystemMessage(Component.literal("[Council] You have voted. The count is on day " + next + ".")); return; }
        int i = CURSOR.getOrDefault(p.getUUID(), -1);
        if (sneak && i >= 0) { CompoundTag k = ks.getCompound(i); k.putInt("votes", k.getInt("votes") + PLAYER_VOTE); voted.add(net.minecraft.nbt.StringTag.valueOf(me)); c.put("voted", voted); Econ.get(l).setDirty(); ballots++; CURSOR.remove(p.getUUID());
            p.sendSystemMessage(Component.literal("[Council] Your vote for " + k.getString("name") + " is in the box.")); Awards.award(p, "voter"); return; }
        i = (i + 1) % ks.size(); CURSOR.put(p.getUUID(), i); CompoundTag k = ks.getCompound(i);
        p.sendSystemMessage(Component.literal(String.format("[Ballot %d/3] %s: %s  (sneak and use to vote)", i + 1, k.getString("name"), platform(k)))); }
    public static void daily(ServerLevel l, long day) { Econ e = Econ.get(l);
        for (Map.Entry<Long, CompoundTag> en : e.councils.entrySet()) { CompoundTag c = en.getValue(); if (day < c.getLong("next")) continue; long town = en.getKey();
            ListTag ks = c.getList("cands", Tag.TAG_COMPOUND); Random r = new Random(town * 131 + day); int best = 0, bv = -1;
            for (int i = 0; i < ks.size(); i++) { CompoundTag k = ks.getCompound(i); int v = k.getInt("votes") + 100 + r.nextInt(300) - 60 * k.getInt("tax"); if (v > bv) { bv = v; best = i; } }
            CompoundTag wn = ks.getCompound(best); c.putString("mayor", wn.getString("name")); c.putInt("tax", wn.getInt("tax")); c.putLong("next", day + TERM); cands(c, town, c.getInt("round") + 1); elections++;
            TownNews.add(l, town, wn.getString("name") + " wins the council election: " + platform(wn) + "."); }
        e.setDirty(); }
    public static String probe() { return "councils: elections " + elections + " ballots " + ballots; }
}
