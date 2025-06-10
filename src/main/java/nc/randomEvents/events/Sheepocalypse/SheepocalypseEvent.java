package nc.randomEvents.events.Sheepocalypse;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.services.participants.EntityManager;
import nc.randomEvents.utils.LocationHelper;
import nc.randomEvents.utils.SoundHelper;

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
import org.bukkit.event.HandlerList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class SheepocalypseEvent extends BaseEvent implements Listener {
    private final RandomEvents plugin;
    private final RewardGenerator rewardGenerator;
    private final EntityManager entityManager;
    private final Set<SheepBomb> activeSheep;
    private final Map<UUID, ItemStack> givenShears = new HashMap<>();
    private boolean isSpawningComplete;
    private boolean isEventActive;
    private static final int GROUP_RADIUS = 50; // Radius for grouping players
    private final Random random = new Random();

    public SheepocalypseEvent(RandomEvents plugin) {
        this.plugin = plugin;
        this.rewardGenerator = plugin.getRewardGenerator();
        this.entityManager = plugin.getEntityManager();
        this.activeSheep = new HashSet<>();
        this.isEventActive = false;
        this.isSpawningComplete = false;
        
        // Register events immediately and log it
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Configure event settings
        setTickInterval(80L); // Spawn sheep every 4 seconds
        int durationSeconds = plugin.getConfigManager().getIntValue(getName(), "duration");
        setDuration(durationSeconds * 20L); // Convert seconds to ticks
        setStripsInventory(false);
        setCanBreakBlocks(true);
        setCanPlaceBlocks(true);
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
    public void onStart(UUID sessionId, Set<Player> players) {
        if (isEventActive) {
            onEnd(sessionId, players); // Use onEnd instead of stopEvent
        }

        isEventActive = true;
        isSpawningComplete = false;
        activeSheep.clear();
        givenShears.clear();



        for (Player player : players) {
            ItemStack shears = new ItemStack(Material.SHEARS);
            ItemMeta meta = shears.getItemMeta();
            meta.displayName(Component.text("Sheep Defuser", NamedTextColor.AQUA).decorate(TextDecoration.ITALIC));
            meta.lore(List.of(Component.text("Right click on sheep bombs to defuse them", NamedTextColor.GRAY)));
            shears.setItemMeta(meta);
            
            // Give shears using equipment manager
            plugin.getEquipmentManager().giveEquipment(player, shears, "sheep_defuser", sessionId);
            givenShears.put(player.getUniqueId(), shears);
            
            player.sendMessage(Component.text("Oh no! ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                .append(Component.text("The sheep have gone mad! Quick, shear them before they explode!", NamedTextColor.YELLOW)));
        }
    }

    @Override
    public void onTick(UUID sessionId, Set<Player> players) {
        if (players.isEmpty()) {
            return;
        }

        // Filter out offline/dead players
        Set<Player> activePlayers = players.stream()
            .filter(p -> p.isOnline() && !p.isDead())
            .collect(Collectors.toSet());

        if (activePlayers.isEmpty()) {
            return;
        }

        // Recalculate groups each tick to handle player movement
        Set<Set<Player>> currentGroups = LocationHelper.groupPlayers(activePlayers, GROUP_RADIUS);

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
                    spawnSheep(spawnLoc, group.iterator().next().getUniqueId()); // Use first player in group as owner
                }
            }
        }
    }

    @Override
    public void onEnd(UUID sessionId, Set<Player> players) {
        isSpawningComplete = true;

        // Clean up any remaining sheep
        for (SheepBomb sheepBomb : new HashSet<>(activeSheep)) {
            sheepBomb.remove();
        }
        activeSheep.clear();

        // Clean up shears and give final rewards
        for (Player player : players) {
            if (player.isOnline()) {
                removeShears(player);
                giveEndRewards(player);
            }
        }
        givenShears.clear();

        // Re-register event listeners to ensure clean state
        HandlerList.unregisterAll(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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

    private void spawnSheep(Location spawnLoc, UUID ownerUUID) {
        World world = spawnLoc.getWorld();
        if (world == null) return;

        // Use EntityManager to spawn and track the sheep
        Sheep sheep = entityManager.spawnTracked(org.bukkit.entity.EntityType.SHEEP, spawnLoc, "explosive_sheep", ownerUUID);
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
        plugin.getLogger().info("[DEBUG] SheepocalypseEvent: Shear event fired, isEventActive=" + isEventActive);
        
        if (!isEventActive) {
            plugin.getLogger().info("[DEBUG] SheepocalypseEvent: Event not active, ignoring");
            return;
        }
        
        if (!(event.getEntity() instanceof Sheep)) {
            plugin.getLogger().info("[DEBUG] SheepocalypseEvent: Not a sheep, ignoring");
            return;
        }

        event.setCancelled(true); // Cancel vanilla wool drops
        
        Sheep sheep = (Sheep) event.getEntity();
        SheepBomb sheepBomb = null;

        plugin.getLogger().info("[DEBUG] Sheepocalypse: Shearing attempt on sheep " + sheep.getUniqueId());

        for (SheepBomb bomb : activeSheep) {
            if (bomb.isSameSheep(sheep)) {
                sheepBomb = bomb;
                break;
            }
        }

        if (sheepBomb != null) {
            Player player = event.getPlayer();
            plugin.getLogger().info("[DEBUG] Sheepocalypse: Found matching SheepBomb, starting shear sequence");
            
            // Cancel any pending explosion tasks immediately
            sheepBomb.cancelExplosion();
            
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
            
            // Start the shearing effect sequence
            plugin.getLogger().info("[DEBUG] Sheepocalypse: Starting shear and poof effect");
            sheepBomb.playShearAndPoofEffect();
            
            // Remove from active sheep AFTER effects start
            activeSheep.remove(sheepBomb);
        } else {
            plugin.getLogger().warning("[DEBUG] Sheepocalypse: No matching SheepBomb found for sheep " + sheep.getUniqueId());
        }
    }

    @EventHandler
    public void onSheepDeath(EntityDeathEvent event) {
        if (!isSpawningComplete || !(event.getEntity() instanceof Sheep)) return;
        
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
}