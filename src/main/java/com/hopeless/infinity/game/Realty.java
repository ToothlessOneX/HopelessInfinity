package com.hopeless.infinity.game;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * 0.28.5 (ported from Hopeless Deep 0.38.0, on Infinity's towns: game/Places): homes for sale and for rent. One house lot in eight has a
 * For Sale sign in its front yard (planet/Buildings puts it there, from the same seed). Use the sign: the rent offer, then the price;
 * sneak and use: take the offer shown. Price: $40 a square block of lot (Infinity's lots: 50 x 165 ft), times the square root of its
 * floors (one here), over three, kept within $2,000-$12,000; rent a hundredth of the price a day, from the bank each morning; two days
 * unpaid and the lease ends. An owner pays property tax at the council's rate (Council); fourteen days unpaid and the bank forecloses.
 * A home is its owner's or tenant's respawn point, and only they may change blocks on its lot (allowed).
 */
public final class Realty {
    private Realty() {}
    public static volatile long sales, leases, rentPaid, taxPaid, evictions, foreclosures, blocked;   // probes
    static final Map<UUID, Integer> CURSOR = new HashMap<>(); static final Map<UUID, Long> CURSOR_AT = new HashMap<>();
    /** Is this lot for sale (decided by its seed, so worldgen and the game agree)? */
    public static boolean forSale(Places.Lot l) { return l.use() == com.hopeless.infinity.planet.Towns.Use.HOUSE && com.hopeless.infinity.planet.Towns.forSale(l.place().id(), l.bi(), l.bj(), l.row(), l.lot()); }
    public static long price(Places.Lot l) { double a = (com.hopeless.infinity.planet.Towns.LOT_RES) * (com.hopeless.infinity.planet.Towns.LOT_D); return Math.max(2000, Math.min(12000, Math.round(40.0 * a / 3 / 100) * 100)); }
    public static long rent(Places.Lot l) { return Math.max(20, price(l) / 100); }

    public static void use(ServerLevel l, ServerPlayer p, BlockPos sign, boolean sneak) {
        Places.Lot lt = Places.lotAt(l, sign.getX(), sign.getZ());
        if (lt == null) { p.sendSystemMessage(Component.literal("[Realty] This sign stands on no lot.")); return; }
        Econ e = Econ.get(l); long key = lt.key(); CompoundTag d = e.deeds.get(key);
        if (d != null) { boolean mine = d.getUUID("owner").equals(p.getUUID());
            p.sendSystemMessage(Component.literal("[Realty] " + (mine ? "Your " : "Taken: ") + (d.getInt("kind") == 0 ? "owned home" : "rented home") + (mine ? (d.getInt("kind") == 1 ? String.format(", paid to day %d, $%,d a day", d.getLong("paidTo"), rent(lt)) : String.format(", tax paid to day %d", d.getLong("paidTo"))) + " - sneak and use to give it up" : "")));
            if (mine && sneak) { e.deeds.remove(key); e.setDirty(); p.sendSystemMessage(Component.literal("[Realty] You gave the home up.")); }
            return; }
        int i = sign.asLong() == CURSOR_AT.getOrDefault(p.getUUID(), Long.MIN_VALUE) ? CURSOR.getOrDefault(p.getUUID(), -1) : -1;
        if (sneak && i >= 0) { boolean buy = i == 1; long cost = buy ? price(lt) : rent(lt);
            if (!e.charge(p, cost, buy ? "to buy the home" : "for the first day's rent")) return;
            CompoundTag n = new CompoundTag(); n.putUUID("owner", p.getUUID()); n.putInt("kind", buy ? 0 : 1); n.putLong("paidTo", Econ.day(l) + (buy ? 7 : 1)); n.putLong("value", price(lt));
            n.putString("addr", Places.addr(lt)); n.putLong("town", lt.place().id()); n.putInt("lx", lt.x()); n.putInt("lz", lt.z());
            e.deeds.put(key, n); e.setDirty(); CURSOR.remove(p.getUUID());
            BlockPos bed = spawnBeside(l, sign); if (bed != null) p.setRespawnPosition(l.dimension(), bed, 0, true, false);
            if (buy) { sales++; Awards.award(p, "homeowner"); } else { leases++; Awards.award(p, "renter"); }
            p.sendSystemMessage(Component.literal("[Realty] " + (buy ? "You own " : "You rent ") + Places.addr(lt) + ". It is your respawn point.")); return; }
        if (sneak) { p.sendSystemMessage(Component.literal("[Realty] Use the sign first to see its offer.")); return; }
        i = (i + 1) % 2; CURSOR.put(p.getUUID(), i); CURSOR_AT.put(p.getUUID(), sign.asLong());
        p.sendSystemMessage(Component.literal(String.format(i == 0 ? "[Realty] For rent: $%,d a day (%s)  - sneak and use to rent" : "[Realty] For sale: $%,d (%s) - sneak and use to buy", i == 0 ? rent(lt) : price(lt), Places.addr(lt)))); }
    static BlockPos spawnBeside(ServerLevel l, BlockPos sign) {
        for (int r = 1; r <= 3; r++) for (BlockPos q : BlockPos.betweenClosed(sign.offset(-r, -1, -r), sign.offset(r, 1, r))) {
            if (l.getBlockState(q).getCollisionShape(l, q).isEmpty() && l.getBlockState(q.above()).getCollisionShape(l, q.above()).isEmpty() && l.getBlockState(q.below()).isFaceSturdy(l, q.below(), net.minecraft.core.Direction.UP) && l.getFluidState(q).isEmpty()) return q.immutable(); }
        return null; }
    /** The deed covering block (x, z), or null. */
    static CompoundTag deedAt(ServerLevel l, int x, int z) { Econ e = Econ.get(l); if (e.deeds.isEmpty()) return null; Places.Lot lt = Places.lotAt(l, x, z); return lt == null ? null : e.deeds.get(lt.key()); }
    /** Does this player own or rent the home at (x, z)? */
    public static boolean ownsAt(ServerLevel l, UUID who, int x, int z) { CompoundTag d = deedAt(l, x, z); return d != null && d.getUUID("owner").equals(who); }
    /** May this player change blocks at (x, z)? Not on a home someone else owns or rents. */
    public static boolean allowed(ServerLevel l, UUID who, int x, int z) { CompoundTag d = deedAt(l, x, z); if (d != null && !d.getUUID("owner").equals(who)) { blocked++; return false; } return true; }
    /** Is the lot at (x, z) taken (owned or rented)? */
    public static boolean taken(ServerLevel l, int x, int z) { return deedAt(l, x, z) != null; }
    /** Each morning: rents, taxes, evictions, foreclosures. */
    public static void daily(ServerLevel l, long day) {
        Econ e = Econ.get(l);
        for (Iterator<Map.Entry<Long, CompoundTag>> it = e.deeds.entrySet().iterator(); it.hasNext(); ) { var en = it.next(); CompoundTag d = en.getValue(); UUID o = d.getUUID("owner");
            if (day < d.getLong("paidTo")) continue;
            boolean rent = d.getInt("kind") == 1; long town = d.getLong("town");
            long due = rent ? Math.max(20, d.getLong("value") / 100) : Math.round(d.getLong("value") * Council.taxRate(l, town));
            ServerPlayer p = l.getServer().getPlayerList().getPlayer(o);
            if (e.balance(o) >= due) { if (p != null) e.pay(p, -due, rent ? "rent" : "property tax"); else { e.bank.merge(o, -due, Long::sum); e.setDirty(); }
                d.putLong("paidTo", d.getLong("paidTo") + (rent ? 1 : 7)); if (rent) rentPaid += due; else taxPaid += due; continue; }
            long late = day - d.getLong("paidTo");
            if (late >= (rent ? 2 : 14)) { it.remove(); if (rent) evictions++; else foreclosures++;
                if (p != null) p.sendSystemMessage(Component.literal("[Realty] " + (rent ? "Your lease ended: rent unpaid - " : "The bank foreclosed: tax unpaid - ") + d.getString("addr"))); }
            else if (p != null) p.sendSystemMessage(Component.literal(String.format("[Realty] $%,d %s is late on %s", due, rent ? "rent" : "tax", d.getString("addr")))); }
        e.setDirty(); }
    public static String probe() { return "realty: sales " + sales + " leases " + leases + " rent $" + rentPaid + " tax $" + taxPaid + " evictions " + evictions + " foreclosures " + foreclosures + " blocked edits " + blocked; }
}
