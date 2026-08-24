package com.velocitychat;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Administrative command for managing player title groups.
 * <p>
 * Usage:
 *   /velocitychat create group &lt;name&gt; [title]   — Create a new group with optional title
 *   /velocitychat group &lt;name&gt; join &lt;player&gt;  — Add a player to a group
 *   /velocitychat group &lt;name&gt; remove &lt;player&gt; — Remove a player from a group
 *   /velocitychat group &lt;name&gt; settitle &lt;title&gt; — Change the group's title
 *   /velocitychat group &lt;name&gt; delete             — Delete a group
 *   /velocitychat list                              — List all groups and their members
 * <p>
 * Aliases: /vchat
 * Permission: velocitychat.admin
 */
public class VelocityChatCommand implements SimpleCommand {

    private final ProxyServer server;
    private final GroupManager groupManager;
    private final ConfigManager config;
    private final ForbiddenWordsManager forbiddenWords;
    private final AutoBroadcast autoBroadcast;
    private final Logger logger;

    public VelocityChatCommand(ProxyServer server, GroupManager groupManager, ConfigManager config,
                               ForbiddenWordsManager forbiddenWords, AutoBroadcast autoBroadcast, Logger logger) {
        this.server = server;
        this.groupManager = groupManager;
        this.config = config;
        this.forbiddenWords = forbiddenWords;
        this.autoBroadcast = autoBroadcast;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            sendHelp(source);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (!checkPerm(source, "velocitychat.admin.create", "velocitychat.admin")) { noPermission(source); return; }
                handleCreate(source, args);
            }
            case "group" -> {
                if (!checkPerm(source, "velocitychat.admin.group.*", "velocitychat.admin")) { noPermission(source); return; }
                handleGroup(source, args);
            }
            case "reload" -> {
                if (!checkPerm(source, "velocitychat.admin.reload", "velocitychat.admin")) { noPermission(source); return; }
                handleReload(source);
            }
            default -> sendHelp(source);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        // 所有玩家都能看到 /velocitychat 命令，权限在子命令级别检查
        return true;
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        // First argument: subcommands
        if (args.length <= 1) {
            return filterSuggestions(List.of("create", "group", "reload"), prefix);
        }

        // Second level suggestions
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "create" -> List.of("group");
                case "group" -> {
                    List<String> suggestions = new ArrayList<>(groupManager.getGroups().keySet());
                    suggestions.add("list");
                    yield filterSuggestions(suggestions, prefix);
                }
                default -> List.of();
            };
        }

        // Third level suggestions
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("group")) {
                return filterSuggestions(List.of("join", "remove", "settitle", "delete", "list"), prefix);
            }
        }

        // Fourth level: player name suggestions for join/remove
        if (args.length == 4 && args[0].equalsIgnoreCase("group")) {
            String action = args[2].toLowerCase();
            if (action.equals("join") || action.equals("remove")) {
                return filterSuggestions(
                        server.getAllPlayers().stream()
                                .map(Player::getUsername)
                                .collect(Collectors.toList()),
                        prefix);
            }
        }

        return List.of();
    }

    // ── Subcommand Handlers ──────────────────────────────────

    private void handleCreate(CommandSource source, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("group")) {
            ColorUtils.sendMessage(source, logger, "§c用法: /velocitychat create group <名称> [称号]");
            return;
        }

        String groupName = args[2];

        // Build title from remaining args
        String title = "";
        if (args.length > 3) {
            title = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            title = ColorUtils.translate(title);
        }

        if (groupManager.createGroup(groupName, title)) {
            ColorUtils.sendMessage(source, logger, "§a群组 '" + groupName + "' 创建成功！");
            if (!title.isEmpty()) {
                ColorUtils.sendMessage(source, logger, "§7称号: " + title);
            }
        } else {
            ColorUtils.sendMessage(source, logger, "§c群组 '" + groupName + "' 已存在！");
        }
    }

    private void handleGroup(CommandSource source, String[] args) {
        if (args.length < 2) {
            ColorUtils.sendMessage(source, logger, "§c用法: /velocitychat group <名称> join/remove/settitle/delete/list [玩家] [称号]");
            return;
        }

        // /vchat group list — 查看所有群组（不需要群组名）
        if (args[1].equalsIgnoreCase("list")) {
            handleList(source);
            return;
        }

        String groupName = args[1];

        if (!groupManager.groupExists(groupName)) {
            ColorUtils.sendMessage(source, logger, "§c群组 '" + groupName + "' 不存在！");
            return;
        }

        if (args.length < 3) {
            // Show group info
            GroupManager.Group group = groupManager.getGroup(groupName);
            if (group != null) {
                String titleDisplay = group.getTitle().isEmpty() ? "§7无" : group.getTitle();
                ColorUtils.sendMessage(source, logger, "§6=== 群组: " + group.getName() + " ===");
                ColorUtils.sendMessage(source, logger, "§7称号: " + titleDisplay);
                ColorUtils.sendMessage(source, logger, "§7成员 (" + group.getMembers().size() + "): " +
                        String.join("§7, ", group.getMembers()));
            }
            return;
        }

        String action = args[2].toLowerCase();

        switch (action) {
            case "join" -> {
                if (!checkPerm(source, "velocitychat.admin.group.join", "velocitychat.admin.group.*", "velocitychat.admin")) { noPermission(source); return; }
                if (args.length < 4) {
                    ColorUtils.sendMessage(source, logger, "§c用法: /velocitychat group " + groupName + " join <玩家ID>");
                    return;
                }
                String playerName = args[3];
                if (groupManager.addMember(groupName, playerName)) {
                    ColorUtils.sendMessage(source, logger, "§a已将 '" + playerName + "' 添加到群组 '" + groupName + "'");
                } else {
                    ColorUtils.sendMessage(source, logger, "§c群组 '" + groupName + "' 不存在！");
                }
            }
            case "remove" -> {
                if (!checkPerm(source, "velocitychat.admin.group.remove", "velocitychat.admin.group.*", "velocitychat.admin")) { noPermission(source); return; }
                if (args.length < 4) {
                    ColorUtils.sendMessage(source, logger, "§c用法: /velocitychat group " + groupName + " remove <玩家ID>");
                    return;
                }
                String playerName = args[3];
                if (groupManager.removeMember(playerName)) {
                    ColorUtils.sendMessage(source, logger, "§a已将 '" + playerName + "' 从群组中移除");
                } else {
                    ColorUtils.sendMessage(source, logger, "§c玩家 '" + playerName + "' 不在任何群组中");
                }
            }
            case "settitle" -> {
                if (!checkPerm(source, "velocitychat.admin.group.settitle", "velocitychat.admin.group.*", "velocitychat.admin")) { noPermission(source); return; }
                if (args.length < 4) {
                    ColorUtils.sendMessage(source, logger, "§c用法: /velocitychat group " + groupName + " settitle <称号>");
                    return;
                }
                String newTitle = ColorUtils.translate(String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                GroupManager.Group group = groupManager.getGroup(groupName);
                if (group != null) {
                    group.setTitle(newTitle);
                    groupManager.save();
                    ColorUtils.sendMessage(source, logger, "§a群组 '" + groupName + "' 的称号已更新为: " + newTitle);
                }
            }
            case "delete" -> {
                if (!checkPerm(source, "velocitychat.admin.group.delete", "velocitychat.admin.group.*", "velocitychat.admin")) { noPermission(source); return; }
                if (groupManager.deleteGroup(groupName)) {
                    ColorUtils.sendMessage(source, logger, "§a群组 '" + groupName + "' 已删除");
                } else {
                    ColorUtils.sendMessage(source, logger, "§c群组 '" + groupName + "' 不存在！");
                }
            }
            case "list" -> {
                if (!checkPerm(source, "velocitychat.admin.group.list", "velocitychat.admin.group.*", "velocitychat.admin")) { noPermission(source); return; }
                handleList(source);
            }
            default -> ColorUtils.sendMessage(source, logger, "§c未知操作。可用: join, remove, settitle, delete, list");
        }
    }

    private void handleList(CommandSource source) {
        Map<String, GroupManager.Group> allGroups = groupManager.getGroups();

        if (allGroups.isEmpty()) {
            ColorUtils.sendMessage(source, logger, "§e当前没有已创建的群组。");
            ColorUtils.sendMessage(source, logger, "§7使用 /velocitychat create group <名称> [称号] 创建");
            return;
        }

        ColorUtils.sendMessage(source, logger, "§6=== 群组列表 (" + allGroups.size() + " 个) ===");

        for (GroupManager.Group group : allGroups.values()) {
            String titleDisplay = group.getTitle().isEmpty()
                    ? "§7(无称号)"
                    : group.getTitle();

            String membersDisplay = group.getMembers().isEmpty()
                    ? "§7(无成员)"
                    : "§7" + String.join("§7, ", group.getMembers());

            ColorUtils.sendMessage(source, logger, "§e" + group.getName() + " §7- 称号: " + titleDisplay);
            ColorUtils.sendMessage(source, logger, "  §8成员: " + membersDisplay);
        }
    }

    private void handleReload(CommandSource source) {
        config.reload();
        groupManager.load();
        forbiddenWords.load(config);
        autoBroadcast.load();
        autoBroadcast.start();
        ColorUtils.sendMessage(source, logger, "§a配置文件、语言文件、群组数据、违禁词列表及定时消息已重载！");
        logger.info("Configuration, groups, forbidden words and auto broadcast reloaded by " + source);
    }

    // ── Permission Helper ─────────────────────────────────────

    /**
     * Check if the source has any of the given permissions.
     * Console always returns true.
     * Players are checked against each permission in order; if any matches, returns true.
     *
     * @param source      The command source
     * @param permissions One or more permission nodes to check (checked in order)
     * @return true if the source has any of the specified permissions
     */
    private boolean checkPerm(CommandSource source, String... permissions) {
        if (!(source instanceof Player)) return true; // console always has permission
        for (String perm : permissions) {
            if (source.hasPermission(perm)) return true;
        }
        return false;
    }

    // ── Help ─────────────────────────────────────────────────

    private void sendHelp(CommandSource source) {
        ColorUtils.sendMessage(source, logger, "§6=== VelocityChat 管理指令 ===");
        ColorUtils.sendMessage(source, logger, "§7/vchat create group <名称> [称号] §f- 创建群组");
        ColorUtils.sendMessage(source, logger, "§7/vchat group <名称> join <玩家ID> §f- 添加成员");
        ColorUtils.sendMessage(source, logger, "§7/vchat group <名称> remove <玩家ID> §f- 移除成员");
        ColorUtils.sendMessage(source, logger, "§7/vchat group <名称> settitle <称号> §f- 修改称号");
        ColorUtils.sendMessage(source, logger, "§7/vchat group <名称> delete §f- 删除群组");
        ColorUtils.sendMessage(source, logger, "§7/vchat group list §f- 查看所有群组");
        ColorUtils.sendMessage(source, logger, "§7/vchat reload §f- 重载配置文件");
        ColorUtils.sendMessage(source, logger, "§7/vchat §f- 显示此帮助");
    }

    private void noPermission(CommandSource source) {
        ColorUtils.sendMessage(source, logger, "§c你没有权限执行此命令！(需 velocitychat.admin)");
    }

    private List<String> filterSuggestions(List<String> suggestions, String prefix) {
        if (prefix.isEmpty()) return suggestions;
        return suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
    }
}
