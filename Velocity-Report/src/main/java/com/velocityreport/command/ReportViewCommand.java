package com.velocityreport.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocityreport.ReportManager;

import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * /reportview &lt;id&gt;
 * <p>
 * Shows full details of a specific report. Permission: {@code velocityreport.staff}.
 */
public class ReportViewCommand implements SimpleCommand {

    private final ReportManager manager;

    public ReportViewCommand(ReportManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length < 1) {
            source.sendMessage(MiniMessage.miniMessage()
                    .deserialize(manager.getMessage("command-reportview-usage")));
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

        source.sendMessage(manager.viewReport(id));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return manager.isStaff(invocation.source());
    }
}
