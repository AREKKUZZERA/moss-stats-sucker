package com.plp.statsplugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.logging.Level;

public class StatsPlugin extends JavaPlugin {

    private StatsManager statsManager;
    private WebServer webServer;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        StatsUtil.setLogger(getLogger());
        StatsUtil.setStatsFolder(resolveStatsFolder());

        this.statsManager = new StatsManager(this);

        Bukkit.getPluginManager().registerEvents(statsManager, this);

        // ПРАВИЛЬНАЯ АСИНХРОННАЯ ПРЕДЗАГРУЗКА ВСЕХ СТАТОВ
        statsManager.preloadAllStatsAsync();

        // Периодическое обновление статистики онлайн игроков
        int intervalTicks = 20 * getConfig().getInt("update-interval-seconds", 60);
        if (intervalTicks > 0) {
            Bukkit.getScheduler().runTaskTimer(
                    this,
                    statsManager::updateAllOnlinePlayers,
                    intervalTicks,
                    intervalTicks
            );
        } else {
            getLogger().warning("update-interval-seconds <= 0, автообновление статистики отключено.");
        }

        // WEB API
        boolean webEnabled = getConfig().getBoolean("web.enabled", true);
        int port = getConfig().getInt("web.port", getConfig().getInt("web-port", 8080));
        String bindAddress = getConfig().getString("web.bind-address", "0.0.0.0");
        int maxPlayers = getConfig().getInt("web.max-response-players", 0);
        int maxTop = getConfig().getInt("web.max-top-results", 20);
        boolean corsEnabled = getConfig().getBoolean("web.cors.enabled", false);
        String corsAllowOrigin = getConfig().getString("web.cors.allow-origin", "*");

        if (webEnabled) {
            if (!isValidPort(port)) {
                getLogger().severe("Некорректный web-порт: " + port + ". Веб-сервер не запущен.");
            } else {
                WebServer.Settings settings = new WebServer.Settings(
                        resolveBindAddress(bindAddress),
                        Math.max(0, maxPlayers),
                        Math.max(1, maxTop),
                        corsEnabled,
                        corsAllowOrigin
                );
                webServer = new WebServer(statsManager, getLogger(), settings);
                webServer.start(port);
                getLogger().info("Web API started on " + settings.bindAddress().getHostAddress() + ":" + port);
            }
        } else {
            getLogger().info("Web API disabled via config.");
        }

        getLogger().info("StatsPlugin enabled");
    }

    @Override
    public void onDisable() {

        if (webServer != null) {
            webServer.stop();
        }

        getLogger().info("StatsPlugin disabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!cmd.getName().equalsIgnoreCase("stat"))
            return false;

        if (args.length != 2) {
            sender.sendMessage("Usage: /stat <player> <minecraft:xxx>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("Player not found.");
            return true;
        }

        String statKey = args[1];
        Integer value = statsManager.getStat(target.getUniqueId(), statKey);

        sender.sendMessage("Stats for " + target.getName() + ": " + statKey + " = " + value);
        return true;
    }

    private File resolveStatsFolder() {
        String customFolder = getConfig().getString("stats-folder", "").trim();
        if (!customFolder.isEmpty()) {
            File candidate = new File(customFolder);
            if (!candidate.isAbsolute()) {
                candidate = new File(getServer().getWorldContainer(), customFolder);
            }
            if (candidate.exists() && candidate.isDirectory()) {
                getLogger().info("Используется stats-folder: " + candidate.getAbsolutePath());
                return candidate;
            }
            getLogger().warning("stats-folder не найден или не является каталогом: " + candidate.getAbsolutePath());
        }

        String worldName = getConfig().getString("stats-world", "world");
        File statsDir = findStatsDirByWorld(worldName);
        if (statsDir != null) {
            getLogger().info("Используется stats каталог мира: " + statsDir.getAbsolutePath());
            return statsDir;
        }

        getLogger().warning("Не удалось найти stats каталог. Статистика может быть недоступна.");
        return null;
    }

    private File findStatsDirByWorld(String worldName) {
        if (worldName != null) {
            var world = Bukkit.getWorld(worldName);
            if (world != null) {
                File stats = new File(world.getWorldFolder(), "stats");
                if (stats.exists() && stats.isDirectory()) {
                    return stats;
                }
            }
        }

        List<org.bukkit.World> worlds = Bukkit.getWorlds();
        for (org.bukkit.World world : worlds) {
            File stats = new File(world.getWorldFolder(), "stats");
            if (stats.exists() && stats.isDirectory()) {
                return stats;
            }
        }
        return null;
    }

    private boolean isValidPort(int port) {
        return port > 0 && port <= 65535;
    }

    private InetAddress resolveBindAddress(String bindAddress) {
        try {
            return InetAddress.getByName(bindAddress);
        } catch (UnknownHostException e) {
            getLogger().log(Level.WARNING, "Некорректный bind-address: " + bindAddress + ". Использую 0.0.0.0");
            try {
                return InetAddress.getByName("0.0.0.0");
            } catch (UnknownHostException ignored) {
                return InetAddress.getLoopbackAddress();
            }
        }
    }
}
