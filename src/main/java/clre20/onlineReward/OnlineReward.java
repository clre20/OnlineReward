package clre20.onlineReward;

import clre20.onlineReward.command.OrwCommand;
import clre20.onlineReward.config.Messages;
import clre20.onlineReward.config.PluginConfig;
import clre20.onlineReward.config.YamlFiles;
import clre20.onlineReward.listener.AfkTracker;
import clre20.onlineReward.reward.DailyStore;
import clre20.onlineReward.reward.Money;
import clre20.onlineReward.reward.VaultMoney;
import clre20.onlineReward.reward.RewardService;
import clre20.onlineReward.update.UpdateChecker;
import clre20.onlineReward.update.UpdateNotifier;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class OnlineReward extends JavaPlugin {

    private volatile PluginConfig config;
    private volatile Messages messages;
    private ExecutorService io;
    private ExecutorService network;
    private AfkTracker afk;
    private DailyStore store;
    private RewardService rewards;
    private UpdateChecker updateChecker;
    private UpdateNotifier updateNotifier;

    @Override
    public void onEnable() {
        loadFiles();

        io = Executors.newSingleThreadExecutor(named("OnlineReward-IO"));
        network = Executors.newSingleThreadExecutor(named("OnlineReward-Update"));

        store = new DailyStore(new File(getDataFolder(), "data.yml"), getLogger());
        store.load();

        afk = new AfkTracker(this);
        afk.register();
        for (Player p : Bukkit.getOnlinePlayers()) {
            afk.track(p);
        }
        rewards = new RewardService(this, afk, store, VaultMoney.create());
        updateChecker = new UpdateChecker(getDescription().getVersion(), network);
        updateNotifier = new UpdateNotifier(this);

        getServer().getPluginManager().registerEvents(updateNotifier, this);

        PluginCommand cmd = getCommand("onlinereward");
        if (cmd != null) {
            OrwCommand executor = new OrwCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        // 經濟插件可能比本插件晚啟用，等伺服器載入完（第一個 tick）再找
        Bukkit.getScheduler().runTask(this, () -> {
            rewards.start();
            logRewardSummary();
            getLogger().info(messages.console(afk.inputSupported() ? "console.afk-input" : "console.afk-fallback"));
        });

        if (config.checkOnStartup) {
            updateChecker.check().thenAccept(r -> {
                if (isEnabled()) {
                    Bukkit.getScheduler().runTask(this, () -> updateNotifier.logToConsole(r));
                }
            });
        }
    }

    @Override
    public void onDisable() {
        if (rewards != null) {
            rewards.stop();
        }
        Bukkit.getScheduler().cancelTasks(this);
        if (io != null) {
            io.shutdown();
            try {
                io.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (store != null) {
            store.saveNow();
        }
        if (network != null) {
            network.shutdownNow();
        }
        if (afk != null) {
            afk.clear();
        }
    }

    /** /orw reload：重新讀取並補齊 config.yml 與 messages.yml。 */
    public void reload() {
        loadFiles();
        if (rewards != null) {
            rewards.onReload();
            logRewardSummary();
        }
    }

    /** 後台輸出目前的獎勵內容；要發錢卻找不到經濟插件時提示。 */
    private void logRewardSummary() {
        PluginConfig cfg = config;
        Money money = rewards.money();
        if (cfg.amount > 0 && !money.available()) {
            getLogger().severe(messages.console("console.economy-missing"));
        }
        getLogger().info(messages.console("console.started",
                "interval", formatDuration(cfg.intervalMillis),
                "amount", cfg.amount > 0 ? money.format(cfg.amount) : "0",
                "commands", cfg.commands.size()));
        if (!cfg.hasReward()) {
            getLogger().warning(messages.console("console.no-reward"));
        }
    }

    private void loadFiles() {
        int migratedTimes = legacyDailyLimitTimes();
        YamlFiles.Result c = YamlFiles.load(this, "config.yml", "config-version");
        if (migratedTimes > 0 && c.config().getInt("reward.daily-max-times", 0) == 0) {
            c.config().set("reward.daily-max-times", migratedTimes);
            try {
                YamlFiles.writeAtomic(new File(getDataFolder(), "config.yml"), c.config().saveToString());
            } catch (java.io.IOException e) {
                getLogger().warning("Cannot write config.yml: " + e.getMessage());
            }
        }
        PluginConfig newConfig = new PluginConfig(c.config());
        YamlFiles.Result m = YamlFiles.load(this, "messages.yml", "messages-version");
        Messages newMessages = new Messages(m.config(), m.defaults(), newConfig.prefix);
        config = newConfig;
        messages = newMessages;
        if (c.updated()) {
            getLogger().info(newMessages.console("console.config-updated", "file", "config.yml"));
        }
        if (migratedTimes > 0) {
            getLogger().info(newMessages.console("console.daily-limit-migrated", "times", migratedTimes));
        }
        if (m.updated()) {
            getLogger().info(newMessages.console("console.config-updated", "file", "messages.yml"));
        }
    }

    /**
     * 1.0.x 的 reward.daily-limit 是金額上限，1.1.0 改成次數（daily-max-times）。
     * 舊設定還在且新鍵不存在時，換算成次數（無條件進位）；沒有舊設定回傳 0。
     */
    private int legacyDailyLimitTimes() {
        File file = new File(getDataFolder(), "config.yml");
        if (!file.isFile()) {
            return 0;
        }
        org.bukkit.configuration.file.YamlConfiguration old = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            old.loadFromString(java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return 0;
        }
        if (old.contains("reward.daily-max-times") || !old.contains("reward.daily-limit")) {
            return 0;
        }
        double limit = old.getDouble("reward.daily-limit", 0);
        double amount = old.getDouble("reward.amount", 5);
        if (!Double.isFinite(limit) || !Double.isFinite(amount) || limit <= 0 || amount <= 0) {
            return 0;
        }
        return (int) Math.min(100_000, Math.ceil(limit / amount - 1e-9));
    }

    /** 毫秒 → 「10 分 0 秒」或「45 秒」（無條件進位到整秒）。 */
    public String formatDuration(long millis) {
        long total = (millis + 999) / 1000;
        long m = total / 60;
        long s = total % 60;
        return m > 0
                ? messages.get("time.minutes-seconds", "m", m, "s", s)
                : messages.get("time.seconds", "s", s);
    }

    private static java.util.concurrent.ThreadFactory named(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    public PluginConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public ExecutorService io() {
        return io;
    }

    public RewardService rewards() {
        return rewards;
    }

    public UpdateChecker updateChecker() {
        return updateChecker;
    }

    public UpdateNotifier updateNotifier() {
        return updateNotifier;
    }
}
