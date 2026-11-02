package clre20.onlineReward.update;

import clre20.onlineReward.OnlineReward;
import clre20.onlineReward.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

/** 版本檢查結果的輸出：後台、/orw version、登入 3 秒後提醒。 */
public final class UpdateNotifier implements Listener {

    private final OnlineReward plugin;

    public UpdateNotifier(OnlineReward plugin) {
        this.plugin = plugin;
    }

    public void logToConsole(UpdateChecker.Result r) {
        Messages m = plugin.messages();
        switch (r.status()) {
            case NEW -> plugin.getLogger().warning(m.console("console.update-new",
                    "latest", r.latest(), "current", r.current(), "url", r.url()));
            case LATEST -> plugin.getLogger().info(m.console("console.update-latest", "current", r.current()));
            case DEV -> plugin.getLogger().info(m.console("console.update-dev",
                    "current", r.current(), "latest", r.latest()));
            case NONE -> plugin.getLogger().info(m.console("console.update-none"));
            case FAILED -> plugin.getLogger().warning(m.console("console.update-failed", "error", r.error()));
        }
    }

    public void send(CommandSender to, UpdateChecker.Result r) {
        Messages m = plugin.messages();
        String text = switch (r.status()) {
            case NEW -> m.get("update.new", "latest", r.latest(), "current", r.current(), "url", r.url());
            case LATEST -> m.get("update.latest", "current", r.current());
            case DEV -> m.get("update.dev", "current", r.current(), "latest", r.latest());
            case NONE -> m.get("update.none");
            case FAILED -> m.get("update.failed");
        };
        to.sendMessage(text);
    }

    /** /orw version：結果回到主執行緒再送出；玩家已離線就不送。 */
    public void checkFor(CommandSender sender) {
        sender.sendMessage(plugin.messages().get("update.checking"));
        UUID id = sender instanceof Player p ? p.getUniqueId() : null;
        plugin.updateChecker().check().thenAccept(r -> runSync(() -> {
            if (id == null) {
                send(Bukkit.getConsoleSender(), r);
                return;
            }
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                send(p, r);
            }
        }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.config().notifyOnJoin || !e.getPlayer().hasPermission("onlinereward.version")) {
            return;
        }
        UUID id = e.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.hasPermission("onlinereward.version")) {
                return;
            }
            plugin.updateChecker().check().thenAccept(r -> {
                if (r.status() == UpdateChecker.Status.NEW) {
                    runSync(() -> {
                        Player again = Bukkit.getPlayer(id);
                        if (again != null) {
                            send(again, r);
                        }
                    });
                }
            });
        }, 60L);
    }

    private void runSync(Runnable r) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, r);
        }
    }
}
