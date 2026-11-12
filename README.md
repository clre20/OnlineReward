# OnlineReward

定時給真的在玩的線上玩家發獎勵的輕量插件。每隔固定時間，全服同時發放一次，可以發錢、執行指令（給物品等），或兩者都給；掛機的玩家領不到，還可以設定每日次數上限與不發放的世界。

## 功能

- **定時發放**
  - 全服同一時間發放，預設每 10 分鐘 5 元，間隔與金額都可在 config.yml 修改
  - 以實際時間計算，伺服器卡頓時不會延後
- **指令獎勵**
  - 每次發放時以後台身分執行你設定的指令，可用 `{player}`、`{uuid}`、`{world}`
  - 可以「5 元再加 1 鐵錠」，也可以把金額設 0「只給 1 鑽石」
  - 人多時分散到幾個 tick 執行，不會一次卡住伺服器
- **掛機判定**（只算真的在玩）
  - 一段時間（預設 5 分鐘）沒有真的在玩就不發，並簡短提示玩家
  - 算在玩：按鍵（走、跳、蹲、衝刺）、點背包或容器裡的物品、聊天
  - 不算：被水流沖、墜落、被推、騎乘、空中揮手、點方塊或實體、打指令，掛機池與墜落機關無效
  - 可選擇只轉視角也算；就算開啟，在水裡、墜落、騎乘、剛傳送後的轉動仍然不算
- **每日上限**（預設不限）
  - 以次數計算，達到上限時提示一次
  - 每天 00:00（伺服器時區）重置，重啟後保留當天紀錄
- **不發放的世界**：世界名稱不分大小寫
- **經濟**：透過 Vault，支援 TheosisEconomy 等任何 Vault 經濟插件；金額顯示格式由經濟插件決定。只用指令獎勵時不需要 Vault
- **基岩版**：Geyser 玩家照常領取
- **自訂文字**：所有訊息都在 messages.yml，支援 `&` 顏色碼與 `&#RRGGBB`
- **自動補齊設定**：啟動與重載時補上缺少的項目，保留你的值與註解；格式壞掉時備份並暫用預設值
- **檢查更新**：啟動時背景檢查 GitHub Releases，管理員登入 3 秒後提醒；結果快取 10 分鐘，不輪詢

## 指令與權限

| 指令 | 說明 | 執行者 | 權限 | 預設 |
|---|---|---|---|---|
| `/orw`、`/orw help` | 顯示有權限的指令 | 玩家／後台 | `onlinereward.help` | 所有人 |
| `/orw status` | 下次發放時間、今天已領次數 | 玩家／後台 | `onlinereward.status` | 所有人 |
| `/orw version` | 顯示版本並檢查更新 | 玩家／後台 | `onlinereward.version` | OP |
| `/orw reload` | 重新載入並補齊 config.yml、messages.yml | 玩家／後台 | `onlinereward.reload` | OP |
| — | 可以領取在線獎勵 | — | `onlinereward.receive` | 所有人 |
| — | 以上全部 | — | `onlinereward.admin` | OP |

> 完整指令為 `/onlinereward`，別名 `/orw`。
> 有 `onlinereward.version` 的玩家在有新版本時，登入 3 秒後會收到提醒。

## 安裝

1. 要發錢時安裝 [Vault](https://www.spigotmc.org/resources/vault.34315/) 與一個 Vault 經濟插件（例如 TheosisEconomy）
2. 把 `OnlineReward-x.y.z.jar` 放進 `plugins/`
3. 重啟伺服器，視需要修改 `plugins/OnlineReward/config.yml` 後輸入 `/orw reload`

支援 Paper 1.21.x ～ 26.x（26.x 需要 Java 25），Java 21 以上。

## 設定範例

### 1. 5 元再加 1 個鐵錠

```yaml
reward:
  amount: 5
  commands:
    - "minecraft:give {player} minecraft:iron_ingot 1"
```

### 2. 只給 1 個鑽石，不給錢

```yaml
reward:
  amount: 0
  commands:
    - "minecraft:give {player} minecraft:diamond 1"
```

只發指令時玩家看到的是 messages.yml 的 `reward.received-commands`，可以改成實際內容，例如 `"{prefix}&a在線獎勵 &b鑽石 x1"`。

### 3. 某些玩家不發

用權限插件拿掉該玩家或群組的 `onlinereward.receive`：

```bash
lp group default permission set onlinereward.receive true
lp group bot permission set onlinereward.receive false
```

---

# OnlineReward (English)

A lightweight plugin that rewards players who are actually playing, on a fixed schedule. Pay money, run commands (e.g. give items), or both; AFK players are skipped, and you can set a daily cap and excluded worlds.

## Features

- **Scheduled payout**: everyone online at the same time, default 5 every 10 minutes, both configurable; wall-clock based so lag does not delay payouts
- **Command rewards**: run console commands each payout with `{player}`, `{uuid}`, `{world}`; e.g. 5 money plus 1 iron ingot, or set amount to 0 and give only a diamond. Spread over ticks when many players are online
- **AFK detection** (real play only): key presses (move, jump, sneak, sprint), clicking items in inventories and chat count; being carried by water, falling, being pushed, riding, swinging at air, clicking blocks or entities and commands do not. Camera-only activity can be enabled, but still never counts in water, while falling, riding or right after a teleport
- **Daily cap** (off by default): number of payouts per day, notifies once, resets at 00:00 server time, survives restarts
- **Excluded worlds**: case-insensitive
- **Economy**: any Vault economy (e.g. TheosisEconomy); amounts formatted by the economy plugin. Vault is not needed for command-only rewards
- **Bedrock**: Geyser players are paid as usual
- **Editable text**: every message in messages.yml, `&` colors and `&#RRGGBB`
- **Auto-filled config**: missing keys added on start and reload, keeping your values and comments; broken files are backed up and defaults used
- **Update check**: async check of GitHub Releases on start, admins notified 3 seconds after join; cached 10 minutes, no polling

## Commands & Permissions

| Command | Description | Executor | Permission | Default |
|---|---|---|---|---|
| `/orw`, `/orw help` | List permitted commands | Player / Console | `onlinereward.help` | Everyone |
| `/orw status` | Next payout and today's count | Player / Console | `onlinereward.status` | Everyone |
| `/orw version` | Show version and check for updates | Player / Console | `onlinereward.version` | OP |
| `/orw reload` | Reload and fill config.yml, messages.yml | Player / Console | `onlinereward.reload` | OP |
| — | Can receive the reward | — | `onlinereward.receive` | Everyone |
| — | All of the above | — | `onlinereward.admin` | OP |

> Full command is `/onlinereward`, alias `/orw`.

## Installation

1. To pay money, install Vault and a Vault economy plugin
2. Drop `OnlineReward-x.y.z.jar` into `plugins/`
3. Restart, edit `config.yml` if needed, then `/orw reload`

Supports Paper 1.21.x – 26.x (26.x requires Java 25), Java 21+.
