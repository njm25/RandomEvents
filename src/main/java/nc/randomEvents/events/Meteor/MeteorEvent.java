package nc.randomEvents.events.Meteor;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.services.SessionRegistry;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.services.RewardGenerator.Tier;
import nc.randomEvents.services.RewardGenerator.TierQuantity;
import nc.randomEvents.services.participants.EntityManager;
import nc.randomEvents.services.participants.ProjectileManager;
import nc.randomEvents.utils.LocationHelper;
import nc.randomEvents.utils.MetadataHelper;
import nc.randomEvents.utils.SoundHelper;
import nc.randomEvents.utils.PersistentDataHelper;
import nc.randomEvents.utils.EntityHelper;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class MeteorEvent extends BaseEvent implements Listener {
    private final RandomEvents plugin;
    private final RewardGenerator rewardGenerator;
    private final ProjectileManager projectileManager;
    private final SessionRegistry sessionRegistry;
    private final Random random = new Random();
    private static final String METEOR_METADATA_KEY = "meteor_event_fireball";
    private static final String METEOR_SESSION_KEY = "meteor_session";
    private static final String ENTITY_KEY = "entity";
    private static final int GROUP_RADIUS = 100; // Radius for grouping players
    private final Map<UUID, Set<Set<Player>>> sessionGroups = new HashMap<>();
    private final Map<UUID, Integer> totalMeteorsSpawned = new HashMap<>();
    private final Map<UUID, Integer> meteorsHit = new HashMap<>();
    private UUID currentSessionId;
    private int spawnedEnemyCount = 0; // Track total spawned enemies

    public MeteorEvent(RandomEvents plugin) {
        this.plugin = plugin;
        this.rewardGenerator = plugin.getRewardGenerator();
        this.projectileManager = plugin.getProjectileManager();
        this.sessionRegistry = plugin.getSessionRegistry();
        setTickInterval(10L);
        setDuration(0);
        setClearEntitiesAtEnd(false);
        setClearProjectilesAtEnd(true);
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public String getName() {
        return "MeteorEvent";
    }

    @Override
    public String getDescription() {
        return "Rains fireballs from the sky that may drop rewards and/or enemies upon impact!";
    }

    @Override
    public void onStart(UUID sessionId, Set<Player> players) {
        this.currentSessionId = sessionId;
        this.spawnedEnemyCount = 0; // Reset counter on event start
        Set<Set<Player>> playerGroups = LocationHelper.groupPlayers(players, GROUP_RADIUS);
        sessionGroups.put(sessionId, playerGroups);
        totalMeteorsSpawned.put(sessionId, 0);
        meteorsHit.put(sessionId, 0);

        for (Set<Player> group : playerGroups) {
            for (Player player : group) {
                player.sendMessage(Component.text("Oh no! ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                    .append(Component.text("Look up! A meteor shower is incoming!", NamedTextColor.YELLOW)));
            }
        }
    }

    @Override
    public void onTick(UUID sessionId, Set<Player> players) {
        Set<Set<Player>> groups = sessionGroups.get(sessionId);
        if (groups == null) return;

        // Get total meteors that should be spawned for this session
        int totalPlayers = players.size();
        int meteorsPerPlayer = plugin.getConfigManager().getConfigValue(getName(), "amountPerPlayer");
        int totalMeteorsForSession = totalPlayers * meteorsPerPlayer;

        // If we've already spawned all meteors, don't spawn more
        Integer spawned = totalMeteorsSpawned.get(sessionId);
        if (spawned != null && spawned >= totalMeteorsForSession) {
            return;
        }

        for (Set<Player> group : groups) {
            Location groupMidpoint = LocationHelper.findMidpoint(group);
            if (groupMidpoint == null) continue;

            // Calculate how many meteors to spawn this tick
            int meteorsThisTick = Math.max(1, (meteorsPerPlayer * group.size()) / 20);
            
            // Adjust meteorsThisTick to not exceed the total remaining
            int remainingMeteors = totalMeteorsForSession - spawned;
            meteorsThisTick = Math.min(meteorsThisTick, remainingMeteors);

            for (int i = 0; i < meteorsThisTick; i++) {
                if (random.nextDouble() < 0.7) {
                    spawnMeteorAtLocation(groupMidpoint, sessionId);
                }
            }
        }
    }

    @Override
    public void onEnd(UUID sessionId, Set<Player> players) {
        sessionGroups.remove(sessionId);
        totalMeteorsSpawned.remove(sessionId);
        meteorsHit.remove(sessionId);
        if (sessionId.equals(currentSessionId)) {
            this.currentSessionId = null;
        }
    }

    private void spawnMeteorMob(Location location, Player target, UUID sessionId) {
        if (sessionId == null) {
            plugin.getLogger().warning("Attempted to spawn meteor mob with null sessionId");
            return;
        }

        // Check if we've reached the spawn limit
        int maxEnemySpawns = plugin.getConfigManager().getConfigValue(getName(), "maxEnemySpawns");
        if (spawnedEnemyCount >= maxEnemySpawns) {
            return; // Skip enemy spawn chance roll entirely
        }

        double enemySpawnChance = plugin.getConfigManager().getConfigValue(getName(), "enemySpawnChance");
        enemySpawnChance = Math.max(0.0, Math.min(1.0, enemySpawnChance));
        
        EntityManager entityManager = plugin.getEntityManager();
        LivingEntity entity = null;
        
        if (random.nextDouble() < enemySpawnChance) {
            entity = entityManager.spawnTracked(EntityType.BLAZE, location, "meteor_blaze", sessionId);
            
            if (entity instanceof Blaze) {
                spawnedEnemyCount++; // Increment counter on successful spawn
                Blaze blaze = (Blaze) entity;
                // Play blaze hurt sound with increased volume
                SoundHelper.playWorldSoundSafely(location.getWorld(), "entity.blaze.hurt", location, 2.0f, 1.0f);
                //EntityHelper.setMovementSpeed(blaze, 0.0); // Make it completely stationary
                //blaze.setGravity(false); // Prevent up/down movement
                if (target != null) {
                    ((Monster) entity).setTarget(target);
                }
                
                // Start the turret shooting task
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!blaze.isValid() || blaze.isDead()) {
                            this.cancel();
                            return;
                        }
                        
                        // Only shoot if we have a target and they're within range
                        LivingEntity target = blaze.getTarget();
                        if (target != null && target.getLocation().distance(blaze.getLocation()) <= 25) { // Increased range to 25 blocks
                            Location blazeLoc = blaze.getLocation().add(0, 0.5, 0);
                            Vector direction = target.getLocation().add(0, 0.5, 0).subtract(blazeLoc).toVector().normalize();
                            
                            // Spawn tracked fireball with half damage
                            projectileManager.spawnTracked(
                                SmallFireball.class,
                                blazeLoc,
                                direction,
                                blaze,
                                sessionId,
                                2.5, // Half of normal Blaze fireball damage (5)
                                1.5  // Speed multiplier
                            );
                            SoundHelper.playWorldSoundSafely(blazeLoc.getWorld(), "entity.blaze.shoot", blazeLoc, 1.0f, 1.0f);
                        }
                    }
                }.runTaskTimer(plugin, 20L, 30L); // Shoot every second (20 ticks)
            }
        } else if (random.nextDouble() < enemySpawnChance) {
            entity = entityManager.spawnTracked(EntityType.MAGMA_CUBE, location, "meteor_magma", sessionId);
            if (entity instanceof MagmaCube) {
                spawnedEnemyCount++; // Increment counter on successful spawn
                MagmaCube magma = (MagmaCube) entity;
                // Play magma cube squish sound with increased volume
                SoundHelper.playWorldSoundSafely(location.getWorld(), "entity.magma_cube.squish", location, 2.0f, 1.0f);
                magma.setSize(6);
                magma.setHealth(12);
                EntityHelper.setMovementSpeed(magma, 0.23 * 4.0); // 4x normal speed (0.23 is base speed)
                // Set the target to the nearest player
                if (target != null) {
                    magma.setTarget(target);
                }
                
                // Start the jumping attack task
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!magma.isValid() || magma.isDead()) {
                            this.cancel();
                            return;
                        }
                        
                        // 50% chance to jump at nearest player
                        if (random.nextDouble() < 0.5) {
                            LivingEntity target = magma.getTarget();
                            if (target != null && target.getLocation().distance(magma.getLocation()) <= 16) {
                                Location magmaLoc = magma.getLocation();
                                Vector direction = target.getLocation().add(0, 0.5, 0)
                                    .subtract(magmaLoc)
                                    .toVector()
                                    .normalize()
                                    .multiply(1.2); // Add some upward momentum for a more arc-like jump
                                direction.setY(0.5);
                                
                                EntityHelper.launchEntity(magma, direction, 1.5);
                                SoundHelper.playWorldSoundSafely(magmaLoc.getWorld(), "entity.magma_cube.squish", magmaLoc,1.0f, 1.0f);
                            }
                        }
                    }
                }.runTaskTimer(plugin, 40L, 40L); // Check for jump every 2 seconds
            }
        }
    }

    private void spawnMeteorAtLocation(Location targetLoc, UUID sessionId) {
        if (sessionId == null) {
            plugin.getLogger().warning("Attempted to spawn meteor with null sessionId");
            return;
        }

        World world = targetLoc.getWorld();
        if (world == null) return;
        
        int radius = plugin.getConfigManager().getConfigValue(getName(), "radius");
        double offsetX = (random.nextDouble() - 0.5) * radius;
        double offsetZ = (random.nextDouble() - 0.5) * radius;
        double spawnY = 256;

        Location meteorSpawnLoc = new Location(world, targetLoc.getX() + offsetX, spawnY, targetLoc.getZ() + offsetZ);
        Vector direction = new Vector((random.nextDouble() - 0.5) * 0.2, -1.0, (random.nextDouble() - 0.5) * 0.2);

        // Use ProjectileManager to spawn the meteor
        Fireball fireball = projectileManager.spawnTracked(
            Fireball.class,
            meteorSpawnLoc,
            direction,
            null, // No shooter
            sessionId,
            0.0, // No damage (we handle this in the hit event)
            1.0  // Normal speed
        );

        if (fireball != null) {
            fireball.setYield(0.0F);
            fireball.setIsIncendiary(false);
            
            // Store both the meteor flag and the session ID
            MetadataHelper.setMetadata(fireball, METEOR_METADATA_KEY, true, plugin);
            PersistentDataHelper.set(fireball.getPersistentDataContainer(), plugin, METEOR_SESSION_KEY, 
                                    PersistentDataType.STRING, sessionId.toString());
            
            // Increment total meteors spawned
            totalMeteorsSpawned.merge(sessionId, 1, Integer::sum);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball)) {
            return;
        }
        
        Fireball fireball = (Fireball) event.getEntity();
        if (!MetadataHelper.hasMetadata(fireball, METEOR_METADATA_KEY)) {
            return;
        }

        // Get the session ID from the meteor
        String sessionIdStr = PersistentDataHelper.get(fireball.getPersistentDataContainer(), 
                                                     plugin, METEOR_SESSION_KEY, PersistentDataType.STRING);
        if (sessionIdStr == null) {
            plugin.getLogger().warning("Meteor hit but had no session ID!");
            return;
        }

        UUID sessionId;
        try {
            sessionId = UUID.fromString(sessionIdStr);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid session ID on meteor: " + sessionIdStr);
            return;
        }

        // Remove metadata
        MetadataHelper.removeMetadata(fireball, METEOR_METADATA_KEY, plugin);
        PersistentDataHelper.remove(fireball.getPersistentDataContainer(), plugin, METEOR_SESSION_KEY);

        // Increment meteors hit
        meteorsHit.merge(sessionId, 1, Integer::sum);

        // Check if all meteors have hit
        Integer totalSpawned = totalMeteorsSpawned.get(sessionId);
        Integer totalHit = meteorsHit.get(sessionId);
        if (totalSpawned != null && totalHit != null && totalSpawned.equals(totalHit)) {
            // All meteors have hit, end the session
            sessionRegistry.endSession(sessionId);
            return;
        }

        // Check if it hit a block
        if (event.getHitBlock() != null) {
            Location impactLocation = event.getHitBlock().getLocation().add(0.5, 1, 0.5);

            // Find nearest player to target
            Player nearestPlayer = null;
            double nearestDistance = Double.MAX_VALUE;
            for (Player player : impactLocation.getWorld().getPlayers()) {
                double distance = player.getLocation().distance(impactLocation);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPlayer = player;
                }
            }

            // Handle loot drops
            double lootChance = plugin.getConfigManager().getConfigValue(getName(), "lootChance");
            lootChance = Math.max(0.0, Math.min(1.0, lootChance));
            
            if (random.nextDouble() < lootChance) {
                List<ItemStack> rewards = rewardGenerator.generateRewards(
                    new TierQuantity()
                        .add(Tier.COMMON, random.nextInt(2) + 1)
                        .add(Tier.BASIC, random.nextInt(2) + 1)
                        .build()
                );
                
                if (!rewards.isEmpty()) {
                    for (ItemStack itemStack : rewards) {
                        impactLocation.getWorld().dropItemNaturally(impactLocation, itemStack);
                    }
                    impactLocation.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, impactLocation, 20, 0.5, 0.5, 0.5, 0.1);
                    SoundHelper.playWorldSoundSafely(impactLocation.getWorld(), "entity.player.levelup", impactLocation, 1.0f, 1.5f);
                    
                    // Only attempt to spawn mobs when loot drops
                    if (nearestPlayer != null) {
                        spawnMeteorMob(impactLocation, nearestPlayer, sessionId);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onSlimeSplit(SlimeSplitEvent event) {
        // Check if it's one of our tracked magma cubes
        if (event.getEntity() instanceof MagmaCube && 
            PersistentDataHelper.has(event.getEntity().getPersistentDataContainer(), plugin, ENTITY_KEY, PersistentDataType.BYTE)) {
            // Prevent splitting
            event.setCancelled(true);
        }
    }
}
