package com.plp.statsplugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.io.FileReader;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StatsUtil {

    private static final Gson gson = new Gson();

    // Кэш stats/ директории, чтобы не искать каждый раз
    private static volatile File cachedStatsFolder = null;
    private static volatile Logger logger = null;

    /**
     * Поиск каталога stats/. Один раз, потом используется кэш.
     */
    private static File getStatsFolder() {

        // Если ранее уже нашли — используем
        if (cachedStatsFolder != null && cachedStatsFolder.exists()) {
            return cachedStatsFolder;
        }

        log(Level.WARNING, "Не найден каталог stats/. Убедитесь, что путь задан в конфиге.");
        return null;
    }

    public static void setStatsFolder(File statsFolder) {
        cachedStatsFolder = statsFolder;
    }

    public static void setLogger(Logger pluginLogger) {
        logger = pluginLogger;
    }

    /**
     * Чтение JSON статистики игрока
     */
    public static JsonObject readStats(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return null;
        }
        return readStats(player.getUniqueId());
    }

    public static JsonObject readStats(UUID uuid) {
        File statsDir = getStatsFolder();
        if (statsDir == null || uuid == null) return null;

        File statsFile = new File(statsDir, uuid + ".json");

        if (!statsFile.exists()) {
            return null;
        }

        try (FileReader reader = new FileReader(statsFile)) {
            return gson.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            log(Level.WARNING, "Ошибка чтения статистики: " + statsFile.getAbsolutePath());
            log(Level.FINE, "Ошибка чтения статистики: " + e.getMessage());
            return null;
        }
    }

    private static void log(Level level, String message) {
        if (logger != null) {
            logger.log(level, message);
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
