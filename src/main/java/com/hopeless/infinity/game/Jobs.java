package com.hopeless.infinity.game;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * 0.28.5 (ported from Hopeless Deep 0.38.0, on Infinity's towns: game/Places): jobs with pay, taken at a job board (planet/Buildings
 * puts one at each post office). Infinity has no trucks yet (owner: no vehicles until he says), so Deep's delivery by box truck becomes:
 *   COURIER - pick up a parcel at a store in this town and carry it to a store in another town within 6 km (pay as Deep's delivery:
 *             $40 + $15 a km); the store is marked restocked when it arrives.
 *   GOODS   - an order for goods from the machines (flour, iron and copper plate, copper wire, circuit boards, steel): bring them to
 *             the board ($ a piece, Deep's prices).
 *   REPAIR  - only while the board's town has a power or water fault (Outage): repair it ($180).
 * Use a board: see the next offer; sneak: take it. With a job: see how it stands; sneak: give it up. Goods are handed in by using the
 * board with them in hand. A job runs out after two game days (48,000 ticks). Probes count offers, takes, finishes and fails.
 * (Deep's freight and tanker runs need its semis: left out until vehicles come.)
 */
public final class Jobs {
    private Jobs() {}
    public enum T { COURIER, GOODS, REPAIR }
    public static volatile long shown, taken, finished, failed, abandoned, goodsIn;
    static final Map<UUID, Integer> CURSOR = new HashMap<>(); static final Map<UUID, Long> CURSOR_AT = new HashMap<>(); static final Map<UUID, CompoundTag> CURSOR_OFFER = new HashMap<>();
    static final String[][] GOODS = {{"hopelessinfinity:flour", "4", "the bakery"}, {"hopelessinfinity:iron_plate", "9", "the machine shop"}, {"hopelessinfinity:copper_plate", "8", "the electricians"},
        {"hopelessinfinity:copper_wire", "6", "the power company"}, {"hopelessinfinity:circuit_board", "30", "the radio shop"}, {"hopelessinfinity:steel_ingot", "14", "the bridge works"}};
    static CompoundTag job(T t, long town, int[] from, String fromName, int[] to, String toName, long toLot, long pay, String item, int count, long expires) {
        CompoundTag c = new CompoundTag(); c.putString("t", t.name()); c.putLong("town", town); c.putIntArray("from", from); c.putString("fromN", fromName); c.putIntArray("to", to); c.putString("toN", toName);
        c.putLong("toLot", toLot); c.putLong("pay", pay); c.putString("item", item); c.putInt("n", count); c.putInt("stage", 0); c.putLong("exp", expires); return c; }
    static T type(CompoundTag j) { try { return T.valueOf(j.getString("t")); } catch (Exception ex) { return T.GOODS; } }
    static final Map<Long, List<CompoundTag>> OFFERS = new HashMap<>();
    public static List<CompoundTag> offers(ServerLevel l, BlockPos board) { long k = board.asLong() * 31 + Econ.day(l); List<CompoundTag> got = OFFERS.get(k); if (got != null) return got;
        List<CompoundTag> made = makeOffers(l, board); if (OFFERS.size() > 256) OFFERS.clear(); OFFERS.put(k, made); return made; }
    static List<CompoundTag> makeOffers(ServerLevel l, BlockPos board) {
        List<CompoundTag> out = new ArrayList<>(); Places.Place home = Places.at(l, board.getX(), board.getZ()); if (home == null) return out;
        long day = Econ.day(l), exp = l.getGameTime() + 48000; Random r = new Random(board.asLong() * 31 + day);
        List<Places.Lot> picks = new ArrayList<>(); for (Places.Lot lt : Places.lots(home)) if (lt.use() == com.hopeless.infinity.planet.Towns.Use.STORE) picks.add(lt);
        List<Places.Lot> drops = new ArrayList<>(); for (Places.Place p : Places.near(l, board.getX(), board.getZ(), 6000)) { if (p.id() == home.id() || p.plan() == null) continue; for (Places.Lot lt : Places.lots(p)) if (lt.use() == com.hopeless.infinity.planet.Towns.Use.STORE) drops.add(lt); if (drops.size() > 400) break; }
        for (int k = 0; k < 3 && !picks.isEmpty() && !drops.isEmpty(); k++) { Places.Lot a = picks.get(r.nextInt(picks.size())), b = drops.get(r.nextInt(drops.size())); double km = Math.hypot(a.x() - b.x(), a.z() - b.z()) / 1000;
            out.add(job(T.COURIER, home.id(), new int[]{a.x(), board.getY(), a.z()}, Places.addr(a), new int[]{b.x(), board.getY(), b.z()}, Places.addr(b), b.key(), Math.round(40 + 15 * km), "", 0, exp)); }
        String[] g = GOODS[r.nextInt(GOODS.length)]; int n = 8 + r.nextInt(17);
        out.add(job(T.GOODS, home.id(), new int[]{board.getX(), board.getY(), board.getZ()}, home.name(), new int[]{board.getX(), board.getY(), board.getZ()}, g[2] + " in " + home.name(), 0, n * Long.parseLong(g[1]), g[0], n, exp));
        CompoundTag f = Econ.get(l).faults.get(home.id()); if (f != null) out.add(job(T.REPAIR, home.id(), new int[]{f.getInt("cx"), board.getY(), f.getInt("cz")}, home.name(), new int[]{f.getInt("cx"), board.getY(), f.getInt("cz")}, (f.getBoolean("power") ? "the substation" : "the water tower") + " in " + home.name(), 0, 180, "", 0, exp));
        return out; }
    static String describe(CompoundTag j) { T t = type(j); long pay = j.getLong("pay");
        return switch (t) {
            case COURIER -> String.format("Courier ($%,d): pick up a parcel at %s, carry it to %s", pay, j.getString("fromN"), j.getString("toN"));
            case GOODS -> String.format("Order ($%,d): %d %s for %s - bring them to this board", pay, j.getInt("n"), itemName(j.getString("item")), j.getString("toN"));
            case REPAIR -> String.format("Repair ($%,d): fix the fault at %s - use a wrench on its %s", pay, j.getString("toN"), j.getString("toN").startsWith("the sub") ? "transformer" : "water tank"); }; }
    static String itemName(String id) { Item it = ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(id)); return it == null ? id : new ItemStack(it).getHoverName().getString().toLowerCase(); }
    public static void use(ServerLevel l, ServerPlayer p, BlockPos board, boolean sneak) {
        Econ e = Econ.get(l); CompoundTag mine = e.job.get(p.getUUID());
        if (mine != null) { if (type(mine) == T.GOODS && handIn(l, p, mine)) return;
            if (sneak) { e.job.remove(p.getUUID()); e.setDirty(); abandoned++; p.sendSystemMessage(Component.literal("[Jobs] You gave up the job.")); return; }
            p.sendSystemMessage(Component.literal("[Jobs] Your job: " + describe(mine) + status(l, mine, p))); return; }
        List<CompoundTag> offers = offers(l, board); if (offers.isEmpty()) { p.sendSystemMessage(Component.literal("[Jobs] No work today.")); return; }
        int i = board.asLong() == CURSOR_AT.getOrDefault(p.getUUID(), Long.MIN_VALUE) ? CURSOR.getOrDefault(p.getUUID(), -1) : -1;
        CompoundTag seen = CURSOR_OFFER.get(p.getUUID()); int at = -1; if (seen != null) for (int q = 0; q < offers.size(); q++) if (offers.get(q) == seen) at = q;
        if (sneak && i >= 0 && at < 0) { CURSOR.remove(p.getUUID()); CURSOR_OFFER.remove(p.getUUID()); p.sendSystemMessage(Component.literal("[Jobs] That job was taken (or the day turned) - use the board to see what is left.")); return; }
        if (sneak && i >= 0) { CompoundTag j = offers.remove(at).copy(); CURSOR_OFFER.remove(p.getUUID()); e.job.put(p.getUUID(), j); e.setDirty(); taken++; CURSOR.remove(p.getUUID());
            p.sendSystemMessage(Component.literal("[Jobs] Taken: " + describe(j) + ". Two days to do it.")); return; }
        i = (i + 1) % offers.size(); CURSOR.put(p.getUUID(), i); CURSOR_AT.put(p.getUUID(), board.asLong()); CURSOR_OFFER.put(p.getUUID(), offers.get(i)); shown++;
        p.sendSystemMessage(Component.literal("[Jobs] " + (i + 1) + "/" + offers.size() + ": " + describe(offers.get(i)) + "  (sneak and use to take it)")); }
    static String status(ServerLevel l, CompoundTag j, ServerPlayer p) { T t = type(j); int[] to = j.getIntArray(j.getInt("stage") == 0 && t == T.COURIER ? "from" : "to"); String left = String.format(" - %.1f h left", (j.getLong("exp") - l.getGameTime()) / 1000.0);
        if (t == T.GOODS) return " - " + j.getInt("got") + " of " + j.getInt("n") + " in" + left; return " - next: " + Places.far(p.getX(), p.getZ(), to[0], to[2]) + left; }
    static boolean handIn(ServerLevel l, ServerPlayer p, CompoundTag j) { Item want = ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(j.getString("item"))); ItemStack hand = p.getMainHandItem();
        if (want == null || !hand.is(want)) return false; int need = j.getInt("n") - j.getInt("got"), give = Math.min(need, hand.getCount()); hand.shrink(give); j.putInt("got", j.getInt("got") + give); goodsIn += give; Econ.get(l).setDirty();
        if (j.getInt("got") >= j.getInt("n")) { finish(l, p, j); Awards.award(p, "goods"); } else p.sendSystemMessage(Component.literal("[Jobs] " + j.getInt("got") + " of " + j.getInt("n") + " handed in.")); return true; }
    /** Every second for each player with a job: picking up, arriving (within 12 m of the store's lot centre: CHOSEN, a lot is 25 ft wide), running out. */
    public static void tick(ServerLevel l) { Econ e = Econ.get(l);
        for (ServerPlayer p : l.getServer().getPlayerList().getPlayers()) { CompoundTag j = e.job.get(p.getUUID()); if (j == null || p.level() != l) continue;
            if (l.getGameTime() > j.getLong("exp")) { e.job.remove(p.getUUID()); e.setDirty(); failed++; p.sendSystemMessage(Component.literal("[Jobs] The job ran out: " + describe(j))); continue; }
            if (type(j) != T.COURIER) continue; int stage = j.getInt("stage"); int[] w = j.getIntArray(stage == 0 ? "from" : "to"); if (Math.hypot(p.getX() - w[0] - 0.5, p.getZ() - w[2] - 0.5) > 12) continue;
            if (stage == 0) { j.putInt("stage", 1); e.setDirty(); p.sendSystemMessage(Component.literal("[Jobs] Parcel picked up. Now to " + j.getString("toN") + " - " + Places.far(p.getX(), p.getZ(), j.getIntArray("to")[0], j.getIntArray("to")[2]))); }
            else finish(l, p, j); } }
    static void finish(ServerLevel l, ServerPlayer p, CompoundTag j) { Econ e = Econ.get(l); e.job.remove(p.getUUID()); finished++; T t = type(j); Awards.award(p, "paycheck");
        if (t == T.COURIER) { e.delivered.put(j.getLong("toLot"), Econ.day(l)); int n = e.bump(p.getUUID(), "deliveries", 1); Awards.award(p, "delivery"); if (n >= 10) Awards.award(p, "trucker"); }
        e.pay(p, j.getLong("pay"), "for the job: " + t.name().toLowerCase()); e.setDirty(); }
    public static String probe() { return "jobs: offers shown " + shown + " taken " + taken + " finished " + finished + " failed " + failed + " given up " + abandoned + " goods in " + goodsIn; }
}
