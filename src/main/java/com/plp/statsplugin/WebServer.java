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

            // HTML-панель
            server.createContext("/moss/ui", this::handleUI);

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

            // ===== ДОБАВЛЕНО: имя игрока =====
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            o.addProperty("name", name != null ? name : "Unknown");

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

                // custom
                JsonObject custom = statRoot.getAsJsonObject("minecraft:custom");
                if (custom != null) {
                    if (custom.has("minecraft:jump"))
                        totalJumps += custom.get("minecraft:jump").getAsInt();
                    if (custom.has("minecraft:deaths"))
                        totalDeaths += custom.get("minecraft:deaths").getAsInt();
                    if (custom.has("minecraft:play_time"))
                        totalPlaytime += custom.get("minecraft:play_time").getAsInt();
                }

                // mined
                JsonObject mined = statRoot.getAsJsonObject("minecraft:mined");
                if (mined != null) {
                    for (String key : mined.keySet()) {
                        totalMinedBlocks += mined.get(key).getAsInt();
                    }
                }

                // crafted
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

    // /moss/top/jumps (старый, "alias" к /moss/top/minecraft:jump)
    private void handleTopJumps(HttpExchange ex) throws IOException {
        handleTopInternal(ex, "minecraft:jump");
    }

    // /moss/top/<stat_key> — универсальный
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

    // Внутренняя логика формирования топа
    private void handleTopInternal(HttpExchange ex, String statKey) throws IOException {
        List<Map.Entry<UUID, JsonObject>> players = new ArrayList<>(statsManager.getStatsCache().entrySet());

        players.sort((a, b) -> {
            int av = StatsUtil.getAnyStat(a.getValue(), statKey);
            int bv = StatsUtil.getAnyStat(b.getValue(), statKey);
            return Integer.compare(bv, av); // по убыванию
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

    // /moss/ui — простая HTML панель
    private void handleUI(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) {
            send(ex, 405, "Method Not Allowed", "text/plain");
            return;
        }

        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Moss Stats Panel</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 20px; background: #111; color: #eee; }
                        h1, h2 { color: #6cf; }
                        .card { background: #1b1b1b; padding: 15px; margin-bottom: 20px; border-radius: 8px; }
                        table { border-collapse: collapse; width: 100%; }
                        th, td { border: 1px solid #333; padding: 8px; text-align: left; }
                        th { background: #222; }
                        input, button { padding: 6px 10px; margin: 2px; background: #222; color: #eee; border: 1px solid #555; border-radius: 4px; }
                        button { cursor: pointer; }
                        button:hover { background: #333; }
                        pre { background: #000; padding: 10px; border-radius: 5px; }
                    </style>
                </head>
                <body>

                <h1>Moss Stats Panel</h1>

                <div class="card">
                    <h2>Summary</h2>
                    <pre id="summary">Loading...</pre>
                </div>

                <div class="card">
                    <h2>Top by Stat</h2>
                    <div>
                        Stat key: <input id="statKey" value="minecraft:jump" />
                        <button onclick="loadTop()">Load</button>
                    </div>
                    <table>
                        <thead>
                            <tr><th>#</th><th>UUID</th><th>Value</th></tr>
                        </thead>
                        <tbody id="topTable"></tbody>
                    </table>
                </div>

                <div class="card">
                    <h2>Online Players</h2>
                    <table>
                        <thead>
                            <tr><th>UUID</th><th>Name</th></tr>
                        </thead>
                        <tbody id="onlineTable"></tbody>
                    </table>
                </div>

                <div class="card">
                    <h2>Player Lookup (By Name or UUID)</h2>
                    <div>
                        <input id="lookupInput" placeholder="Enter player name or UUID" style="width:300px;">
                        <button onclick="lookupPlayer()">Lookup</button>
                    </div>
                    <pre id="lookupResult">Enter a name or UUID...</pre>
                </div>

                <script>
                async function loadSummary() {
                    const res = await fetch('/moss/summary');
                    const data = await res.json();
                    document.getElementById('summary').textContent = JSON.stringify(data, null, 2);
                }

                async function loadOnline() {
                    const res = await fetch('/moss/online');
                    const data = await res.json();
                    const tbody = document.getElementById('onlineTable');
                    tbody.innerHTML = '';
                    data.forEach(p => {
                        const tr = document.createElement('tr');
                        tr.innerHTML = '<td>' + p.uuid + '</td><td>' + p.name + '</td>';
                        tbody.appendChild(tr);
                    });
                }

                async function loadTop() {
                    const key = encodeURIComponent(document.getElementById('statKey').value);
                    const res = await fetch('/moss/top/' + key);
                    const data = await res.json();
                    const tbody = document.getElementById('topTable');
                    tbody.innerHTML = '';
                    data.forEach((row, idx) => {
                        const tr = document.createElement('tr');
                        tr.innerHTML =
                            '<td>' + (idx + 1) + '</td>' +
                            '<td>' + row.uuid + '</td>' +
                            '<td>' + row.value + '</td>';
                        tbody.appendChild(tr);
                    });
                }

                async function lookupPlayer() {
                    const input = document.getElementById('lookupInput').value.trim();
                    let res;

                    try {
                        // Try UUID lookup
                        if (input.length >= 32) {
                            res = await fetch('/moss/players/' + input);
                        } else {
                            // Lookup by name
                            res = await fetch('/moss/player/' + encodeURIComponent(input));
                        }

                        if (!res.ok) {
                            document.getElementById('lookupResult').textContent =
                                "Player not found or error: " + res.status;
                            return;
                        }

                        const data = await res.json();
                        document.getElementById('lookupResult')
                            .textContent = JSON.stringify(data, null, 2);

                    } catch (e) {
                        document.getElementById('lookupResult').textContent = "Error: " + e;
                    }
                }

                loadSummary();
                loadOnline();
                loadTop();
                setInterval(loadOnline, 10000);
                </script>

                </body>
                </html>
                """;

        send(ex, 200, html, "text/html; charset=UTF-8");
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