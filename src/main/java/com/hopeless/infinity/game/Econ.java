package com.hopeless.infinity.game;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * 0.38.0: the economy's books (world data hopelessinfinity_play, kept in the overworld). Money is US dollars in whole numbers, kept in a
 * bank account per player; an ATM turns it into emeralds and back ($20 an emerald, CityReg ATM). Also kept here: the deeds and leases
 * of homes (Realty), each player's job (Jobs), the town councils and their laws (Council), the power and water faults (Outage),
 * the gas stations' fuel deliveries and the landmarks and quests a player has done. Pure data and small helpers; the systems that
 * use it are in this package.
 */
public final class Econ extends SavedData {
    public static final int EMERALD = 20;                                       // dollars per emerald at an ATM
    public final Map<UUID, Long> bank = new HashMap<>();
    public final Map<UUID, CompoundTag> job = new HashMap<>();                 // the job a player has taken (Jobs.Job.save)
    public final Map<Long, CompoundTag> deeds = new HashMap<>();               // lot key -> {owner, kind (0 own, 1 rent), paidTo (day), value, lien}
    public final Map<Long, CompoundTag> councils = new HashMap<>();            // town id -> Council data
    public final Map<Long, CompoundTag> faults = new HashMap<>();              // town id -> {power, water, since, x, y, z}
    public final Map<UUID, Set<String>> done = new HashMap<>();                // landmarks visited, quest ids done, counters as "key=n"
    public final Map<UUID, CompoundTag> quest = new HashMap<>();
    public final Map<Long, Long> delivered = new HashMap<>();                  // lot key -> the day a delivery last reached it (restocks it: LootFill)
    public long lastDay = -1;
    public static volatile long paid, spent, payments;                         // probes

    public static Econ get(ServerLevel l) { return l.getServer().overworld().getDataStorage().computeIfAbsent(Econ::load, Econ::new, "hopelessinfinity_play"); }
    public static long day(ServerLevel l) { return l.getServer().overworld().getDayTime() / 24000L; }

    public long balance(UUID u) { return bank.getOrDefault(u, 0L); }
    /** Pays a player (negative takes); tells them in the chat. */
    public void pay(Player p, long dollars, String what) {
        bank.merge(p.getUUID(), dollars, Long::sum); setDirty(); payments++; if (dollars > 0) paid += dollars; else spent -= dollars;
        p.sendSystemMessage(Component.literal(String.format("[Bank] %s$%,d %s - balance $%,d", dollars >= 0 ? "+" : "-", Math.abs(dollars), what, balance(p.getUUID()))));
 }
    /** Takes the money if the account holds it. */
    public boolean charge(Player p, long dollars, String what) { if (balance(p.getUUID()) < dollars) { p.sendSystemMessage(Component.literal(String.format("[Bank] $%,d needed for %s - balance $%,d", dollars, what, balance(p.getUUID())))); return false; } pay(p, -dollars, what); return true; }
    public boolean has(UUID u, String k) { Set<String> s = done.get(u); return s != null && s.contains(k); }
    public boolean mark(UUID u, String k) { boolean n = done.computeIfAbsent(u, q -> new HashSet<>()).add(k); if (n) setDirty(); return n; }
    public int count(UUID u, String k) { Set<String> s = done.get(u); if (s == null) return 0; for (String e : s) if (e.startsWith(k + "=")) return Integer.parseInt(e.substring(k.length() + 1)); return 0; }
    public int bump(UUID u, String k, int by) { int n = count(u, k) + by; Set<String> s = done.computeIfAbsent(u, q -> new HashSet<>()); s.removeIf(e -> e.startsWith(k + "=")); s.add(k + "=" + n); setDirty(); return n; }

    static Econ load(CompoundTag t) {
        Econ e = new Econ(); e.lastDay = t.getLong("day");
        for (Tag x : t.getList("bank", Tag.TAG_COMPOUND)) { CompoundTag c = (CompoundTag) x; e.bank.put(c.getUUID("u"), c.getLong("v")); }
        for (Tag x : t.getList("job", Tag.TAG_COMPOUND)) { CompoundTag c = (CompoundTag) x; e.job.put(c.getUUID("u"), c.getCompound("v")); }
        for (Tag x : t.getList("quest", Tag.TAG_COMPOUND)) { CompoundTag c = (CompoundTag) x; e.quest.put(c.getUUID("u"), c.getCompound("v")); }
        for (Tag x : t.getList("deeds", Tag.TAG_COMPOUND)) { CompoundTag c = (CompoundTag) x; e.deeds.put(c.getLong("k"), c.getCompound("v")); }
        for (Tag x : t.getList("councils", Tag.TAG_COMPOUND)) { CompoundTag c = (CompoundTag) x; e.councils.put(c.getLong("k"), c.getCompound("v")); }
        for (Tag x : t.getList("faults", Tag.TAG_COMPOUND)) { CompoundTag c = (CompoundTag) x; e.faults.put(c.getLong("k"), c.getCompound("v")); }
        for (Tag x : t.getList("delivered", Tag.TAG_COMPOUND)) { CompoundTag c = (CompoundTag) x; e.delivered.put(c.getLong("k"), c.getLong("v")); }
        for (Tag x : t.getList("done", Tag.TAG_COMPOUND)) { CompoundTag c = (CompoundTag) x; Set<String> s = new HashSet<>(); for (Tag q : c.getList("v", Tag.TAG_STRING)) s.add(q.getAsString()); e.done.put(c.getUUID("u"), s); }
        return e; }
    @Override public CompoundTag save(CompoundTag t) {
        t.putLong("day", lastDay);
        ListTag b = new ListTag(); bank.forEach((u, v) -> { CompoundTag c = new CompoundTag(); c.putUUID("u", u); c.putLong("v", v); b.add(c); }); t.put("bank", b);
        ListTag j = new ListTag(); job.forEach((u, v) -> { CompoundTag c = new CompoundTag(); c.putUUID("u", u); c.put("v", v); j.add(c); }); t.put("job", j);
        ListTag q = new ListTag(); quest.forEach((u, v) -> { CompoundTag c = new CompoundTag(); c.putUUID("u", u); c.put("v", v); q.add(c); }); t.put("quest", q);
        for (String name : new String[]{"deeds", "councils", "faults"}) { Map<Long, CompoundTag> m = name.equals("deeds") ? deeds : name.equals("councils") ? councils : faults;
            ListTag l = new ListTag(); m.forEach((k, v) -> { CompoundTag c = new CompoundTag(); c.putLong("k", k); c.put("v", v); l.add(c); }); t.put(name, l); }
        ListTag dl = new ListTag(); delivered.forEach((k, v) -> { CompoundTag c = new CompoundTag(); c.putLong("k", k); c.putLong("v", v); dl.add(c); }); t.put("delivered", dl);
        ListTag d = new ListTag(); done.forEach((u, s) -> { CompoundTag c = new CompoundTag(); c.putUUID("u", u); ListTag l = new ListTag(); for (String x : s) l.add(net.minecraft.nbt.StringTag.valueOf(x)); c.put("v", l); d.add(c); }); t.put("done", d);
        return t; }
    public static String probe() { return "economy: payments " + payments + " paid $" + paid + " spent $" + spent; }
}
