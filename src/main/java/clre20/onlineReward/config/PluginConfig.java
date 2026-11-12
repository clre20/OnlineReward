package clre20.onlineReward.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** config.yml 的不可變快照；重載時整份換掉。所有值都有範圍限制與預設值。 */
public final class PluginConfig {

    public static final int MAX_COMMANDS = 50;
    public static final int MAX_COMMAND_LENGTH = 256;

    public final String prefix;
    public final long intervalMillis;
    public final double amount;
    /** 每次發放以後台身分執行的指令（已去掉開頭的 /）。 */
    public final List<String> commands;
    public final int commandsPerTick;
    /** 每人每日最多領幾次，0 = 不限。 */
    public final int dailyMaxTimes;
    public final Set<String> disabledWorlds;
    public final boolean logPayout;
    public final boolean afkEnabled;
    public final long afkMillis;
    public final boolean afkCountLooking;
    public final boolean afkCountChat;
    public final boolean afkCountCommands;
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
        List<String> cmds = new ArrayList<>();
        for (String raw : c.getStringList("reward.commands")) {
            if (raw == null) {
                continue;
            }
            String cmd = raw.strip();
            while (cmd.startsWith("/")) {
                cmd = cmd.substring(1).strip();
            }
            // 換行會讓後台把一行拆成兩條指令，不接受
            if (cmd.isEmpty() || cmd.length() > MAX_COMMAND_LENGTH || cmd.indexOf('\n') >= 0 || cmd.indexOf('\r') >= 0) {
                continue;
            }
            cmds.add(cmd);
            if (cmds.size() >= MAX_COMMANDS) {
                break;
            }
        }
        commands = List.copyOf(cmds);
        commandsPerTick = (int) clamp(c.getLong("reward.commands-per-tick", 20), 1, 200);
        dailyMaxTimes = (int) clamp(c.getLong("reward.daily-max-times", 0), 0, 100_000);

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
        afkCountLooking = c.getBoolean("afk.count-looking", false);
        afkCountChat = c.getBoolean("afk.count-chat", true);
        afkCountCommands = c.getBoolean("afk.count-commands", false);

        checkOnStartup = c.getBoolean("update-checker.check-on-startup", true);
        notifyOnJoin = c.getBoolean("update-checker.notify-on-join", true);
    }

    /** 有東西可發（金額大於 0 或有指令）。 */
    public boolean hasReward() {
        return amount > 0 || !commands.isEmpty();
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
