# VelocityChat 更新日志 / Changelog

## v2.1.0
- 新增定时消息：按时刻调度向全服玩家广播公告（默认关闭，在 `auto_broadcast.yml` 中 `enabled: true` 开启）
  - 独立的 `auto_broadcast.yml` 文件配置（首次启动自动生成）
  - 按时刻调度：`time: "HH:mm"`（支持 `"9:00"`）+ `repeat` 循环方式
    - `repeat: daily` — 每天循环
    - `repeat: weekly` + `weekday: 1-7`（1=周一 … 7=周日）— 每周指定星期循环
    - `repeat: monthly` + `day: 1-31`（超出当月天数则取当月最后一天）— 每月指定日期循环
    - `repeat: once` — 仅播放一次（当日时刻已过则不再播放）
    - 定时消息按目标时刻计算延迟，播放后自动排入下一次（重启后自动重新计算）
  - 间隔循环：`repeat` 使用时长格式 `xxhxxminxxs`（如 `30min`、`1h30min`、`45s`、`2h`），每隔这么久循环一次
  - 支持 `&` 颜色代码与 `{online}` 占位符（当前在线玩家数）
  - 修改后执行 `/vchat reload` 生效；代理关闭时自动停止定时任务
- 版本号更新至 2.1.0

- New timed announcements: broadcast messages to all players on a schedule (off by default — enable with `enabled: true` in `auto_broadcast.yml`)
  - Configured in a separate `auto_broadcast.yml` (generated on first startup)
  - Clock-time scheduling: `time: "HH:mm"` (`"9:00"` works) + a `repeat` recurrence mode
    - `repeat: daily` — every day
    - `repeat: weekly` + `weekday: 1-7` (1=Mon … 7=Sun) — every week on that weekday
    - `repeat: monthly` + `day: 1-31` (clamped to the last day of the month) — every month on that day
    - `repeat: once` — play only once (skipped if today's time already passed)
    - Time-based messages compute the delay to the next occurrence and reschedule afterwards (recomputed on restart)
  - Interval looping: `repeat` accepts a duration string `xxhxxminxxs` (e.g. `30min`, `1h30min`, `45s`, `2h`) to loop every that period
  - Supports `&` color codes and the `{online}` placeholder (online player count)
  - Run `/vchat reload` after editing; the scheduled task stops automatically on proxy shutdown
- Version bumped to 2.1.0

## v2.0.0
- 新增违禁词检测：开启后，`/br` 与 `/yq` 消息若包含违禁词将被拦截
  - `config.yml` 中 `forbidden-words-enabled` 控制开关（默认开启）
  - 违禁词存放在独立的 `banned_words.txt` 文件中，用分号 `;` 分隔，支持 `#` 注释
  - 内置默认违禁词：傻逼、你妈、操你妈、傻逼九月、傻子、傻逼玩意、智障、乐子、妈逼
  - 匹配不区分大小写，并忽略颜色代码（如 `&c`/`§c`，含加粗/下划线等格式码）
  - 兼容记事本保存的 UTF-8 BOM 文件（首行违禁词不再失效）
  - 拥有 `forbidden-words-bypass-permission`（默认 `velocitychat.admin`）的玩家可绕过
  - 修改 `banned_words.txt` 后执行 `/vchat reload` 热重载（并发安全）
- 服务器别名改为在 `config.yml` 中直接以 map 形式配置（服务器ID: "&a显示名称"），无需再改语言文件
- 修复控制台颜色：控制台执行 `/br`、`/k`、`/yq`、`/vchat` 时，提示/反馈消息不再显示字面 `§` 颜色符号，改为正确显示 ANSI 颜色
- 版本号更新至 2.0.0

- New forbidden-word filter: when enabled, `/br` and `/yq` messages containing a banned word are blocked
  - Toggle with `forbidden-words-enabled` in `config.yml` (on by default)
  - Banned words live in a separate `banned_words.txt`, separated by semicolons `;`, `#` for comments
  - Built-in default banned words: 傻逼, 你妈, 操你妈, 傻逼九月, 傻子, 傻逼玩意, 智障, 乐子, 妈逼
  - Matching is case-insensitive and ignores color codes (e.g. `&c`/`§c`, including formatting codes like bold/underline)
  - Handles UTF-8 BOM files saved by Notepad (first word no longer silently fails)
  - Players with `forbidden-words-bypass-permission` (default `velocitychat.admin`) bypass the filter
  - Run `/vchat reload` after editing `banned_words.txt` (concurrency-safe)
- Server aliases are now configured directly as a map in `config.yml` (`serverId: "&aDisplayName"`) — no need to touch the language files
- Fixed console colors: `/br`, `/k`, `/yq` and `/vchat` no longer print literal `§` codes in the console — they now render with ANSI colors
- Version bumped to 2.0.0

## v1.10.0
- 新增 `/k <消息>` 别称：现在可使用 `/br`、`/k` 或 `/broadcast` 发送跨服聊天
- `/k` 始终被注册（无论 config.yml 中 `broadcast-aliases` 是否列出，旧配置也无需手动修改）
- 版本号更新至 1.10.0

- New `/k <message>` alias: `/br`, `/k` and `/broadcast` all send cross-server chat now
- `/k` is always registered regardless of the `broadcast-aliases` list in config.yml (no manual edit needed for existing configs)
- Version bumped to 1.10.0

## v1.9.1
- 修复 `/yq` 邀请悬停提示占位符未替换的问题：鼠标悬停在"点击加入"上不再显示 `点击进入{1}`，而是正确显示目标服务器名称（如 `点击进入 §a生存服`）
- 版本号更新至 1.9.1

- Fixed `/yq` invite hover tooltip not resolving its placeholder: hovering "Click to Join" no longer shows a literal `{1}`, it now correctly shows the target server name (e.g. `Click to join §aSurvival`)
- Version bumped to 1.9.1

## v1.9.0
- 新增跨服邀请功能：玩家输入 `/yq [消息]` 在跨服聊天中广播邀请，其他玩家点击"点击加入"即可进入该玩家所在的服务器
- 邀请冷却可配置：`invite-cooldown`（秒，0 = 关闭）
- 邀请冷却权限绕过：`invite-cooldown-bypass` + `invite-bypass-permission`（默认 `velocitychat.admin`）
- 兼容升级：数据目录中的旧版语言文件缺少新版本新增的消息键时，会自动从内置语言文件补齐（用户自定义的键不会被覆盖）

- New cross-server invite feature: `/yq [message]` broadcasts a server invite into the cross-server chat; other players can click it to join the inviter's server
- Configurable invite cooldown: `invite-cooldown` (seconds, 0 = disabled)
- Configurable cooldown bypass: `invite-cooldown-bypass` + `invite-bypass-permission` (default `velocitychat.admin`)
- Upgrade compatibility: older language files in the data directory automatically gain newly-added message keys from the built-in files (user customizations are never overwritten)

## v1.7.0
- 修复封禁玩家尝试连接时仍显示离开消息的问题（`[-]xxx离开了unknown`）
  - 若玩家从未连接到任何后端服务器（如封禁连接被拒），不再广播离开消息

- Fixed banned players still triggering disconnect messages (`[-]xxx left unknown`)
  - If the player never connected to a backend server (e.g. banned connection rejected), leave message is now suppressed

## v1.6.0
- `/br` Tab 补全优化：玩家输入内容后不再显示补全提示
- 代码内部优化

- `/br` tab completion optimization: no longer shows suggestions after player has typed
- Internal code improvements

## v1.5.0
- 修复断线时偶尔显示 `unknown` 的问题（缓存玩家最后所在服务器）
- 优化断线消息可靠性

- Fixed disconnect event occasionally showing `unknown` (cache last known server)
- Improved disconnect message reliability

## v1.4.0
- 控制台不再显示进服/切服/离服消息
- 权限细分：`velocitychat.admin.create` / `.group.join` / `.group.remove` / `.group.settitle` / `.group.delete` / `.group.list` / `.reload`
- 玩家进服自动注册权限节点到 LuckPerms

- Console no longer shows join/switch/leave messages
- Granular permissions: `velocitychat.admin.*` sub-nodes
- Permissions auto-register with LuckPerms on player join

## v1.3.0
- `/br` 冷却时间设置
- 管理员可绕过冷却（`velocitychat.admin`）

- `/br` cooldown config
- Admins can bypass cooldown

## v1.2.0
- 进服/切服/离服消息可见性控制：`all` / `admin` / `none`

- Join/switch/leave message visibility: `all` / `admin` / `none`

## v1.1.0
- 群组称号系统：`/vchat create group`、`/vchat group join`
- 称号支持 `&` 颜色代码
- `/vchat reload` 热重载

- Group title system: `/vchat create group`, `/vchat group join`
- `&` color codes in titles
- `/vchat reload` hot-reload

## v1.0.0
- `/br <消息>` 跨服聊天，支持 `&` 颜色代码
- 进服/切服/离服自动消息
- 服务器别名（如 lobby → 登录服）
- 中英双语

- `/br <message>` cross-server chat with `&` color codes
- Join/switch/leave announcements
- Server aliases (e.g. lobby → 登录服)
- Chinese & English
