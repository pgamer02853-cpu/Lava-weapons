package com.rizgamer.lavaweapons;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Lava Weapons - made by RIZGAMER */
public final class LavaWeapons extends JavaPlugin {
    private static LavaWeapons inst;
    public Items items;
    public Cooldowns cd;
    public Zones zones;
    public State st;
    public Abilities ab;
    public Volcano volcano;
    public Ranks ranks;

    public static LavaWeapons get() { return inst; }

    @Override
    public void onEnable() {
        inst = this;
        saveDefaultConfig();
        st = new State();
        items = new Items(this);
        cd = new Cooldowns(this);
        zones = new Zones(this);
        volcano = new Volcano(this);
        ab = new Abilities(this);

        Bukkit.getPluginManager().registerEvents(new Events(this), this);
        LavaCommand cmd = new LavaCommand(this);
        Bukkit.getPluginManager().registerEvents(cmd, this);
        PluginCommand pc = getCommand("lava");
        if (pc != null) { pc.setExecutor(cmd); pc.setTabCompleter(cmd); }

        new BukkitRunnable() {
            @Override public void run() { for (Player p : Bukkit.getOnlinePlayers()) Dmg.updateBlessing(p); }
        }.runTaskTimer(this, 40L, 20L);
        new HeldFx(this);

        ranks = new Ranks(this);
        Bukkit.getPluginManager().registerEvents(ranks, this);
        Bukkit.getPluginManager().registerEvents(new RespawnFix(this), this);
        PluginCommand rc = getCommand("rank");
        if (rc != null) { rc.setExecutor(ranks); rc.setTabCompleter(ranks); }
        ranks.applyAll();
        if (SwapFix.state() == 1)
            getLogger().warning("Attribute swapping is OFF on this Paper server. Run /lava fixswap and restart to enable lunge/breach swapping.");
        getLogger().info("LavaWeapons enabled - made by RIZGAMER");
    }

    @Override
    public void onDisable() {
        if (ranks != null) ranks.removeAll();
        if (volcano != null) volcano.restoreAllNow();
        if (zones != null) zones.clear();
    }
}
