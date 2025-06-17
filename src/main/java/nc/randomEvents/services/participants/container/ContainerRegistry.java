package nc.randomEvents.services.participants.container;

import org.bukkit.Location;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ContainerRegistry {
    private final Map<Location, ContainerData> containers;

    public ContainerRegistry() {
        this.containers = new ConcurrentHashMap<>();
    }

    public void registerContainer(Location location, ContainerData data) {
        containers.put(location, data);
    }

    public void unregisterContainer(Location location) {
        containers.remove(location);
    }

    public ContainerData getContainer(Location location) {
        return containers.get(location);
    }

    public boolean hasContainer(Location location) {
        return containers.containsKey(location);
    }

    public Map<Location, ContainerData> getAllContainers() {
        return containers;
    }

    public void clear() {
        containers.clear();
    }
} 