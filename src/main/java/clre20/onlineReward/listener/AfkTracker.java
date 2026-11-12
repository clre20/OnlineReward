package clre20.onlineReward.listener;

import clre20.onlineReward.OnlineReward;
import clre20.onlineReward.config.PluginConfig;
import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 記錄每位線上玩家最後一次「真的在玩」的時間。
 *
 * <p>算在玩：
 * <ul>
 *   <li>按鍵改變（前後左右、跳、蹲、衝刺；Paper 1.21.3 起的 PlayerInputEvent）。按住不放只算一次。</li>
 *   <li>點擊背包或容器裡的物品。</li>
 *   <li>聊天（count-chat）、打指令（count-commands）。</li>
 *   <li>轉視角（count-looking，預設關閉）。開啟時，在水或岩漿裡、氣泡柱裡、騎乘、墜落中、
 *       剛傳送或重生後 2 秒內的轉動都不算，而且要累積轉動 30 度以上才算一次。</li>
 * </ul>
 * 被水流沖、墜落、被推動、空中揮手、點方塊或實體都不算，這些都能用機關或連點器做出來。
 *
 * <p>舊版伺服器（沒有 PlayerInputEvent）時，改用「站在地上、沒在水裡、沒騎乘時自己移動」判定。
 * 每人一個可變物件，更新不配置新物件；同一秒內重複的更新直接略過。
 */
public final class AfkTracker implements Listener {

    private static final long THROTTLE_MS = 1000L;
    private static final long TELEPORT_IGNORE_MS = 2000L;
    private static final float LOOK_THRESHOLD = 30f;
    private static final float FALL_THRESHOLD = 1.0f;
    private static final String INPUT_EVENT = "org.bukkit.event.player.PlayerInputEvent";

    private static final class Activity {
        volatile long last;
        long ignoreLookUntil;
        float lookAccum;

        Activity(long now) {
            this.last = now;
        }
    }

    private final OnlineReward plugin;
    private final Map<UUID, Activity> activity = new ConcurrentHashMap<>();
    private boolean inputSupported;

    public AfkTracker(OnlineReward plugin) {
        this.plugin = plugin;
    }

    /** 註冊監聽；有 PlayerInputEvent 時另外用名稱註冊（編譯時不需要該類別）。 */
    @SuppressWarnings("unchecked")
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        try {
            Class<?> c = Class.forName(INPUT_EVENT);
            if (PlayerEvent.class.isAssignableFrom(c)) {
                plugin.getServer().getPluginManager().registerEvent((Class<? extends Event>) c, this,
                        EventPriority.MONITOR, (listener, event) -> {
                            if (event instanceof PlayerEvent pe) {
                                touch(pe.getPlayer().getUniqueId(), System.currentTimeMillis());
                            }
                        }, plugin, false);
                inputSupported = true;
            }
        } catch (ClassNotFoundException ignored) {
            inputSupported = false;
        }
    }

    public boolean inputSupported() {
        return inputSupported;
    }

    public void track(Player player) {
        long now = System.currentTimeMillis();
        Activity a = new Activity(now);
        a.ignoreLookUntil = now + TELEPORT_IGNORE_MS;
        activity.put(player.getUniqueId(), a);
    }

    public void clear() {
        activity.clear();
    }

    /** 最後活動距今幾毫秒；沒有紀錄時視為剛活動。 */
    public long idleMillis(UUID id, long now) {
        Activity a = activity.get(id);
        return a == null ? 0 : now - a.last;
    }

    private void touch(UUID id, long now) {
        Activity a = activity.get(id);
        if (a != null && now - a.last >= THROTTLE_MS) {
            a.last = now;
        }
    }

    /** 被環境帶著走的狀態：這時的轉動或移動不代表玩家在操作。 */
    private static boolean passive(Player p) {
        if (p.isInsideVehicle() || p.isInWater() || p.isInLava() || p.isInBubbleColumn()) {
            return true;
        }
        return p.getFallDistance() > FALL_THRESHOLD && !p.isGliding() && !p.isFlying();
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
        if (to == null) {
            return;
        }
        float dYaw = Math.abs(wrap(to.getYaw() - from.getYaw()));
        float dPitch = Math.abs(to.getPitch() - from.getPitch());
        boolean looked = dYaw > 0f || dPitch > 0f;
        // 有按鍵事件時，純移動一律不看；最常見的情況在這裡直接返回
        if (!looked && inputSupported) {
            return;
        }
        PluginConfig cfg = plugin.config();
        boolean checkWalk = !inputSupported;
        boolean checkLook = looked && cfg.afkCountLooking;
        if (!checkWalk && !checkLook) {
            return;
        }
        Activity a = activity.get(e.getPlayer().getUniqueId());
        if (a == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - a.last < THROTTLE_MS) {
            return;
        }
        Player p = e.getPlayer();
        if (now < a.ignoreLookUntil || passive(p)) {
            a.lookAccum = 0f;
            return;
        }
        if (checkWalk && p.isOnGround()) {
            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();
            if (dx * dx + dz * dz > 0.0004) {
                a.last = now;
                a.lookAccum = 0f;
                return;
            }
        }
        if (checkLook) {
            a.lookAccum += dYaw + dPitch;
            if (a.lookAccum >= LOOK_THRESHOLD) {
                a.lookAccum = 0f;
                a.last = now;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent e) {
        ignoreLook(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        ignoreLook(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        ignoreLook(e.getPlayer().getUniqueId());
    }

    private void ignoreLook(UUID id) {
        Activity a = activity.get(id);
        if (a != null) {
            a.ignoreLookUntil = System.currentTimeMillis() + TELEPORT_IGNORE_MS;
            a.lookAccum = 0f;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent e) {
        if (plugin.config().afkCountChat) {
            touch(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (plugin.config().afkCountCommands) {
            touch(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent e) {
        HumanEntity who = e.getWhoClicked();
        // 點到背包外（空白處）不算
        if (who instanceof Player && e.getClickedInventory() != null) {
            touch(who.getUniqueId(), System.currentTimeMillis());
        }
    }

    private static float wrap(float deg) {
        deg %= 360f;
        if (deg >= 180f) {
            deg -= 360f;
        } else if (deg < -180f) {
            deg += 360f;
        }
        return deg;
    }
}
