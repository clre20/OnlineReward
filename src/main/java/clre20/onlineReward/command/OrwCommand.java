package clre20.onlineReward.command;

import clre20.onlineReward.OnlineReward;
import clre20.onlineReward.config.Messages;
import clre20.onlineReward.config.PluginConfig;
import clre20.onlineReward.reward.RewardService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
                status(sender);
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
        var economy = RewardService.findEconomy();
        String amount = RewardService.format(economy, cfg.amount);
        sender.sendMessage(m.get("command.status",
                "time", plugin.formatDuration(plugin.rewards().millisUntilNext()),
                "interval", plugin.formatDuration(cfg.intervalMillis),
                "amount", amount));
        if (sender instanceof Player p) {
            String today = RewardService.format(economy, plugin.rewards().today(p.getUniqueId()));
            if (cfg.dailyLimit > 0) {
                sender.sendMessage(m.get("command.status-today-limit",
                        "today", today, "limit", RewardService.format(economy, cfg.dailyLimit)));
            } else {
                sender.sendMessage(m.get("command.status-today", "today", today));
            }
        }
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
        if (args.length != 1) {
            return out;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        for (String s : SUBS) {
            if (s.startsWith(prefix) && sender.hasPermission(perm(s))) {
                out.add(s);
            }
        }
        return out;
    }
}
