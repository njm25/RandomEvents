package nc.randomEvents.commands;


import org.bukkit.command.CommandSender;

import nc.randomEvents.RandomEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HelpCommand implements SubCommand {

    private final RandomEvents plugin;
    private final Map<String, SubCommand> subCommands;
    private static final int ITEMS_PER_PAGE = 6;

    public HelpCommand(RandomEvents plugin, Map<String, SubCommand> subCommands) {
        this.plugin = plugin;
        this.subCommands = subCommands;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        List<Map.Entry<String, SubCommand>> uniqueCommands = subCommands.entrySet().stream()
            .filter(entry -> !entry.getKey().equals("re")) // Exclude alias
            .collect(Collectors.toList());

        int totalPages = (int) Math.ceil((double) uniqueCommands.size() / ITEMS_PER_PAGE);
        
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid page number!");
                return false;
            }
        }

        if (page < 1 || page > totalPages) {
            sender.sendMessage("§cPage must be between 1 and " + totalPages + "!");
            return false;
        }

        sender.sendMessage("§6--- RandomEvents Help (Page " + page + "/" + totalPages + ") ---");
        
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, uniqueCommands.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<String, SubCommand> entry = uniqueCommands.get(i);
            String commandName = entry.getKey();
            SubCommand subCommand = entry.getValue();
            String description = subCommand.getDescription();
            if (description == null || description.trim().isEmpty()) {
                description = "No description available.";
            }
            sender.sendMessage("§a/" + plugin.getCommand("randomevents").getLabel() + " " + commandName + "§7 - " + description);
        }

        if (page < totalPages) {
            sender.sendMessage("§7Type '/re help " + (page + 1) + "' to see the next page");
        }
        
        return true;
    }

    @Override
    public String getDescription() {
        return "Shows this help message. Usage: /re help [page]";
    }

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> completions = new ArrayList<>();
            int totalPages = (int) Math.ceil((double) subCommands.size() / ITEMS_PER_PAGE);
            for (int i = 1; i <= totalPages; i++) {
                completions.add(String.valueOf(i));
            }
            return completions;
        }
        return new ArrayList<>();
    }
} 