package com.plp.statsplugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import java.io.File;
import java.io.FileReader;

public class StatsUtil {

    private static final Gson gson = new Gson();

    // Кэш stats/ директории, чтобы не искать каждый раз
    private static File cachedStatsFolder = null;

    /**
     * Поиск каталога stats/. Один раз, потом используется кэш.
     */
    private static File getStatsFolder() {

        // Если ранее уже нашли — используем
        if (cachedStatsFolder != null && cachedStatsFolder.exists()) {
            return cachedStatsFolder;
        }

        // Пробуем сначала основной мир "world"
        World main = Bukkit.getWorld("world");
        if (main != null) {
            File stats = new File(main.getWorldFolder(), "stats");
            if (stats.exists() && stats.isDirectory()) {
                cachedStatsFolder = stats;
                return cachedStatsFolder;
            }
        }

        // Фолбэк — ищем первый попавшийся stats/
        for (World w : Bukkit.getWorlds()) {
            if (w == null) continue;

            File stats = new File(w.getWorldFolder(), "stats");
            if (stats.exists() && stats.isDirectory()) {
                cachedStatsFolder = stats;
                return cachedStatsFolder;
            }
        }

        Bukkit.getLogger().warning("[StatsPlugin] Не найден каталог stats/ ни в одном мире!");
        return null;
    }

    /**
     * Чтение JSON статистики игрока
     */
    public static JsonObject readStats(OfflinePlayer player) {
        File statsDir = getStatsFolder();
        if (statsDir == null) return null;

        File statsFile = new File(statsDir, player.getUniqueId() + ".json");

        if (!statsFile.exists())
            return null;

        try (FileReader reader = new FileReader(statsFile)) {
            return gson.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[StatsPlugin] Ошибка чтения статистики: " + statsFile.getAbsolutePath());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Чтение custom статистики (minecraft:custom)
     */
    public static int getCustomStat(JsonObject root, String statKey) {
        if (root == null) return 0;

        try {
            JsonObject stats = root.getAsJsonObject("stats");
            if (stats == null) return 0;

            JsonObject custom = stats.getAsJsonObject("minecraft:custom");
            if (custom == null) return 0;

            if (custom.has(statKey)) {
                return custom.get(statKey).getAsInt();
            }
        } catch (Exception ignored) {}

        return 0;
    }

    /**
     * Универсальный поиск статистики:
     * custom / mined / crafted / used / broken / picked_up / dropped
     */
    public static int getAnyStat(JsonObject root, String statKey) {
        if (root == null) return 0;

        JsonObject statsRoot;
        try {
            statsRoot = root.getAsJsonObject("stats");
        } catch (Exception e) {
            return 0;
        }

        if (statsRoot == null) return 0;

        String[] sections = new String[]{
                "minecraft:custom",
                "minecraft:mined",
                "minecraft:crafted",
                "minecraft:used",
                "minecraft:broken",
                "minecraft:picked_up",
                "minecraft:dropped"
        };

        for (String section : sections) {
            try {
                JsonObject sec = statsRoot.getAsJsonObject(section);
                if (sec == null) continue;

                if (sec.has(statKey)) {
                    return sec.get(statKey).getAsInt();
                }
            } catch (Exception ignored) {}
        }

        return 0;
    }
}