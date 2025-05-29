package nc.randomEvents.services.events.Test;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.services.events.Event;
import nc.randomEvents.services.EquipmentManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class TestEvent implements Event {
    private final RandomEvents plugin;
    private final EquipmentManager equipmentManager;
    private final Map<UUID, BukkitTask> activeTasks;
    private static final int EVENT_DURATION = 15; // 60 seconds

    public TestEvent(RandomEvents plugin) {
        this.plugin = plugin;
        this.equipmentManager = plugin.getEquipmentManager();
        this.activeTasks = new HashMap<>();
    }

    @Override
    public String getName() {
        return "TestEvent";
    }

    @Override
    public String getDescription() {
        return "A test event for equipment management functionality";
    }

    @Override
    public void execute(Set<Player> players) {
        for (Player player : players) {
            if (!player.isOnline() || player.isDead()) continue;
            
            // Test different equipment scenarios
            testEquipmentScenarios(player);
            
            // Schedule event end
            scheduleEventEnd(player);
        }
    }

    private void testEquipmentScenarios(Player player) {
        // Scenario 1: Give a single item (book)
/*        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        equipmentManager.giveEquipment(player, book, "test_book");

        // Scenario 2: Give a full kit
        Map<Integer, ItemStack> kit = new HashMap<>();
        kit.put(0, new ItemStack(Material.DIAMOND_SWORD));
        kit.put(1, new ItemStack(Material.SHIELD));
        kit.put(2, new ItemStack(Material.COOKED_BEEF, 16));
        equipmentManager.giveFullKit(player, kit, "test_kit");
 */
        // Scenario 3: Store and clear inventory
        equipmentManager.storeAndClearInventory(player, "test_inventory");
   
        }

    private void scheduleEventEnd(Player player) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Return all equipment
            //equipmentManager.returnEquipment(player, "test_book");
            //equipmentManager.returnFullKit(player, "test_kit");
            equipmentManager.restoreInventory(player, "test_inventory");
            
            // Remove task from active tasks
            activeTasks.remove(player.getUniqueId());
        }, EVENT_DURATION * 20L); // Convert seconds to ticks

        activeTasks.put(player.getUniqueId(), task);
    }

    public void cleanup() {
        // Cancel all active tasks
        for (BukkitTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
    }
} 