package clre20.onlineReward.reward;

import clre20.onlineReward.OnlineReward;
import clre20.onlineReward.config.Messages;
import clre20.onlineReward.config.PluginConfig;
import clre20.onlineReward.listener.AfkTracker;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

/**
 * 全服同一時間發放。每秒檢查一次是否到時間（只比較一個數字），
 * 用實際時間計算，伺服器卡頓時不會延後發放。
 */
public final class RewardService {

    private static final double EPSILON = 1e-9;

    private final OnlineReward plugin;
    private final AfkTracker afk;
    private final DailyStore store;
    private BukkitTask task;
    private long nextPayout;
    private long intervalMillis;
    private boolean economyMissingLogged;

    public RewardService(OnlineReward plugin, AfkTracker afk, DailyStore store) {
        this.plugin = plugin;
        this.afk = afk;
        this.store = store;
    }

    public void start() {
        intervalMillis = plugin.config().intervalMillis;
        nextPayout = System.currentTimeMillis() + intervalMillis;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** 重載後若間隔改變，從現在重新計時。 */
    public void onReload() {
        long interval = plugin.config().intervalMillis;
        if (interval != intervalMillis) {
            intervalMillis = interval;
            nextPayout = System.currentTimeMillis() + interval;
        }
        economyMissingLogged = false;
    }

    public long millisUntilNext() {
        return Math.max(0, nextPayout - System.currentTimeMillis());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (now < nextPayout) {
            return;
        }
        // 卡很久時也只發一次，下一次從現在重新計時
        nextPayout = now + intervalMillis;
        payout(now);
    }

    public static Economy findEconomy() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    private void payout(long now) {
        PluginConfig cfg = plugin.config();
        Messages msg = plugin.messages();
        if (cfg.amount <= 0) {
            return;
        }
        Economy economy = findEconomy();
        if (economy == null) {
            if (!economyMissingLogged) {
                economyMissingLogged = true;
                plugin.getLogger().warning(msg.console("console.no-economy"));
            }
            return;
        }
        economyMissingLogged = false;

        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("onlinereward.receive")) {
                continue;
            }
            if (cfg.worldDisabled(player.getWorld().getName())) {
                continue;
            }
            if (cfg.afkEnabled && afk.idleMillis(player.getUniqueId(), now) >= cfg.afkMillis) {
                player.sendMessage(msg.get("reward.afk"));
                continue;
            }

            double give = cfg.amount;
            double already = 0;
            if (cfg.dailyLimit > 0) {
                already = store.get(player.getUniqueId());
                double left = cfg.dailyLimit - already;
                if (left <= EPSILON) {
                    continue; // 已達上限，當天只在達到的那一次提示
                }
                give = Math.min(give, left);
            }

            if (!deposit(economy, player, give, msg)) {
                continue;
            }
            store.add(player.getUniqueId(), give);
            count++;
            player.sendMessage(msg.get("reward.received", "amount", format(economy, give)));
            if (cfg.dailyLimit > 0 && already + give >= cfg.dailyLimit - EPSILON) {
                player.sendMessage(msg.get("reward.daily-limit"));
            }
        }

        store.saveAsync(plugin.io());
        if (cfg.logPayout && count > 0) {
            plugin.getLogger().info(msg.console("console.payout", "count", count));
        }
    }

    private boolean deposit(Economy economy, Player player, double amount, Messages msg) {
        try {
            EconomyResponse r = economy.depositPlayer(player, amount);
            if (r != null && r.transactionSuccess()) {
                return true;
            }
            String error = r == null ? "null" : String.valueOf(r.errorMessage);
            plugin.getLogger().warning(msg.console("console.deposit-failed",
                    "player", player.getName(), "error", error));
        } catch (RuntimeException e) {
            plugin.getLogger().warning(msg.console("console.deposit-failed",
                    "player", player.getName(), "error", e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
        return false;
    }

    public static String format(Economy economy, double amount) {
        if (economy != null) {
            try {
                String s = economy.format(amount);
                if (s != null && !s.isBlank()) {
                    return s;
                }
            } catch (RuntimeException ignored) {
                // 用預設格式
            }
        }
        if (amount == Math.rint(amount)) {
            return String.valueOf((long) amount);
        }
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    public double today(java.util.UUID id) {
        return store.get(id);
    }
}
