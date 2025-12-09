package com.plp.statsplugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WebServer {

    private final StatsManager statsManager;
    private HttpServer server;
    private final Gson gson = new Gson();

    public WebServer(StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    public void start(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/moss/players", this::handleAllPlayers);
            server.createContext("/moss/players/", this::handlePlayerByUUID);
            server.createContext("/moss/player/", this::handlePlayerByName);
            server.createContext("/moss/online", this::handleOnline);
            server.createContext("/moss/summary", this::handleSummary);

            // Старый фиксированный топ по прыжкам
            server.createContext("/moss/top/jumps", this::handleTopJumps);

            // Универсальный топ: /moss/top/<stat_key>
            server.createContext("/moss/top/", this::handleTopGeneric);

            server.setExecutor(null);
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // /moss/players
    private void handleAllPlayers(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) {
            send(ex, 405, "Method Not Allowed", "text/plain");
            return;
        }

        JsonArray arr = new JsonArray();

        for (UUID uuid : statsManager.getStatsCache().keySet()) {

            JsonObject o = new JsonObject();
            o.addProperty("uuid", uuid.toString());

            // Имя игрока
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            o.addProperty("name", name != null ? name : "Unknown");

            // Статус онлайн
            boolean isOnline = Bukkit.getPlayer(uuid) != null;
            o.addProperty("online", isOnline);

            // Полная статистика
            o.add("stats", statsManager.getFullStats(uuid));

            arr.add(o);
        }

        send(ex, 200, gson.toJson(arr), "application/json; charset=UTF-8");
    }

    // /moss/players/<uuid>
    private void handlePlayerByUUID(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) {
            send(ex, 405, "Method Not Allowed", "text/plain");
            return;
        }

        String[] parts = ex.getRequestURI().getPath().split("/");
        if (parts.length < 4) {
            send(ex, 400, "Usage: /moss/players/<uuid>", "text/plain");
            return;
        }

        try {
            UUID uuid = UUID.fromString(parts[3]);
            JsonObject stats = statsManager.getFullStats(uuid);
            send(ex, 200, gson.toJson(stats), "application/json; charset=UTF-8");
        } catch (Exception e) {
            send(ex, 400, "Invalid UUID", "text/plain");
        }
    }

    // /moss/player/<name>
    private void handlePlayerByName(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) {
            send(ex, 405, "Method Not Allowed", "text/plain");
            return;
        }

        String[] parts = ex.getRequestURI().getPath().split("/");
        if (parts.length < 4) {
            send(ex, 400, "Usage: /moss/player/<name>", "text/plain");
            return;
        }

        String nameEncoded = parts[3];
        String name = URLDecoder.decode(nameEncoded, StandardCharsets.UTF_8);

        UUID uuid = statsManager.getUUID(name);

        if (uuid == null) {
            send(ex, 404, "Player not found", "text/plain");
            return;
        }

        JsonObject stats = statsManager.getFullStats(uuid);
        send(ex, 200, gson.toJson(stats), "application/json; charset=UTF-8");
    }

    // /moss/online
    private void handleOnline(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) {
            send(ex, 405, "Method Not Allowed", "text/plain");
            return;
        }

        JsonArray arr = new JsonArray();

        for (Player p : Bukkit.getOnlinePlayers()) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", p.getUniqueId().toString());
            o.addProperty("name", p.getName());
            o.add("stats", statsManager.getFullStats(p.getUniqueId()));
            arr.add(o);
        }

        send(ex, 200, gson.toJson(arr), "application/json; charset=UTF-8");
    }

    // /moss/summary
    private void handleSummary(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) {
            send(ex, 405, "Method Not Allowed", "text/plain");
            return;
        }

        int totalPlayers = statsManager.getStatsCache().size();
        int totalJumps = 0;
        int totalDeaths = 0;
        int totalPlaytime = 0;
        int totalMinedBlocks = 0;
        int totalCraftedItems = 0;

        for (JsonObject player : statsManager.getStatsCache().values()) {
            try {
                JsonObject statRoot = player.getAsJsonObject("stats");
                if (statRoot == null)
                    continue;

                JsonObject custom = statRoot.getAsJsonObject("minecraft:custom");
                if (custom != null) {
                    if (custom.has("minecraft:jump"))
                        totalJumps += custom.get("minecraft:jump").getAsInt();
                    if (custom.has("minecraft:deaths"))
                        totalDeaths += custom.get("minecraft:deaths").getAsInt();
                    if (custom.has("minecraft:play_time"))
                        totalPlaytime += custom.get("minecraft:play_time").getAsInt();
                }

                JsonObject mined = statRoot.getAsJsonObject("minecraft:mined");
                if (mined != null) {
                    for (String key : mined.keySet()) {
                        totalMinedBlocks += mined.get(key).getAsInt();
                    }
                }

                JsonObject crafted = statRoot.getAsJsonObject("minecraft:crafted");
                if (crafted != null) {
                    for (String key : crafted.keySet()) {
                        totalCraftedItems += crafted.get(key).getAsInt();
                    }
                }

            } catch (Exception ignored) {
            }
        }

        JsonObject out = new JsonObject();
        JsonObject totals = new JsonObject();

        out.addProperty("players", totalPlayers);
        totals.addProperty("total_jumps", totalJumps);
        totals.addProperty("total_deaths", totalDeaths);
        totals.addProperty("total_playtime", totalPlaytime);
        totals.addProperty("blocks_mined", totalMinedBlocks);
        totals.addProperty("items_crafted", totalCraftedItems);

        out.add("totals", totals);

        send(ex, 200, gson.toJson(out), "application/json; charset=UTF-8");
    }

    // /moss/top/jumps
    private void handleTopJumps(HttpExchange ex) throws IOException {
        handleTopInternal(ex, "minecraft:jump");
    }

    // /moss/top/<stat_key>
    private void handleTopGeneric(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) {
            send(ex, 405, "Method Not Allowed", "text/plain");
            return;
        }

        String[] parts = ex.getRequestURI().getPath().split("/");
        if (parts.length < 4) {
            send(ex, 400, "Usage: /moss/top/<stat_key>", "text/plain");
            return;
        }

        String encodedKey = parts[3];
        String statKey = URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);

        handleTopInternal(ex, statKey);
    }

    private void handleTopInternal(HttpExchange ex, String statKey) throws IOException {
        List<Map.Entry<UUID, JsonObject>> players = new ArrayList<>(statsManager.getStatsCache().entrySet());

        players.sort((a, b) -> {
            int av = StatsUtil.getAnyStat(a.getValue(), statKey);
            int bv = StatsUtil.getAnyStat(b.getValue(), statKey);
            return Integer.compare(bv, av);
        });

        JsonArray arr = new JsonArray();

        for (int i = 0; i < Math.min(20, players.size()); i++) {
            UUID uuid = players.get(i).getKey();
            int value = StatsUtil.getAnyStat(players.get(i).getValue(), statKey);

            JsonObject o = new JsonObject();
            o.addProperty("uuid", uuid.toString());
            o.addProperty("value", value);
            o.addProperty("stat_key", statKey);
            arr.add(o);
        }

        send(ex, 200, gson.toJson(arr), "application/json; charset=UTF-8");
    }

    private void send(HttpExchange exchange, int code, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(code, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public void stop() {
        if (server != null)
            server.stop(0);
    }
}
