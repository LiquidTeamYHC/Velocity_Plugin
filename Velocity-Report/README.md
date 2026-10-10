<div align="center">

# VelocityReport

**A cross-server report plugin for the Velocity proxy**

[![简体中文](https://img.shields.io/badge/简体中文-Read_in_Chinese-blue?style=for-the-badge&logo=github)](README_zh_CN.md)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](../../LICENSE)
[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)]()
[![Velocity](https://img.shields.io/badge/Velocity-3.x%20%2F%204.x-blueviolet.svg)]()

</div>

Let players report others from **any server** on your network — the report is stored centrally and shown to staff on every server, no matter which server the player was on.

## ✨ Features

- **Cross-server reports** — players can report anyone across your whole proxy network; staff see a notification no matter which server they're on
- **Offline reports** — a player can be reported even after they log out, as long as they were last online within a configurable window (24 hours by default; `0` disables it)
- **Clickable reason menu** — `/report <player>` without a reason opens a clickable menu of predefined reasons
- **Custom reasons** — `/report <player> <reason>` supports free-text reasons with a 256-character limit
- **Two storage backends** — SQLite (zero-config, default) or MySQL (via HikariCP connection pool)
- **Bilingual** — built-in `zh_CN` (Simplified Chinese) and `en_US` (English) language files, switchable at runtime
- **Full staff workflow** — view report lists & details, resolve, close, reopen, per-report resolution notes
- **Report history** — browse every past report by player with pagination
- **Cooldown** — configurable anti-spam cooldown with a `velocityreport.bypasscooldown` permission
- **MiniMessage formatting** — all messages are fully customizable using [Adventure MiniMessage](https://docs.advntr.dev/minimessage/format.html)
- **Staff notifications** — in-game notification on submit and on join for pending reports
- **Staff list command** — manage the fallback staff list in game with `/reportstaff add|remove|list`, no config editing needed

## ✅ Requirements

| Requirement | Version |
|---|---|
| Java | 17+ |
| Proxy | Velocity 3.x / 4.x (API 3.3.0+) |
| Optional | A permissions plugin (e.g. [LuckPerms](https://luckperms.net)) for granular staff permissions |

## 📦 Installation

1. Download the plugin JAR (build it yourself or grab a release)
2. Drop it into the `plugins/` folder of your Velocity proxy
3. Restart the proxy (or run `/velocity reload` with the plugin reloader installed)
4. A `config.yml` is generated on first run — review it and restart if you change anything

## 🎮 Commands

### Player commands

| Command | Description | Permission |
|---|---|---|
| `/report <player> [reason]` (alias `/rep`) | Report a player — online, or offline within the configured window. Without a reason, opens a clickable reason menu | `velocityreport.report` (default: everyone) |

### Staff commands

| Command | Description | Permission |
|---|---|---|
| `/reports` (aliases `/reportslist`, `/reportlist`) | List all pending reports with pagination | `velocityreport.staff` |
| `/reportview <id>` | View full details of a single report | `velocityreport.staff` |
| `/reportclose <id> [resolution]` (aliases `/closereport`, `/resolvereport`) | Close/resolve a report, optionally with a resolution note | `velocityreport.staff` |
| `/reportcd view <id>` | Open the interactive report-detail menu | `velocityreport.staff` |
| `/reportcd resolve <id>` | Mark a report as resolved | `velocityreport.staff` |
| `/reportcd reopen <id>` | Reopen a resolved report | `velocityreport.staff` |
| `/reportadmin <subcommand>` (aliases `/ra`, `/reportadm`) | One entry point for all staff actions; no arguments prints the help. Subcommands: `reload`, `list [page]`, `view <id>`, `close <id> [note]`, `resolve\|reopen <id>`, `history [page] [player]`, `staff add\|remove\|list [player]` | `velocityreport.staff` |
| `/reportstaff` / `/reportstaff list` | Show the staff list (config.yml and staff-users.yml shown separately) | `velocityreport.staff` |
| `/reportstaff add <player>` | Add a player to the staff list (written to `staff-users.yml`, merged with `staff-users` in config.yml) | `velocityreport.staff` |
| `/reportstaff remove <player>` | Remove a player from the staff list (names listed in config.yml have to be removed there) | `velocityreport.staff` |

> **Tip:** The `/reportcd` detail menu has built-in clickable buttons — no need to type the subcommands.

## 🔑 Permissions

| Permission | Default | Description |
|---|---|---|
| `velocityreport.report` | true (all players) | Allows submitting reports (`/report` is not permission-checked, so everyone can use it) |
| `velocityreport.staff` | staff only | Access to all staff commands & staff report notifications (a name on the **`staff-users` list counts as having this node**) |
| `velocityreport.reload` | staff only | Use `/reportadmin reload`. **Not interchangeable with `staff`**: staff alone cannot reload, and reload alone grants nothing else |
| `velocityreport.bypasscooldown` | false | Ignore the report cooldown |
| `velocityreport.notify` | false | Receive staff notifications only (no commands; the `staff-users` list applies too) |

> **Without a permissions plugin:** Velocity grants players no permissions of its own (an undefined permission counts as false), so on a proxy without LuckPerms, adding a player to `staff-users` in `config.yml` — or with `/reportstaff add <player>` in game — gives them every staff command and notifications. **Reloading is the exception**: `/reportadmin reload` only answers to `velocityreport.reload`, so use the console for that case.

## ⚙️ Configuration (`config.yml`)

Key settings (all commented in the generated file):

| Setting | Default | Description |
|---|---|---|
| `language` | `zh_CN` | Language file: `zh_CN` or `en_US` |
| `database.type` | `sqlite` | `sqlite` or `mysql` |
| `database.sqlite.filename` | `reports.db` | SQLite database file |
| `database.mysql.*` | — | MySQL host / port / database / user / password + HikariCP pool tuning |
| `cooldown-seconds` | `60` | Seconds between reports (set `0` to disable) |
| `offline-report-window-hours` | `24` | How long after their last logout a player can still be reported (`0` = offline reports disabled) |
| `report-reasons` | 5 presets | Reason IDs + icons shown in the clickable menu (`hacking`, `chat`, `griefing`, `admin-abuse`, `other`) |
| `reports-per-page` | `10` | Reports per list page |
| `staff-users` | (empty) | Fallback staff list when no permissions plugin is installed (case-insensitive). Can also be filled with `/reportstaff add <player>`, which writes to `staff-users.yml` and is merged with this list |
| `server-aliases` | 3 presets | Map of server name → display text (e.g. `lobby: "&eLobby"`), supports `&` color codes and MiniMessage |
| `messages` | — | Override individual language-file messages (takes priority) |

## 🔨 Building from source

JDK 17+ and Maven 3.8+ are required.

```bash
git clone https://github.com/LiquidTeam11/Velocity_Plugin.git
cd Velocity_Plugin/Velocity-Report
mvn package
```

The built plugin is at `target/Velocity-Report-2.3.0.jar`.

## 📜 Changelog

See [历史更新.md](历史更新.md) for the version history.

## 👤 Author

- **YuHongChen (LiquidTeam)** — main developer
- QQ: `1464670605`

## 📄 License

This project is licensed under the [MIT License](../../LICENSE).
