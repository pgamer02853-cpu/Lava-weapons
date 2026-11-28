package com.rizgamer.lavaweapons;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Tiny in-game rank system: /rank ... (prefix in chat + tab, permissions). */
public final class Ranks implements Listener, CommandExecutor, TabCompleter {
    public static final class Rank {
        final String name;
        String prefix = "";
        boolean all = false;
        final LinkedHashSet<String> perms = new LinkedHashSet<>();
        Rank(String name) { this.name = name; }
    }

    public static final List<String> ESSENTIALS = List.of(
            "essentials.spawn", "essentials.home", "essentials.sethome", "essentials.sethome.multiple",
            "essentials.delhome", "essentials.tpa", "essentials.tpahere", "essentials.tpaccept",
            "essentials.tpdeny", "essentials.tpcancel", "essentials.back", "essentials.warp",
            "essentials.warp.list", "essentials.msg", "essentials.mail", "essentials.mail.send",
            "essentials.help", "essentials.list", "essentials.ignore", "essentials.afk");

    private final LavaWeapons pl;
    private final File file;
    private final Map<String, Rank> ranks = new ConcurrentHashMap<>();
    private final Map<UUID, String> players = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private volatile String defaultRank = null;

    Ranks(LavaWeapons pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "ranks.yml");
        load();
    }

    // ------------------------------------------------------------ storage
    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        defaultRank = y.getString("default");
        ConfigurationSection rs = y.getConfigurationSection("ranks");
        if (rs != null) {
            for (String k : rs.getKeys(false)) {
                Rank r = new Rank(k);
                r.prefix = rs.getString(k + ".prefix", "");
                r.all = rs.getBoolean(k + ".all", false);
                r.perms.addAll(rs.getStringList(k + ".permissions"));
                ranks.put(k.toLowerCase(Locale.ROOT), r);
            }
        }
        ConfigurationSection ps = y.getConfigurationSection("players");
        if (ps != null) {
            for (String k : ps.getKeys(false)) {
                try { players.put(UUID.fromString(k), ps.getString(k, "")); } catch (IllegalArgumentException ignored) { }
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        if (defaultRank != null) y.set("default", defaultRank);
        for (Rank r : ranks.values()) {
            y.set("ranks." + r.name + ".prefix", r.prefix);
            y.set("ranks." + r.name + ".all", r.all);
            y.set("ranks." + r.name + ".permissions", new ArrayList<>(r.perms));
        }
        for (Map.Entry<UUID, String> e : players.entrySet()) y.set("players." + e.getKey(), e.getValue());
        try {
            pl.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            pl.getLogger().warning("Could not save ranks.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ apply
    private Rank rankOf(UUID id) {
        String n = players.get(id);
        Rank r = n == null ? null : ranks.get(n.toLowerCase(Locale.ROOT));
        if (r == null && defaultRank != null) r = ranks.get(defaultRank.toLowerCase(Locale.ROOT));
        return r;
    }

    private static Component prefixOf(Rank r) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(r.prefix);
    }

    public void apply(Player p) {
        remove(p);
        Rank r = rankOf(p.getUniqueId());
        if (r != null) {
            PermissionAttachment a = p.addAttachment(pl);
            if (r.all) for (Permission perm : Bukkit.getPluginManager().getPermissions()) a.setPermission(perm.getName(), true);
            for (String node : r.perms) {
                if (node.startsWith("-")) a.setPermission(node.substring(1), false);
                else a.setPermission(node, true);
            }
            attachments.put(p.getUniqueId(), a);
        }
        p.recalculatePermissions();
        p.updateCommands();
        if (pl.getConfig().getBoolean("ranks.tab", true)) {
            Component name = Component.text(p.getName());
            if (r != null && !r.prefix.isEmpty()) name = prefixOf(r).append(name);
            p.playerListName(name);
        }
    }

    public void remove(Player p) {
        PermissionAttachment a = attachments.remove(p.getUniqueId());
        if (a != null) {
            try { p.removeAttachment(a); } catch (IllegalArgumentException ignored) { }
        }
    }

    public void applyAll() { for (Player p : Bukkit.getOnlinePlayers()) apply(p); }
    public void removeAll() { for (Player p : Bukkit.getOnlinePlayers()) remove(p); }

    @EventHandler public void onJoin(PlayerJoinEvent e) { apply(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { remove(e.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent e) {
        if (!pl.getConfig().getBoolean("ranks.chat", true)) return;
        Rank r = rankOf(e.getPlayer().getUniqueId());
        if (r == null || r.prefix.isEmpty()) return;
        final Component pre = prefixOf(r);
        e.renderer(ChatRenderer.viewerUnaware((source, name, msg) ->
                pre.append(name).append(Component.text(": ")).append(msg)));
    }

    // ------------------------------------------------------------ commands
    private void msg(CommandSender s, String t, NamedTextColor c) { s.sendMessage(Component.text(t, c)); }

    private Rank find(CommandSender s, String n) {
        Rank r = ranks.get(n.toLowerCase(Locale.ROOT));
        if (r == null) msg(s, "Rank not found: " + n, NamedTextColor.RED);
        return r;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("lavaweapons.admin")) { msg(s, "No permission!", NamedTextColor.RED); return true; }
        if (a.length == 0) {
            msg(s, "/rank create <name> [prefix] | delete <name> | prefix <name> <prefix> | addperm <name> <perm> | delperm <name> <perm>", NamedTextColor.YELLOW);
            msg(s, "/rank allperms <name> <on|off> | preset <name> essentials | give <player> <rank> | take <player> | default <name> | list | info <name>", NamedTextColor.YELLOW);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (a.length < 2) { msg(s, "Use: /rank create <name> [prefix]", NamedTextColor.RED); return true; }
                String key = a[1].toLowerCase(Locale.ROOT);
                if (ranks.containsKey(key)) { msg(s, "Rank already exists.", NamedTextColor.RED); return true; }
                Rank r = new Rank(a[1]);
                if (a.length >= 3) r.prefix = String.join(" ", Arrays.copyOfRange(a, 2, a.length)) + " ";
                ranks.put(key, r);
                if (defaultRank == null) defaultRank = key;
                save();
                msg(s, "Rank created: " + a[1], NamedTextColor.GREEN);
            }
            case "delete" -> {
                if (a.length < 2) return true;
                Rank r = find(s, a[1]); if (r == null) return true;
                String key = r.name.toLowerCase(Locale.ROOT);
                ranks.remove(key);
                players.values().removeIf(v -> v.equalsIgnoreCase(key));
                if (key.equalsIgnoreCase(defaultRank)) defaultRank = null;
                save(); applyAll();
                msg(s, "Rank deleted.", NamedTextColor.GREEN);
            }
            case "prefix" -> {
                if (a.length < 3) { msg(s, "Use: /rank prefix <name> <prefix>", NamedTextColor.RED); return true; }
                Rank r = find(s, a[1]); if (r == null) return true;
                r.prefix = String.join(" ", Arrays.copyOfRange(a, 2, a.length)) + " ";
                save(); applyAll();
                msg(s, "Prefix set.", NamedTextColor.GREEN);
            }
            case "addperm" -> {
                if (a.length < 3) return true;
                Rank r = find(s, a[1]); if (r == null) return true;
                r.perms.add(a[2].toLowerCase(Locale.ROOT)); save(); applyAll();
                msg(s, "Added " + a[2] + " to " + r.name, NamedTextColor.GREEN);
            }
            case "delperm" -> {
                if (a.length < 3) return true;
                Rank r = find(s, a[1]); if (r == null) return true;
                r.perms.remove(a[2].toLowerCase(Locale.ROOT)); save(); applyAll();
                msg(s, "Removed " + a[2] + " from " + r.name, NamedTextColor.GREEN);
            }
            case "allperms" -> {
                if (a.length < 3) { msg(s, "Use: /rank allperms <name> <on|off>", NamedTextColor.RED); return true; }
                Rank r = find(s, a[1]); if (r == null) return true;
                r.all = a[2].equalsIgnoreCase("on"); save(); applyAll();
                msg(s, r.name + " all permissions: " + (r.all ? "ON" : "OFF"), NamedTextColor.GREEN);
            }
            case "preset" -> {
                if (a.length < 3 || !a[2].equalsIgnoreCase("essentials")) { msg(s, "Use: /rank preset <name> essentials", NamedTextColor.RED); return true; }
                Rank r = find(s, a[1]); if (r == null) return true;
                r.perms.addAll(ESSENTIALS); save(); applyAll();
                msg(s, "Essentials basics (spawn, home, sethome, tpa, back, warp, msg...) added to " + r.name, NamedTextColor.GREEN);
            }
            case "give" -> {
                if (a.length < 3) { msg(s, "Use: /rank give <player> <rank>", NamedTextColor.RED); return true; }
                Rank r = find(s, a[2]); if (r == null) return true;
                UUID id = resolve(a[1]);
                if (id == null) { msg(s, "Player not found.", NamedTextColor.RED); return true; }
                players.put(id, r.name); save();
                Player on = Bukkit.getPlayer(id); if (on != null) apply(on);
                msg(s, a[1] + " is now " + r.name, NamedTextColor.GREEN);
            }
            case "take" -> {
                if (a.length < 2) return true;
                UUID id = resolve(a[1]);
                if (id == null) { msg(s, "Player not found.", NamedTextColor.RED); return true; }
                players.remove(id); save();
                Player on = Bukkit.getPlayer(id); if (on != null) apply(on);
                msg(s, a[1] + " back to default rank.", NamedTextColor.GREEN);
            }
            case "default" -> {
                if (a.length < 2) return true;
                Rank r = find(s, a[1]); if (r == null) return true;
                defaultRank = r.name.toLowerCase(Locale.ROOT); save(); applyAll();
                msg(s, "Default rank: " + r.name, NamedTextColor.GREEN);
            }
            case "list" -> {
                if (ranks.isEmpty()) { msg(s, "No ranks yet. Use /rank create", NamedTextColor.YELLOW); return true; }
                for (Rank r : ranks.values())
                    msg(s, r.name + (r.name.equalsIgnoreCase(defaultRank) ? " (default)" : "") + "  prefix: " + r.prefix.trim()
                            + "  perms: " + r.perms.size() + (r.all ? "  ALL" : ""), NamedTextColor.GRAY);
            }
            case "info" -> {
                if (a.length < 2) return true;
                Rank r = find(s, a[1]); if (r == null) return true;
                msg(s, r.name + " | prefix: " + r.prefix.trim() + " | all: " + r.all, NamedTextColor.GOLD);
                msg(s, String.join(", ", r.perms), NamedTextColor.GRAY);
            }
            default -> msg(s, "Unknown subcommand. Type /rank", NamedTextColor.RED);
        }
        return true;
    }

    private UUID resolve(String name) {
        Player on = Bukkit.getPlayerExact(name);
        if (on != null) return on.getUniqueId();
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        return op == null ? null : op.getUniqueId();
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        if (!s.hasPermission("lavaweapons.admin")) return List.of();
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("create", "delete", "prefix", "addperm", "delperm", "allperms", "preset", "give", "take", "default", "list", "info"));
        } else if (a.length == 2) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (sub.equals("give") || sub.equals("take")) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            else if (!sub.equals("create") && !sub.equals("list")) for (Rank r : ranks.values()) out.add(r.name);
        } else if (a.length == 3) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (sub.equals("give")) for (Rank r : ranks.values()) out.add(r.name);
            else if (sub.equals("allperms")) out.addAll(List.of("on", "off"));
            else if (sub.equals("preset")) out.add("essentials");
            else if (sub.equals("addperm")) out.addAll(ESSENTIALS);
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
