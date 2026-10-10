package com.velocityreport.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocityreport.ReportManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /report &lt;player&gt; [reason]
 * <p>
 * Without reason: shows a clickable reason-selection menu.
 * With reason: files the report directly.
 * The target may be online, or offline within the configured window.
 * Permission: {@code velocityreport.report} (default true).
 */
public class ReportCommand implements SimpleCommand {

    /** Upper bound on tab-complete suggestions, so a busy network does not flood the client. */
    private static final int SUGGEST_LIMIT = 50;

    private final ReportManager manager;

    public ReportCommand(ReportManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        // ── Must be a player ──
        if (!(source instanceof Player reporter)) {
            source.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(manager.getMessage("command-player-only")));
            return;
        }

        // ── No player specified ──
        if (args.length < 1) {
            source.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(manager.getMessage("command-report-usage")));
            return;
        }

        String reportedName = args[0];

        // ── Target self check (pre-validate before building menus) ──
        if (reporter.getUsername().equalsIgnoreCase(reportedName)) {
            source.sendMessage(manager.fileReport(reporter, reportedName, ""));
            return;
        }

        // ── No reason → show clickable reason menu ──
        if (args.length < 2) {
            // Reject before showing the menu, so an unreportable name does not
            // lead the player into a menu whose options all fail.
            var targetError = manager.checkReportable(reportedName);
            if (targetError != null) {
                source.sendMessage(targetError);
                return;
            }
            source.sendMessage(manager.buildReasonMenu(reportedName));
            return;
        }

        // ── Resolve reason (reason ID → display name) ──
        String rawReason = args[1];
        String reason = resolveReason(rawReason);

        // "other" reason → guide to custom input
        if (reason == null) {
            source.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(manager.getMessage("command-report-custom-reason")
                            .replace("{player}", reportedName)));
            return;
        }

        // If the user typed extra words after the reason ID, join them as custom
        if (args.length > 2 && reason.equals(rawReason)) {
            reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        }

        // ── Reason length limit ──
        if (reason.length() > 256) {
            source.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(manager.getMessage("command-reason-too-long")));
            return;
        }

        // ── Delegate to manager ──
        var result = manager.fileReport(reporter, reportedName, reason);
        source.sendMessage(result);
    }

    /**
     * If the second argument matches a known reason ID, return its display name.
     * Otherwise return the raw input as-is (custom reason).
     */
    private String resolveReason(String input) {
        for (var reason : manager.getReportReasons()) {
            if (reason.getId().equalsIgnoreCase(input)) {
                // "other" is special — tell the user to type a custom reason
                if ("other".equals(reason.getId())) {
                    return null; // handled below
                }
                return translateLegacyColorsString(manager.getReasonDisplayName(reason.getId()));
            }
        }
        return input; // custom reason
    }

    /** Basic & color → MiniMessage conversion for single strings. */
    private static String translateLegacyColorsString(String input) {
        return input
            .replace("&0", "<black>").replace("&1", "<dark_blue>")
            .replace("&2", "<dark_green>").replace("&3", "<dark_aqua>")
            .replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
            .replace("&6", "<gold>").replace("&7", "<gray>")
            .replace("&8", "<dark_gray>").replace("&9", "<blue>")
            .replace("&a", "<green>").replace("&b", "<aqua>")
            .replace("&c", "<red>").replace("&d", "<light_purple>")
            .replace("&e", "<yellow>").replace("&f", "<white>")
            .replace("&k", "<obfuscated>").replace("&l", "<bold>")
            .replace("&m", "<strikethrough>").replace("&n", "<underlined>")
            .replace("&o", "<italic>").replace("&r", "<reset>");
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            // Online players plus offline players still inside the report window
            String prefix = args.length == 1 ? args[0] : "";
            return CompletableFuture.completedFuture(manager.getReportableNames(prefix, SUGGEST_LIMIT));
        }
        // Suggest reason IDs when typing the second argument
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            List<String> matches = manager.getReportReasons().stream()
                    .map(r -> r.getId())
                    .filter(id -> id.startsWith(prefix))
                    .sorted()
                    .toList();
            return CompletableFuture.completedFuture(matches);
        }
        return CompletableFuture.completedFuture(List.of());
    }
}
