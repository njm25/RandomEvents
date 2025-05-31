package nc.randomEvents.commands;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.services.EventManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new HashMap<>();
    private final List<String> subCommandNames = new ArrayList<>();

    public CommandManager(RandomEvents plugin) {
        // Register subcommands
        registerSubCommand("Help", new HelpCommand(plugin, subCommands));
        registerSubCommand("Start", new StartEventCommand(plugin));
        registerSubCommand("AddWorld", new AddWorldCommand(plugin));
        registerSubCommand("RemoveWorld", new RemoveWorldCommand(plugin));
        registerSubCommand("Reload", new ReloadCommand(plugin));
   

        // Register the main command and its alias with Bukkit
        PluginCommand mainCommand = plugin.getCommand("randomevents");
        if (mainCommand != null) {
            mainCommand.setExecutor(this);
            mainCommand.setTabCompleter(this);
        } else {
            plugin.getLogger().severe("Command 'randomevents' not found in plugin.yml!");
        }
    }

    private void registerSubCommand(String name, SubCommand command) {
        subCommands.put(name.toLowerCase(), command);
        if (!subCommandNames.contains(name.toLowerCase())) { // Avoid adding aliases to the main suggestion list if already present by primary name
            subCommandNames.add(name.toLowerCase());
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            return false;
        }

        SubCommand subCommand = subCommands.get(args[0].toLowerCase());
        if (subCommand == null) {
            return false;
        }

        return subCommand.execute(sender, args);
    }   

    
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.isOp()) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return subCommandNames.stream()
                    .filter(name -> name.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommandArg = args[0].toLowerCase();
            SubCommand subCmd = subCommands.get(subCommandArg);
            if (subCmd instanceof StartEventCommand) {
                return ((StartEventCommand) subCmd).onTabComplete(sender, args);
            } else if (subCmd instanceof AddWorldCommand) {
                return ((AddWorldCommand) subCmd).onTabComplete(sender, args);
            } else if (subCmd instanceof RemoveWorldCommand) {
                return ((RemoveWorldCommand) subCmd).onTabComplete(sender, args);
            }
        }

        return Collections.emptyList();
    }
}
