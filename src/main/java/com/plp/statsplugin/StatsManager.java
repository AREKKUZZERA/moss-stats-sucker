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

    // Основной кэш статистики
    private final Map<UUID, JsonObject> statsCache = new ConcurrentHashMap<>();

    public StatsManager(StatsPlugin plugin) {
        this.plugin = plugin;
    }

    // ============================
    // ASYNC ПРЕДЗАГРУЗКА ВСЕХ СТАТИСТИК
    // ============================
    public void preloadAllStatsAsync() {

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            long start = System.currentTimeMillis();

            plugin.getLogger().info("[StatsPlugin] Загружаю статистику оффлайн игроков...");

            int loaded = 0;

            for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
                JsonObject stats = StatsUtil.readStats(p);
                if (stats != null) {
                    statsCache.put(p.getUniqueId(), stats);
                    loaded++;
                }
            }

            long elapsed = System.currentTimeMillis() - start;

            plugin.getLogger().info("[StatsPlugin] Загружено: " + loaded +
                    " игроков (" + elapsed + " ms)");
        });
    }

    // ============================
    // Чтение статистики
    // ============================
    public Integer getStat(UUID uuid, String statKey) {
        JsonObject obj = statsCache.get(uuid);
        if (obj == null) return 0;

        return StatsUtil.getAnyStat(obj, statKey);
    }

    public JsonObject getFullStats(UUID uuid) {
        return statsCache.getOrDefault(uuid, new JsonObject());
    }

    // ============================
    // Ручное обновление одного игрока
    // ============================
    public void updatePlayer(Player p) {
        JsonObject stats = StatsUtil.readStats(p);
        if (stats != null) {
            statsCache.put(p.getUniqueId(), stats);
        }
    }

    // ============================
    // Автообновление всех ONLINE игроков
    // ============================
    public void updateAllOnlinePlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayer(p);
        }
    }

    // ============================
    // EVENTS
    // ============================
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> updatePlayer(e.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> updatePlayer(e.getPlayer()));
    }

    // ============================
    // API UTIL METHODS
    // ============================

    public Map<UUID, JsonObject> getStatsCache() {
        return statsCache;
    }

    public UUID getUUID(String name) {
        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            if (p.getName() != null && p.getName().equalsIgnoreCase(name)) {
                return p.getUniqueId();
            }
        }
        return null;
    }
}
