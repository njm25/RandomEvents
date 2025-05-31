package nc.randomEvents.events.Sheepocalypse;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.events.Event;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.utils.LocationHelper;
import nc.randomEvents.utils.SoundHelper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
import java.util.stream.Collectors;

public class SheepocalypseEvent implements Event, Listener {
    private final RandomEvents plugin;
    private final RewardGenerator rewardGenerator;
    private final Set<SheepBomb> activeSheep;
    private final Map<UUID, ItemStack> givenShears = new HashMap<>();
    private BukkitTask spawnTask;
    private BukkitTask checkEndTask;
    private boolean isEventActive;
    private boolean isSpawningComplete;
    private static final int GROUP_RADIUS = 50; // Radius for grouping players
    private final Random random = new Random();

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
        return "Sheepocalypse";
    }

    @Override
    public String getDescription() {
        return "Spawns explosive sheep that must be sheared before they detonate!";
    }

    @Override
    public void execute(Set<Player> players) {
        if (isEventActive) {
            stopEvent(false);
        }

        isEventActive = true;
        isSpawningComplete = false;
        activeSheep.clear();
        givenShears.clear();
        for (Player player : players) {
            ItemStack shears = new ItemStack(Material.SHEARS);
            player.getInventory().addItem(shears);
            givenShears.put(player.getUniqueId(), shears);
            player.sendMessage(Component.text("Oh no! ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                .append(Component.text("The sheep have gone mad! Quick, shear them before they explode!", NamedTextColor.YELLOW)));
        }

        // Start spawning sheep near player groups
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isEventActive || players.isEmpty()) {
                stopEvent(false);
                return;
            }

            // Recalculate groups each tick to handle player movement
            Set<Set<Player>> currentGroups = LocationHelper.groupPlayers(
                players.stream().filter(p -> p.isOnline() && !p.isDead()).collect(Collectors.toSet()),
                GROUP_RADIUS
            );

            for (Set<Player> group : currentGroups) {
                Location groupMidpoint = LocationHelper.findMidpoint(group);
                if (groupMidpoint == null) continue;

                // Find the player farthest from the midpoint to calculate spawn radius
                double maxDistance = 0;
                for (Player p : group) {
                    double dist = p.getLocation().distance(groupMidpoint);
                    maxDistance = Math.max(maxDistance, dist);
                }

                // Add 10 blocks to the max distance for spawn radius
                double spawnRadius = maxDistance + 10;

                // Try to spawn sheep - one for each player in the group
                for (int i = 0; i < group.size(); i++) {
                    Location spawnLoc = findSafeSpawnLocation(groupMidpoint, spawnRadius);
                    if (spawnLoc != null) {
                        spawnSheep(spawnLoc);
                    }
                }
            }
        }, 0L, 80L); // Spawn every 4 seconds

        // Get duration from config (default 45 seconds)
        int durationSeconds = plugin.getConfigManager().getConfigValue(getName(), "duration");
        int durationTicks = durationSeconds * 20;

        // Schedule spawning end
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            isSpawningComplete = true;
            if (spawnTask != null) {
                spawnTask.cancel();
                spawnTask = null;
            }
        }, durationTicks);

        // Start checking for event end
        checkEndTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (isSpawningComplete && activeSheep.isEmpty()) {
                for (Player player : players) {
                    if (player.isOnline()) {
                        removeShears(player);
                        giveEndRewards(player);
                    }
                }
                stopEvent(false);
            }
        }, 20L, 20L);
    }

    private Location findSafeSpawnLocation(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) return null;

        for (int attempts = 0; attempts < 15; attempts++) { // Increased attempts
            // Get random angle and distance within radius
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = radius * 0.5 + random.nextDouble() * (radius * 0.5); // Between 50% and 100% of radius
            
            // Calculate potential location
            double x = center.getX() + (Math.cos(angle) * distance);
            double z = center.getZ() + (Math.sin(angle) * distance);
            
            Location potentialLoc = new Location(world, x, 0, z);
            int highestY = world.getHighestBlockYAt(potentialLoc);
            potentialLoc.setY(highestY);

            // Check if the location is too high or too low relative to center
            if (Math.abs(potentialLoc.getY() - center.getY()) > 10) {
                continue; // Skip if height difference is too large
            }

            // Move up until we find air (in case of trees/overhangs)
            while (potentialLoc.getY() < world.getMaxHeight() - 2 && !potentialLoc.getBlock().getType().isAir()) {
                potentialLoc.add(0, 1, 0);
            }

            if (isSafeLocation(potentialLoc)) {
                return potentialLoc.add(0.5, 0, 0.5); // Center on block
            }
        }
        return null;
    }

    private boolean isSafeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        
        // Check block at feet level
        if (!loc.getBlock().getType().isAir()) return false;
        
        // Check block above (head level)
        Location headLoc = loc.clone().add(0, 1, 0);
        if (!headLoc.getBlock().getType().isAir()) return false;
        
        // Check block below (ground)
        Location groundLoc = loc.clone().add(0, -1, 0);
        if (!groundLoc.getBlock().getType().isSolid()) return false;
        
        // Make sure we're not spawning in water or lava
        if (loc.getBlock().isLiquid() || headLoc.getBlock().isLiquid()) return false;
        
        return true;
    }

    private void spawnSheep(Location spawnLoc) {
        World world = spawnLoc.getWorld();
        if (world == null) return;

        Sheep sheep = world.spawn(spawnLoc, Sheep.class);
        sheep.setRemoveWhenFarAway(true);

        // Play spawn sounds
        SoundHelper.playWorldSoundSafely(world, "entity.enderman.teleport", spawnLoc, 1.0f, 1.2f);
        SoundHelper.playWorldSoundSafely(world, "entity.sheep.ambient", spawnLoc, 2.0f, 1.0f);
        SoundHelper.playWorldSoundSafely(world, "block.note.pling", spawnLoc, 1.0f, 2.0f);

        // Create sheep bomb with configurable timer
        int bombTimerSeconds = plugin.getConfigManager().getConfigValue(getName(), "bombTimer");
        final SheepBomb[] sheepBombRef = new SheepBomb[1];
        sheepBombRef[0] = new SheepBomb(plugin, sheep, () -> activeSheep.remove(sheepBombRef[0]), bombTimerSeconds * 20);
        activeSheep.add(sheepBombRef[0]);
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
                player.getWorld().dropItemNaturally(sheep.getLocation(), reward);
            }
            
            // Play success sound
            SoundHelper.playPlayerSoundSafely(player, "entity.player.levelup", player.getLocation(), 1.0f, 2.0f);
            
            sheepBomb.playShearAndPoofEffect(); // This will handle the shearing visual and removal after effects
        }
    }

    @EventHandler
    public void onSheepDeath(EntityDeathEvent event) {
        if (!isEventActive || !(event.getEntity() instanceof Sheep)) return;
        
        Sheep sheep = (Sheep) event.getEntity();

        // Find and remove the corresponding SheepBomb
        for (SheepBomb bomb : new HashSet<>(activeSheep)) {
            if (bomb.isSameSheep(sheep)) {
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