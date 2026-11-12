package clre20.onlineReward.reward;

import clre20.onlineReward.OnlineReward;
import clre20.onlineReward.config.Messages;
import clre20.onlineReward.config.PluginConfig;
import clre20.onlineReward.listener.AfkTracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 全服同一時間發放。每秒檢查一次是否到時間（只比較一個數字），
 * 用實際時間計算，伺服器卡頓時不會延後發放。
 *
 * <p>錢在發放當下直接存入；指令放進佇列，每 tick 最多執行 commands-per-tick 條，
 * 人多時分散到幾個 tick，不會一次塞爆主執行緒。佇列空了就停止每 tick 的任務。
 */
public final class RewardService {

    /** Java 版名稱是英數字與底線；Floodgate 會加上 . 或 * 之類的前綴。其他字元不放進後台指令。 */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.*\\-]{1,32}");
    private static final int MAX_QUEUE = 20_000;

    private record Pending(UUID id, String template, String command) {
    }

    private final OnlineReward plugin;
    private final AfkTracker afk;
    private final DailyStore store;
    private final Money money;
    private final ArrayDeque<Pending> queue = new ArrayDeque<>();
    /** 已經警告過的失敗指令（設定裡的原文），重載時清空。 */
    private final Set<String> failedWarned = new HashSet<>();
    private BukkitTask task;
    private BukkitTask drainTask;
    private long nextPayout;
    private long intervalMillis;
    private boolean economyMissingLogged;

    public RewardService(OnlineReward plugin, AfkTracker afk, DailyStore store, Money money) {
        this.plugin = plugin;
        this.afk = afk;
        this.store = store;
        this.money = money;
    }

    public Money money() {
        return money;
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
        if (drainTask != null) {
            drainTask.cancel();
            drainTask = null;
        }
        queue.clear();
    }

    /** 重載後若間隔改變，從現在重新計時。 */
    public void onReload() {
        long interval = plugin.config().intervalMillis;
        if (interval != intervalMillis) {
            intervalMillis = interval;
            nextPayout = System.currentTimeMillis() + interval;
        }
        economyMissingLogged = false;
        failedWarned.clear();
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

    private void payout(long now) {
        PluginConfig cfg = plugin.config();
        Messages msg = plugin.messages();
        if (!cfg.hasReward()) {
            return;
        }
        boolean giveMoney = cfg.amount > 0;
        if (giveMoney && !money.available()) {
            if (!economyMissingLogged) {
                economyMissingLogged = true;
                plugin.getLogger().warning(msg.console("console.economy-missing"));
            }
            giveMoney = false;
        } else if (giveMoney) {
            economyMissingLogged = false;
        }
        if (!giveMoney && cfg.commands.isEmpty()) {
            return;
        }

        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("onlinereward.receive")) {
                continue;
            }
            if (cfg.worldDisabled(player.getWorld().getName())) {
                continue;
            }
            UUID id = player.getUniqueId();
            if (cfg.afkEnabled && afk.idleMillis(id, now) >= cfg.afkMillis) {
                player.sendMessage(msg.get("reward.afk"));
                continue;
            }
            int already = cfg.dailyMaxTimes > 0 ? store.get(id) : 0;
            if (cfg.dailyMaxTimes > 0 && already >= cfg.dailyMaxTimes) {
                continue; // 已達上限，當天只在達到的那一次提示
            }

            boolean moneyGiven = false;
            if (giveMoney) {
                String error = money.deposit(player, cfg.amount);
                if (error == null) {
                    moneyGiven = true;
                } else {
                    plugin.getLogger().warning(msg.console("console.deposit-failed",
                            "player", player.getName(), "error", error));
                }
            }
            boolean commandsQueued = enqueue(player, cfg);
            if (!moneyGiven && !commandsQueued) {
                continue;
            }

            store.increment(id);
            count++;
            if (moneyGiven) {
                player.sendMessage(msg.get("reward.received", "amount", money.format(cfg.amount)));
            } else {
                player.sendMessage(msg.get("reward.received-commands"));
            }
            if (cfg.dailyMaxTimes > 0 && already + 1 >= cfg.dailyMaxTimes) {
                player.sendMessage(msg.get("reward.daily-limit", "limit", cfg.dailyMaxTimes));
            }
        }

        store.saveAsync(plugin.io());
        if (cfg.logPayout && count > 0) {
            plugin.getLogger().info(msg.console("console.payout", "count", count));
        }
        if (!queue.isEmpty() && drainTask == null) {
            drainTask = Bukkit.getScheduler().runTaskTimer(plugin, this::drain, 1L, 1L);
        }
    }

    /** 把這位玩家的指令放進佇列；名稱不安全或佇列已滿時不放。 */
    private boolean enqueue(Player player, PluginConfig cfg) {
        if (cfg.commands.isEmpty()) {
            return false;
        }
        String name = player.getName();
        if (!SAFE_NAME.matcher(name).matches()) {
            plugin.getLogger().warning(plugin.messages().console("console.unsafe-name", "player", name));
            return false;
        }
        if (queue.size() + cfg.commands.size() > MAX_QUEUE) {
            plugin.getLogger().warning(plugin.messages().console("console.queue-full", "player", name));
            return false;
        }
        String uuid = player.getUniqueId().toString();
        String world = player.getWorld().getName();
        for (String c : cfg.commands) {
            String cmd = c.replace("{player}", name).replace("{uuid}", uuid);
            if (cmd.contains("{world}")) {
                cmd = SAFE_NAME.matcher(world).matches() ? cmd.replace("{world}", world) : null;
            }
            if (cmd != null) {
                queue.add(new Pending(player.getUniqueId(), c, cmd));
            }
        }
        return true;
    }

    /** 每 tick 執行最多 commands-per-tick 條；玩家已離線就略過他的指令。 */
    private void drain() {
        int budget = plugin.config().commandsPerTick;
        while (budget > 0 && !queue.isEmpty()) {
            Pending p = queue.poll();
            if (Bukkit.getPlayer(p.id()) == null) {
                continue;
            }
            budget--;
            String error;
            try {
                error = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), p.command()) ? null : "unknown command";
            } catch (RuntimeException e) {
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            if (error != null && failedWarned.size() < 100 && failedWarned.add(p.template())) {
                plugin.getLogger().warning(plugin.messages().console("console.command-failed",
                        "command", p.command(), "error", error));
            }
        }
        if (queue.isEmpty() && drainTask != null) {
            drainTask.cancel();
            drainTask = null;
        }
    }

    public int today(UUID id) {
        return store.get(id);
    }
}
