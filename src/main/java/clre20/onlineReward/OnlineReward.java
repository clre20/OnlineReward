package clre20.onlineReward;

import clre20.onlineReward.command.OrwCommand;
import clre20.onlineReward.config.Messages;
import clre20.onlineReward.config.PluginConfig;
import clre20.onlineReward.config.YamlFiles;
import clre20.onlineReward.listener.AfkTracker;
import clre20.onlineReward.reward.DailyStore;
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

        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe(messages.console("console.no-vault"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        io = Executors.newSingleThreadExecutor(named("OnlineReward-IO"));
        network = Executors.newSingleThreadExecutor(named("OnlineReward-Update"));

        store = new DailyStore(new File(getDataFolder(), "data.yml"), getLogger());
        store.load();

        afk = new AfkTracker();
        for (Player p : Bukkit.getOnlinePlayers()) {
            afk.track(p);
        }
        rewards = new RewardService(this, afk, store);
        updateChecker = new UpdateChecker(getDescription().getVersion(), network);
        updateNotifier = new UpdateNotifier(this);

        getServer().getPluginManager().registerEvents(afk, this);
        getServer().getPluginManager().registerEvents(updateNotifier, this);

        PluginCommand cmd = getCommand("onlinereward");
        if (cmd != null) {
            OrwCommand executor = new OrwCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        // 經濟插件可能比本插件晚啟用，等伺服器載入完（第一個 tick）再找
        Bukkit.getScheduler().runTask(this, () -> {
            var economy = RewardService.findEconomy();
            if (economy == null) {
                getLogger().severe(messages.console("console.no-economy"));
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            rewards.start();
            getLogger().info(messages.console("console.enabled",
                    "interval", formatDuration(config.intervalMillis),
                    "amount", RewardService.format(economy, config.amount)));
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
        }
    }

    private void loadFiles() {
        YamlFiles.Result c = YamlFiles.load(this, "config.yml", "config-version");
        PluginConfig newConfig = new PluginConfig(c.config());
        YamlFiles.Result m = YamlFiles.load(this, "messages.yml", "messages-version");
        Messages newMessages = new Messages(m.config(), m.defaults(), newConfig.prefix);
        config = newConfig;
        messages = newMessages;
        if (c.updated()) {
            getLogger().info(newMessages.console("console.config-updated", "file", "config.yml"));
        }
        if (m.updated()) {
            getLogger().info(newMessages.console("console.config-updated", "file", "messages.yml"));
        }
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
