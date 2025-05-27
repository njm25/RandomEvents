package nc.randomEvents.services.events;

import nc.randomEvents.RandomEvents;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Sheep;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

public class SheepBomb {
    private final RandomEvents plugin;
    private final Sheep sheep;
    private BukkitTask colorChangeTask;
    private BukkitTask moveTask;
    private int timeLeft = 200; // 10 seconds (200 ticks)
    private Location fleeDestination = null;
    private final Random random = new Random();
    private final Runnable onExplode;

    public SheepBomb(RandomEvents plugin, Sheep sheep, Runnable onExplode) {
        this.plugin = plugin;
        this.sheep = sheep;
        this.onExplode = onExplode;
        
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

                // If we don't have a flee destination or we've reached it, pick a new one
                if (fleeDestination == null || sheep.getLocation().distanceSquared(fleeDestination) < 4) {
                    // Pick a random point 15-25 blocks away in a random direction
                    Location sheepLoc = sheep.getLocation();
                    double angle = random.nextDouble() * 2 * Math.PI;
                    double distance = 15 + random.nextDouble() * 10; // Between 15-25 blocks
                    double dx = Math.cos(angle) * distance;
                    double dz = Math.sin(angle) * distance;
                    
                    Location newDest = sheepLoc.clone().add(dx, 0, dz);
                    // Set Y to highest block at that X/Z
                    World world = sheepLoc.getWorld();
                    if (world != null) {
                        newDest.setY(world.getHighestBlockYAt(newDest));
                        fleeDestination = newDest.add(0.5, 1.0, 0.5); // Center on block
                    }
                }

                // Use pathfinding to move towards the destination
                if (fleeDestination != null && sheep.getPathfinder() != null) {
                    sheep.getPathfinder().moveTo(fleeDestination);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // Update movement every half second
    }

    private void startCountdown() {
        colorChangeTask = new BukkitRunnable() {
            private boolean hasPlayedHiss = false;
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
                    // Add creeper hiss only once when entering final second
                    if (timeLeft == 20 && !hasPlayedHiss) {
                        sheep.getWorld().playSound(sheep.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);
                        hasPlayedHiss = true;
                    }
                } else if (timeLeft <= 120) { // 3-6 seconds
                    sheep.setColor(DyeColor.YELLOW);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void cancelExplosion() {
        if (colorChangeTask != null) {
            colorChangeTask.cancel();
            colorChangeTask = null;
        }
        timeLeft = Integer.MAX_VALUE; // Prevent any future explosion attempts
    }

    private void explode() {
        if (sheep != null && !sheep.isDead()) {
            Location loc = sheep.getLocation();
            World world = sheep.getWorld();
            
            // Play explosion sound and particles
            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            world.createExplosion(loc, 0.0f, false, false); // No block damage
            
            // Spawn wind charge and particles
            world.spawnEntity(loc, org.bukkit.entity.EntityType.WIND_CHARGE);
            world.spawnParticle(Particle.CLOUD, loc, 40, 0.5, 0.5, 0.5, 0.2);
            
            // Remove sheep immediately
            if (onExplode != null) {
                onExplode.run();
            }
            remove();
        }
    }

    public void playShearAndPoofEffect() {
        if (sheep != null && !sheep.isDead()) {
            Location loc = sheep.getLocation().add(0, 1, 0);
            World world = sheep.getWorld();
            
            // Immediate shear effect and visual
            sheep.setSheared(true);
            world.playSound(loc, Sound.ENTITY_SHEEP_SHEAR, 1.0f, 1.0f);
            world.spawnParticle(Particle.CLOUD, loc, 20, 0.2, 0.2, 0.2, 0);
            
            // Schedule poof effect after a short delay
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!sheep.isValid() || sheep.isDead()) {
                        return;
                    }
                    
                    // Start poof sequence
                    new BukkitRunnable() {
                        int ticks = 0;
                        @Override
                        public void run() {
                            if (!sheep.isValid() || sheep.isDead()) {
                                this.cancel();
                                return;
                            }

                            ticks++;
                            Location currentLoc = sheep.getLocation().add(0, 1, 0);
                            
                            if (ticks % 10 == 0) { // Every half second
                                world.spawnParticle(Particle.CLOUD, currentLoc, 5, 0.2, 0.2, 0.2, 0);
                                world.playSound(currentLoc, Sound.ENTITY_SHEEP_AMBIENT, 0.5f, 1.5f);
                            }

                            if (ticks >= 20) { // After 1 second
                                // Final poof
                                world.playSound(currentLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                                world.spawnParticle(Particle.CLOUD, currentLoc, 30, 0.3, 0.5, 0.3, 0.05);
                                remove();
                                this.cancel();
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                }
            }.runTaskLater(plugin, 5L); // Start poof effect after 5 ticks (0.25 seconds)
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