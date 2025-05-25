package nc.randomEvents.commands;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.services.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.stream.Collectors;

public class AddWorldCommand implements SubCommand {
    private final DataManager dataManager;

    public AddWorldCommand(RandomEvents plugin) {
        this.dataManager = plugin.getDataManager();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("Usage: /randomevents addworld <world-name>");
            return false;
        }

        String worldName = args[1];
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            sender.sendMessage("Error: World '" + worldName + "' not found.");
            return true;
        }

        if (dataManager.addAcceptedWorld(world.getName())) {
            sender.sendMessage("World '" + world.getName() + "' added to accepted worlds.");
        } else {
            sender.sendMessage("World '" + world.getName() + "' is already in the accepted worlds list.");
        }
        return true;
    }

    @Override
    public String getDescription() {
        return "Adds a world to the list of worlds where random events can occur.";
    }

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> currentWorlds = dataManager.getAcceptedWorlds();
            return Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .filter(name -> !currentWorlds.contains(name.toLowerCase())) // Suggest only worlds not already added
                    .sorted()
                    .collect(Collectors.toList());
        }
        return null; // Uses default Bukkit world name completion if more args or handled by CommandManager
    }
} 