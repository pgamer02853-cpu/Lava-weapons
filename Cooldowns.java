package com.rizgamer.lavaweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.*;

public final class Cooldowns {
    private final LavaWeapons pl;
    private final Map<UUID, Map<String, Long>> map = new HashMap<>();

    Cooldowns(LavaWeapons pl) { this.pl = pl; }

    public double seconds(String key) { return pl.getConfig().getDouble("cooldowns." + key, 10.0); }

    public static String fmt(long sec) {
        if (sec >= 3600) return (sec / 3600) + "h " + ((sec % 3600) / 60) + "m";
        if (sec >= 60) return (sec / 60) + "m " + (sec % 60) + "s";
        return sec + "s";
    }

    /** Seconds as text; short cooldowns keep their decimals (0.4s). */
    public static String fmtD(double sec) {
        if (sec < 1.0) return String.format(java.util.Locale.ROOT, "%.1fs", sec);
        return fmt((long) Math.round(sec));
    }

    public boolean tryUse(Player p, String key, String name) { return tryUse(p, key, name, true); }

    public boolean tryUse(Player p, String key, String name, boolean message) {
        long now = System.currentTimeMillis();
        Map<String, Long> m = map.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        Long until = m.get(key);
        if (until != null && until > now) {
            if (message) {
                long left = (until - now + 999) / 1000;
                p.sendActionBar(Component.text(name + " cooldown: " + fmt(left), NamedTextColor.RED));
            }
            return false;
        }
        m.put(key, now + (long) (seconds(key) * 1000.0));
        return true;
    }

    public void clear(UUID id) { map.remove(id); }
}
