package fun.nellgrief.donatebridge;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class RankGiver {

    private final DonateBridge plugin;
    private final Logger log;
    private Map<String, Object> ranksConfig;
    private Map<String, Object> durationsConfig;
    private Map<String, Object> messagesConfig;

    public RankGiver(DonateBridge plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
        loadSections();
    }

    public void reloadConfig() {
        loadSections();
    }

    private void loadSections() {
        var cfg = plugin.getConfig();
        ranksConfig     = cfg.getConfigurationSection("ranks")     != null
                ? cfg.getConfigurationSection("ranks").getValues(false)     : Map.of();
        durationsConfig = cfg.getConfigurationSection("durations") != null
                ? cfg.getConfigurationSection("durations").getValues(false) : Map.of();
        messagesConfig  = cfg.getConfigurationSection("messages")  != null
                ? cfg.getConfigurationSection("messages").getValues(false)  : Map.of();
    }

    public void giveRank(String nickname, String productName) {
        String group = (String) ranksConfig.get(productName);
        if (group == null) {
            log.warning("Неизвестный продукт: '" + productName + "' — добавь его в config.yml -> ranks");
            return;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(nickname);
        UUID uuid = offlinePlayer.getUniqueId();

        if (uuid == null || uuid.equals(new UUID(0, 0))) {
            log.warning("UUID не найден для игрока '" + nickname + "'.");
            return;
        }

        int days = 0;
        Object durObj = durationsConfig.get(group);
        if (durObj instanceof Integer d)    days = d;
        else if (durObj instanceof String s) {
            try { days = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        final int finalDays = days;
        final String finalGroup = group;

        LuckPerms lp;
        try {
            lp = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            log.severe("LuckPerms API недоступен!");
            return;
        }

        lp.getUserManager().loadUser(uuid).thenAcceptAsync(user -> {
            if (user == null) {
                log.warning("LuckPerms не смог загрузить пользователя " + nickname);
                return;
            }

            InheritanceNode.Builder nodeBuilder = InheritanceNode.builder(finalGroup);
            if (finalDays > 0) {
                nodeBuilder.expiry(Duration.ofDays(finalDays));
            }
            InheritanceNode node = nodeBuilder.build();

            user.data().add(node);
            lp.getUserManager().saveUser(user).join();

            log.info("✓ Группа '" + finalGroup + "' выдана игроку " + nickname +
                    (finalDays > 0 ? " на " + finalDays + " дней" : " (постоянно)"));

            String template = (String) messagesConfig.getOrDefault(
                    "rank-given",
                    "&a[NellGrief] &fСпасибо за покупку! Привилегия &e{rank} &fвыдана!");

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null && online.isOnline()) {
                    String msg = ChatColor.translateAlternateColorCodes('&',
                            template.replace("{rank}", finalGroup).replace("{player}", nickname));
                    online.sendMessage(msg);
                } else {
                    log.info("Игрок " + nickname + " оффлайн — привилегия активируется при входе.");
                }
            });

        }).exceptionally(ex -> {
            log.severe("Ошибка при выдаче ранга для " + nickname + ": " + ex.getMessage());
            return null;
        });
    }
}
