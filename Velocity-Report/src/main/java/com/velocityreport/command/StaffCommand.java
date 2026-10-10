package com.velocityreport.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocityreport.ReportManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /reportstaff [list|add|remove] [player]
 * <p>
 * Manages the staff list used on networks without a permission plugin.
 * Names added here are written to {@code staff-users.yml} in the plugin folder
 * and merged with the {@code staff-users} list in config.yml — config.yml is
 * never rewritten, so its comments stay intact.
 * Permission: {@code velocityreport.staff}.
 */
public class StaffCommand implements SimpleCommand {

    /** Staff subcommands; also offered by /reportadmin. */
    public static final List<String> SUBCOMMANDS = List.of("list", "add", "remove");

    private final ReportManager manager;
    private final ProxyServer server;

    public StaffCommand(ReportManager manager, ProxyServer server) {
        this.manager = manager;
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        dispatch(invocation.source(), invocation.arguments());
    }

    /**
     * Handles {@code [list|add|remove] [player]} arguments.
     * Also used by {@code /reportadmin staff}, so both entry points behave identically.
     */
    public void dispatch(CommandSource source, String[] args) {
        // No arguments (or "list") → show the current list
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            source.sendMessage(manager.buildStaffList());
            return;
        }

        if (args.length < 2) {
            source.sendMessage(message("command-staff-usage"));
            return;
        }

        String target = args[1];
        switch (args[0].toLowerCase()) {
            case "add" -> source.sendMessage(manager.addStaffUser(target));
            case "remove", "del", "delete" -> source.sendMessage(manager.removeStaffUser(target));
            default -> source.sendMessage(message("command-staff-usage"));
        }
    }

    private Component message(String key) {
        return MiniMessage.miniMessage().deserialize(manager.getMessage(key));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return manager.isStaff(invocation.source());
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(suggestArguments(invocation.arguments()));
    }

    /** Tab-completion for {@code [sub] [player]}; also used by {@code /reportadmin staff}. */
    public List<String> suggestArguments(String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase() : "";
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }

        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            String sub = args[0].toLowerCase();

            if (sub.startsWith("remove") || sub.startsWith("del")) {
                return manager.getStaffUsers().stream()
                        .filter(name -> name.toLowerCase().startsWith(prefix))
                        .toList();
            }
            if (sub.startsWith("add")) {
                return server.getAllPlayers().stream()
                        .map(Player::getUsername)
                        .filter(name -> name.toLowerCase().startsWith(prefix))
                        .sorted()
                        .toList();
            }
        }
        return List.of();
    }
}
