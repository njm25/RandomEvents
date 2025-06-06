package nc.randomEvents.commands;

import nc.randomEvents.RandomEvents;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class StartEventCommand implements SubCommand {
    private final RandomEvents plugin;

    public StartEventCommand(RandomEvents plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("Usage: /randomevents start <event-name>");
            return false;
        }

        String eventName = args[1];
        if (plugin.getEventManager().startEvent(eventName)) {
            sender.sendMessage("Event '" + eventName + "' started successfully.");
        } else {
            sender.sendMessage("Failed to start event '" + eventName + "'. Check console for details.");
        }
        return true;
    }

    @Override
    public String getDescription() {
        return "Starts a specified random event.";
    }

    // Basic tab completion for event names
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return plugin.getEventManager().getEventNames().stream()
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
} 