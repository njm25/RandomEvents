package nc.randomEvents.listeners;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.core.ServiceListener;
import nc.randomEvents.services.SessionRegistry;

public class BlockListener implements ServiceListener {
    private final SessionRegistry sessionRegistry;

    public BlockListener(RandomEvents plugin) {
        this.sessionRegistry = plugin.getSessionRegistry();
    }

    @Override
    public void registerListener(RandomEvents plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID sessionId = sessionRegistry.getSessionIdForPlayer(player);
        if (sessionId != null) {
            BaseEvent baseEvent = sessionRegistry.getSession(sessionId).getEvent();
            if (!baseEvent.canBreakBlocks()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        UUID sessionId = sessionRegistry.getSessionIdForPlayer(player);
        if (sessionId != null) {
            BaseEvent baseEvent = sessionRegistry.getSession(sessionId).getEvent();
            if (!baseEvent.canPlaceBlocks()) {
                event.setCancelled(true);
            }
        }
      
    }
}
