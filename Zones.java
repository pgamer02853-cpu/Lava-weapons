package com.rizgamer.lavaweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Null Zone: no custom power works inside. */
public final class Zones {
    private static final class Zone {
        final UUID world; final double cx, cz; final long until;
        Zone(UUID world, double cx, double cz, long until) { this.world = world; this.cx = cx; this.cz = cz; this.until = until; }
    }

    private final LavaWeapons pl;
    private final List<Zone> list = new ArrayList<>();

    Zones(LavaWeapons pl) {
        this.pl = pl;
        new BukkitRunnable() { @Override public void run() { tick(); } }.runTaskTimer(pl, 20L, 20L);
    }

    private double half() { return pl.getConfig().getDouble("null-zone.size", 100) / 2.0; }

    public boolean nullified(Location l) {
        if (l == null || l.getWorld() == null || list.isEmpty()) return false;
        long now = System.currentTimeMillis();
        double h = half();
        for (Zone z : list) {
            if (z.until <= now || !z.world.equals(l.getWorld().getUID())) continue;
            if (Math.abs(l.getX() - z.cx) <= h && Math.abs(l.getZ() - z.cz) <= h) return true;
        }
        return false;
    }

    public void start(Player p) {
        long dur = pl.getConfig().getLong("null-zone.duration-seconds", 900) * 1000L;
        Location l = p.getLocation();
        list.add(new Zone(l.getWorld().getUID(), l.getX(), l.getZ(), System.currentTimeMillis() + dur));
        l.getWorld().playSound(l, Sound.BLOCK_BEACON_DEACTIVATE, 3f, 0.5f);
        double h = half();
        for (Player o : l.getWorld().getPlayers()) {
            if (Math.abs(o.getLocation().getX() - l.getX()) <= h && Math.abs(o.getLocation().getZ() - l.getZ()) <= h)
                o.sendMessage(Component.text("Null Zone activated! No custom power works here for "
                        + (dur / 60000) + " minutes.", NamedTextColor.LIGHT_PURPLE));
        }
    }

    private void tick() {
        if (list.isEmpty()) return;
        long now = System.currentTimeMillis();
        double h = half();
        Iterator<Zone> it = list.iterator();
        while (it.hasNext()) {
            Zone z = it.next();
            World w = Bukkit.getWorld(z.world);
            if (w == null) { it.remove(); continue; }
            boolean expired = z.until <= now;
            long left = Math.max(0, (z.until - now) / 1000);
            for (Player p : w.getPlayers()) {
                if (Math.abs(p.getLocation().getX() - z.cx) > h || Math.abs(p.getLocation().getZ() - z.cz) > h) continue;
                if (expired) p.sendActionBar(Component.text("Null Zone ended", NamedTextColor.GREEN));
                else p.sendActionBar(Component.text(String.format("Null Zone  %02d:%02d", left / 60, left % 60), NamedTextColor.LIGHT_PURPLE));
            }
            if (expired) it.remove();
        }
    }

    public void clear() { list.clear(); }
}
