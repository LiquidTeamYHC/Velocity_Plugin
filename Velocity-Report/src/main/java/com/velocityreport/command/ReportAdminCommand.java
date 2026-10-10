package com.velocityreport.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocityreport.ReportManager;
import com.velocityreport.VelocityReport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /reportadmin &lt;subcommand&gt; [args...]
 * <p>
 * Single entry point for every staff action: reload, list, view, close,
 * resolve / reopen, history and the staff list.
 * <p>
 * The individual commands (/reports, /reportview, /reportclose, /reportcd,
 * /reportstaff) keep working exactly as before — this one just collects them
 * under one roof, so nothing has to be relearned. Reloading has no command of
 * its own: it is only reachable as {@code /reportadmin reload}.
 * <p>
 * Permission: {@code velocityreport.staff} (or a name on the staff list) for
 * everything except {@code reload}, which needs {@code velocityreport.reload}.
 */
public class ReportAdminCommand implements SimpleCommand {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "reload", "list", "view", "close", "resolve", "reopen", "history", "staff");

    /** Subcommands that act on a report ID and need the sender's identity. */
    private static final List<String> ID_SUBCOMMANDS = List.of("view", "close", "resolve", "reopen");

    private final VelocityReport plugin;
    private final ReportManager manager;
    private final StaffCommand staffCommand;

    public ReportAdminCommand(VelocityReport plugin, ReportManager manager, StaffCommand staffCommand) {
        this.plugin = plugin;
        this.manager = manager;
        this.staffCommand = staffCommand;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            source.sendMessage(message("command-reportadmin-help"));
            return;
        }

        String sub = args[0].toLowerCase();

        // ── Subcommands that need a player as the handler ──
        if (sub.equals("close") || sub.equals("resolve") || sub.equals("reopen")) {
            if (!(source instanceof Player handler)) {
                source.sendMessage(message("command-player-only"));
                return;
            }
            Integer id = parseId(source, args);
            if (id == null) return;

            switch (sub) {
                case "close" -> {
                    String resolution = args.length > 2
                            ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                            : manager.getMessage("command-reportclose-default-resolution");
                    source.sendMessage(manager.closeReport(id, handler, resolution));
                }
                case "resolve" -> source.sendMessage(manager.toggleReportStatus(id, handler, true));
                default -> source.sendMessage(manager.toggleReportStatus(id, handler, false));
            }
            return;
        }

        switch (sub) {
            case "help", "?" -> source.sendMessage(message("command-reportadmin-help"));

            case "reload" -> {
                try {
                    plugin.reloadConfig();
                    source.sendMessage(message("command-reload-success"));
                } catch (Exception e) {
                    source.sendMessage(message("command-reload-fail"));
                }
            }

            case "list", "reports" -> {
                Integer page = parsePage(source, args);
                if (page != null) source.sendMessage(manager.listReports(page));
            }

            case "view" -> {
                Integer id = parseId(source, args);
                if (id != null) source.sendMessage(manager.viewReport(id));
            }

            case "history", "cd" -> source.sendMessage(history(args));

            case "staff" -> staffCommand.dispatch(source, Arrays.copyOfRange(args, 1, args.length));

            default -> source.sendMessage(message("command-reportadmin-unknown", "{input}", args[0]));
        }
    }

    /** {@code /reportadmin history [page] [player]} — a non-numeric second argument is a player name. */
    private Component history(String[] args) {
        int page = 1;
        String playerName = null;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
                if (args.length >= 3) {
                    playerName = args[2];
                }
            } catch (NumberFormatException e) {
                playerName = args[1];
            }
        }
        return manager.buildReportHistoryList(page, playerName);
    }

    /** Parses the report ID; a missing or non-numeric value is reported back to the source. */
    private Integer parseId(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(message("command-reportadmin-usage-id"));
            return null;
        }
        try {
            return Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            source.sendMessage(message("command-invalid-id", "{input}", args[1]));
            return null;
        }
    }

    /** Parses the optional page number; a non-numeric value is reported back to the source. */
    private Integer parsePage(CommandSource source, String[] args) {
        if (args.length < 2) return 1;
        try {
            return Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            source.sendMessage(message("command-invalid-page", "{input}", args[1]));
            return null;
        }
    }

    /** Builds a localized message, applying {@code {placeholder}, value} pairs. */
    private Component message(String key, String... replacements) {
        String resolved = manager.getMessage(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            resolved = resolved.replace(replacements[i], replacements[i + 1]);
        }
        return MiniMessage.miniMessage().deserialize(resolved);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        String[] args = invocation.arguments();

        // Reloading has a node of its own: neither velocityreport.staff nor the
        // staff list grants it, so it can be handed out (or withheld) on its own.
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            return invocation.source().hasPermission("velocityreport.reload");
        }
        return manager.isStaff(invocation.source());
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase() : "";
            return CompletableFuture.completedFuture(
                    SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList());
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("staff")) {
            return CompletableFuture.completedFuture(
                    staffCommand.suggestArguments(Arrays.copyOfRange(args, 1, args.length)));
        }
        if (args.length == 2 && ID_SUBCOMMANDS.contains(sub)) {
            return CompletableFuture.completedFuture(List.of("<id>"));
        }
        return CompletableFuture.completedFuture(List.of());
    }
}
