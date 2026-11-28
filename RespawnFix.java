package com.rizgamer.lavaweapons;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bed / respawn-anchor respawn wins over /setspawn (EssentialsSpawn).
 * We remember the vanilla result first (LOWEST) and put it back last (HIGHEST).
 */
public final class RespawnFix implements Listener {
    private final LavaWeapons pl;
    private final Map<UUID, Location> vanilla = new HashMap<>();

    RespawnFix(LavaWeapons pl) { this.pl = pl; }

    @EventHandler(priority = EventPriority.LOWEST)
    public void first(PlayerRespawnEvent e) {
        if (!pl.getConfig().getBoolean("respawn-fix", true)) return;
        UUID id = e.getPlayer().getUniqueId();
        if (e.isBedSpawn() || e.isAnchorSpawn()) vanilla.put(id, e.getRespawnLocation().clone());
        else vanilla.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void last(PlayerRespawnEvent e) {
        Location v = vanilla.remove(e.getPlayer().getUniqueId());
        if (v != null) e.setRespawnLocation(v);
    }
}
