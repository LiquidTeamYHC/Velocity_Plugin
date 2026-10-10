package com.velocityreport.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocityreport.ReportManager;

import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * /reports [page]
 * <p>
 * Lists open reports with pagination. Permission: {@code velocityreport.staff}.
 */
public class ReportsCommand implements SimpleCommand {

    private final ReportManager manager;

    public ReportsCommand(ReportManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                source.sendMessage(MiniMessage.miniMessage()
                        .deserialize(manager.getMessage("command-invalid-page")
                                .replace("{input}", args[0])));
                return;
            }
        }

        source.sendMessage(manager.listReports(page));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return manager.isStaff(invocation.source());
    }
}
