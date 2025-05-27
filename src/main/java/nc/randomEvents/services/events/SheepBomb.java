package nc.randomEvents.services.events;

import nc.randomEvents.RandomEvents;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Sheep;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class SheepBomb {
    private final RandomEvents plugin;
    private final Sheep sheep;
    private BukkitTask colorChangeTask;
    private BukkitTask moveTask;
    private int timeLeft = 200; // 10 seconds (200 ticks)

    public SheepBomb(RandomEvents plugin, Sheep sheep) {
        this.plugin = plugin;
        this.sheep = sheep;
        
        // Initialize sheep
        sheep.setColor(DyeColor.LIME);
        
        // Start the countdown and movement
        startCountdown();
        startMovement();
    }

    private void startMovement() {
        moveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (sheep == null || sheep.isDead()) {
                    cancel();
                    return;
                }

                // Random sheep noises
                if (Math.random() < 0.3) { // 30% chance each tick to make noise
                    sheep.getWorld().playSound(sheep.getLocation(), Sound.ENTITY_SHEEP_AMBIENT, 0.8f, 1.0f);
                }

                // Find nearest player within 10 blocks
                sheep.getWorld().getNearbyPlayers(sheep.getLocation(), 10).forEach(player -> {
                    // Calculate direction away from player
                    Vector direction = sheep.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                    
                    // Apply reasonable sheep speed (0.3 is about normal sheep speed)
                    direction.multiply(0.3);
                    
                    // Maintain Y velocity to work with gravity properly
                    direction.setY(sheep.getVelocity().getY());
                    
                    // Apply the velocity
                    sheep.setVelocity(direction);
                });
            }
        }.runTaskTimer(plugin, 0L, 10L); // Update movement every half second
    }

    private void startCountdown() {
        colorChangeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (sheep == null || sheep.isDead()) {
                    cancel();
                    return;
                }

                timeLeft -= 1; // Decrease by 1 tick
                
                if (timeLeft <= 0) {
                    explode();
                    cancel();
                    return;
                }

                // Update color based on time left
                if (timeLeft <= 60) { // Last 3 seconds
                    sheep.setColor(DyeColor.RED);
                    // Play tick sound during red phase
                    if (timeLeft % 20 == 0) { // Every second
                        sheep.getWorld().playSound(sheep.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 0.5f);
                    }
                } else if (timeLeft <= 120) { // 3-6 seconds
                    sheep.setColor(DyeColor.YELLOW);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void explode() {
        if (sheep != null && !sheep.isDead()) {
            Location loc = sheep.getLocation();
            sheep.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            sheep.getWorld().createExplosion(loc, 2.0f, true, true);
            remove();
        }
    }

    public void remove() {
        if (colorChangeTask != null) {
            colorChangeTask.cancel();
        }
        if (moveTask != null) {
            moveTask.cancel();
        }
        if (sheep != null && !sheep.isDead()) {
            sheep.remove();
        }
    }

    public boolean isSameSheep(Sheep other) {
        return sheep != null && !sheep.isDead() && sheep.getUniqueId().equals(other.getUniqueId());
    }
} 