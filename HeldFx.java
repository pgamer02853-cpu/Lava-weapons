package com.rizgamer.lavaweapons;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;

/** Lava flames around the hand while a Lava weapon is held. */
public final class HeldFx extends BukkitRunnable {
    private final LavaWeapons pl;
    private final Random rnd = new Random();

    HeldFx(LavaWeapons pl) {
        this.pl = pl;
        runTaskTimer(pl, 10L, 3L);
    }

    @Override
    public void run() {
        if (!pl.getConfig().getBoolean("held-fx", true)) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Weapon w = pl.items.of(p.getInventory().getItemInMainHand());
            if (w == null || !w.lava) continue;
            if (pl.zones.nullified(p.getLocation())) continue;
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection().normalize();
            Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
            if (right.lengthSquared() < 1.0e-4) right = new Vector(1, 0, 0); else right.normalize();
            Location h = eye.clone().add(dir.clone().multiply(0.9)).add(right.multiply(0.4)).add(0, -0.45, 0);
            World wd = p.getWorld();
            wd.spawnParticle(Particle.FLAME, h, 2, 0.10, 0.20, 0.10, 0.004);
            if (rnd.nextInt(3) == 0) wd.spawnParticle(Particle.DRIPPING_LAVA, h, 1, 0.12, 0.25, 0.12, 0);
            if (rnd.nextInt(6) == 0) wd.spawnParticle(Particle.LAVA, h, 1, 0.10, 0.20, 0.10, 0);
        }
    }
}
