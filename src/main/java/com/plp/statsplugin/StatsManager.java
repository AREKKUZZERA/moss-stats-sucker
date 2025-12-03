package com.plp.statsplugin;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StatsManager implements Listener {

    private final StatsPlugin plugin;
    final Map<UUID, JsonObject> statsCache = new ConcurrentHashMap<>();

    public StatsManager(StatsPlugin plugin) {
        this.plugin = plugin;
    }

    // Загружаем ВСЕХ оффлайн игроков (stats/<uuid>.json)
    public void loadAllOfflineStats() {
        plugin.getLogger().info("Loading offline player statistics...");

        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            JsonObject stats = StatsUtil.readStats(p);
            if (stats != null) {
                statsCache.put(p.getUniqueId(), stats);
            }
        }

        plugin.getLogger().info("Loaded " + statsCache.size() + " offline player stats.");
    }

    public Integer getStat(UUID uuid, String statKey) {
        JsonObject obj = statsCache.get(uuid);
        if (obj == null)
            return 0;

        try {
            return StatsUtil.getCustomStat(obj, statKey);
        } catch (Exception e) {
            return 0;
        }
    }

    public void updatePlayer(Player p) {
        JsonObject stats = StatsUtil.readStats(p);
        if (stats != null) {
            statsCache.put(p.getUniqueId(), stats);
        }
    }

    public void updateAllOnlinePlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayer(p);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> updatePlayer(e.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> updatePlayer(e.getPlayer()));
    }

    // API access
    public Map<UUID, JsonObject> getStatsCache() {
        return statsCache;
    }

    public UUID getUUID(String name) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(name);
        return p.hasPlayedBefore() ? p.getUniqueId() : null;
    }

    public JsonObject getFullStats(UUID uuid) {
        return statsCache.getOrDefault(uuid, new JsonObject());
    }
}