package nc.randomEvents.events.Meteor;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.services.RewardGenerator.Tier;
import nc.randomEvents.services.RewardGenerator.TierQuantity;
import nc.randomEvents.utils.LocationHelper;
import nc.randomEvents.utils.MetadataHelper;
import nc.randomEvents.utils.SoundHelper;
import nc.randomEvents.services.EntityManager;
import nc.randomEvents.utils.PersistentDataHelper;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.*;

public class MeteorEvent extends BaseEvent implements Listener {
    private final RandomEvents plugin;
    private final RewardGenerator rewardGenerator;
    private final Random random = new Random();
    private static final String METEOR_METADATA_KEY = "meteor_event_fireball";
    private static final String METEOR_SESSION_KEY = "meteor_session";
    private static final String ENTITY_KEY = "entity";
    private static final int GROUP_RADIUS = 100; // Radius for grouping players
    private final Map<UUID, Set<Set<Player>>> sessionGroups = new HashMap<>();
    private UUID currentSessionId;

    public MeteorEvent(RandomEvents plugin) {
        this.plugin = plugin;
        this.rewardGenerator = plugin.getRewardGenerator();
        
        // Configure base event settings
        setCanBreakBlocks(false);
        setCanPlaceBlocks(false);
        setStripsInventory(false);
        setTickInterval(10L);
        setDuration(200L);
        setClearEntitiesAtEnd(false);
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public String getName() {
        return "MeteorEvent";
    }

    @Override
    public String getDescription() {
        return "Rains fireballs from the sky that may drop rewards upon impact!";
    }

    @Override
    public void onStart(UUID sessionId, Set<Player> players) {
        this.currentSessionId = sessionId;
        Set<Set<Player>> playerGroups = LocationHelper.groupPlayers(players, GROUP_RADIUS);
        sessionGroups.put(sessionId, playerGroups);

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

        for (Set<Player> group : groups) {
            Location groupMidpoint = LocationHelper.findMidpoint(group);
            if (groupMidpoint == null) continue;

            int meteorsPerPlayer = plugin.getConfigManager().getConfigValue(getName(), "amountPerPlayer");
            int meteorsThisTick = Math.max(1, (meteorsPerPlayer * group.size()) / 20);

            for (int i = 0; i < meteorsThisTick; i++) {
                if (random.nextDouble() < 0.7) {
                    spawnMeteorAtLocation(groupMidpoint);
                }
            }
        }
    }

    @Override
    public void onEnd(UUID sessionId, Set<Player> players) {
        sessionGroups.remove(sessionId);
        if (sessionId.equals(currentSessionId)) {
            this.currentSessionId = null;
        }
    }

    private void spawnMeteorMob(Location location, Player target, UUID sessionId) {
        if (sessionId == null) {
            plugin.getLogger().warning("Attempted to spawn meteor mob with null sessionId");
            return;
        }

        double enemySpawnChance = plugin.getConfigManager().getConfigValue(getName(), "enemySpawnChance");
        enemySpawnChance = Math.max(0.0, Math.min(1.0, enemySpawnChance));
        
        EntityManager entityManager = plugin.getEntityManager();
        LivingEntity entity = null;
        
        if (random.nextDouble() < enemySpawnChance) {
            entity = entityManager.spawnTracked(EntityType.BLAZE, location, "meteor_blaze", sessionId);
            
            if (entity instanceof Blaze) {
                //entity.setAI(false);
                //entity.setGravity(false);
                if (target != null) {
                    ((Monster) entity).setTarget(target);
                }
            }
        }    // If Blaze didn't spawn, try Magma Cube
        else if (random.nextDouble() < enemySpawnChance) {
            entity = entityManager.spawnTracked(EntityType.MAGMA_CUBE, location, "meteor_magma", sessionId);
            if (entity instanceof MagmaCube) {
                //((MagmaCube) entity).setSize(2);
                // No need to set target for Magma Cubes - they'll naturally chase nearby players
            }
        }
    }

    private void spawnMeteorAtLocation(Location targetLoc) {
        if (currentSessionId == null) {
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

        Fireball fireball = world.spawn(meteorSpawnLoc, Fireball.class);
        Vector direction = new Vector((random.nextDouble() - 0.5) * 0.2, -1.0, (random.nextDouble() - 0.5) * 0.2);
        fireball.setDirection(direction.normalize());
        fireball.setYield(0.0F);
        fireball.setIsIncendiary(false);
        fireball.setShooter(null);
        
        // Store both the meteor flag and the session ID
        MetadataHelper.setMetadata(fireball, METEOR_METADATA_KEY, true, plugin);
        PersistentDataHelper.set(fireball.getPersistentDataContainer(), plugin, METEOR_SESSION_KEY, 
                                PersistentDataType.STRING, currentSessionId.toString());
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

        //plugin.getLogger().info("Meteor hit detected for session: " + sessionId);

        // Check if it hit a block
        if (event.getHitBlock() != null) {
            Location impactLocation = event.getHitBlock().getLocation().add(0.5, 1, 0.5);
            //plugin.getLogger().info("Meteor hit block at " + impactLocation);

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
            
            // plugin.getLogger().info("Rolling for loot with chance: " + lootChance);
            if (random.nextDouble() < lootChance) {
                List<ItemStack> rewards = rewardGenerator.generateRewards(
                    new TierQuantity()
                        .add(Tier.COMMON, random.nextInt(2) + 1)
                        .add(Tier.BASIC, random.nextInt(2) + 1)
                        .build()
                );
                
                if (!rewards.isEmpty()) {
                    //plugin.getLogger().info("Dropping " + rewards.size() + " reward items");
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
