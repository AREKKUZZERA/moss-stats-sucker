package com.plp.statsplugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.io.FileReader;

public class StatsUtil {

    private static final Gson gson = new Gson();

    public static JsonObject readStats(OfflinePlayer player) {
        // Папка мира "world" — при необходимости поменяй на свой
        File worldFolder = Bukkit.getWorld("world").getWorldFolder();
        File statsFile = new File(worldFolder, "stats/" + player.getUniqueId() + ".json");

        if (!statsFile.exists())
            return null;

        try (FileReader reader = new FileReader(statsFile)) {
            return gson.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
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

    /**
     * Универсальный getter для статистики.
     * Ищет ключ statKey по стандартным секциям:
     * custom, mined, crafted, used, broken, picked_up, dropped.
     *
     * Пример statKey:
     * - "minecraft:jump"
     * - "minecraft:stone"
     */
    public static int getAnyStat(JsonObject root, String statKey) {
        try {
            JsonObject statsRoot = root.getAsJsonObject("stats");
            if (statsRoot == null)
                return 0;

            String[] sections = new String[] {
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
                if (sec == null)
                    continue;
                if (sec.has(statKey)) {
                    return sec.get(statKey).getAsInt();
                }
            }

            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
}