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

    private static File getStatsFolder() {

        for (World w : Bukkit.getWorlds()) {
            File folder = w.getWorldFolder();
            File statsDir = new File(folder, "stats");

            if (statsDir.exists() && statsDir.isDirectory()) {
                return statsDir;
            }
        }

        Bukkit.getLogger().warning("[StatsPlugin] Не найдено подходящего stats/ каталога ни в одном мире!");
        return null;
    }

    public static JsonObject readStats(OfflinePlayer player) {
        File statsDir = getStatsFolder();
        if (statsDir == null) return null;

        File statsFile = new File(statsDir, player.getUniqueId() + ".json");
        if (!statsFile.exists()) return null;

        try (FileReader reader = new FileReader(statsFile)) {
            return gson.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[StatsPlugin] Ошибка чтения файла статистики: " + statsFile.getAbsolutePath());
            e.printStackTrace();
            return null;
        }
    }

    public static Integer getCustomStat(JsonObject root, String statKey) {
        try {
            return root
                    .getAsJsonObject("stats")
                    .getAsJsonObject("minecraft:custom")
                    .getAsJsonPrimitive(statKey)
                    .getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }

    public static int getAnyStat(JsonObject root, String statKey) {
        try {
            JsonObject statsRoot = root.getAsJsonObject("stats");
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
                JsonObject sec = statsRoot.getAsJsonObject(section);
                if (sec == null) continue;
                if (sec.has(statKey)) {
                    return sec.get(statKey).getAsInt();
                }
            }
        } catch (Exception ignored) {}

        return 0;
    }
}
