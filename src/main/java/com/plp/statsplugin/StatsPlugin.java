package com.plp.statsplugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class StatsPlugin extends JavaPlugin {

    private StatsManager statsManager;
    private WebServer webServer;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        this.statsManager = new StatsManager(this);

        Bukkit.getPluginManager().registerEvents(statsManager, this);

        // ПРАВИЛЬНАЯ АСИНХРОННАЯ ПРЕДЗАГРУЗКА ВСЕХ СТАТОВ
        statsManager.preloadAllStatsAsync();

        // Периодическое обновление статистики онлайн игроков
        int intervalTicks = 20 * getConfig().getInt("update-interval-seconds", 60);
        if (intervalTicks > 0) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(
                    this,
                    statsManager::updateAllOnlinePlayers,
                    intervalTicks,
                    intervalTicks
            );
        }

        // WEB API
        int port = getConfig().getInt("web-port", 8080);
        webServer = new WebServer(statsManager);
        webServer.start(port);

        getLogger().info("Web API started on port " + port);
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
}
