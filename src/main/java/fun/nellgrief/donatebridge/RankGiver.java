package fun.nellgrief.donatebridge;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.InheritanceNode;

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
            log.warning("Неизвестный продукт: '" + productName + "' — добавь в config.yml -> ranks");
            return;
        }

        UUID uuid;
        try {
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(nickname);
            uuid = offlinePlayer.getUniqueId();
        } catch (Exception e) {
            log.warning("Ошибка получения UUID для '" + nickname + "': " + e.getMessage());
            return;
        }

        if (uuid == null || uuid.equals(new UUID(0, 0))) {
            log.warning("UUID не найден для игрока '" + nickname + "'.");
            return;
        }

        int days = 0;
        Object durObj = durationsConfig.get(group);
        if (durObj instanceof Integer) days = (Integer) durObj;
        else if (durObj instanceof String) {
            try { days = Integer.parseInt((String) durObj); } catch (NumberFormatException ignored) {}
        }
        final int finalDays = days;
        final String finalGroup = group;
        final UUID finalUuid = uuid;

        LuckPerms lp;
        try {
            lp = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            log.severe("LuckPerms API недоступен!");
            return;
        }

        final LuckPerms finalLp = lp;

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
            finalLp.getUserManager().saveUser(user).join();

            log.info("Группа '" + finalGroup + "' выдана игроку " + nickname +
                    (finalDays > 0 ? " на " + finalDays + " дней" : " (постоянно)"));

            final String template = (String) messagesConfig.getOrDefault(
                    "rank-given",
                    "&a[NellGrief] &fСпасибо за покупку! Привилегия &e{rank} &fвыдана!");

            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(finalUuid);
                if (online != null && online.isOnline()) {
                    String msg = org.bukkit.ChatColor.translateAlternateColorCodes('&',
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
