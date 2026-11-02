package clre20.onlineReward.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** config.yml 的不可變快照；重載時整份換掉。所有值都有範圍限制與預設值。 */
public final class PluginConfig {

    public final String prefix;
    public final long intervalMillis;
    public final double amount;
    public final double dailyLimit;
    public final Set<String> disabledWorlds;
    public final boolean logPayout;
    public final boolean afkEnabled;
    public final long afkMillis;
    public final boolean checkOnStartup;
    public final boolean notifyOnJoin;

    public PluginConfig(YamlConfiguration c) {
        prefix = c.getString("prefix", "&8[&a在線獎勵&8] &r");

        // 測試用：-Donlinereward.debug.intervalSeconds=20
        long debugInterval = Long.getLong("onlinereward.debug.intervalSeconds", 0L);
        intervalMillis = debugInterval > 0
                ? clamp(debugInterval, 5, 86_400) * 1000L
                : clamp(c.getLong("reward.interval-minutes", 10), 1, 1440) * 60_000L;

        amount = clamp(finite(c.getDouble("reward.amount", 5), 5), 0, 1_000_000_000);
        dailyLimit = clamp(finite(c.getDouble("reward.daily-limit", 0), 0), 0, 1_000_000_000_000d);

        Set<String> worlds = new HashSet<>();
        List<String> list = c.getStringList("reward.disabled-worlds");
        for (String w : list) {
            if (w != null && !w.isBlank()) {
                worlds.add(w.trim().toLowerCase(Locale.ROOT));
            }
        }
        disabledWorlds = Set.copyOf(worlds);
        logPayout = c.getBoolean("reward.log-payout", false);

        afkEnabled = c.getBoolean("afk.enabled", true);
        long debugAfk = Long.getLong("onlinereward.debug.afkSeconds", 0L);
        afkMillis = debugAfk > 0
                ? clamp(debugAfk, 1, 86_400) * 1000L
                : clamp(c.getLong("afk.minutes", 5), 1, 1440) * 60_000L;

        checkOnStartup = c.getBoolean("update-checker.check-on-startup", true);
        notifyOnJoin = c.getBoolean("update-checker.notify-on-join", true);
    }

    public boolean worldDisabled(String worldName) {
        return !disabledWorlds.isEmpty() && disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    private static long clamp(long v, long min, long max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double finite(double v, double fallback) {
        return Double.isFinite(v) ? v : fallback;
    }
}
