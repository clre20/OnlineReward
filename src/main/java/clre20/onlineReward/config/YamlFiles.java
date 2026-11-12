package clre20.onlineReward.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;

/**
 * 讀取 jar 內附預設檔與使用者檔，補齊缺少的項目、移除已不使用的項目，
 * 保留使用者的值與註解；格式壞掉時備份並暫用預設值，不覆蓋原檔。
 */
public final class YamlFiles {

    private YamlFiles() {
    }

    /** 讀取結果：實際使用的設定、預設設定、這次是否寫回檔案。 */
    public record Result(YamlConfiguration config, YamlConfiguration defaults, boolean updated) {
    }

    public static Result load(JavaPlugin plugin, String name, String versionKey) {
        YamlConfiguration defaults = loadDefaults(plugin, name);
        File file = new File(plugin.getDataFolder(), name);

        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                writeAtomic(file, defaults.saveToString());
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Cannot create " + name, e);
            }
            return new Result(defaults, defaults, false);
        }

        YamlConfiguration user = new YamlConfiguration();
        try {
            user.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (InvalidConfigurationException | IOException e) {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            File broken = new File(plugin.getDataFolder(), name + ".broken-" + stamp);
            try {
                Files.copy(file.toPath(), broken.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // 備份失敗也不覆蓋原檔
            }
            plugin.getLogger().warning(name + " format error, using defaults. Backup: " + broken.getName()
                    + " (" + firstLine(e.getMessage()) + ")");
            return new Result(defaults, defaults, false);
        }

        if (!needsUpdate(defaults, user, versionKey)) {
            return new Result(user, defaults, false);
        }

        // 以預設檔（含註解）為底，填回使用者的值與註解；預設檔沒有的項目自然移除。
        // 版本號變了代表有項目改了意思，這時用新版的註解，不沿用舊註解
        boolean versionChanged = !String.valueOf(defaults.get(versionKey)).equals(String.valueOf(user.get(versionKey)));
        YamlConfiguration merged = loadDefaults(plugin, name);
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || key.equals(versionKey)) {
                continue;
            }
            if (user.contains(key, true) && !user.isConfigurationSection(key)) {
                merged.set(key, user.get(key));
            }
            if (!versionChanged) {
                copyComments(user, merged, key);
            }
        }
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) && !versionChanged) {
                copyComments(user, merged, key);
            }
        }
        try {
            Files.copy(file.toPath(), new File(plugin.getDataFolder(), name + ".bak").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            writeAtomic(file, merged.saveToString());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Cannot write " + name, e);
        }
        return new Result(merged, defaults, true);
    }

    private static boolean needsUpdate(YamlConfiguration defaults, YamlConfiguration user, String versionKey) {
        if (!String.valueOf(defaults.get(versionKey)).equals(String.valueOf(user.get(versionKey)))) {
            return true;
        }
        for (String key : defaults.getKeys(true)) {
            if (!user.contains(key, true)) {
                return true;
            }
            if (defaults.isConfigurationSection(key) != user.isConfigurationSection(key)) {
                return true;
            }
        }
        for (String key : user.getKeys(true)) {
            if (!defaults.contains(key, true)) {
                return true;
            }
        }
        return false;
    }

    private static void copyComments(YamlConfiguration from, YamlConfiguration to, String key) {
        if (!from.contains(key, true)) {
            return;
        }
        List<String> above = from.getComments(key);
        if (!above.isEmpty()) {
            to.setComments(key, above);
        }
        List<String> inline = from.getInlineComments(key);
        if (!inline.isEmpty()) {
            to.setInlineComments(key, inline);
        }
    }

    static YamlConfiguration loadDefaults(JavaPlugin plugin, String name) {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "Bundled " + name + " is broken", e);
        }
        return yaml;
    }

    public static void writeAtomic(File file, String content) throws IOException {
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }
}
