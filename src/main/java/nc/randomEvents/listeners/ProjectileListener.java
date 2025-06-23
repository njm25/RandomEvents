package nc.randomEvents.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Projectile;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.ServiceListener;
import nc.randomEvents.utils.PersistentDataHelper;

public class ProjectileListener implements ServiceListener {

    private final RandomEvents plugin;
    private static final String PROJECTILE_KEY = "event_projectile";
    private static final String PROJECTILE_DAMAGE_KEY = "projectile_damage";
    
    public ProjectileListener(RandomEvents plugin) {
        this.plugin = plugin;
    }

    @Override
    public void registerListener(RandomEvents plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        
        // Check if it's one of our tracked projectiles
        if (damager instanceof Projectile && 
            PersistentDataHelper.has(damager.getPersistentDataContainer(), plugin, PROJECTILE_KEY, PersistentDataType.BYTE)) {
            
            // Check for custom damage
            Double customDamage = PersistentDataHelper.get(damager.getPersistentDataContainer(), plugin, 
                                                         PROJECTILE_DAMAGE_KEY, PersistentDataType.DOUBLE);
            if (customDamage != null) {
                event.setDamage(customDamage);
            }
        }
    }
    
}
