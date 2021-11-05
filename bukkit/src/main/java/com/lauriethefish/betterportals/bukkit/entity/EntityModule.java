package com.lauriethefish.betterportals.bukkit.entity;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.lauriethefish.betterportals.bukkit.entity.faking.*;
import com.lauriethefish.betterportals.bukkit.nms.NMSEntityTracker;

public class EntityModule extends AbstractModule {
    @Override
    public void configure() {
        /*
        install(new FactoryModuleBuilder()
                .implement(IEntityTracker.class, EntityTracker.class)
                .build(IEntityTracker.Factory.class)
        );

        bind(IEntityPacketManipulator.class).to(EntityPacketManipulator.class);
        bind(EntityTrackingManager.class).to(EventEntityTrackingManager.class);*/

        // TODO: Enable this only when on correct version with a manual override in the config
        install(new FactoryModuleBuilder()
                .implement(IEntityTracker.class, NMSEntityTracker.class)
                .build(IEntityTracker.Factory.class)
        );

        bind(IEntityPacketManipulator.class).to(EntityPacketManipulator.class);
        bind(EntityTrackingManager.class).to(NoUpdateEntityTrackingManager.class);

    }
}
