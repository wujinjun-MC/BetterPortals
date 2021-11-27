package com.lauriethefish.betterportals.bukkit.entity;

import com.lauriethefish.betterportals.bukkit.portal.IPortal;
import org.bukkit.entity.Entity;

import java.util.Collection;

/**
 * Finds the entities within the view distance of the portal, as specified in the config.
 */
public interface IPortalEntityManager {
    Collection<Entity> getOriginEntities();
    Collection<Entity> getDestinationEntities();

    /**
     * Updates the current entities around the portal
     * @param ticksSinceActivated Ticks since the parent {@link com.lauriethefish.betterportals.bukkit.portal.Portal} was activated, since this can be configured to not happen every tick.
     */
    void update(int ticksSinceActivated);

    interface Factory {
        IPortalEntityManager create(IPortal portal, boolean requireDestination);
    }
}
