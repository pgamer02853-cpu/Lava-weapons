package com.rizgamer.lavaweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/** God Blade: Divine Volcano. */
public final class Volcano {
    private record Pos(int x, int y, int z) {}
    private record Step(Pos pos, Material mat) {}
    private static final class Eruption {
        final World w; final Map<Pos, BlockData> orig = new LinkedHashMap<>();
        Eruption(World w) { this.w = w; }
    }

    private final LavaWeapons plugin;
    private final List<Eruption> active = new ArrayList<>();

    Volcano(LavaWeapons plugin) { this.plugin = plugin; }

    private int cfgI(String p, int d) { return plugin.getConfig().getInt(p, d); }

    private Material pick(Random r) {
        int n = r.nextInt(100);
        if (n < 26) return Material.BLACKSTONE;
        if (n < 42) return Material.BASALT;
        if (n < 58) return Material.NETHERRACK;
        if (n < 72) return Material.MAGMA_BLOCK;
        if (n < 80) return Material.OBSIDIAN;
        if (n < 85) return Material.POLISHED_BLACKSTONE_BRICKS;
        if (n < 89) return Material.CRACKED_POLISHED_BLACKSTONE_BRICKS;
        if (n < 92) return Material.CRYING_OBSIDIAN;
        if (n < 95) return Material.SMOOTH_BASALT;
        if (n < 97) return Material.GILDED_BLACKSTONE;
        return Material.SHROOMLIGHT;
    }

    public void start(Player p) {
        UUID id = p.getUniqueId();
        if (plugin.st.locked.contains(id)) return;
        if (!plugin.cd.tryUse(p, "divine_volcano", "Divine Volcano")) return;

        World w = p.getWorld();
        int R = cfgI("volcano.radius", 22), H = cfgI("volcano.height", 24);
        int cx = p.getLocation().getBlockX(), cz = p.getLocation().getBlockZ();
        boolean normal = w.getEnvironment() == World.Environment.NORMAL;
        int baseY = normal ? w.getHighestBlockYAt(cx, cz) : p.getLocation().getBlockY() - 1;
        Random rnd = new Random(cx * 341873128L + cz * 132897987L);

        List<Step> plan = new ArrayList<>();
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > R) continue;
                double h = H * Math.pow(1 - d / R, 0.9) + (rnd.nextDouble() - 0.5) * 2.0 * (1 - d / R);
                if (d <= 3) h = H;
                int hh = (int) Math.round(h);
                if (hh <= 0) continue;
                int x = cx + dx, z = cz + dz;
                int gy = normal ? w.getHighestBlockYAt(x, z) : baseY;
                for (int y = Math.min(gy, baseY) + 1; y <= baseY; y++) plan.add(new Step(new Pos(x, y, z), pick(rnd)));
                for (int k = 1; k <= hh; k++) {
                    Material m = (k == hh && d <= 3) ? Material.CHISELED_POLISHED_BLACKSTONE : pick(rnd);
                    plan.add(new Step(new Pos(x, baseY + k, z), m));
                }
            }
        }
        plan.sort(Comparator.comparingInt(s -> s.pos().y()));

        Eruption er = new Eruption(w);
        active.add(er);
        plugin.st.locked.add(id);
        p.teleport(p.getLocation().add(0, 2, 0));
        w.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 3f, 0.5f);
        p.sendMessage(Component.text("Divine Volcano!", NamedTextColor.GOLD));

        final double targetY = baseY + H + 3;
        final int per = cfgI("volcano.blocks-per-tick", 300);
        final boolean inv = plugin.getConfig().getBoolean("volcano.caster-invulnerable", true);
        final double pulseDmg = plugin.getConfig().getDouble("damage.volcano-pulse", 14);
        final Location center = new Location(w, cx + 0.5, baseY + H / 2.0, cz + 0.5);

        new BukkitRunnable() {
            int idx = 0, tick = 0, after = 0;

            void end() {
                cancel();
                plugin.st.locked.remove(id);
                plugin.st.put(plugin.st.noFall, id, 15000);
                scheduleRestore(er);
            }

            @Override public void run() {
                tick++;
                if (!p.isOnline() || p.isDead()) { end(); return; }
                double dy = targetY - p.getLocation().getY();
                p.setVelocity(new Vector(0, Math.max(-0.2, Math.min(0.55, dy * 0.12)), 0));
                p.setFallDistance(0);
                if (inv) p.setFireTicks(0);

                int n = 0;
                while (idx < plan.size() && n < per) {
                    Step s = plan.get(idx++);
                    Block b = w.getBlockAt(s.pos().x(), s.pos().y(), s.pos().z());
                    if (b.getType() == Material.BEDROCK) continue;
                    er.orig.putIfAbsent(s.pos(), b.getBlockData());
                    b.setType(s.mat(), false);
                    n++;
                }
                if (tick % 20 == 0) {
                    for (Entity e : w.getNearbyEntities(center, R + 4, H / 2.0 + 12, R + 4)) {
                        if (e instanceof LivingEntity le && le != p && !(le instanceof ArmorStand)) {
                            Dmg.hurt(p, le, pulseDmg, DamageType.EXPLOSION, null, false);
                            w.spawnParticle(Particle.EXPLOSION, le.getLocation(), 2);
                        }
                    }
                    w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.6f);
                }
                if (tick % 3 == 0) {
                    double ang = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * R;
                    Location f = new Location(w, cx + Math.cos(ang) * rr, baseY + 2 + rnd.nextInt(H), cz + Math.sin(ang) * rr);
                    w.spawnParticle(Particle.LAVA, f, 6, 1, 1, 1, 0);
                    w.spawnParticle(Particle.LARGE_SMOKE, f, 4, 1, 1, 1, 0.02);
                }
                if (idx >= plan.size()) { after++; if (after >= 25) end(); }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void scheduleRestore(Eruption er) {
        long ticks = plugin.getConfig().getLong("volcano.restore-after-seconds", 300) * 20L;
        new BukkitRunnable() {
            List<Map.Entry<Pos, BlockData>> list = null;
            int idx = 0;
            @Override public void run() {
                if (list == null) { list = new ArrayList<>(er.orig.entrySet()); Collections.reverse(list); }
                int n = 0;
                while (idx < list.size() && n < 400) {
                    Map.Entry<Pos, BlockData> e = list.get(idx++);
                    er.w.getBlockAt(e.getKey().x(), e.getKey().y(), e.getKey().z()).setBlockData(e.getValue(), false);
                    n++;
                }
                if (idx >= list.size()) { active.remove(er); cancel(); }
            }
        }.runTaskTimer(plugin, ticks, 1L);
    }

    /** On plugin disable: put every mountain back immediately. */
    public void restoreAllNow() {
        for (Eruption er : new ArrayList<>(active)) {
            List<Map.Entry<Pos, BlockData>> list = new ArrayList<>(er.orig.entrySet());
            Collections.reverse(list);
            for (Map.Entry<Pos, BlockData> e : list)
                er.w.getBlockAt(e.getKey().x(), e.getKey().y(), e.getKey().z()).setBlockData(e.getValue(), false);
        }
        active.clear();
    }
}
