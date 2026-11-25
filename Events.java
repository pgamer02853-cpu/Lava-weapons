package com.rizgamer.lavaweapons;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

public final class Events implements Listener {
    private final LavaWeapons pl;
    Events(LavaWeapons pl) { this.pl = pl; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (pl.st.locked.contains(p.getUniqueId())) { e.setCancelled(true); return; }
        if (e.getHand() != EquipmentSlot.HAND) return;
        Weapon w = pl.items.of(e.getItem());
        if (w == null) return;
        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                    && e.getClickedBlock().getType().isInteractable() && !p.isSneaking()) return; // let chests/doors work
            e.setCancelled(true);
            pl.ab.rightClick(p, w);
        } else if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            pl.ab.leftClickAir(p, w);
        }
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();
        Weapon w = pl.items.of(p.getInventory().getItemInMainHand());
        if (w != null) pl.ab.swing(p, w);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        if (Dmg.busy) return;
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity v)) return;
        if (pl.st.locked.contains(p.getUniqueId())) { e.setCancelled(true); return; }
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        Weapon w = pl.items.of(p.getInventory().getItemInMainHand());
        if (w == null || w == Weapon.TIME_CLOCK) return;
        if (pl.zones.nullified(p.getLocation())) return; // inside Null Zone: vanilla behaviour
        e.setCancelled(true);
        pl.ab.melee(p, v, w);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnyDamage(EntityDamageEvent e) {
        Entity en = e.getEntity();
        UUID id = en.getUniqueId();
        DamageCause c = e.getCause();
        if (en instanceof Player) {
            boolean hard = c == DamageCause.VOID || c == DamageCause.KILL || c == DamageCause.SUICIDE;
            if (!hard && pl.st.active(pl.st.shield, id)) { e.setCancelled(true); return; }
            if (!hard && pl.st.locked.contains(id) && pl.getConfig().getBoolean("volcano.caster-invulnerable", true)) {
                e.setCancelled(true); return;
            }
            if (c == DamageCause.FALL && pl.st.active(pl.st.noFall, id)) { e.setCancelled(true); return; }
        }
        if ((c == DamageCause.BLOCK_EXPLOSION || c == DamageCause.ENTITY_EXPLOSION) && pl.st.active(pl.st.blastImmune, id))
            e.setCancelled(true);
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e) {
        Item it = e.getEntity();
        if (pl.items.of(it.getItemStack()) != null) {
            it.setInvulnerable(true);
            it.setUnlimitedLifetime(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { if (pl.st.locked.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { if (pl.st.locked.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        pl.st.shield.remove(id); pl.st.slamPending.remove(id); pl.st.locked.remove(id);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        pl.st.locked.remove(id); pl.st.slamPending.remove(id);
    }
}
