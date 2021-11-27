package com.lauriethefish.betterportals.bukkit.nms.direct;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.lauriethefish.betterportals.bukkit.block.IMultiBlockChangeManager;
import com.lauriethefish.betterportals.bukkit.block.IBlockMap;
import com.lauriethefish.betterportals.bukkit.entity.IEntityFinder;
import com.lauriethefish.betterportals.bukkit.entity.faking.IEntityTracker;

/**
 * NOTE: This class is referred to by reflection - check for reflective references before renaming
 */
public class NmsOptimisationModule extends AbstractModule  {
    @Override
    protected void configure() {
        install(new FactoryModuleBuilder()
                .implement(IMultiBlockChangeManager.class, MultiBlockChangeManager_NMS.class)
                .build(IMultiBlockChangeManager.Factory.class)
        );

        install(new FactoryModuleBuilder()
            .implement(IBlockMap.class, NmsBlockMap.class)
            .build(IBlockMap.Factory.class)
        );

        install(new FactoryModuleBuilder()
            .implement(IEntityTracker.class, NMSEntityTracker.class)
            .build(IEntityTracker.Factory.class)
        );

        bind(IEntityFinder.class).to(NmsEntityFinder.class);
    }
}
