package com.rizgamer.lavaweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public final class Abilities {
    private final LavaWeapons pl;
    private final Random rnd = new Random();
    private final Map<String, long[]> hitCounts = new HashMap<>();

    Abilities(LavaWeapons pl) {
        this.pl = pl;
        new BukkitRunnable() { @Override public void run() { pendingSlams(); } }.runTaskTimer(pl, 2L, 2L);
    }

    private double cfg(String p, double d) { return pl.getConfig().getDouble(p, d); }
    private long now() { return System.currentTimeMillis(); }

    // ------------------------------------------------------------ entry points
    public void rightClick(Player p, Weapon w) {
        if (pl.zones.nullified(p.getLocation())) {
            p.sendActionBar(Component.text("Null Zone: custom powers are disabled here", NamedTextColor.DARK_GRAY));
            return;
        }
        switch (w) {
            case LAVA_BLADE -> meteorStorm(p);
            case LAVA_HAMMER -> leap(p, true);
            case LAVA_SPEAR -> impale(p);
            case GOD_BLADE -> { if (p.isSneaking()) aegis(p); else pl.volcano.start(p); }
            case GOD_HAMMER -> { if (p.isSneaking()) aegis(p); else leap(p, false); }
            case TIME_CLOCK -> { if (pl.cd.tryUse(p, "null_zone", "Null Zone")) pl.zones.start(p); }
        }
    }

    public void leftClickAir(Player p, Weapon w) {
        if (pl.zones.nullified(p.getLocation())) return;
        if (w == Weapon.LAVA_HAMMER && p.isSneaking() && !p.isOnGround()) slam(p, null);
    }

    public void swing(Player p, Weapon w) {
        if (w != Weapon.LAVA_SPEAR || p.isSneaking() || pl.zones.nullified(p.getLocation())) return;
        if (!pl.cd.tryUse(p, "ember_lunge", "Ember Lunge", false)) return;
        Vector d = p.getLocation().getDirection().normalize();
        p.setVelocity(new Vector(d.getX() * 1.5, Math.max(d.getY() * 1.5, 0.2), d.getZ() * 1.5));
        pl.st.put(pl.st.noFall, p.getUniqueId(), 4000);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.4f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || t++ > 8) { cancel(); return; }
                fx(p.getLocation().add(0, 1, 0), true, 6);
            }
        }.runTaskTimer(pl, 0L, 1L);
    }

    /** Called AFTER the vanilla hit was accepted. vanilla = damage the vanilla hit already dealt. */
    public void melee(Player p, LivingEntity v, Weapon w, double vanilla, boolean crit) {
        double mult = crit ? 1.5 : 1.0;
        v.setFireTicks(Math.max(v.getFireTicks(), pl.getConfig().getInt("fire-seconds", 5) * 20));
        switch (w) {
            case LAVA_BLADE -> follow(p, v, w, cfg("damage.lava-blade", 16) * mult - vanilla, true, false);
            case LAVA_HAMMER -> {
                if (p.isSneaking() && !p.isOnGround() && p.getFallDistance() > 1)
                    Bukkit.getScheduler().runTask(pl, () -> slam(p, v));
                else follow(p, v, w, cfg("damage.lava-hammer", 16) * mult - vanilla, false, false);
            }
            case LAVA_SPEAR -> {
                if (p.getFallDistance() >= cfg("spear.oneshot-fall-distance", 15)) follow(p, v, w, 0, true, true);
                else follow(p, v, w, cfg("damage.lava-spear", 16) * mult - vanilla, true, false);
                pl.st.put(pl.st.noFall, p.getUniqueId(), 4000);
            }
            case GOD_BLADE -> {
                String key = p.getUniqueId() + ":" + v.getUniqueId();
                long[] h = hitCounts.get(key);
                if (h == null || now() - h[1] > 10000) h = new long[]{0, 0};
                h[0]++; h[1] = now(); hitCounts.put(key, h);
                if (h[0] >= 4) { breakArmor(v); hitCounts.remove(key); }
                follow(p, v, w, 0, true, true);
            }
            case GOD_HAMMER -> {
                if (p.getFallDistance() >= 3.5 && pl.cd.tryUse(p, "godfall_smash", "Godfall Smash", false)) breakArmor(v);
                follow(p, v, w, 0, true, true);
            }
            default -> { }
        }
    }

    /** Extra damage one tick after the vanilla hit (so crit / smash / wind burst all stay normal). */
    private void follow(Player p, LivingEntity v, Weapon w, double extra, boolean bypass, boolean kill) {
        if (!kill && extra <= 0.5) return;
        final UUID vid = v.getUniqueId();
        Bukkit.getScheduler().runTask(pl, () -> {
            if (!v.isValid() || v.isDead() || !p.isOnline()) return;
            if (pl.st.active(pl.st.totemPop, vid)) return; // totem already popped: victim survives
            v.setNoDamageTicks(0);
            if (kill) Dmg.kill(p, v, w);
            else Dmg.hurt(p, v, extra, bypass, w);
        });
    }

    // ------------------------------------------------------------ helpers
    private void fx(Location l, boolean lava, int n) {
        World w = l.getWorld();
        if (lava) {
            w.spawnParticle(Particle.FLAME, l, n, 0.25, 0.25, 0.25, 0.02);
            w.spawnParticle(Particle.LAVA, l, Math.max(1, n / 4), 0.25, 0.25, 0.25, 0);
        } else {
            w.spawnParticle(Particle.DUST, l, n, 0.25, 0.25, 0.25, 0, new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.5f));
            w.spawnParticle(Particle.END_ROD, l, Math.max(1, n / 4), 0.25, 0.25, 0.25, 0.01);
        }
    }

    public void breakArmor(LivingEntity v) {
        if (v.getEquipment() == null) return;
        v.getEquipment().setArmorContents(new ItemStack[4]);
        v.getWorld().playSound(v.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
        v.getWorld().spawnParticle(Particle.EXPLOSION, v.getLocation().add(0, 1, 0), 2);
    }

    private boolean valid(Player p, Entity e) {
        return e instanceof LivingEntity && e != p && !(e instanceof ArmorStand);
    }

    // ------------------------------------------------------------ Volcanic Leap
    private void leap(Player p, boolean lava) {
        if (!pl.cd.tryUse(p, "volcanic_leap", "Volcanic Leap")) return;
        Location l = p.getLocation();
        Vector d = l.getDirection().normalize();
        Vector v = l.getPitch() < -55 ? new Vector(d.getX() * 0.35, 2.7, d.getZ() * 0.35)
                : new Vector(d.getX() * 2.3, Math.max(d.getY() * 2.3, 0.9), d.getZ() * 2.3);
        p.setVelocity(v);
        pl.st.put(pl.st.noFall, p.getUniqueId(), 15000);
        p.getWorld().playSound(l, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.6f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || t++ > 40) { cancel(); return; }
                double a = t * 0.7;
                Location b = p.getLocation();
                for (int i = 0; i < 2; i++) {
                    double ang = a + i * Math.PI;
                    fx(b.clone().add(Math.cos(ang) * 1.1, 0.4 + (t % 12) * 0.12, Math.sin(ang) * 1.1), lava, 3);
                }
            }
        }.runTaskTimer(pl, 0L, 1L);
    }

    // ------------------------------------------------------------ Volcanic Slam
    private void slam(Player p, LivingEntity hit) {
        if (!pl.cd.tryUse(p, "volcanic_slam", "Volcanic Slam")) return;
        if (hit != null) { slamAt(p, hit.getLocation()); return; }
        Vector d = p.getLocation().getDirection().normalize();
        p.setVelocity(new Vector(d.getX() * 0.4, -2.6, d.getZ() * 0.4));
        pl.st.put(pl.st.slamPending, p.getUniqueId(), 10000);
        pl.st.put(pl.st.noFall, p.getUniqueId(), 12000);
    }

    private void pendingSlams() {
        if (pl.st.slamPending.isEmpty()) return;
        Iterator<Map.Entry<UUID, Long>> it = pl.st.slamPending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || e.getValue() < now()) { it.remove(); continue; }
            if (p.isOnGround()) { it.remove(); slamAt(p, p.getLocation()); }
        }
    }

    private void slamAt(Player p, Location loc) {
        World w = loc.getWorld();
        double r = cfg("slam.radius", 10);
        pl.st.put(pl.st.blastImmune, p.getUniqueId(), 3000);
        pl.st.put(pl.st.noFall, p.getUniqueId(), 4000);
        for (Entity en : w.getNearbyEntities(loc, r, r, r)) {
            if (en instanceof LivingEntity le && valid(p, en)) {
                pl.st.put(pl.st.blastImmune, le.getUniqueId(), 3000);
                Dmg.kill(p, le, Weapon.LAVA_HAMMER);
            }
        }
        w.createExplosion(loc, (float) cfg("slam.explosion-power", 4), false,
                pl.getConfig().getBoolean("slam.break-blocks", true), p);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
        w.spawnParticle(Particle.FLAME, loc, 150, r / 3, 0.5, r / 3, 0.15);
        w.spawnParticle(Particle.LAVA, loc, 60, r / 3, 0.5, r / 3, 0);
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.6f);
        w.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3f, 0.8f);
    }

    // ------------------------------------------------------------ Impale Charge
    private void impale(Player p) {
        if (!pl.cd.tryUse(p, "impale_charge", "Impale Charge")) return;
        final double fall = p.getFallDistance();
        pl.st.put(pl.st.noFall, p.getUniqueId(), 6000);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);
        new BukkitRunnable() {
            int t = 0;
            final Set<UUID> hit = new HashSet<>();
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || t++ >= 25) { cancel(); return; }
                Vector dir = p.getLocation().getDirection().normalize();
                p.setVelocity(dir.clone().multiply(1.7));
                Location front = p.getLocation().add(0, 1, 0).add(dir.clone().multiply(1.2));
                fx(front, true, 8);
                for (Entity en : p.getWorld().getNearbyEntities(front, 2.2, 2.2, 2.2)) {
                    if (!valid(p, en) || !hit.add(en.getUniqueId())) continue;
                    LivingEntity le = (LivingEntity) en;
                    if (fall >= cfg("spear.oneshot-fall-distance", 15)) Dmg.kill(p, le, Weapon.LAVA_SPEAR);
                    else Dmg.hurt(p, le, cfg("damage.impale-charge", 24), true, Weapon.LAVA_SPEAR);
                    le.setVelocity(dir.clone().multiply(1.0).setY(0.4));
                    p.getWorld().playSound(le.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.7f);
                }
            }
        }.runTaskTimer(pl, 0L, 1L);
    }

    // ------------------------------------------------------------ Holy Aegis
    private void aegis(Player p) {
        if (!pl.cd.tryUse(p, "holy_aegis", "Holy Aegis")) return;
        long secs = pl.getConfig().getLong("holy-aegis-seconds", 20);
        pl.st.put(pl.st.shield, p.getUniqueId(), secs * 1000);
        p.setFireTicks(0);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2f, 1.2f);
        p.sendMessage(Component.text("Holy Aegis active for " + secs + "s", NamedTextColor.GOLD));
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || !pl.st.active(pl.st.shield, p.getUniqueId())) {
                    if (p.isOnline()) p.sendMessage(Component.text("Holy Aegis ended", NamedTextColor.GRAY));
                    cancel(); return;
                }
                t += 2;
                p.setFireTicks(0);
                Location b = p.getLocation();
                for (int i = 0; i < 3; i++) {
                    double ang = t * 0.35 + i * 2.09;
                    double y = ((t * 0.06) + i * 0.7) % 2.0;
                    fx(b.clone().add(Math.cos(ang) * 1.0, y, Math.sin(ang) * 1.0), false, 2);
                }
            }
        }.runTaskTimer(pl, 0L, 2L);
    }

    // ------------------------------------------------------------ Meteor Storm
    private void meteorStorm(Player p) {
        if (!pl.cd.tryUse(p, "meteor_storm", "Meteor Storm")) return;
        double r = cfg("meteor.radius", 28);
        int max = (int) cfg("meteor.max-targets", 40);
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(r, r, r)) {
            if (valid(p, e)) { targets.add((LivingEntity) e); if (targets.size() >= max) break; }
        }
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 0.6f);
        for (LivingEntity t : targets) launchMeteor(p, t, null, rnd.nextInt(30));
        for (int i = 0; i < 8; i++) {
            Location g = p.getLocation().add((rnd.nextDouble() - 0.5) * 36, 0, (rnd.nextDouble() - 0.5) * 36);
            g.setY(w.getHighestBlockYAt(g) + 1);
            launchMeteor(p, null, g, rnd.nextInt(40));
        }
    }

    private void launchMeteor(Player owner, LivingEntity target, Location fixed, int delay) {
        new BukkitRunnable() {
            @Override public void run() {
                World w = (target != null ? target.getLocation() : fixed).getWorld();
                Location tgt0 = target != null ? target.getLocation() : fixed.clone();
                Location cur = tgt0.clone().add((rnd.nextDouble() - 0.5) * 20, 45, (rnd.nextDouble() - 0.5) * 20);
                ItemStack ms = new ItemStack(Material.STONE);
                ItemMeta mm = ms.getItemMeta();
                NamespacedKey model = NamespacedKey.fromString("lavaweapons:meteor");
                if (model != null) mm.setItemModel(model);
                ms.setItemMeta(mm);
                ItemDisplay d = w.spawn(cur, ItemDisplay.class, disp -> {
                    disp.setItemStack(ms);
                    disp.setTransformation(new Transformation(new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 0, 1),
                            new Vector3f(3.2f, 3.2f, 3.2f), new AxisAngle4f(0, 0, 0, 1)));
                    disp.setTeleportDuration(2);
                    disp.setBrightness(new Display.Brightness(15, 15));
                    disp.setPersistent(false);
                });
                new BukkitRunnable() {
                    int ticks = 0;
                    @Override public void run() {
                        Location tl = (target != null && target.isValid()) ? target.getLocation() : tgt0;
                        Vector dir = tl.toVector().subtract(cur.toVector());
                        double dist = dir.length();
                        if (dist < 2.2 || ticks++ > 100) { impact(owner, tl); d.remove(); cancel(); return; }
                        cur.add(dir.normalize().multiply(1.9));
                        d.teleport(cur);
                        w.spawnParticle(Particle.FLAME, cur, 8, 0.5, 0.5, 0.5, 0.03);
                        w.spawnParticle(Particle.LARGE_SMOKE, cur, 3, 0.4, 0.4, 0.4, 0.01);
                        w.spawnParticle(Particle.LAVA, cur, 1, 0.3, 0.3, 0.3, 0);
                    }
                }.runTaskTimer(pl, 0L, 1L);
            }
        }.runTaskLater(pl, delay);
    }

    private void impact(Player owner, Location l) {
        World w = l.getWorld();
        w.spawnParticle(Particle.EXPLOSION_EMITTER, l, 1);
        w.spawnParticle(Particle.FLAME, l, 80, 1.5, 0.5, 1.5, 0.12);
        w.spawnParticle(Particle.LAVA, l, 40, 1.5, 0.5, 1.5, 0);
        w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.7f);
        for (Entity en : w.getNearbyEntities(l, 3.5, 3.5, 3.5)) {
            if (!valid(owner, en)) continue;
            Dmg.hurt(owner, (LivingEntity) en, cfg("damage.meteor", 24), DamageType.MAGIC, Weapon.LAVA_BLADE, true);
            ((LivingEntity) en).setFireTicks(160);
        }
    }
}
