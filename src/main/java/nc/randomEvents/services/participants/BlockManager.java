package nc.randomEvents.services.participants;

import nc.randomEvents.core.SessionParticipant;
import nc.randomEvents.services.SessionRegistry;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;

public class BlockManager implements SessionParticipant, Listener {
    private final SessionRegistry sessionRegistry;
    private final RandomEvents plugin;
    public BlockManager(RandomEvents plugin) {
        this.plugin = plugin;
        this.sessionRegistry = plugin.getSessionRegistry(); 
        this.sessionRegistry.registerParticipant(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onSessionStart(UUID sessionId) {
        plugin.getLogger().info("BlockManager tracking new session: " + sessionId);
    }

    @Override
    public void onSessionEnd(UUID sessionId) {
        plugin.getLogger().info("BlockManager tracking session end: " + sessionId);
    }

    @Override
    public void cleanupSession(UUID sessionId, boolean force) {
        plugin.getLogger().info("BlockManager cleaning up session: " + sessionId);
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
