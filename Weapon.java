package com.rizgamer.lavaweapons;

public enum Weapon {
    LAVA_BLADE("lava_blade", "Lava Blade", "NETHERITE_SWORD", 0xFF6A1A, true),
    LAVA_HAMMER("lava_hammer", "Lava Hammer", "MACE", 0xFF6A1A, true),
    LAVA_SPEAR("lava_spear", "Lava Spear", "NETHERITE_SPEAR", 0xFF6A1A, true),
    GOD_BLADE("god_blade", "God Blade", "NETHERITE_SWORD", 0xFFD34D, false),
    GOD_HAMMER("god_hammer", "God Hammer", "MACE", 0xFFD34D, false),
    TIME_CLOCK("time_clock", "Time Clock", "CLOCK", 0xFFC83D, false);

    public final String id, display, base;
    public final int color;
    public final boolean lava;

    Weapon(String id, String display, String base, int color, boolean lava) {
        this.id = id; this.display = display; this.base = base; this.color = color; this.lava = lava;
    }

    public boolean isGod() { return this == GOD_BLADE || this == GOD_HAMMER; }

    public static Weapon byId(String id) {
        if (id == null) return null;
        for (Weapon w : values()) if (w.id.equalsIgnoreCase(id)) return w;
        return null;
    }
}
