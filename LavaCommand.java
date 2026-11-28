package com.rizgamer.lavaweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class LavaCommand implements CommandExecutor, TabCompleter, Listener {
    private final LavaWeapons pl;
    LavaCommand(LavaWeapons pl) { this.pl = pl; }

    private static final class MenuHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private void msg(CommandSender s, String t, NamedTextColor c) { s.sendMessage(Component.text(t, c)); }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("lavaweapons.admin")) { msg(s, "No permission!", NamedTextColor.RED); return true; }
        if (a.length == 0) { msg(s, "Use: /lava give <weapon|all> [player] | /lava menu | /lava reload | /lava resetcd [player]", NamedTextColor.YELLOW); return true; }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "give" -> {
                if (a.length < 2) { msg(s, "Use: /lava give <weapon|all> [player]", NamedTextColor.YELLOW); return true; }
                Player target = a.length >= 3 ? Bukkit.getPlayerExact(a[2]) : (s instanceof Player pp ? pp : null);
                if (target == null) { msg(s, "Player not found.", NamedTextColor.RED); return true; }
                List<Weapon> give = new ArrayList<>();
                if (a[1].equalsIgnoreCase("all")) give.addAll(Arrays.asList(Weapon.values()));
                else { Weapon w = Weapon.byId(a[1]); if (w == null) { msg(s, "Unknown weapon.", NamedTextColor.RED); return true; } give.add(w); }
                for (Weapon w : give) {
                    Map<Integer, ItemStack> left = target.getInventory().addItem(pl.items.create(w));
                    left.values().forEach(i -> target.getWorld().dropItemNaturally(target.getLocation(), i));
                }
                msg(s, "Gave " + (give.size() == 1 ? give.get(0).display : "all items") + " to " + target.getName(), NamedTextColor.GREEN);
            }
            case "menu" -> {
                if (!(s instanceof Player p)) { msg(s, "Players only.", NamedTextColor.RED); return true; }
                Inventory inv = Bukkit.createInventory(new MenuHolder(), 9, Component.text("Lava Weapons - by RIZGAMER"));
                int i = 1;
                for (Weapon w : Weapon.values()) inv.setItem(i++, pl.items.create(w));
                p.openInventory(inv);
            }
            case "fixswap" -> {
                String r = SwapFix.fix();
                if (r.equals("DONE")) msg(s, "Done! paper-global.yml fixed (backup: paper-global.yml.bak). RESTART the server to enable attribute swapping.", NamedTextColor.GREEN);
                else if (r.equals("ALREADY")) msg(s, "Swapping is already enabled in paper-global.yml.", NamedTextColor.YELLOW);
                else if (r.equals("NOTFOUND")) msg(s, "Key not found. Open config/paper-global.yml and set update-equipment-on-player-actions: false", NamedTextColor.RED);
                else msg(s, r, NamedTextColor.RED);
            }
            case "reload" -> { pl.reloadConfig(); msg(s, "Config reloaded.", NamedTextColor.GREEN); }
            case "resetcd" -> {
                Player t = a.length >= 2 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player pp ? pp : null);
                if (t == null) { msg(s, "Player not found.", NamedTextColor.RED); return true; }
                pl.cd.clear(t.getUniqueId());
                msg(s, "Cooldowns reset for " + t.getName(), NamedTextColor.GREEN);
            }
            default -> msg(s, "Unknown subcommand.", NamedTextColor.RED);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        if (!s.hasPermission("lavaweapons.admin")) return List.of();
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("give", "menu", "reload", "resetcd", "fixswap"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("give")) { for (Weapon w : Weapon.values()) out.add(w.id); out.add("all"); }
        else if ((a.length == 3 && a[0].equalsIgnoreCase("give")) || (a.length == 2 && a[0].equalsIgnoreCase("resetcd")))
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder)) return;
        e.setCancelled(true);
        ItemStack it = e.getCurrentItem();
        if (it == null || !(e.getWhoClicked() instanceof Player p)) return;
        Weapon w = pl.items.of(it);
        if (w != null) p.getInventory().addItem(pl.items.create(w));
    }
}
