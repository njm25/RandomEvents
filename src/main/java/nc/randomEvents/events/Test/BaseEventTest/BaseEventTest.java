package nc.randomEvents.events.Test.BaseEventTest;

import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.RandomEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import java.util.Set;
import java.util.UUID;

public class BaseEventTest extends BaseEvent {
    private int tickCount = 0;
    private int testPhase = 0;

    public BaseEventTest(RandomEvents plugin) {

        // Test different configurations
        setTickInterval(10L);  // Tick every half second
        setDuration(200L);     // Run for 10 seconds
        setStripsInventory(true);  // Test inventory stripping
        setCanBreakBlocks(false);  // Test block breaking restriction
        setCanPlaceBlocks(false);  // Test block placing restriction
    }

    @Override
    public void onStart(UUID sessionId, Set<Player> players) {
        players.forEach(player -> {
            player.sendMessage(Component.text("BaseEventTest starting with configuration:").color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("- Tick Interval: " + getTickInterval() + " ticks").color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("- Duration: " + getDuration() + " ticks").color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("- Strips Inventory: " + stripsInventory()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("- Can Break Blocks: " + canBreakBlocks()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("- Can Place Blocks: " + canPlaceBlocks()).color(NamedTextColor.YELLOW));
            
            // Give instructions
            player.sendMessage(Component.text("Try to break or place blocks - it should be prevented!").color(NamedTextColor.AQUA));
            player.sendMessage(Component.text("Your inventory should be temporarily stored.").color(NamedTextColor.AQUA));
        });
    }

    @Override
    public void onTick(UUID sessionId, Set<Player> players) {
        tickCount++;
        
        // Every 2 seconds (4 ticks), change the test phase
        if (tickCount % 4 == 0) {
            testPhase++;
            players.forEach(player -> {
                switch (testPhase) {
                    case 1:
                        player.sendMessage(Component.text("Phase 1: Testing block interactions...").color(NamedTextColor.GOLD));
                        break;
                    case 2:
                        player.sendMessage(Component.text("Phase 2: Testing inventory state...").color(NamedTextColor.GOLD));
                        break;
                    case 3:
                        setCanBreakBlocks(true);
                        setCanPlaceBlocks(true);
                        player.sendMessage(Component.text("Phase 3: Enabling block interactions...").color(NamedTextColor.GOLD));
                        player.sendMessage(Component.text("You should now be able to break and place blocks!").color(NamedTextColor.AQUA));
                        break;
                    case 4:
                        setCanBreakBlocks(false);
                        setCanPlaceBlocks(false);
                        player.sendMessage(Component.text("Phase 4: Disabling block interactions...").color(NamedTextColor.GOLD));
                        player.sendMessage(Component.text("Block interactions should be prevented again!").color(NamedTextColor.AQUA));
                        break;
                }
            });
        }
    }

    @Override
    public void onEnd(UUID sessionId, Set<Player> players) {
        players.forEach(player -> {
            player.sendMessage(Component.text("BaseEventTest complete! Summary:").color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("- Completed " + testPhase + " test phases").color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("- Ticked " + tickCount + " times").color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Your inventory should now be restored.").color(NamedTextColor.AQUA));
        });
    }

    @Override
    public String getName() {
        return "BaseEventTest";
    }

    @Override
    public String getDescription() {
        return "Tests core functionality of the BaseEvent system including timing, inventory management, and block interaction controls";
    }
}
