package com.hopeless.infinity.game;

import com.hopeless.infinity.planet.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.28.5 (ported from Hopeless Deep 0.38.0, on Infinity's planet: game/Places): the atlas and printed town maps.
 * Atlas: use it and it becomes a written book for where you are: every town and city within 8 km (name, kind, people, distance and
 * heading), the megacities' great gates and the Cobble & Sons origin quarry when near (planet/Quarry).
 * Town map: use an empty map on a job board and the board prints a map of the 256 x 256 blocks round it, the game's own map drawn of
 * the open sky (Infinity's towns are under it, unlike Deep's caves, so the vanilla map shows them as they are), named for the town.
 */
public final class Atlas {
    private Atlas() {}
    public static volatile long atlases, maps;
    public static void write(ServerLevel l, ServerPlayer p, InteractionHand h) {
        int x = p.getBlockX(), z = p.getBlockZ(); List<String> lines = new ArrayList<>(); lines.add("ATLAS"); lines.add("Drawn at " + x + ", " + z + " on day " + Econ.day(l)); lines.add(""); lines.add("PLACES");
        List<Places.Place> ps = Places.near(l, x, z, 8000);
        for (Places.Place s : ps) lines.add(s.name() + " (" + s.site().kind.name().toLowerCase().replace('_', ' ') + String.format(", %,d people) ", s.site().pop) + Places.far(x, z, s.cx(), s.cz()));
        lines.add(""); lines.add("LANDMARKS");
        for (Places.Place s : ps) if (s.city() != null && s.site().kind == Sites.Kind.MEGACITY) { double[] g = s.city().xz(Metro.GATE_U, 0); lines.add("Great gate, " + s.name() + " " + Places.far(x, z, g[0], g[1])); }
        PlanetWorld w = PlanetWorld.current(); Quarry.Site q = w == null ? null : Quarry.site(w); if (q != null && q.face() == com.hopeless.infinity.HopelessInfinity.face(l) && Math.hypot(q.x() - x, q.z() - z) < 20000) lines.add("Cobble & Sons origin quarry " + Places.far(x, z, q.x(), q.z()));
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK); CompoundTag t = book.getOrCreateTag(); t.putString("title", "Atlas"); t.putString("author", "Planetary Survey Office");
        ListTag pages = new ListTag(); StringBuilder pg = new StringBuilder(); int n = 0;
        for (String ln : lines) { int rows = Math.max(1, (ln.length() + 18) / 19); if (n > 0 && (n + rows > 14 || pg.length() + ln.length() > 250)) { pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(pg.toString())))); pg.setLength(0); n = 0; } pg.append(ln).append('\n'); n += rows; }
        if (pg.length() > 0) pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(pg.toString())))); t.put("pages", pages);
        ItemStack held = p.getItemInHand(h); if (!p.getAbilities().instabuild) held.shrink(1); if (held.isEmpty()) p.setItemInHand(h, book); else if (!p.getInventory().add(book)) p.drop(book, false);
        atlases++; Awards.award(p, "atlas"); p.sendSystemMessage(Component.literal("[Atlas] Filled in for " + ps.size() + " places.")); }
    /** An empty map used on a job board: a filled map of the 256 x 256 blocks round the board (scale 1: 2 blocks a pixel), named for the town. */
    public static void printMap(ServerLevel l, ServerPlayer p, InteractionHand h, BlockPos board) {
        ItemStack map = MapItem.create(l, board.getX(), board.getZ(), (byte) 1, true, false); MapItem.renderBiomePreviewMap(l, map);
        Places.Place s = Places.at(l, board.getX(), board.getZ()); map.setHoverName(Component.literal("Map of " + (s != null ? s.name() : "here")));
        ItemStack held = p.getItemInHand(h); if (!p.getAbilities().instabuild) held.shrink(1); if (held.isEmpty()) p.setItemInHand(h, map); else if (!p.getInventory().add(map)) p.drop(map, false);
        maps++; p.sendSystemMessage(Component.literal("[Atlas] The board prints a map of " + (s != null ? s.name() : "here") + ".")); }
    public static String probe() { return "atlas: books " + atlases + " maps " + maps; }
}
