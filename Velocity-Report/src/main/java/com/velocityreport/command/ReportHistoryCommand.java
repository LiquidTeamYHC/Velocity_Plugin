package com.velocityreport.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocityreport.ReportManager;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /reportcd &lt;subcommand&gt; [args...]
 * <p>
 * Admin report history management with clickable GUI.
 * Subcommands: list, view, resolve, reopen
 * Permission: {@code velocityreport.staff}
 */
public class ReportHistoryCommand implements SimpleCommand {

    private final ReportManager manager;
    private final ProxyServer server;

    public ReportHistoryCommand(ReportManager manager, ProxyServer server) {
        this.manager = manager;
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!(source instanceof Player)) {
            source.sendMessage(MiniMessage.miniMessage()
                    .deserialize(manager.getMessage("command-player-only")));
            return;
        }

        Player player = (Player) source;

        // Default: show first page of all reports
        if (args.length == 0) {
            source.sendMessage(manager.buildReportHistoryList(1, null));
            return;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "list" -> {
                int page = 1;
                String playerName = null;
                if (args.length >= 2) {
                    try {
                        page = Integer.parseInt(args[1]);
                        if (args.length >= 3) {
                            playerName = args[2];
                        }
                    } catch (NumberFormatException e) {
                        // Second arg wasn't a page number, treat as player name
                        playerName = args[1];
                    }
                }
                source.sendMessage(manager.buildReportHistoryList(page, playerName));
            }
            case "view" -> {
                if (args.length < 2) {
                    source.sendMessage(MiniMessage.miniMessage()
                            .deserialize(manager.getMessage("command-reportcd-view-usage")));
                    return;
                }
                try {
                    int id = Integer.parseInt(args[1]);
                    source.sendMessage(manager.buildReportDetailMenu(id));
                } catch (NumberFormatException e) {
                    source.sendMessage(MiniMessage.miniMessage()
                            .deserialize(manager.getMessage("command-invalid-id")
                                    .replace("{input}", args[1])));
                }
            }
            case "resolve" -> {
                if (args.length < 2) {
                    source.sendMessage(MiniMessage.miniMessage()
                            .deserialize(manager.getMessage("command-reportcd-resolve-usage")));
                    return;
                }
                try {
                    int id = Integer.parseInt(args[1]);
                    source.sendMessage(manager.toggleReportStatus(id, player, true));
                } catch (NumberFormatException e) {
                    source.sendMessage(MiniMessage.miniMessage()
                            .deserialize(manager.getMessage("command-invalid-id")
                                    .replace("{input}", args[1])));
                }
            }
            case "reopen" -> {
                if (args.length < 2) {
                    source.sendMessage(MiniMessage.miniMessage()
                            .deserialize(manager.getMessage("command-reportcd-reopen-usage")));
                    return;
                }
                try {
                    int id = Integer.parseInt(args[1]);
                    source.sendMessage(manager.toggleReportStatus(id, player, false));
                } catch (NumberFormatException e) {
                    source.sendMessage(MiniMessage.miniMessage()
                            .deserialize(manager.getMessage("command-invalid-id")
                                    .replace("{input}", args[1])));
                }
            }
            default -> {
                // If it looks like a player name, search by name
                source.sendMessage(manager.buildReportHistoryList(1, args[0]));
            }
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return manager.isStaff(invocation.source());
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            List<String> subs = List.of("list", "view", "resolve", "reopen");
            return CompletableFuture.completedFuture(
                subs.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList()));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("view")
                || args[0].equalsIgnoreCase("resolve")
                || args[0].equalsIgnoreCase("reopen"))) {
            // Could suggest report IDs here, but that's expensive
            return CompletableFuture.completedFuture(List.of("<id>"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            String prefix = args[1].toLowerCase();
            List<String> players = server.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(players);
        }
        return CompletableFuture.completedFuture(List.of());
    }
}
