package clre20.onlineReward.reward;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

/**
 * 每人當天已領的金額（只在主執行緒讀寫）。
 * 只保留當天的資料，換日整份清空，檔案大小只跟當天領過的人數有關。
 * 寫入：髒資料標記，發放後交給單一 I/O 執行緒寫一次；關服同步寫入。
 */
public final class DailyStore {

    private final File file;
    private final Logger logger;
    private final Map<UUID, Double> today = new HashMap<>();
    private LocalDate date = LocalDate.now();
    private boolean dirty;

    public DailyStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void load() {
        today.clear();
        date = LocalDate.now();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.warning("data.yml is broken and will be reset (" + e.getClass().getSimpleName() + ")");
            dirty = true;
            return;
        }
        if (!date.toString().equals(yaml.getString("date"))) {
            dirty = true;
            return;
        }
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                double v = players.getDouble(key);
                if (Double.isFinite(v) && v > 0) {
                    today.put(UUID.fromString(key), v);
                }
            } catch (IllegalArgumentException ignored) {
                // 不是 UUID 的鍵直接略過
            }
        }
    }

    /** 換日時清空。 */
    private void rollover() {
        LocalDate now = LocalDate.now();
        if (!now.equals(date)) {
            date = now;
            if (!today.isEmpty()) {
                today.clear();
                dirty = true;
            }
        }
    }

    public double get(UUID id) {
        rollover();
        Double v = today.get(id);
        return v == null ? 0 : v;
    }

    public void add(UUID id, double amount) {
        rollover();
        today.merge(id, amount, Double::sum);
        dirty = true;
    }

    /** 有變動時把快照交給 I/O 執行緒寫入。 */
    public void saveAsync(ExecutorService io) {
        rollover();
        if (!dirty) {
            return;
        }
        dirty = false;
        String content = snapshot();
        io.execute(() -> write(content));
    }

    /** 關服時同步寫入。 */
    public void saveNow() {
        rollover();
        if (!dirty) {
            return;
        }
        dirty = false;
        write(snapshot());
    }

    private String snapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("date", date.toString());
        Map<String, Object> players = new HashMap<>();
        for (Map.Entry<UUID, Double> e : today.entrySet()) {
            players.put(e.getKey().toString(), Math.round(e.getValue() * 10000d) / 10000d);
        }
        yaml.createSection("players", players);
        return yaml.saveToString();
    }

    private synchronized void write(String content) {
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            File tmp = new File(parent, file.getName() + ".tmp");
            Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.warning("Cannot save data.yml: " + e.getMessage());
            // 保留髒標記之外的資料在記憶體；下次發放會再寫
        }
    }
}
