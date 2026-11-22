package clre20.onlineReward.command;

import clre20.onlineReward.OnlineReward;
import clre20.onlineReward.config.Messages;
import clre20.onlineReward.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /onlinereward（/orw）：help、status、version、reload。不帶參數＝help。 */
public final class OrwCommand implements TabExecutor {

    private static final String[] SUBS = {"help", "status", "version", "reload"};

    private final OnlineReward plugin;

    public OrwCommand(OnlineReward plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages m = plugin.messages();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        // label 來自玩家輸入，只允許英數字，避免把奇怪的字元放進訊息
        String safeLabel = label.matches("[A-Za-z0-9_:-]{1,32}") ? label : "orw";

        switch (sub) {
            case "help" -> {
                if (!check(sender, "help")) {
                    return true;
                }
                sender.sendMessage(m.get("command.help-header", "version", plugin.getDescription().getVersion()));
                for (String s : SUBS) {
                    if (sender.hasPermission(perm(s))) {
                        sender.sendMessage(m.get("command.help-" + s, "label", safeLabel));
                    }
                }
            }
            case "status" -> {
                if (!check(sender, "status")) {
                    return true;
                }
                if (args.length >= 2) {
                    statusPlayer(sender, args[1]);
                } else {
                    status(sender);
                }
            }
            case "version" -> {
                if (!check(sender, "version")) {
                    return true;
                }
                plugin.updateNotifier().checkFor(sender);
            }
            case "reload" -> {
                if (!check(sender, "reload")) {
                    return true;
                }
                plugin.reload();
                sender.sendMessage(plugin.messages().get("command.reload"));
            }
            default -> sender.sendMessage(m.get("command.unknown", "label", safeLabel));
        }
        return true;
    }

    private void status(CommandSender sender) {
        Messages m = plugin.messages();
        PluginConfig cfg = plugin.config();
        String amount = plugin.rewards().money().format(cfg.amount);
        sender.sendMessage(m.get("command.status-next",
                "time", plugin.formatDuration(plugin.rewards().millisUntilNext()),
                "interval", plugin.formatDuration(cfg.intervalMillis),
                "amount", amount));
        if (sender instanceof Player p) {
            int today = plugin.rewards().today(p.getUniqueId());
            if (cfg.dailyMaxTimes > 0) {
                sender.sendMessage(m.get("command.status-times-limit", "count", today, "limit", cfg.dailyMaxTimes));
            } else {
                sender.sendMessage(m.get("command.status-times", "count", today));
            }
        }
    }

    private void statusPlayer(CommandSender sender, String targetName) {
        Messages m = plugin.messages();
        boolean isSelf = sender instanceof Player p && p.getName().equalsIgnoreCase(targetName);
        if (!isSelf && !sender.hasPermission("onlinereward.status.others")
                && !sender.hasPermission("onlinereward.admin") && (sender instanceof Player)) {
            sender.sendMessage(m.get("command.no-permission"));
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            target = Bukkit.getPlayer(targetName);
        }

        UUID targetId = null;
        String resolvedName = targetName;
        boolean online = false;

        if (target != null) {
            targetId = target.getUniqueId();
            resolvedName = target.getName();
            online = true;
        } else {
            try {
                targetId = UUID.fromString(targetName);
                OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
                if (op.getName() != null) {
                    resolvedName = op.getName();
                }
                online = op.isOnline();
                if (online && op.getPlayer() != null) {
                    target = op.getPlayer();
                }
            } catch (IllegalArgumentException e) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
                if (op.hasPlayedBefore() || op.isOnline()) {
                    targetId = op.getUniqueId();
                    if (op.getName() != null) {
                        resolvedName = op.getName();
                    }
                    online = op.isOnline();
                    if (online && op.getPlayer() != null) {
                        target = op.getPlayer();
                    }
                }
            }
        }

        if (targetId == null) {
            sender.sendMessage(m.get("command.player-not-found", "player", targetName));
            return;
        }

        PluginConfig cfg = plugin.config();
        long now = System.currentTimeMillis();
        int today = plugin.rewards().today(targetId);

        sender.sendMessage(m.get("command.status-player-header", "player", resolvedName));

        if (online && target != null) {
            sender.sendMessage(m.get("command.status-player-online",
                    "status", Messages.color("&a在線")));
            sender.sendMessage(m.get("command.status-player-world",
                    "world", target.getWorld().getName()));

            long idle = plugin.afk().idleMillis(targetId, now);
            boolean isAfk = cfg.afkEnabled && idle >= cfg.afkMillis;
            if (cfg.afkEnabled) {
                sender.sendMessage(m.get("command.status-player-afk",
                        "afk", isAfk ? Messages.color("&c掛機中") : Messages.color("&a活躍中"),
                        "idle", plugin.formatDuration(idle),
                        "threshold", plugin.formatDuration(cfg.afkMillis)));
            } else {
                sender.sendMessage(m.get("command.status-player-afk-disabled"));
            }

            if (cfg.dailyMaxTimes > 0) {
                sender.sendMessage(m.get("command.status-player-times-limit",
                        "count", today, "limit", cfg.dailyMaxTimes));
            } else {
                sender.sendMessage(m.get("command.status-player-times", "count", today));
            }

            String eligibleText;
            if (!target.hasPermission("onlinereward.receive")) {
                eligibleText = m.get("command.status-ineligible-permission");
            } else if (cfg.worldDisabled(target.getWorld().getName())) {
                eligibleText = m.get("command.status-ineligible-world");
            } else if (isAfk) {
                eligibleText = m.get("command.status-ineligible-afk");
            } else if (cfg.dailyMaxTimes > 0 && today >= cfg.dailyMaxTimes) {
                eligibleText = m.get("command.status-ineligible-limit");
            } else {
                eligibleText = m.get("command.status-eligible");
            }
            sender.sendMessage(m.get("command.status-player-eligible", "eligible", eligibleText));
        } else {
            sender.sendMessage(m.get("command.status-player-online",
                    "status", Messages.color("&7離線")));
            if (cfg.dailyMaxTimes > 0) {
                sender.sendMessage(m.get("command.status-player-times-limit",
                        "count", today, "limit", cfg.dailyMaxTimes));
            } else {
                sender.sendMessage(m.get("command.status-player-times", "count", today));
            }
            sender.sendMessage(m.get("command.status-player-eligible",
                    "eligible", m.get("command.status-ineligible-offline")));
        }

        sender.sendMessage(m.get("command.status-player-next",
                "time", plugin.formatDuration(plugin.rewards().millisUntilNext()),
                "interval", plugin.formatDuration(cfg.intervalMillis)));
    }

    private boolean check(CommandSender sender, String sub) {
        if (sender.hasPermission(perm(sub))) {
            return true;
        }
        sender.sendMessage(plugin.messages().get("command.no-permission"));
        return false;
    }

    private static String perm(String sub) {
        return "onlinereward." + sub;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String s : SUBS) {
                if (s.startsWith(prefix) && sender.hasPermission(perm(s))) {
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2 && "status".equalsIgnoreCase(args[0])) {
            if (sender.hasPermission("onlinereward.status.others")
                    || sender.hasPermission("onlinereward.admin") || !(sender instanceof Player)) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(p.getName());
                    }
                }
            }
            return out;
        }
        return out;
    }
}
