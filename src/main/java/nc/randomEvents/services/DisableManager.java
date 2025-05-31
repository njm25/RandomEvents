package nc.randomEvents.services;

import nc.randomEvents.RandomEvents;

public class DisableManager {
    private final SessionRegistry sessionRegistry;

    public DisableManager(RandomEvents plugin) {
        this.sessionRegistry = plugin.getSessionRegistry();
    }
    
    public void disablePlugin() {
        sessionRegistry.endAll();
    }
    
}
