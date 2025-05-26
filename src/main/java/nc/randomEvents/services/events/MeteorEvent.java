package nc.randomEvents.services.events;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.services.RewardGenerator.Tier;
import nc.randomEvents.services.RewardGenerator.TierQuantity;

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

public class MeteorEvent implements Event, Listener {
    private final RandomEvents plugin;
    private final RewardGenerator rewardGenerator;
    private final Random random = new Random();
    private static final String METEOR_METADATA_KEY = "meteor_event_fireball";

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
    public void execute(List<Player> players) {
        for (Player player : players) {
            player.sendMessage(Component.text("Oh no! ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                .append(Component.text("Look up! Meteors are incoming!", NamedTextColor.YELLOW)));

            new BukkitRunnable() {
                int meteorsToSpawn = random.nextInt(10) + 15; // Increased from 7-12 to 15-24 meteors per player
                int meteorsSpawned = 0;

                @Override
                public void run() {
                    if (meteorsSpawned >= meteorsToSpawn || !player.isOnline() || !player.isValid()) {
                        this.cancel();
                        return;
                    }
                    spawnMeteorForPlayer(player);
                    meteorsSpawned++;
                }
            }.runTaskTimer(plugin, 0L, random.nextInt(20) + 10L); // Spawn one every 0.5 - 1.5 seconds (10-29 ticks)
        }
    }

    private void spawnMeteorForPlayer(Player player) {
        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        
        double offsetX = (random.nextDouble() - 0.5) * 40;
        double offsetZ = (random.nextDouble() - 0.5) * 40;
        double spawnY = Math.min(world.getMaxHeight() - 5, playerLoc.getY() + 60 + random.nextInt(40));

        Location meteorSpawnLoc = new Location(world, playerLoc.getX() + offsetX, spawnY, playerLoc.getZ() + offsetZ);

        Fireball fireball = world.spawn(meteorSpawnLoc, Fireball.class);
        Vector direction = new Vector((random.nextDouble() - 0.5) * 0.2, -1.0, (random.nextDouble() - 0.5) * 0.2);
        fireball.setDirection(direction.normalize());
        fireball.setYield(0.0F);           // Small explosion (affects ~1-2 blocks)
        fireball.setIsIncendiary(false);   // Prevent fire
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

            double rewardDropChance = 0.40; // 40% chance to drop rewards
            if (random.nextDouble() < rewardDropChance) {
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
                    impactLocation.getWorld().playSound(impactLocation, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                }
            }
        }
        // The fireball will explode on its own based on its properties (yield, incendiary)
    }
}
