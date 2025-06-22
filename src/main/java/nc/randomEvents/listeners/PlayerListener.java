package nc.randomEvents.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.data.PlayerData;
import nc.randomEvents.services.DataManager;

public class PlayerListener implements Listener {
    private final DataManager dataManager;

    public PlayerListener(RandomEvents plugin) {
        this.dataManager = plugin.getDataManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = dataManager.get(PlayerData.class, player.getUniqueId().toString());
        if (playerData == null) {
            playerData = new PlayerData(player.getUniqueId(), 0);
            dataManager.set(playerData.getId(), playerData);
        }
    }
    
}
