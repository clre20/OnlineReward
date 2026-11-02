package clre20.onlineReward.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 記錄每位線上玩家最後一次「有在動」的時間。
 * 算有在動：轉動視角、聊天、打指令、與方塊或實體互動、點擊背包物品。
 * 單純被水流、活塞推動只改變位置不改變視角，所以不算。
 * 每人一個可變物件，更新不配置新物件；同一秒內重複的更新直接略過。
 */
public final class AfkTracker implements Listener {

    private static final long THROTTLE_MS = 1000L;

    private static final class Activity {
        volatile long last;

        Activity(long now) {
            this.last = now;
        }
    }

    private final Map<UUID, Activity> activity = new ConcurrentHashMap<>();

    public void track(Player player) {
        activity.put(player.getUniqueId(), new Activity(System.currentTimeMillis()));
    }

    public void clear() {
        activity.clear();
    }

    /** 最後活動距今幾毫秒；沒有紀錄時視為剛活動。 */
    public long idleMillis(UUID id, long now) {
        Activity a = activity.get(id);
        return a == null ? 0 : now - a.last;
    }

    private void touch(UUID id) {
        Activity a = activity.get(id);
        if (a == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - a.last >= THROTTLE_MS) {
            a.last = now;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        track(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        activity.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        // 最常見的情況（視角沒變）在這裡直接返回
        if (to == null || (from.getYaw() == to.getYaw() && from.getPitch() == to.getPitch())) {
            return;
        }
        touch(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent e) {
        touch(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        touch(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        // 踩壓力板、絆線屬於 PHYSICAL，掛機機器可以觸發，不算
        if (e.getAction() == Action.PHYSICAL) {
            return;
        }
        touch(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        touch(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent e) {
        touch(e.getWhoClicked().getUniqueId());
    }
}
