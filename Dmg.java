package com.rizgamer.lavaweapons;

import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class Dmg {
    /** true while the plugin itself is dealing damage (so the melee listener ignores it) */
    public static boolean busy = false;

    private Dmg() {}

    public static boolean hasGod(Player p) {
        LavaWeapons pl = LavaWeapons.get();
        for (ItemStack s : p.getInventory().getContents()) {
            Weapon w = pl.items.of(s);
            if (w != null && w.isGod()) return true;
        }
        return false;
    }

    public static boolean blessed(LivingEntity e) {
        return e instanceof Player p && hasGod(p) && !LavaWeapons.get().zones.nullified(p.getLocation());
    }

    public static void hurt(Player att, LivingEntity v, double dmg, boolean bypassArmor, Weapon src) {
        hurt(att, v, dmg, bypassArmor ? DamageType.MAGIC : DamageType.PLAYER_ATTACK, src, true);
    }

    public static void kill(Player att, LivingEntity v, Weapon src) {
        hurt(att, v, 100000, DamageType.MAGIC, src, true);
    }

    public static void hurt(Player att, LivingEntity v, double dmg, DamageType type, Weapon src, boolean fire) {
        LavaWeapons pl = LavaWeapons.get();
        if (v == att || v instanceof ArmorStand || !v.isValid() || v.isDead()) return;
        if (v instanceof Player vp) {
            if (vp.getGameMode() == GameMode.CREATIVE || vp.getGameMode() == GameMode.SPECTATOR) return;
            if (pl.st.active(pl.st.shield, vp.getUniqueId())) return;
        }
        if (src != null && blessed(v)) {
            double cap = src.lava ? pl.getConfig().getDouble("god-blessing.lava-damage-cap-hearts", 5) * 2
                                  : pl.getConfig().getDouble("god-blessing.god-vs-god-hearts", 25) * 2;
            dmg = Math.min(dmg, cap);
        }
        DamageSource ds = DamageSource.builder(type).withCausingEntity(att).withDirectEntity(att).build();
        busy = true;
        try { v.damage(dmg, ds); } finally { busy = false; }
        if (fire && v.isValid() && !v.isDead())
            v.setFireTicks(Math.max(v.getFireTicks(), pl.getConfig().getInt("fire-seconds", 5) * 20));
    }

    /** God Blessing: 50 hearts while a god weapon is in the inventory (disabled inside a Null Zone). */
    public static void updateBlessing(Player p) {
        LavaWeapons pl = LavaWeapons.get();
        boolean want = hasGod(p) && !pl.zones.nullified(p.getLocation());
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a == null) return;
        NamespacedKey key = new NamespacedKey(pl, "god_blessing");
        AttributeModifier cur = null;
        for (AttributeModifier m : a.getModifiers()) if (key.equals(m.getKey())) cur = m;
        double extra = pl.getConfig().getDouble("god-blessing.hearts", 50) * 2 - 20;
        if (want && cur == null) {
            a.addTransientModifier(new AttributeModifier(key, extra, AttributeModifier.Operation.ADD_NUMBER));
            p.setHealth(a.getValue());
        } else if (!want && cur != null) {
            a.removeModifier(cur);
            if (p.getHealth() > a.getValue()) p.setHealth(a.getValue());
        }
    }
}
