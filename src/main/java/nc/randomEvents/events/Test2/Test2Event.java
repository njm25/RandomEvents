package nc.randomEvents.events.Test2;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.services.EquipmentManager;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.utils.GiveItemHelper;
import nc.randomEvents.services.RewardGenerator.Tier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Test2Event extends BaseEvent {
    private final RewardGenerator rewardGenerator;
    private final EquipmentManager equipmentManager;
    public Test2Event(RandomEvents plugin) {
        this.rewardGenerator = plugin.getRewardGenerator();
        this.equipmentManager = plugin.getEquipmentManager();
        
        // Configure event timing
        setTickInterval(20L); // Tick every second
        setDuration(200L); // Run for 10 seconds
        setStripsInventory(false);
        setCanBreakBlocks(false);
        setCanPlaceBlocks(false);
    }
    
    @Override
    public void onStart(UUID sessionId, Set<Player> players) {
        players.forEach(player -> {
            player.sendMessage(Component.text("Test2Event has started! Will tick for 10 seconds.").color(NamedTextColor.GREEN));
            // Equip players with test equipment
            equipmentManager.giveEquipment(player, new ItemStack(Material.DIAMOND_AXE), "test_equipment", sessionId);
        });
    }
    
    @Override
    public void onTick(UUID sessionId, Set<Player> players) {
        players.forEach(player -> {
            player.sendMessage(Component.text("Test2Event tick!").color(NamedTextColor.YELLOW));
        });
    }
    
    @Override
    public void onEnd(UUID sessionId, Set<Player> players) {
        players.forEach(player -> {
            player.sendMessage(Component.text("Test2Event has ended!").color(NamedTextColor.RED));
            
            // Generate rewards that players keep
            List<ItemStack> rewards = rewardGenerator.generateRewards(
                new RewardGenerator.TierQuantity()
                    .add(Tier.COMMON, 2)
                    .add(Tier.RARE, 1)
                    .build()
            );
            
            // Give rewards to players
            for (ItemStack reward : rewards) {
                GiveItemHelper.giveItemToPlayer(player, reward);
            }
        });
    }
    
    @Override
    public String getName() {
        return "Test2";
    }
    
    @Override
    public String getDescription() {
        return "A test event using the new session-based system with equipment and rewards";
    }
}
