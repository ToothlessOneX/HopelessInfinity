package com.hopeless.infinity.game;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/** 0.28.5 each town's news (Deep's TownLife ledger, its part the ported systems use): the last 16 items a town, for the radio's news
 *  station; each item also told to the players in or near the town. Kept for the session (Deep's ledger was kept the same way). */
public final class TownNews {
    private TownNews() {}
    static final Map<Long, Deque<String>> NEWS = new java.util.concurrent.ConcurrentHashMap<>(); public static volatile long items;
    public static void add(ServerLevel l, long town, String item) { Deque<String> d = NEWS.computeIfAbsent(town, k -> new ArrayDeque<>()); synchronized (d) { d.addFirst("day " + Econ.day(l) + ": " + item); while (d.size() > 16) d.removeLast(); } items++;
        for (ServerPlayer p : l.getServer().getPlayerList().getPlayers()) { if (p.level() != l) continue; Places.Place s = Places.at(l, p.getBlockX(), p.getBlockZ()); if (s != null && s.id() == town) p.sendSystemMessage(Component.literal("[News] " + item)); } }
    public static List<String> recent(long town, int n) { Deque<String> d = NEWS.get(town); if (d == null) return List.of(); synchronized (d) { List<String> o = new ArrayList<>(d); return o.size() > n ? o.subList(0, n) : o; } }
}
