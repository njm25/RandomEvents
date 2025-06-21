package nc.randomEvents.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.data.PlayerData;

public class PlayerListener implements Listener {
    private final RandomEvents plugin;

    public PlayerListener(RandomEvents plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = plugin.getDataManager().get(PlayerData.class, player.getUniqueId().toString());
        if (playerData == null) {
            playerData = new PlayerData(player.getUniqueId(), 0);
            plugin.getDataManager().set(playerData.getId(), playerData);
        }
    }
    
}
