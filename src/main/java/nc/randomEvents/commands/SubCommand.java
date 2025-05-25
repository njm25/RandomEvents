package nc.randomEvents.commands;

import org.bukkit.command.CommandSender;

public interface SubCommand {
    boolean execute(CommandSender sender, String[] args);
    String getDescription();
    // We can add a List<String> onTabComplete(CommandSender sender, String[] args) later if needed for specific subcommand args
}