package nc.randomEvents.events.tests;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.core.LootContainer;
import nc.randomEvents.core.LootContainer.ContainerMaterial;
import nc.randomEvents.core.LootContainer.ContainerType;
import nc.randomEvents.services.RewardGenerator.Tier;
import nc.randomEvents.services.participants.ContainerManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ContainerManagerTest extends BaseEvent {
	private final ContainerManager containerManager;
	private final Map<UUID, List<Location>> playerContainers = new HashMap<>();

	public ContainerManagerTest(RandomEvents plugin) {
		this.containerManager = plugin.getContainerManager();
		setTickInterval(20L); // 1 second
		setDuration(400L); // 40 seconds
		setClearContainerAtEndDefault(true); // Test container clearing
	}

	@Override
	public String getName() {
		return "ContainerManagerTest";
	}

	@Override
	public String getDescription() {
		return "A test event for container management functionality";
	}

	@Override
	public void onStart(UUID sessionId, Set<Player> players) {
		for (Player player : players) {
			player.sendMessage(Component.text("Container Manager Test Event Started!", NamedTextColor.GREEN));
			List<Location> containers = new ArrayList<>();

			// Test 1: Instant reward green shulker (cleared)
			create(loc(player), sessionId, ContainerType.INSTANT_REWARD, "instant_green", ContainerMaterial.GREEN_SHULKER_BOX, true, containers);

			// Test 2: Regular green shulker (cleared)
			create(loc(player), sessionId, ContainerType.REGULAR, "regular_green", ContainerMaterial.GREEN_SHULKER_BOX, true, containers);

			// Test 3: Instant reward red shulker (not cleared)
			create(loc(player), sessionId, ContainerType.INSTANT_REWARD, "instant_red", ContainerMaterial.RED_SHULKER_BOX, false, containers);

			// Test 4: Regular red shulker (not cleared)
			create(loc(player), sessionId, ContainerType.REGULAR, "regular_red", ContainerMaterial.RED_SHULKER_BOX, false, containers);

			// Test 5: Regular purple with quest items (forced clear by session)
			Location questLoc = loc(player);
			if (questLoc != null) {
				LootContainer c = containerManager.createContainer(questLoc, sessionId, ContainerType.REGULAR, false);
				c.setMaterial(ContainerMaterial.PURPLE_SHULKER_BOX);
				c.addQuestItem(new ItemStack(Material.GOLDEN_APPLE, 3));
				c.addQuestItem(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
				c.addQuestItem(new ItemStack(Material.NETHERITE_INGOT, 1));
				if (c.spawn()) containers.add(questLoc);
			}

			// Test 6: Regular cyan with quest items + normal items + rewards
			Location mixedLoc = loc(player);
			if (mixedLoc != null) {
				ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
				ItemMeta m1 = sword.getItemMeta();
				m1.displayName(Component.text("Test Sword (Quest Item)", NamedTextColor.AQUA));
				sword.setItemMeta(m1);

				ItemStack steak = new ItemStack(Material.COOKED_BEEF, 32);
				ItemMeta m2 = steak.getItemMeta();
				m2.displayName(Component.text("Test Steak (Gift)", NamedTextColor.GREEN));
				steak.setItemMeta(m2);

				LootContainer c = containerManager.createContainer(mixedLoc, sessionId, ContainerType.REGULAR, false);
				c.setMaterial(ContainerMaterial.CYAN_SHULKER_BOX);
				c.addQuestItem(sword);
				c.addLootItem(steak);
				c.addRewardTier(Tier.COMMON, 3);
				c.addRewardTier(Tier.BASIC, 5);
				if (c.spawn()) containers.add(mixedLoc);
			}

			// Test 7: Regular chest with custom name
			Location customLoc = loc(player);
			if (customLoc != null) {
				LootContainer c = containerManager.createContainer(customLoc, sessionId, ContainerType.REGULAR, true);
				c.setMaterial(ContainerMaterial.CHEST);
				c.setInventoryName("§6§lCustom Treasure Chest");
				c.addLootItem(new ItemStack(Material.NETHERITE_INGOT, 1));
				c.addLootItem(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
				c.addLootItem(new ItemStack(Material.DIAMOND_BLOCK, 2));
				if (c.spawn()) {
					containers.add(customLoc);
					player.sendMessage(Component.text("Created custom named chest! Right-click to see the custom title.", NamedTextColor.GREEN));
				}
			}

			playerContainers.put(player.getUniqueId(), containers);
		}
	}

	private void create(Location loc, UUID sessionId, ContainerType type, String idPrefix,
	                    ContainerMaterial mat, boolean clear, List<Location> addTo) {
		if (loc == null) return;
		LootContainer c = containerManager.createContainer(loc, sessionId, type, clear);
		c.setMaterial(mat);
		c.addLootItem(new ItemStack(Material.DIAMOND, 1));
		c.addLootItem(new ItemStack(Material.EMERALD, 2));
		c.addLootItem(new ItemStack(Material.GOLD_INGOT, 3));
		if (c.spawn()) addTo.add(loc);
	}

	private Location loc(Player player) {
		return findSafeLocation(player.getLocation());
	}

	@Override
	public void onTick(UUID sessionId, Set<Player> players) {}

	@Override
	public void onEnd(UUID sessionId, Set<Player> players) {
		for (Player player : players) {
			player.sendMessage(Component.text("Container Manager Test Event Ended!", NamedTextColor.RED));
		}
	}

	private Location findSafeLocation(Location center) {
		World world = center.getWorld();
		if (world == null) return null;

		for (int x = -10; x <= 10; x++) {
			for (int z = -10; z <= 10; z++) {
				Location loc = center.clone().add(x, 0, z);
				if (isSafeLocation(loc)) return loc;
			}
		}
		return null;
	}

	private boolean isSafeLocation(Location loc) {
		Block block = loc.getBlock();
		Block above = loc.clone().add(0, 1, 0).getBlock();
		Block below = loc.clone().subtract(0, 1, 0).getBlock();
		return block.getType().isAir()
			&& above.getType().isAir()
			&& below.getType().isSolid()
			&& below.getType() != Material.WATER
			&& below.getType() != Material.LAVA;
	}
}
