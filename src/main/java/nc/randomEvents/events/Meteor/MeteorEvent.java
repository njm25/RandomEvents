package nc.randomEvents.events.Meteor;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.services.RewardGenerator.Tier;
import nc.randomEvents.services.RewardGenerator.TierQuantity;
import nc.randomEvents.utils.LocationHelper;
import nc.randomEvents.utils.MetadataHelper;
import nc.randomEvents.utils.SoundHelper;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class MeteorEvent extends BaseEvent implements Listener {
    private final RandomEvents plugin;
    private final RewardGenerator rewardGenerator;
    private final Random random = new Random();
    private static final String METEOR_METADATA_KEY = "meteor_event_fireball";
    private static final int GROUP_RADIUS = 100; // Radius for grouping players
    private final Map<UUID, Set<Set<Player>>> sessionGroups = new HashMap<>();

    public MeteorEvent(RandomEvents plugin) {
        this.plugin = plugin;
        this.rewardGenerator = plugin.getRewardGenerator();
        
        // Configure base event settings
        setCanBreakBlocks(false); // Prevent meteor impacts from breaking blocks
        setCanPlaceBlocks(false); // No block placement needed
        setStripsInventory(false); // Don't strip player inventories
        setTickInterval(10L); // Tick every half second
        setDuration(200L); // 10 second event duration
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
        // Group players within 100 blocks of each other
        Set<Set<Player>> playerGroups = LocationHelper.groupPlayers(players, GROUP_RADIUS);
        sessionGroups.put(sessionId, playerGroups);

        for (Set<Player> group : playerGroups) {
            // Notify all players in the group
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
            // Find the midpoint of this group
            Location groupMidpoint = LocationHelper.findMidpoint(group);
            if (groupMidpoint == null) continue;

            // Calculate meteors per tick based on group size
            int meteorsPerPlayer = plugin.getConfigManager().getConfigValue(getName(), "amountPerPlayer");
            int meteorsThisTick = Math.max(1, (meteorsPerPlayer * group.size()) / 20); // Spread over duration

            // Spawn meteors for this group
            for (int i = 0; i < meteorsThisTick; i++) {
                if (random.nextDouble() < 0.7) { // 70% chance per tick to spawn meteor
                    spawnMeteorAtLocation(groupMidpoint);
                }
            }
        }
    }

    @Override
    public void onEnd(UUID sessionId, Set<Player> players) {
        sessionGroups.remove(sessionId);
    }

    private void spawnMeteorAtLocation(Location targetLoc) {
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
        MetadataHelper.setMetadata(fireball, METEOR_METADATA_KEY, true, plugin);
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

        // Remove metadata to prevent processing this fireball again if event somehow re-fires.
        MetadataHelper.removeMetadata(fireball, METEOR_METADATA_KEY, plugin);

        // Check if it hit a block (not an entity)
        if (event.getHitBlock() != null) {
            Location impactLocation = event.getHitBlock().getLocation().add(0.5, 1, 0.5); // Center on top of block

            double lootChance = plugin.getConfigManager().getConfigValue(getName(), "lootChance");
            // Ensure lootChance is between 0 and 1
            lootChance = Math.max(0.0, Math.min(1.0, lootChance));
            
            if (random.nextDouble() < lootChance) {
                int numberOfItemStacks = random.nextInt(2) + 1; // 1 or 2 different item types
                int numberOfItemStacks1 = random.nextInt(2) + 1; // 1 or 2 different item types
                List<ItemStack> rewards = rewardGenerator.generateRewards(
                    new TierQuantity()
                        .add(Tier.COMMON, numberOfItemStacks)
                        .add(Tier.BASIC, numberOfItemStacks1)
                        .build()
                );
                
                if (!rewards.isEmpty()) {
                    for (ItemStack itemStack : rewards) {
                        fireball.getWorld().dropItemNaturally(impactLocation, itemStack);
                    }
                    impactLocation.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, impactLocation, 20, 0.5, 0.5, 0.5, 0.1);
                    SoundHelper.playWorldSoundSafely(impactLocation.getWorld(), "entity.player.levelup", impactLocation, 1.0f, 1.5f);
                }
            }
        }
    }
}
