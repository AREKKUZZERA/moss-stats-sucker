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
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

public class StatsManager implements Listener {

    private final StatsPlugin plugin;

    // Основной кэш статистики
    private final Map<UUID, JsonObject> statsCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> nameToUuid = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> uuidToName = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Boolean> onlinePlayers = new ConcurrentHashMap<>();

    public StatsManager(StatsPlugin plugin) {
        this.plugin = plugin;
    }

    // ============================
    // ASYNC ПРЕДЗАГРУЗКА ВСЕХ СТАТИСТИК
    // ============================
    public void preloadAllStatsAsync() {
        List<UUID> uuids = new ArrayList<>();
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player == null || player.getUniqueId() == null) {
                continue;
            }
            uuids.add(player.getUniqueId());
            cacheName(player.getUniqueId(), player.getName());
        }

        if (uuids.isEmpty()) {
            plugin.getLogger().info("[StatsPlugin] Нет оффлайн игроков для загрузки.");
            return;
        }

        plugin.getLogger().info("[StatsPlugin] Загружаю статистику оффлайн игроков: " + uuids.size());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> loadStatsForUuids(uuids, "оффлайн"));
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
        if (p == null || p.getUniqueId() == null) {
            return;
        }
        updatePlayerAsync(p.getUniqueId());
    }

    // ============================
    // Автообновление всех ONLINE игроков
    // ============================
    public void updateAllOnlinePlayers() {
        List<Player> onlineSnapshot = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.clear();
        for (Player player : onlineSnapshot) {
            onlinePlayers.put(player.getUniqueId(), Boolean.TRUE);
            cacheName(player.getUniqueId(), player.getName());
        }

        List<UUID> uuids = onlineSnapshot.stream()
                .map(Player::getUniqueId)
                .collect(Collectors.toList());

        if (!uuids.isEmpty()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> loadStatsForUuids(uuids, "онлайн"));
        }
    }

    // ============================
    // EVENTS
    // ============================
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        onlinePlayers.put(player.getUniqueId(), Boolean.TRUE);
        cacheName(player.getUniqueId(), player.getName());
        updatePlayer(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        onlinePlayers.remove(player.getUniqueId());
        cacheName(player.getUniqueId(), player.getName());
        updatePlayer(player);
    }

    // ============================
    // API UTIL METHODS
    // ============================

    public Map<UUID, JsonObject> getStatsCache() {
        return statsCache;
    }

    public UUID getUUID(String name) {
        if (name == null) {
            return null;
        }
        return nameToUuid.get(name.toLowerCase());
    }

    public String getPlayerName(UUID uuid) {
        if (uuid == null) {
            return "Unknown";
        }
        return uuidToName.getOrDefault(uuid, "Unknown");
    }

    public List<UUID> getOnlinePlayerIds() {
        return new ArrayList<>(onlinePlayers.keySet());
    }

    public Set<UUID> getOnlinePlayerIdSet() {
        return new HashSet<>(onlinePlayers.keySet());
    }

    private void updatePlayerAsync(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            JsonObject stats = StatsUtil.readStats(uuid);
            if (stats != null) {
                statsCache.put(uuid, stats);
            } else {
                statsCache.remove(uuid);
            }
        });
    }

    private void loadStatsForUuids(List<UUID> uuids, String label) {
        long start = System.currentTimeMillis();
        AtomicInteger loaded = new AtomicInteger();

        for (UUID uuid : uuids) {
            JsonObject stats = StatsUtil.readStats(uuid);
            if (stats != null) {
                statsCache.put(uuid, stats);
                loaded.incrementAndGet();
            } else {
                statsCache.remove(uuid);
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        plugin.getLogger().info("[StatsPlugin] Загружено " + loaded.get() + " статистик (" + label + ") за " + elapsed + " ms");
    }

    private void cacheName(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) {
            return;
        }
        String lower = name.toLowerCase();
        uuidToName.put(uuid, name);
        nameToUuid.put(lower, uuid);
    }
}
