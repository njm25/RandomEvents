package nc.randomEvents.commands;

import org.bukkit.command.CommandSender;

import nc.randomEvents.RandomEvents;

public class ReloadCommand implements SubCommand {

    private final RandomEvents plugin;

    public ReloadCommand(RandomEvents plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        this.plugin.getConfigManager().reload();
        this.plugin.reloadConfig();
        this.plugin.getRewardGenerator().reloadRewards();
        this.plugin.getDataManager().reloadData();
        sender.sendMessage("RandomEvents reloaded successfully.");
        return true;
    }

    @Override
    public String getDescription() {
        return "Reloads the plugin";
    }
    
}
