package nc.randomEvents.listeners;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.LootContainer;
import nc.randomEvents.core.LootContainer.ContainerType;
import nc.randomEvents.core.ServiceListener;
import nc.randomEvents.utils.PersistentDataHelper;
import nc.randomEvents.utils.SoundHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class ContainerListener implements ServiceListener {
	private final RandomEvents plugin;
	private static final String CONTAINER_KEY = "container";
	private static final String CONTAINER_ID_KEY = "container_id";
	private static final String CONTAINER_SESSION_KEY = "container_session";
	private static final String CONTAINER_TYPE_KEY = "container_type";
	private static final String CLEAR_AT_END_KEY = "clear_at_end";

	public ContainerListener(RandomEvents plugin) {
		this.plugin = plugin;
	}

	@Override
	public void registerListener(RandomEvents plugin) {
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	@EventHandler
	public void onBlockBreak(BlockBreakEvent event) {
		Block block = event.getBlock();
		if (!isEventContainer(block)) return;
		
		ContainerType type = getContainerType(block);
		if (type == ContainerType.REGULAR) {
			event.setCancelled(true);
			event.getPlayer().sendMessage(Component.text("This container can only be removed by emptying it.", NamedTextColor.RED));
		} else if (type == ContainerType.INSTANT_REWARD) {
			// Allow breaking instant reward containers and give items to player
			event.setCancelled(true); // Cancel the break event to handle it ourselves
			handleInstantRewardContainer(event.getPlayer(), block);
		}
	}

	@EventHandler
	public void onChunkLoad(ChunkLoadEvent event) {
		for (BlockState blockState : event.getChunk().getTileEntities()) {
			Block block = blockState.getBlock();

			if (!isEventContainer(block)) continue;

			// Defensive: Remove if not a container
			if (!(block.getState() instanceof Container container)) {
				block.setType(Material.AIR);
				plugin.getLogger().info("Removed glitched event container (not a container block) at " + block.getLocation());
				continue;
			}

			UUID sessionId = getEventSessionId(block);
			ContainerType type = getContainerType(block);
			String containerId = PersistentDataHelper.get(container.getPersistentDataContainer(), plugin, CONTAINER_ID_KEY, PersistentDataType.STRING);
			Byte clearAtEndByte = PersistentDataHelper.get(container.getPersistentDataContainer(), plugin, CLEAR_AT_END_KEY, PersistentDataType.BYTE);
			boolean clearAtEnd = clearAtEndByte != null && clearAtEndByte == 1;

			// Remove if missing critical tags
			if (sessionId == null || type == null || containerId == null) {
				block.setType(Material.AIR);
				plugin.getLogger().info("Removed glitched event container (missing tags) at " + block.getLocation());
				continue;
			}

			// Remove if clearAtEnd=true and session is no longer active
			if (clearAtEnd && !plugin.getSessionRegistry().isActive(sessionId)) {
				block.setType(Material.AIR);
				plugin.getLogger().info("Cleaned up stale event container at " + block.getLocation());
				continue;
			}

			if (plugin.getContainerManager().isRegistered(block.getLocation(), sessionId)) continue;

			// Validate/fix tags if needed
			boolean needsUpdate = false;
			Byte tag = PersistentDataHelper.get(container.getPersistentDataContainer(), plugin, CONTAINER_KEY, PersistentDataType.BYTE);
			if (tag == null || tag != 1) needsUpdate = true;
			String id = PersistentDataHelper.get(container.getPersistentDataContainer(), plugin, CONTAINER_ID_KEY, PersistentDataType.STRING);
			if (id == null || !id.equals(containerId)) needsUpdate = true;
			String sess = PersistentDataHelper.get(container.getPersistentDataContainer(), plugin, CONTAINER_SESSION_KEY, PersistentDataType.STRING);
			if (sess == null || !sess.equals(sessionId.toString())) needsUpdate = true;
			String t = PersistentDataHelper.get(container.getPersistentDataContainer(), plugin, CONTAINER_TYPE_KEY, PersistentDataType.STRING);
			if (t == null || !t.equals(type.name())) needsUpdate = true;
			Byte clear = PersistentDataHelper.get(container.getPersistentDataContainer(), plugin, CLEAR_AT_END_KEY, PersistentDataType.BYTE);
			if (clear == null || (clearAtEnd ? clear != 1 : clear != 0)) needsUpdate = true;

			if (needsUpdate) {
				PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, CONTAINER_KEY, PersistentDataType.BYTE, (byte) 1);
				PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, CONTAINER_ID_KEY, PersistentDataType.STRING, containerId);
				PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, CONTAINER_SESSION_KEY, PersistentDataType.STRING, sessionId.toString());
				PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, CONTAINER_TYPE_KEY, PersistentDataType.STRING, type.name());
				PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, CLEAR_AT_END_KEY, PersistentDataType.BYTE, (byte) (clearAtEnd ? 1 : 0));
				container.update();
				plugin.getLogger().info("Fixed missing/corrupt tags for event container at " + block.getLocation());
			}
			plugin.getContainerManager().registerContainer(block.getLocation(), sessionId);
			plugin.getLogger().info("Re-registered event container at " + block.getLocation());
		}
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		Block block = event.getClickedBlock();
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || !isEventContainer(block)) return;

		ContainerType type = getContainerType(block);
		if (type == ContainerType.INSTANT_REWARD) {
			event.setCancelled(true);
			handleInstantRewardContainer(event.getPlayer(), block);
		}
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;

		Inventory clicked = event.getClickedInventory();
		Inventory top = event.getView().getTopInventory();

		if (!(top.getHolder() instanceof Container container)) return;
		Block block = container.getBlock();
		if (!isEventContainer(block)) return;

		// Only prevent shift-clicking items INTO the container (from player inventory to container)
		if (event.isShiftClick() && clicked == player.getInventory()) {
			event.setCancelled(true);
			return;
		}

		// Allow shift-clicking items OUT of the container (from container to player inventory)
		if (event.isShiftClick() && clicked == top) {
			// This is shift-clicking from container to player inventory - allow it
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (top.isEmpty()) handleContainerEmpty(block);
			});
			return;
		}

		if (clicked == top && event.getAction().name().contains("PICKUP")) {
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (top.isEmpty()) handleContainerEmpty(block);
			});
			return;
		}

		if (clicked == top) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onInventoryDrag(InventoryDragEvent event) {
		Inventory top = event.getView().getTopInventory();
		if (!(top.getHolder() instanceof Container container)) return;
		Block block = container.getBlock();
		if (!isEventContainer(block)) return;

		for (int slot : event.getRawSlots()) {
			if (slot < top.getSize()) {
				event.setCancelled(true);
				return;
			}
		}
	}

	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {
		Inventory inv = event.getInventory();
		if (!(inv.getHolder() instanceof Container container)) return;
		Block block = container.getBlock();
		if (!isEventContainer(block)) return;

		if (inv.isEmpty()) {
			ContainerType type = getContainerType(block);
			if (type == ContainerType.REGULAR || type == ContainerType.INSTANT_REWARD) {
				handleContainerEmpty(block);
			}
		}
	}

	@EventHandler public void onBlockFromTo(BlockFromToEvent e) { if (isEventContainer(e.getToBlock())) e.setCancelled(true); }
	@EventHandler public void onBlockBurn(BlockBurnEvent e) { if (isEventContainer(e.getBlock())) e.setCancelled(true); }
	@EventHandler public void onBlockIgnite(BlockIgniteEvent e) { if (isEventContainer(e.getBlock())) e.setCancelled(true); }

	@EventHandler
	public void onBlockPistonExtend(BlockPistonExtendEvent event) {
		for (Block block : event.getBlocks()) {
			if (isEventContainer(block)) handleContainerEmpty(block);
		}
	}

	@EventHandler
	public void onBlockPistonRetract(BlockPistonRetractEvent event) {
		for (Block block : event.getBlocks()) {
			if (isEventContainer(block)) handleContainerEmpty(block);
		}
	}

	@EventHandler
	public void onEntityChangeBlock(EntityChangeBlockEvent event) {
		if (event.getEntity() instanceof Enderman && isEventContainer(event.getBlock())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onEntityExplode(EntityExplodeEvent event) {
		event.blockList().removeIf(block -> {
			if (isEventContainer(block)) {
				handleContainerEmpty(block);
				return true;
			}
			return false;
		});
	}

	@EventHandler
	public void onBlockExplode(BlockExplodeEvent event) {
		event.blockList().removeIf(block -> {
			if (isEventContainer(block)) {
				handleContainerEmpty(block);
				return true;
			}
			return false;
		});
	}

	private void handleInstantRewardContainer(Player player, Block block) {
		if (!(block.getState() instanceof Container container)) return;

		Location loc = block.getLocation().add(0.5, 0.5, 0.5);
		player.getWorld().spawnParticle(Particle.PORTAL, loc, 50, 0.5, 0.5, 0.5, 1);
		player.getWorld().spawnParticle(Particle.END_ROD, loc, 20, 0.3, 0.3, 0.3, 0.1);
		SoundHelper.playWorldSoundSafely(player.getWorld(), "block.enchantment_table.use", loc, 1.0f, 1.0f);
		SoundHelper.playWorldSoundSafely(player.getWorld(), "entity.player.levelup", loc, 1.0f, 0.5f);

		for (ItemStack item : container.getInventory().getContents()) {
			if (item != null) {
				player.getInventory().addItem(item).forEach((slot, leftover) -> {
					player.getWorld().dropItemNaturally(player.getLocation(), leftover);
					player.sendMessage(Component.text("Your inventory was full! Some items were dropped.", NamedTextColor.RED));
				});
			}
		}

		block.setType(Material.AIR);
		UUID sessionId = getEventSessionId(block);
		if (sessionId != null) {
			plugin.getContainerManager().unregisterContainer(block.getLocation(), sessionId);
		}
	}

	private void handleContainerEmpty(Block block) {
		block.setType(Material.AIR);
		UUID sessionId = getEventSessionId(block);
		if (sessionId != null) {
			plugin.getContainerManager().unregisterContainer(block.getLocation(), sessionId);
		}
	}

	private boolean isEventContainer(Block block) {
		if (!(block.getState() instanceof Container)) return false;
		return PersistentDataHelper.has(((Container) block.getState()).getPersistentDataContainer(), plugin, CONTAINER_KEY, PersistentDataType.BYTE);
	}

	private UUID getEventSessionId(Block block) {
		if (!(block.getState() instanceof Container container)) return null;
		String raw = PersistentDataHelper.get(container.getPersistentDataContainer(), plugin, CONTAINER_SESSION_KEY, PersistentDataType.STRING);
		try { return raw != null ? UUID.fromString(raw) : null; }
		catch (IllegalArgumentException e) {
			plugin.getLogger().warning("Invalid session ID: " + raw);
			return null;
		}
	}

	private ContainerType getContainerType(Block block) {
		if (!(block.getState() instanceof Container container)) return null;
		String str = PersistentDataHelper.get(container.getPersistentDataContainer(), plugin, CONTAINER_TYPE_KEY, PersistentDataType.STRING);
		try { return str != null ? ContainerType.valueOf(str) : null; }
		catch (IllegalArgumentException e) {
			plugin.getLogger().warning("Invalid container type: " + str);
			return null;
		}
	}
}
