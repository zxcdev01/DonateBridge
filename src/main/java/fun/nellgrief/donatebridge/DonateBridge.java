package fun.nellgrief.donatebridge;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class DonateBridge extends JavaPlugin implements CommandExecutor {

    private BridgeHttpServer httpServer;
    private RankGiver rankGiver;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String secret = getConfig().getString("secret-token", "");
        if (secret.equals("ЗАМЕНИ_НА_СВОЙ_СЕКРЕТНЫЙ_ТОКЕН") || secret.isBlank()) {
            getLogger().severe("════════════════════════════════════════════");
            getLogger().severe("  СТОП! Установи secret-token в config.yml!");
            getLogger().severe("  Придумай любой пароль и вставь туда.");
            getLogger().severe("  Тот же пароль — в Cloudflare Worker (BRIDGE_SECRET).");
            getLogger().severe("════════════════════════════════════════════");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        int port = getConfig().getInt("http-port", 8765);

        rankGiver  = new RankGiver(this);
        httpServer = new BridgeHttpServer(this, port, secret, rankGiver);
        httpServer.start();

        getCommand("donatebridge").setExecutor(this);

        getLogger().info("════════════════════════════════════════════");
        getLogger().info("  DonateBridge v1.0.0 запущен!");
        getLogger().info("  HTTP bridge слушает порт: " + port);
        getLogger().info("  Жду запросы от Cloudflare Worker...");
        getLogger().info("════════════════════════════════════════════");
    }

    @Override
    public void onDisable() {
        if (httpServer != null) httpServer.stop();
        getLogger().info("DonateBridge остановлен.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("donatebridge.admin")) {
            sender.sendMessage("§cНет доступа.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e[DonateBridge] §fИспользование: /donatebridge <reload|status>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                rankGiver.reloadConfig();
                sender.sendMessage("§a[DonateBridge] §fКонфиг перезагружен!");
            }
            case "status" -> {
                boolean lpOk = getServer().getPluginManager().getPlugin("LuckPerms") != null;
                sender.sendMessage("§a[DonateBridge] §fСтатус: §aработает");
                sender.sendMessage("§a[DonateBridge] §fПорт: §e" + getConfig().getInt("http-port", 8765));
                sender.sendMessage("§a[DonateBridge] §fLuckPerms: " + (lpOk ? "§aподключён" : "§cНЕ НАЙДЕН!"));
            }
            default -> sender.sendMessage("§e[DonateBridge] §fНеизвестная команда.");
        }
        return true;
    }
}
