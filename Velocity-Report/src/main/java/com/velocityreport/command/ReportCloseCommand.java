package com.velocityreport.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocityreport.ReportManager;

import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * /reportclose &lt;id&gt; [resolution]
 * <p>
 * Closes a report with an optional resolution note.
 * Permission: {@code velocityreport.staff}.
 */
public class ReportCloseCommand implements SimpleCommand {

    private final ReportManager manager;

    public ReportCloseCommand(ReportManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        // ── Must be a player (we need the handler identity) ──
        if (!(source instanceof Player handler)) {
            source.sendMessage(MiniMessage.miniMessage()
                    .deserialize(manager.getMessage("command-player-only")));
            return;
        }

        if (args.length < 1) {
            source.sendMessage(MiniMessage.miniMessage()
                    .deserialize(manager.getMessage("command-reportclose-usage")));
            return;
        }

        int id;
        try {
            id = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            source.sendMessage(MiniMessage.miniMessage()
                    .deserialize(manager.getMessage("command-invalid-id")
                            .replace("{input}", args[0])));
            return;
        }

        String resolution = args.length > 1
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : manager.getMessage("command-reportclose-default-resolution");

        source.sendMessage(manager.closeReport(id, handler, resolution));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return manager.isStaff(invocation.source());
    }
}
