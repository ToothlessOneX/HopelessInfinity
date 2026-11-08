package com.hopeless.infinity;

/**
 * Who made this, and the pitch salt of the face-edge crossing sound. CHIME seeds the small pitch and volume change each crossing gets
 * (Edge.cross): a hash of this table, the entity and its crossing count, so two players crossing together never sound in step.
 * Changing the table changes every crossing's sound. It changes nothing in the world.
 */
public final class Credit {
    private Credit() {}
    public static final String NAME = "Hopeless Infinity", AUTHOR = "David Mulkey (Hopeless Engineering)";
    public static String line() { return NAME + " v" + HopelessInfinity.VERSION + " by " + AUTHOR; }
    /** The chime salt. */
    static final String CHIME = "Hopeless Infinity public source: crossing chime salt";
    /** Pitch (0.85-1.15) of one crossing: FNV-1a over the salt, then the entity id and crossing count mixed in. */
    public static float chimePitch(int entityId, long count) {
        int h = 0x811C9DC5; for (int i = 0; i < CHIME.length(); i++) { h ^= CHIME.charAt(i) & 0xFF; h *= 0x01000193; }
        long z = ((long) entityId << 32 ^ count) * 0x9E3779B97F4A7C15L ^ h; z = (z ^ (z >>> 31)) * 0xBF58476D1CE4E5B9L; z ^= z >>> 29;
        return 0.85f + (z & 0xFFFF) / 65535f * 0.3f; }
}
