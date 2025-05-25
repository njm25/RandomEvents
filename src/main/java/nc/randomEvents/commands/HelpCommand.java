package nc.randomEvents.commands;


import org.bukkit.command.CommandSender;

import nc.randomEvents.RandomEvents;

import java.util.Map;

public class HelpCommand implements SubCommand {

    private final RandomEvents plugin;
    private final Map<String, SubCommand> subCommands;

    public HelpCommand(RandomEvents plugin, Map<String, SubCommand> subCommands) {
        this.plugin = plugin;
        this.subCommands = subCommands;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage("§6--- RandomEvents Help ---");
        // Iterate over unique command instances, not aliases
        subCommands.entrySet().stream()
            .filter(entry -> !entry.getKey().equals("re")) // Exclude alias
            .forEach(entry -> {
                String commandName = entry.getKey();
                SubCommand subCommand = entry.getValue();
                String description = subCommand.getDescription();
                if (description == null || description.trim().isEmpty()) {
                    description = "No description available.";
                }
                sender.sendMessage("§a/" + plugin.getCommand("randomevents").getLabel() + " " + commandName + "§7 - " + description);
            });
        return true;
    }

    @Override
    public String getDescription() {
        return "Shows this help message.";
    }
} 