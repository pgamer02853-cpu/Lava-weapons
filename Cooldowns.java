package com.rizgamer.lavaweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.*;

public final class Cooldowns {
    private final LavaWeapons pl;
    private final Map<UUID, Map<String, Long>> map = new HashMap<>();

    Cooldowns(LavaWeapons pl) { this.pl = pl; }

    public long seconds(String key) { return pl.getConfig().getLong("cooldowns." + key, 10L); }

    public static String fmt(long sec) {
        if (sec >= 3600) return (sec / 3600) + "h " + ((sec % 3600) / 60) + "m";
        if (sec >= 60) return (sec / 60) + "m " + (sec % 60) + "s";
        return sec + "s";
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
        m.put(key, now + seconds(key) * 1000L);
        return true;
    }

    public void clear(UUID id) { map.remove(id); }
}
