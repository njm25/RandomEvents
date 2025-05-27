package nc.randomEvents.services.events;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.services.RewardGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.HandlerList;

import java.util.*;

public class SheepocalypseEvent implements Event, Listener {
    private final RandomEvents plugin;
    private final RewardGenerator rewardGenerator;
    private final Set<SheepBomb> activeSheep;
    private final Map<UUID, ItemStack> givenShears = new HashMap<>();
    private BukkitTask spawnTask;
    private BukkitTask checkEndTask;
    private boolean isEventActive;
    private boolean isSpawningComplete;
    private static final int EVENT_SPAWN_DURATION = 45 * 20; // 45 seconds of spawning (in ticks)

    public SheepocalypseEvent(RandomEvents plugin) {
        this.plugin = plugin;
        this.rewardGenerator = plugin.getRewardGenerator();
        this.activeSheep = new HashSet<>();
        this.isEventActive = false;
        this.isSpawningComplete = false;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public String getName() {
        return "sheepocalypse";
    }

    @Override
    public String getDescription() {
        return "Spawns explosive sheep that must be sheared before they detonate!";
    }

    @Override
    public void execute(List<Player> players) {
        if (isEventActive) {
            stopEvent(); // Stop any existing event before starting a new one
        }

        isEventActive = true;
        isSpawningComplete = false;
        activeSheep.clear();
        givenShears.clear();
        
        // Store participating players for later
        List<Player> participatingPlayers = new ArrayList<>(players);
        
        // Give all players shears
        for (Player player : players) {
            ItemStack shears = new ItemStack(Material.SHEARS);
            player.getInventory().addItem(shears);
            givenShears.put(player.getUniqueId(), shears);
            player.sendMessage("The Sheepocalypse has begun! Quick, shear the sheep before they explode!");
        }

        // Start spawning sheep near random players
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isEventActive || players.isEmpty()) {
                stopEvent();
                return;
            }
            
            // Pick a random player to spawn sheep near
            Player targetPlayer = players.get(new Random().nextInt(players.size()));
            World world = targetPlayer.getWorld();
            
            // Spawn sheep near the player (between 4-11 blocks away)
            for (int i = 0; i < 1; i++) {
                Location spawnLoc = null;
                int attempts = 0;
                
                while (spawnLoc == null && attempts < 10) {
                    attempts++;
                    
                    // Get random angle and distance
                    double angle = Math.random() * 2 * Math.PI;
                    double distance = 4 + Math.random() * 7; // Between 4 and 11 blocks
                    
                    // Convert to x/z coordinates
                    int x = targetPlayer.getLocation().getBlockX() + (int)(Math.cos(angle) * distance);
                    int z = targetPlayer.getLocation().getBlockZ() + (int)(Math.sin(angle) * distance);
                    
                    // Get the highest non-air block
                    Location potentialLoc = new Location(world, x, 0, z);
                    potentialLoc.setY(world.getHighestBlockYAt(x, z));
                    
                    // Check if Y level is within 6 blocks of player
                    if (Math.abs(potentialLoc.getY() - targetPlayer.getLocation().getY()) > 6) {
                        continue;
                    }
                    
                    // Move up until we find air (in case of trees/overhangs)
                    while (potentialLoc.getBlock().getType() != Material.AIR && potentialLoc.getY() < world.getMaxHeight() - 2) {
                        potentialLoc.add(0, 1, 0);
                        // Check Y level again after moving up
                        if (Math.abs(potentialLoc.getY() - targetPlayer.getLocation().getY()) > 6) {
                            potentialLoc = null;
                            break;
                        }
                    }
                    
                    // Verify location is valid
                    if (potentialLoc != null && 
                        potentialLoc.getBlock().getType() == Material.AIR && 
                        potentialLoc.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
                        
                        // Center the sheep on the block
                        spawnLoc = potentialLoc.add(0.5, 0, 0.5);
                    }
                }
                
                // If we found a valid location, spawn the sheep
                if (spawnLoc != null) {
                    Sheep sheep = world.spawn(spawnLoc, Sheep.class);
                    sheep.setRemoveWhenFarAway(true);
                    
                    // Play spawn sound
                    world.playSound(sheep.getLocation(), Sound.ENTITY_SHEEP_AMBIENT, 2.0f, 1.0f);
                    world.playSound(sheep.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                    
                    SheepBomb sheepBomb = new SheepBomb(plugin, sheep);
                    activeSheep.add(sheepBomb);
                }
            }
        }, 0L, 80L); // Spawn every 4 seconds (80 ticks)

        // Schedule spawning end
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            isSpawningComplete = true;
            if (spawnTask != null) {
                spawnTask.cancel();
                spawnTask = null;
            }
            for (Player player : participatingPlayers) {
                if (player.isOnline()) {
                    player.sendMessage("No more sheep will spawn! Deal with the remaining ones!");
                }
            }
        }, EVENT_SPAWN_DURATION);

        // Start checking for event end (when all sheep are gone)
        checkEndTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (isSpawningComplete && activeSheep.isEmpty()) {
                for (Player player : participatingPlayers) {
                    if (player.isOnline()) {
                        player.sendMessage("The Sheepocalypse is over!");
                        removeShears(player);
                    }
                }
                stopEvent();
            }
        }, 20L, 20L); // Check every second
    }

    private void removeShears(Player player) {
        ItemStack shears = givenShears.get(player.getUniqueId());
        if (shears != null) {
            player.getInventory().remove(shears);
            givenShears.remove(player.getUniqueId());
        }
    }

    public void stopEvent() {
        if (!isEventActive) return; // Prevent double cleanup
        
        isEventActive = false;
        isSpawningComplete = true;
        
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        if (checkEndTask != null) {
            checkEndTask.cancel();
            checkEndTask = null;
        }
        
        // Clean up any remaining sheep
        for (SheepBomb sheepBomb : new HashSet<>(activeSheep)) {
            sheepBomb.remove();
        }
        activeSheep.clear();

        // Clean up any remaining shears
        for (UUID playerId : new ArrayList<>(givenShears.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                removeShears(player);
            }
        }
        givenShears.clear();
        
        HandlerList.unregisterAll(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onSheepShear(PlayerShearEntityEvent event) {
        if (!isEventActive || !(event.getEntity() instanceof Sheep)) return;

        event.setCancelled(true); // Prevent regular wool drops
        
        Sheep sheep = (Sheep) event.getEntity();
        SheepBomb sheepBomb = null;

        for (SheepBomb bomb : activeSheep) {
            if (bomb.isSameSheep(sheep)) {
                sheepBomb = bomb;
                break;
            }
        }

        if (sheepBomb != null) {
            Player player = event.getPlayer();
            //player.sendMessage("You saved a sheep from exploding!");
            
            // Drop rewards on ground
            List<ItemStack> rewards = rewardGenerator.generateRewards(RewardGenerator.Tier.BASIC, 1);
            for (ItemStack reward : rewards) {
                player.getWorld().dropItemNaturally(player.getLocation(), reward);
            }
            
            // Play success sound
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
            
            activeSheep.remove(sheepBomb);
            sheepBomb.remove();
        }
    }

    @EventHandler
    public void onSheepDeath(EntityDeathEvent event) {
        if (!isEventActive || !(event.getEntity() instanceof Sheep)) return;
        
        // Clear drops from explosive sheep
        event.getDrops().clear();
        event.setDroppedExp(0);
    }
}