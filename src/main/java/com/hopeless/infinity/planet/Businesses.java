package com.hopeless.infinity.planet;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 0.27.4 WHO KEEPS EACH STORE (owner, 2026-11-04: "every business needs a name. Some businesses are chains and others are small family owned
 * businesses"; the 54 chain names approved 2026-11-04, each screened against US trademarks and businesses first). Pure Java (tools/
 * BusinessesTest). For each town's Main Street store lots, in a fixed order (so every chunk agrees):
 *  - CHAIN_SHARE = 40 % of them are chain stores (derived: US retail 2012, 877,000 stores of 523,000 firms (STATED: Smith & Ocampo, The
 *    Evolution of U.S. Retail Concentration, Census of Retail Trade microdata); every store past a firm's first belongs to a chain, so at
 *    least (877 - 523) / 877 = 40.4 % do);
 *  - a chain only where the town is big enough for it (minPop, CHOSEN per kind of business), at most cap() of one chain in a town;
 *  - 0.27.4 the rest are family businesses: a surname drawn in proportion to how many Americans bear it (STATED: U.S. Census Bureau, 2010
 *    Census surnames File B; the 43,284 names borne by 500 or more people, 90.6 % of the population, in data/hopelessinfinity/
 *    surnames_2010.txt.gz; only the name and count are used), on a kind of independent store (CHOSEN list after the ILSR's count of US
 *    independent stores by kind, 2007: grocery, clothing, florists, pharmacies, furniture, sporting goods, hardware, garden, appliances,
 *    shoes, books; with the Main Street trades: diner, bakery, barber, tailor, jeweler), in a shop-sign pattern (CHOSEN), black lettering.
 * Chains that keep no Main Street store (utilities, developers, freight, spacelines, media, tech) are listed for their towers, plants and
 * terminals (minPop 0, street false). Sign colours CHOSEN (dye colours a Minecraft sign's text can take).
 */
public final class Businesses {
    public static final double CHAIN_SHARE = 0.40; public static final int PER_TOWN = 25000;
    public record Chain(String name, String kind, int minPop, boolean street, String color, String[] lines) {}
    static Chain c(String name, String kind, int minPop, String color, String... lines) { return new Chain(name, kind, minPop, true, color, lines); }
    static Chain corp(String name, String kind) { return new Chain(name, kind, 0, false, "white", new String[]{name}); }
    public static final List<Chain> CHAINS = List.of(
        corp("Skyrung Development", "tower developer"), corp("Lattice & Crown Developments", "tower developer"), corp("Ironvale Construction", "construction"), corp("Obsidian Spire Holdings", "real estate holding"),
        corp("Kindling Fusion Works", "fusion power"), corp("Bellwether Gas & Power", "gas and electric utility"), corp("Sunsheaf Power", "solar power"), corp("Aquifer Works", "water utility"),
        corp("Cartway Freight", "freight"), corp("Packrail Freight", "rail freight"), corp("Farreach Lines", "spaceport line"), corp("Starhollow Spacelines", "spaceport line"),
        corp("Ninefold Media", "media"), corp("Observer Systems", "technology"), corp("Enderlight Labs", "research"), corp("Composter & Co.", "recycling"),
        c("Signalvane Wireless", "phone store", 5000, "light_blue", "SIGNALVANE", "WIRELESS"),
        c("Granite Ledger Bank", "bank", 500, "white", "GRANITE LEDGER", "BANK"), c("Emerald Hollow Savings", "savings bank", 2500, "lime", "EMERALD HOLLOW", "SAVINGS"), c("Lapis Federal", "bank", 10000, "blue", "LAPIS", "FEDERAL"),
        c("Bulwark Mutual", "insurance", 2500, "white", "BULWARK", "MUTUAL"),
        c("Harvest Lantern Grocers", "grocery", 1500, "orange", "HARVEST", "LANTERN", "GROCERS"), c("Briarberry Market", "grocery", 5000, "red", "BRIARBERRY", "MARKET"),
        c("Mooshburger", "burgers", 2500, "red", "MOOSHBURGER"), c("Featherfall Hot Chicken", "fried chicken", 2500, "orange", "FEATHERFALL", "HOT CHICKEN"), c("Furnace Fired Pizza", "pizza", 1500, "orange", "FURNACE FIRED", "PIZZA"),
        c("Cocoa Bean Coffee House", "coffee", 5000, "brown", "COCOA BEAN", "COFFEE HOUSE"), c("Sugarcane Donuts", "donuts", 2500, "pink", "SUGARCANE", "DONUTS"), c("Slab Sandwich Co.", "sandwiches", 2500, "yellow", "SLAB", "SANDWICH CO."),
        c("Sandstone Taqueria", "tacos", 2500, "yellow", "SANDSTONE", "TAQUERIA"), c("Copper Kettle Diner", "diner", 1000, "orange", "COPPER KETTLE", "DINER"), c("Lantern House Grill", "grill", 10000, "yellow", "LANTERN HOUSE", "GRILL"),
        c("Snow Golem Creamery", "ice cream", 2500, "light_blue", "SNOW GOLEM", "CREAMERY"),
        c("Bannerline Department Store", "department store", 25000, "white", "BANNERLINE", "DEPARTMENT", "STORE"), c("Penny Barrel", "dollar store", 300, "green", "PENNY BARREL"),
        c("Hopper Stop", "gas and convenience", 0, "lime", "HOPPER STOP"), c("Quarry Fuel", "gas station", 0, "yellow", "QUARRY FUEL"),
        c("Remedy Row Pharmacy", "pharmacy", 2500, "red", "REMEDY ROW", "PHARMACY"), c("Anvil Hardware", "hardware", 1000, "gray", "ANVIL", "HARDWARE"), c("Comparator Electronics", "electronics", 10000, "red", "COMPARATOR", "ELECTRONICS"),
        c("Loom & Thread", "clothing", 5000, "purple", "LOOM & THREAD"), c("Lectern Books", "books", 10000, "brown", "LECTERN", "BOOKS"), c("Plank & Peg Home Center", "home improvement", 10000, "orange", "PLANK & PEG", "HOME CENTER"),
        c("Oak & Spruce Furniture", "furniture", 10000, "brown", "OAK & SPRUCE", "FURNITURE"), c("Trident Sports", "sporting goods", 10000, "cyan", "TRIDENT", "SPORTS"), c("Note Block Music", "music", 10000, "magenta", "NOTE BLOCK", "MUSIC"),
        c("Wolfden Pet Supply", "pet supplies", 10000, "gray", "WOLFDEN", "PET SUPPLY"),
        c("Bedrock Inn", "hotel", 2500, "white", "BEDROCK INN"), c("Spawnpoint Suites", "hotel", 25000, "cyan", "SPAWNPOINT", "SUITES"), c("Glowstone Cinemas", "cinema", 10000, "yellow", "GLOWSTONE", "CINEMAS"),
        c("Ironclad Fitness", "gym", 5000, "red", "IRONCLAD", "FITNESS"), c("Hearthward Rentals", "property management", 5000, "white", "HEARTHWARD", "RENTALS"), c("Barrel & Chest Storage", "self storage", 2500, "orange", "BARREL & CHEST", "STORAGE"),
        c("Scaffold Staffing", "staffing", 10000, "white", "SCAFFOLD", "STAFFING"),
        c("Cobble & Sons Gate Sales", "gates", 2500, "light_blue", "COBBLE & SONS", "GATE SALES"));   // the company itself (owner: "the presence of this company needs to be found everywhere")
    public static final int SIGN_CHARS = 15;   // a Minecraft sign line shows about 15 capitals

    /** The business of each store lot of a plan: lot key -> chain, or null (a family business). Made once a plan, in a fixed order. */
    static final ConcurrentHashMap<Integer, Map<Long, Chain>> BY_SITE = new ConcurrentHashMap<>();
    public static Map<Long, Chain> of(Towns.Plan p) { return BY_SITE.computeIfAbsent(p.site.id, k -> assign(p)); }
    public static Chain at(Towns.Plan p, long lotKey) { return of(p).get(lotKey); }
    /** The store lots of a plan in a fixed order (blocks by key, then lots along the block). */
    public static List<Long> storeLots(Towns.Plan p) { List<Long> keys = new ArrayList<>(); for (var en : p.blocks.entrySet()) if (en.getValue() == Towns.Use.STORE) keys.add(en.getKey()); Collections.sort(keys);
        List<Long> out = new ArrayList<>(); for (long k : keys) { int i = (int) (k >> 32), j = (int) k; for (int l = 0; l < 12; l++) { long lk = Towns.lotKey(i, j, 0, l); Towns.Use u = p.lots.get(lk); if (u == null || u == Towns.Use.STORE) out.add(lk); } } return out; }
    static Map<Long, Chain> assign(Towns.Plan p) {
        Map<Long, Chain> m = new HashMap<>(); long pop = p.site.pop; List<Chain> can = new ArrayList<>(); for (Chain ch : CHAINS) if (ch.street && pop >= ch.minPop) can.add(ch);
        if (can.isEmpty()) return m; Map<String, Integer> used = new HashMap<>(); List<Long> lots = storeLots(p); int n = lots.size(), want = (int) Math.round(CHAIN_SHARE * n), k = 0;
        int cap = cap(pop, n, can.size());
        long sd = MoonTerrain.hash(p.site.id, 51, 0, 0, 0);
        // the chain stores spread along the street: the store lots shuffled by the town's seed, the first CHAIN_SHARE of them chains
        List<Long> order = new ArrayList<>(lots); Collections.shuffle(order, new Random(sd));
        for (int i = 0; i < n && k < want; i++) { long lk = order.get(i); Chain best = null;
            for (int t = 0; t < can.size() && best == null; t++) { Chain ch = can.get((int) Math.floorMod(sd + 7919L * (i + t), can.size())); if (used.getOrDefault(ch.name, 0) < cap) best = ch; }
            if (best == null) break; used.merge(best.name, 1, Integer::sum); m.put(lk, best); k++; }
        return m; }

    // ---- 0.27.4 the family businesses
    public static final String[] FAMILY_KINDS = {"GROCERY", "CLOTHING", "FLOWERS", "PHARMACY", "FURNITURE", "SPORTING GOODS", "HARDWARE", "GARDEN SHOP", "APPLIANCES", "SHOES", "BOOKS", "DINER", "BAKERY", "BARBER SHOP", "TAILOR", "JEWELERS"};
    static volatile String[] SUR; static volatile long[] CUM;   // surnames and their running count totals
    static synchronized void loadSurnames() { if (SUR != null) return; List<String> n = new ArrayList<>(); List<Long> c = new ArrayList<>();
        try (java.io.InputStream raw = open("/data/hopelessinfinity/surnames_2010.txt.gz"); java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(raw), java.nio.charset.StandardCharsets.UTF_8))) {
            String ln; long run = 0; while ((ln = r.readLine()) != null) { if (ln.startsWith("#") || ln.isBlank()) continue; int k = ln.indexOf(','); String nm = ln.substring(0, k); if (nm.length() + 2 > SIGN_CHARS) continue; run += Long.parseLong(ln.substring(k + 1).trim()); n.add(nm); c.add(run); } }
        catch (Exception e) { throw new IllegalStateException("surnames_2010.txt.gz", e); }
        String[] a = n.toArray(new String[0]); long[] cu = new long[c.size()]; for (int i = 0; i < cu.length; i++) cu[i] = c.get(i); CUM = cu; SUR = a; }
    /** The surname table: from the jar, or (tools run from the repo root) from the source folder. */
    static java.io.InputStream open(String res) throws java.io.IOException { java.io.InputStream in = Businesses.class.getResourceAsStream(res); return in != null ? in : new java.io.FileInputStream("src/main/resources" + res); }
    public static int surnames() { loadSurnames(); return SUR.length; }
    /** A surname drawn by its count (u in 0..1). */
    public static String surname(double u) { loadSurnames(); long t = (long) (u * CUM[CUM.length - 1]); int i = Arrays.binarySearch(CUM, t + 1); if (i < 0) i = -i - 1; return SUR[Math.min(i, SUR.length - 1)]; }
    /** A family store's sign lines (each at most SIGN_CHARS). */
    public static String[] familyLines(long seed) { String s = surname(MoonTerrain.u(seed, 0)), kind = FAMILY_KINDS[(int) Math.floorMod(seed >>> 7, FAMILY_KINDS.length)]; int pat = (int) Math.floorMod(seed >>> 21, 4);
        String a = switch (pat) { case 0 -> s + "'S"; case 1 -> s; case 2 -> s.length() + 6 <= SIGN_CHARS ? s + " BROS." : s + "'S"; default -> s.length() + 7 <= SIGN_CHARS ? s + " & SONS" : s + "'S"; };
        return new String[]{a, kind, "", ""}; }
    public static String familySign(long seed, String facing) { return "sign:dark_oak_wall_sign[facing=" + facing + "]|black|0|" + String.join("|", familyLines(seed)); }
    public static final java.util.concurrent.atomic.LongAdder FAMILY = new java.util.concurrent.atomic.LongAdder();

    /** 0.27.4 how many stores one chain may keep in a town: 1 + pop / 25,000 (CHOSEN, about McDonald's US density: STATED about 13,500
     *  restaurants for 335 million people, one per 25,000), raised (owner, 2026-11-04: "more stores per chain") until the chains that fit the
     *  town can fill CHAIN_SHARE of its store lots - the 39 street chains stand in for the thousands a real town draws on. */
    public static int cap(long pop, int lots, int chainsThatFit) { return Math.max(1 + (int) (pop / PER_TOWN), chainsThatFit == 0 ? 0 : (int) Math.ceil(CHAIN_SHARE * lots / chainsThatFit)); }
    /** The wall sign block string for a chain's store: "sign:<state>|<colour>|<glow>|<line 1>|...|<line 4>" (world/PlanetGenerator places it with its text). */
    public static String sign(Chain ch, String facing) { String[] l = new String[4]; for (int i = 0; i < 4; i++) l[i] = i < ch.lines.length ? ch.lines[i] : "";
        return "sign:dark_oak_wall_sign[facing=" + facing + "]|" + ch.color + "|1|" + String.join("|", l); }
}
