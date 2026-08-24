<div align="center">

# VelocityChat

**A cross-server proxy chat plugin for Velocity**

[![简体中文](https://img.shields.io/badge/简体中文-Read_in_Chinese-blue?style=for-the-badge&logo=github)](README_zh_CN.md)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](../../LICENSE)
[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)]()
[![Velocity](https://img.shields.io/badge/Velocity-3.x-blueviolet.svg)]()

</div>

Chat together across your entire server network. Install **only on the Velocity proxy** — no backend server plugins required.

## Features

- **Cross-server broadcast** — `/br <message>` sends a chat message to every player on every backend server
- **Server invites** — `/yq [message]` broadcasts a server invite into the cross-server chat; other players click it to join the inviter's server (configurable cooldown & permission bypass)
- **Forbidden-word filter** — block `/br` and `/yq` messages containing banned words (configurable, words stored in `banned_words.txt` separated by `;`, admin bypass)
- **Timed announcements** — schedule announcements by clock time with daily / weekly / monthly / one-shot recurrence, or loop on an interval like `30min` (configured in `auto_broadcast.yml`)
- **Color codes** — full `&` color code support (`&c`, `&6`, `&l`, `&r`, …)
- **Join / switch / leave announcements** — automatic messages when players join, switch servers, or disconnect
- **Server aliases** — show friendly names like "登录服" instead of raw server IDs, configured directly in `config.yml` (`serverId: "&aDisplayName"`)
- **Group titles** — create groups and assign title prefixes shown before player names in chat
- **Granular permissions** — `velocitychat.admin.*` sub-nodes, works with LuckPerms (optional dependency)
- **Broadcast cooldown** — optional anti-spam cooldown, admins can bypass
- **Message visibility** — join/switch/leave alerts can be shown to everyone, admins only, or disabled
- **Bilingual** — built-in `zh_CN` (Simplified Chinese) and `en_US` (English) language files, switchable at runtime

## Requirements

| Requirement | Version |
|---|---|
| Java | 17+ |
| Proxy | Velocity 3.x (all modern versions) |
| Optional | A permissions plugin (e.g. [LuckPerms](https://luckperms.net)) for granular admin permissions |

## Installation

1. Download the plugin JAR (build it yourself or grab a release)
2. Drop it into the `plugins/` folder of your Velocity proxy
3. Restart the proxy
4. A `config.yml` is generated on first run — review it and restart if you change anything

> **No backend plugins needed** — this runs entirely on the proxy.

## Commands

### Player commands

| Command | Description | Permission |
|---|---|---|
| `/br <message>` (alias `/broadcast`, configurable) | Send a cross-server chat message to all players | everyone |
| `/yq [message]` | Broadcast a server invite into the cross-server chat; others click to join your server | everyone |

### Admin commands (`/velocitychat`, alias `/vchat`)

| Command | Description | Permission |
|---|---|---|
| `/vchat create group <name> [title]` | Create a new group with an optional title | `velocitychat.admin.create` |
| `/vchat group <name> join <player>` | Add a player to a group | `velocitychat.admin.group.join` |
| `/vchat group <name> remove <player>` | Remove a player from a group | `velocitychat.admin.group.remove` |
| `/vchat group <name> settitle <title>` | Change the group's title | `velocitychat.admin.group.settitle` |
| `/vchat group <name> delete` | Delete a group | `velocitychat.admin.group.delete` |
| `/vchat group list` | List all groups and their members | `velocitychat.admin.group.list` |
| `/vchat reload` | Hot-reload config, language files, group data, forbidden words & timed announcements | `velocitychat.admin.reload` |

Titles support `&` color codes, e.g. `/vchat create group admin &c&l[Admin]`.

## Permissions

| Permission | Description |
|---|---|
| `velocitychat.admin` | Top-level admin permission — grants all sub-nodes and bypasses the broadcast cooldown |
| `velocitychat.admin.create` | Create groups |
| `velocitychat.admin.group.*` | Wildcard for all group-management permissions |
| `velocitychat.admin.group.join` | Add players to groups |
| `velocitychat.admin.group.remove` | Remove players from groups |
| `velocitychat.admin.group.settitle` | Change group titles |
| `velocitychat.admin.group.delete` | Delete groups |
| `velocitychat.admin.group.list` | List groups and members |
| `velocitychat.admin.reload` | Reload configuration |

> **Tip:** LuckPerms example — `/lpv user <player> permission set velocitychat.admin true`

## Configuration (`config.yml`)

Key settings (all commented in the generated file):

| Setting | Default | Description |
|---|---|---|
| `language` | `zh_CN` | Language file: `zh_CN` or `en_US` |
| `broadcast-aliases` | `br`, `broadcast` | Aliases for the cross-server broadcast command |
| `broadcast-cooldown` | `0` | Seconds between `/br` uses (`0` = no cooldown) |
| `broadcast-cooldown-bypass` | `false` | When enabled, `velocitychat.admin` players ignore the cooldown |
| `invite-cooldown` | `30` | Seconds between `/yq` invites (`0` = no cooldown) |
| `invite-cooldown-bypass` | `false` | When enabled, players with `invite-bypass-permission` ignore the invite cooldown |
| `invite-bypass-permission` | `velocitychat.admin` | Permission that bypasses the invite cooldown |
| `forbidden-words-enabled` | `true` | Enable the forbidden-word filter on `/br` and `/yq` messages |
| `forbidden-words-bypass-permission` | `velocitychat.admin` | Permission that bypasses the forbidden-word filter |
| `server-aliases` | map presets | Servers that show a display name: `serverId: "&aDisplayName"` (supports `&` colors) |
| `notify-mode` | `all` | Join/switch/leave visibility: `all`, `admin` (admins only), or `none` (disabled) |
| `groups` | — | Preset groups generated on first startup |
| `messages` | — | Override individual language-file messages (takes priority) |

Group data is stored in a `groups.yml` file. Banned words live in `banned_words.txt`, and timed announcements are configured in `auto_broadcast.yml` (all generated on first startup).

## Building from source

JDK 17+ and Maven 3.8+ are required.

```bash
git clone https://github.com/LiquidTeamYHC/Velocity_Plugin.git
cd Velocity_Plugin/VelocityChat
mvn package
```

The built plugin is at `target/VelocityChat-2.1.0.jar`.

## Changelog

See [更新日志-CHANGELOG.md](更新日志-CHANGELOG.md) for the full version history.

## Author

- **YuHongChen (LiquidTeam)** — main developer
- QQ: `1464670605`

## License

This project is licensed under the [MIT License](../../LICENSE).
