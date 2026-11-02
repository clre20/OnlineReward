# OnlineReward

定時發錢給線上玩家的輕量插件。每隔固定時間，全服同時發放一次獎勵；掛機的玩家領不到，還可以設定每日上限與不發放的世界。

## 功能

- **定時發放**
  - 全服同一時間發放，預設每 10 分鐘 5 元，間隔與金額都可在 config.yml 修改
  - 以實際時間計算，伺服器卡頓時不會延後
- **掛機判定**
  - 一段時間沒動（預設 5 分鐘）就不發，並簡短提示玩家
  - 轉動視角、聊天、打指令、與方塊或實體互動、點擊背包物品算「有在動」
  - 單純被水流、活塞推動或踩壓力板不算，掛機池無效
- **每日上限**（預設不限）
  - 以金額計算，快到上限時只發剩下的部分，達到上限時提示一次
  - 每天 00:00（伺服器時區）重置，重啟後保留當天紀錄
- **不發放的世界**：世界名稱不分大小寫
- **經濟**：透過 Vault，支援 TheosisEconomy 等任何 Vault 經濟插件；金額顯示格式由經濟插件決定
- **基岩版**：Geyser 玩家照常領取
- **自訂文字**：所有訊息都在 messages.yml，支援 `&` 顏色碼與 `&#RRGGBB`
- **自動補齊設定**：啟動與重載時補上缺少的項目，保留你的值與註解；格式壞掉時備份並暫用預設值
- **檢查更新**：啟動時背景檢查 GitHub Releases，管理員登入 3 秒後提醒；結果快取 10 分鐘，不輪詢

## 指令與權限

| 指令 | 說明 | 執行者 | 權限 | 預設 |
|---|---|---|---|---|
| `/orw`、`/orw help` | 顯示有權限的指令 | 玩家／後台 | `onlinereward.help` | 所有人 |
| `/orw status` | 下次發放時間、今天已領金額 | 玩家／後台 | `onlinereward.status` | 所有人 |
| `/orw version` | 顯示版本並檢查更新 | 玩家／後台 | `onlinereward.version` | OP |
| `/orw reload` | 重新載入並補齊 config.yml、messages.yml | 玩家／後台 | `onlinereward.reload` | OP |
| — | 可以領取在線獎勵 | — | `onlinereward.receive` | 所有人 |
| — | 以上全部 | — | `onlinereward.admin` | OP |

> 完整指令為 `/onlinereward`，別名 `/orw`。
> 有 `onlinereward.version` 的玩家在有新版本時，登入 3 秒後會收到提醒。

## 安裝

1. 安裝 [Vault](https://www.spigotmc.org/resources/vault.34315/) 與一個 Vault 經濟插件（例如 TheosisEconomy）
2. 把 `OnlineReward-x.y.z.jar` 放進 `plugins/`
3. 重啟伺服器，視需要修改 `plugins/OnlineReward/config.yml` 後輸入 `/orw reload`

支援 Paper 1.21.x ～ 26.x（26.x 需要 Java 25），Java 21 以上。

### 3. 某些玩家不發

用權限插件拿掉該玩家或群組的 `onlinereward.receive`：

```bash
lp group default permission set onlinereward.receive true
lp group bot permission set onlinereward.receive false
```

---

# OnlineReward (English)

A lightweight plugin that pays online players on a fixed schedule. All online players are paid at the same moment; AFK players are skipped, and you can set a daily cap and excluded worlds.

## Features

- **Scheduled payout**: everyone online at the same time, default 5 every 10 minutes, both configurable; wall-clock based so lag does not delay payouts
- **AFK detection**: no reward after a period of inactivity (default 5 minutes) with a short notice. Turning the camera, chatting, commands, interacting with blocks or entities and clicking inventory items count as activity; being pushed by water or pistons, or stepping on pressure plates, does not
- **Daily cap** (off by default): money-based, pays only the remainder near the cap, notifies once, resets at 00:00 server time, survives restarts
- **Excluded worlds**: case-insensitive
- **Economy**: any Vault economy (e.g. TheosisEconomy); amounts formatted by the economy plugin
- **Bedrock**: Geyser players are paid as usual
- **Editable text**: every message in messages.yml, `&` colors and `&#RRGGBB`
- **Auto-filled config**: missing keys added on start and reload, keeping your values and comments; broken files are backed up and defaults used
- **Update check**: async check of GitHub Releases on start, admins notified 3 seconds after join; cached 10 minutes, no polling

## Commands & Permissions

| Command | Description | Executor | Permission | Default |
|---|---|---|---|---|
| `/orw`, `/orw help` | List permitted commands | Player / Console | `onlinereward.help` | Everyone |
| `/orw status` | Next payout and today's total | Player / Console | `onlinereward.status` | Everyone |
| `/orw version` | Show version and check for updates | Player / Console | `onlinereward.version` | OP |
| `/orw reload` | Reload and fill config.yml, messages.yml | Player / Console | `onlinereward.reload` | OP |
| — | Can receive the reward | — | `onlinereward.receive` | Everyone |
| — | All of the above | — | `onlinereward.admin` | OP |

> Full command is `/onlinereward`, alias `/orw`.

## Installation

1. Install Vault and a Vault economy plugin
2. Drop `OnlineReward-x.y.z.jar` into `plugins/`
3. Restart, edit `config.yml` if needed, then `/orw reload`

Supports Paper 1.21.x – 26.x (26.x requires Java 25), Java 21+.
