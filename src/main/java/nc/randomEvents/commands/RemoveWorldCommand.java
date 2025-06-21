package nc.randomEvents.commands;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.services.DataManager;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.stream.Collectors;

public class RemoveWorldCommand implements SubCommand {
    private final DataManager dataManager;

    public RemoveWorldCommand(RandomEvents plugin) {
        this.dataManager = plugin.getDataManager();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("Usage: /randomevents removeworld <world-name>");
            return false;
        }

        String worldName = args[1];
        // No need to check if world exists with Bukkit.getWorld(worldName) as we are just removing a string

        if (dataManager.removeAcceptedWorld(worldName)) {
            sender.sendMessage("World '" + worldName + "' removed from accepted worlds.");
        } else {
            sender.sendMessage("World '" + worldName + "' was not in the accepted worlds list.");
        }
        return true;
    }

    @Override
    public String getDescription() {
        return "Removes a world from the list of worlds where random events can occur.";
    }

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return dataManager.getAcceptedWorldNames().stream()
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return null; // Uses default Bukkit world name completion if more args or handled by CommandManager
    }
} 