package nc.randomEvents.core;

import org.bukkit.event.Listener;

import nc.randomEvents.RandomEvents;

public interface ServiceListener extends Listener {
    public void registerListener(RandomEvents plugin);
}
