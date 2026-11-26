package com.rizgamer.lavaweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class Items {
    private final LavaWeapons pl;
    public final NamespacedKey KEY;

    Items(LavaWeapons pl) { this.pl = pl; this.KEY = new NamespacedKey(pl, "weapon_id"); }

    private record Ab(String name, String trigger, String cd, String desc) {}

    private List<Ab> abilities(Weapon w) {
        return switch (w) {
            case LAVA_BLADE -> List.of(
                new Ab("Molten Edge", "Every hit", null, "8 hearts, ignores armor, sets target on fire"),
                new Ab("Meteor Storm", "Right-Click", "meteor_storm", "Meteors fall on every enemy nearby"));
            case LAVA_HAMMER -> List.of(
                new Ab("Magma Crush", "Every hit", null, "8 hearts + fire (armor reduces it), Wind Burst II"),
                new Ab("Volcanic Leap", "Right-Click", "volcanic_leap", "Launch into the air (look up = straight up)"),
                new Ab("Volcanic Slam", "Sneak + Left-Click while falling", "volcanic_slam", "10 block radius one-shot + crater"));
            case LAVA_SPEAR -> List.of(
                new Ab("Ember Lunge", "Left-Click", "ember_lunge", "Free lunge forward"),
                new Ab("Impale Charge", "Right-Click", "impale_charge", "Charge dash, no fall damage, huge damage"));
            case GOD_BLADE -> List.of(
                new Ab("Divine Judgement", "Every hit", null, "One-shot kill, 4 hits destroy armor"),
                new Ab("Divine Volcano", "Right-Click", "divine_volcano", "Rise into the sky, a volcano grows beneath you"),
                new Ab("Holy Aegis", "Sneak + Right-Click", "holy_aegis", "20 seconds of invulnerability"),
                new Ab("God Blessing", "In inventory", null, "50 hearts, Lava weapons deal max 5 hearts to you"));
            case GOD_HAMMER -> List.of(
                new Ab("Divine Judgement", "Every hit", null, "One-shot kill (normal Mace smash + Wind Burst II work too)"),
                new Ab("Volcanic Leap", "Right-Click", "volcanic_leap", "Launch into the air"),
                new Ab("Godfall Smash", "Hit while falling", "godfall_smash", "Destroys the target's armor"),
                new Ab("Holy Aegis", "Sneak + Right-Click", "holy_aegis", "20 seconds of invulnerability"),
                new Ab("God Blessing", "In inventory", null, "50 hearts, Lava weapons deal max 5 hearts to you"));
            case TIME_CLOCK -> List.of(
                new Ab("Null Zone", "Right-Click", "null_zone", "15 minutes: no custom power works in 100x100 blocks"));
        };
    }

    private static Component line(String s, TextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private List<Component> lore(Weapon w) {
        List<Component> l = new ArrayList<>();
        TextColor main = TextColor.color(w.color);
        l.add(Component.empty());
        l.add(line("Powers", NamedTextColor.GRAY));
        int i = 1;
        for (Ab a : abilities(w)) {
            l.add(line(i + ". " + a.name() + "  [" + a.trigger() + "]", main));
            String cd = a.cd() == null ? "" : "  |  Cooldown: " + Cooldowns.fmtD(pl.getConfig().getDouble("cooldowns." + a.cd(), 0));
            l.add(line("   " + a.desc() + cd, NamedTextColor.GRAY));
            i++;
        }
        l.add(Component.empty());
        l.add(line("Made by RIZGAMER", NamedTextColor.DARK_GRAY));
        return l;
    }

    public ItemStack create(Weapon w) {
        Material m = Material.matchMaterial(w.base);
        if (m == null) m = (w == Weapon.LAVA_SPEAR) ? Material.NETHERITE_SWORD : Material.DIAMOND_SWORD;
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(w.display, TextColor.color(w.color), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore(w));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        if (w == Weapon.LAVA_HAMMER || w == Weapon.GOD_HAMMER) meta.addEnchant(Enchantment.WIND_BURST, 2, true); // hidden, only applied
        meta.setEnchantmentGlintOverride(true);
        NamespacedKey model = NamespacedKey.fromString("lavaweapons:" + w.id);
        if (model != null) meta.setItemModel(model);
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, w.id);
        try { meta.setMaxStackSize(1); } catch (Throwable ignored) { }
        it.setItemMeta(meta);
        return it;
    }

    public Weapon of(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        return Weapon.byId(id);
    }
}
