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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

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
            // If there's an existing event, stop it WITHOUT giving rewards
            stopEvent(false);
        }

        isEventActive = true;
        isSpawningComplete = false;
        activeSheep.clear();
        givenShears.clear();
        
        // Give all players shears
        for (Player player : players) {
            ItemStack shears = new ItemStack(Material.SHEARS);
            player.getInventory().addItem(shears);
            givenShears.put(player.getUniqueId(), shears);
            player.sendMessage(Component.text("Oh no! ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                .append(Component.text("The sheep have gone mad! Quick, shear them before they explode!", NamedTextColor.YELLOW)));
        }

        // Start spawning sheep near random players
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isEventActive || players.isEmpty()) {
                stopEvent(false);
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
                    
                    // Play spawn sounds
                    world.playSound(sheep.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                    world.playSound(sheep.getLocation(), Sound.ENTITY_SHEEP_AMBIENT, 2.0f, 1.0f);
                    world.playSound(sheep.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                    
                    // Use array to hold reference that can be modified in lambda
                    final SheepBomb[] sheepBombRef = new SheepBomb[1];
                    sheepBombRef[0] = new SheepBomb(plugin, sheep, () -> {
                        // This will be called when the sheep explodes
                        activeSheep.remove(sheepBombRef[0]);
                    });
                    activeSheep.add(sheepBombRef[0]);
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
        }, EVENT_SPAWN_DURATION);

        // Start checking for event end (when all sheep are gone)
        checkEndTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (isSpawningComplete && activeSheep.isEmpty()) {
                for (Player player : players) {
                    if (player.isOnline()) {
                        removeShears(player);
                        giveEndRewards(player);
                    }
                }
                stopEvent(false); // Don't give rewards again, they were just given
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
            
            // Cancel any pending explosion tasks immediately
            sheepBomb.cancelExplosion();
            activeSheep.remove(sheepBomb); // Remove from active sheep BEFORE playing effects
            
            // Drop rewards on ground
            // 30% chance for COMMON reward, 5% chance for RARE
            RewardGenerator.Tier tier = RewardGenerator.Tier.BASIC;
            double rand = Math.random();
            if (rand < 0.05) {
                tier = RewardGenerator.Tier.RARE;
            } else if (rand < 0.35) {
                tier = RewardGenerator.Tier.COMMON;
            }
            
            List<ItemStack> rewards = rewardGenerator.generateRewards(tier, 1);
            for (ItemStack reward : rewards) {
                player.getWorld().dropItemNaturally(player.getLocation(), reward);
            }
            
            // Play success sound
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
            
            sheepBomb.playShearAndPoofEffect(); // This will handle the shearing visual and removal after effects
        }
    }

    @EventHandler
    public void onSheepDeath(EntityDeathEvent event) {
        if (!isEventActive || !(event.getEntity() instanceof Sheep)) return;
        
        Sheep sheep = (Sheep) event.getEntity();
        SheepBomb sheepBomb = null;

        // Find and remove the corresponding SheepBomb
        for (SheepBomb bomb : new HashSet<>(activeSheep)) {
            if (bomb.isSameSheep(sheep)) {
                sheepBomb = bomb;
                activeSheep.remove(bomb);
                bomb.remove(); // Make sure to clean up the SheepBomb
                break;
            }
        }
        
        // Clear drops from explosive sheep
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    private void giveEndRewards(Player player) {
        // 50% chance for COMMON, 20% chance for RARE
        RewardGenerator.Tier tier = RewardGenerator.Tier.BASIC;
        double rand = Math.random();
        if (rand < 0.20) {
            tier = RewardGenerator.Tier.RARE;
        } else if (rand < 0.70) {
            tier = RewardGenerator.Tier.COMMON;
        }
        
        List<ItemStack> rewards = rewardGenerator.generateRewards(tier, 2); // Give 2 items for completing
        for (ItemStack reward : rewards) {
            player.getWorld().dropItemNaturally(player.getLocation(), reward);
        }
    }

    public void stopEvent(boolean giveRewards) {
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

        // Clean up any remaining shears and optionally give rewards
        for (UUID playerId : new ArrayList<>(givenShears.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                removeShears(player);
                if (giveRewards) {
                    giveEndRewards(player);
                }
            }
        }
        givenShears.clear();
        
        HandlerList.unregisterAll(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void stopEvent() {
        stopEvent(false); // Default to not giving rewards for backward compatibility
    }
}