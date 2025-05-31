package nc.randomEvents.events.Test2;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.Set;

public class Test2Event extends BaseEvent {
    
    public Test2Event(RandomEvents plugin) {
        // Configure event timing
        setTickInterval(20L); // Tick every second
        setDuration(200L); // Run for 10 seconds
    }
    
    @Override
    public void onStart(Set<Player> players) {
        players.forEach(player -> 
            player.sendMessage(ChatColor.GREEN + "Test2Event has started! Will tick for 10 seconds."));
    }
    
    @Override
    public void onTick(Set<Player> players) {
        players.forEach(player -> 
            player.sendMessage(ChatColor.YELLOW + "Test2Event tick!"));
    }
    
    @Override
    public void onEnd(Set<Player> players) {
        players.forEach(player -> 
            player.sendMessage(ChatColor.RED + "Test2Event has ended!"));
    }
    
    @Override
    public String getName() {
        return "Test2";
    }
    
    @Override
    public String getDescription() {
        return "A test event using the new session-based system";
    }
}
