package nc.randomEvents.commands;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class ListCommand implements SubCommand {

    private final RandomEvents plugin;
    private static final int ITEMS_PER_PAGE = 6;

    public ListCommand(RandomEvents plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getDescription() {
        return "List all available events. Usage: /re list [page]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        List<String> eventNames = plugin.getEventManager().getEventNames();
        int totalPages = (int) Math.ceil((double) eventNames.size() / ITEMS_PER_PAGE);
        
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

        sender.sendMessage("§6--- Available Events (Page " + page + "/" + totalPages + ") ---");
        
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, eventNames.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            String eventName = eventNames.get(i);
            BaseEvent event = plugin.getEventManager().getEvent(eventName);
            String description = event != null ? event.getDescription() : "No description available.";
            sender.sendMessage("§a" + eventName + "§7 - " + description);
        }

        if (page < totalPages) {
            sender.sendMessage("§7Type '/re list " + (page + 1) + "' to see the next page");
        }
        
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> completions = new ArrayList<>();
            int totalPages = (int) Math.ceil((double) plugin.getEventManager().getEventNames().size() / ITEMS_PER_PAGE);
            for (int i = 1; i <= totalPages; i++) {
                completions.add(String.valueOf(i));
            }
            return completions;
        }
        return new ArrayList<>();
    }
}