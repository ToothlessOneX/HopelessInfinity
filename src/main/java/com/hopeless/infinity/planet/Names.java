package com.hopeless.infinity.planet;

/** 0.22.0 names of places, from the place's id (the same every time). Hopeless Deep's naming (civ/Names, 0.21.0) with open-country word lists
 *  in the US manner (two parts, "Oakford", "Clearwater Springs"; GAME). */
public final class Names {
    private Names() {}
    static final String[] PRE = {"Ash", "Bear", "Birch", "Black", "Blue", "Bright", "Brook", "Cedar", "Clear", "Cold", "Copper", "Crown", "Deer", "Eagle", "Elm", "Fair",
        "Fox", "Glen", "Gold", "Grand", "Green", "Hart", "Hazel", "High", "Iron", "Lake", "Laurel", "Lime", "Long", "Maple", "Marsh", "Mill", "Moss", "North", "Oak", "Pine",
        "Pleasant", "Red", "Rich", "River", "Rock", "Rose", "Salt", "Sand", "Silver", "Spring", "Stone", "Sugar", "Summer", "Sun", "Walnut", "West", "White", "Willow", "Wolf", "Wood"};
    static final String[] SUF = {"ford", "field", "ton", "ville", "burg", "dale", "wood", "port", "view", "brook", "land", "mont", "well", "haven", "ridge", "stead", "bridge", "water"};
    static final String[] TAIL = {"", "", "", "", "", "", " Springs", " Falls", " Junction", " Heights", " Crossing", " Center", " Hills", " Landing"};
    static long mix(long a) { a ^= a >>> 33; a *= 0xff51afd7ed558ccdL; a ^= a >>> 33; a *= 0xc4ceb9fe1a85ec53L; a ^= a >>> 33; return a; }
    /** A place's name ("Oakford"; a city "Oakford City" one time in four). */
    public static String place(long id, boolean city) {
        long h = mix(id ^ 0x5DEECE66DL); String n = PRE[(int) Math.floorMod(h, PRE.length)] + SUF[(int) Math.floorMod(h >>> 16, SUF.length)];
        if (city) return Math.floorMod(h >>> 40, 4) == 0 ? n + " City" : n;
        return n + TAIL[(int) Math.floorMod(h >>> 32, TAIL.length)]; }
}
