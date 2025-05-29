package nc.randomEvents.services.events.Meteor;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.services.RewardGenerator.Tier;
import nc.randomEvents.services.RewardGenerator.TierQuantity;
import nc.randomEvents.services.events.Event;
import nc.randomEvents.utils.LocationUtils;
import nc.randomEvents.utils.SoundHelper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;
import java.util.Random;
import java.util.Set;

public class MeteorEvent implements Event, Listener {
    private final RandomEvents plugin;
    private final RewardGenerator rewardGenerator;
    private final Random random = new Random();
    private static final String METEOR_METADATA_KEY = "meteor_event_fireball";
    private static final int GROUP_RADIUS = 100; // Radius for grouping players

    public MeteorEvent(RandomEvents plugin) {
        this.plugin = plugin;
        this.rewardGenerator = plugin.getRewardGenerator();
        Bukkit.getPluginManager().registerEvents(this, plugin);
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
    public void execute(Set<Player> players) {
        // Group players within 100 blocks of each other
        Set<Set<Player>> playerGroups = LocationUtils.groupPlayers(players, GROUP_RADIUS);

        for (Set<Player> group : playerGroups) {
            // Find the midpoint of this group
            Location groupMidpoint = LocationUtils.findMidpoint(group);
            if (groupMidpoint == null) continue;

            // Calculate total meteors based on group size
            int meteorsPerPlayer = plugin.getConfigManager().getConfigValue(getName(), "amountPerPlayer");
            final int totalMeteorsForGroup = meteorsPerPlayer * group.size();

            // Notify all players in the group
            for (Player player : group) {
                player.sendMessage(Component.text("Oh no! ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                    .append(Component.text("Look up! A meteor shower is incoming!", NamedTextColor.YELLOW)));
            }

            // Spawn meteors for this group
            new BukkitRunnable() {
                int meteorsToSpawn = totalMeteorsForGroup;
                int meteorsSpawned = 0;

                @Override
                public void run() {
                    // Check if any players from the group are still online and valid
                    boolean validGroupExists = group.stream().anyMatch(p -> p.isOnline() && p.isValid());
                    
                    if (meteorsSpawned >= meteorsToSpawn || !validGroupExists) {
                        this.cancel();
                        return;
                    }
                    spawnMeteorAtLocation(groupMidpoint);
                    meteorsSpawned++;
                }
            }.runTaskTimer(plugin, 0L, random.nextInt(20) + 10L);
        }
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
        fireball.setMetadata(METEOR_METADATA_KEY, new FixedMetadataValue(plugin, true));
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball)) {
            return;
        }
        Fireball fireball = (Fireball) event.getEntity();
        if (!fireball.hasMetadata(METEOR_METADATA_KEY)) {
            return;
        }

        // Remove metadata to prevent processing this fireball again if event somehow re-fires.
        fireball.removeMetadata(METEOR_METADATA_KEY, plugin);

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
        // The fireball will explode on its own based on its properties (yield, incendiary)
    }
}
