package com.lauriethefish.betterportals.bukkit.block.bukkit;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.lauriethefish.betterportals.bukkit.block.IMultiBlockChangeManager;
import com.lauriethefish.betterportals.bukkit.block.IViewableBlockArray;
import com.lauriethefish.betterportals.bukkit.util.VersionUtil;

public class BukkitBlockMapModule extends AbstractModule {

    protected void configure() {
        install(new FactoryModuleBuilder()
                .implement(IViewableBlockArray.class, BukkitViewableBlockArray.class)
                .build(IViewableBlockArray.Factory.class)
        );

        install(new FactoryModuleBuilder()
            .implement(IMultiBlockChangeManager.class, VersionUtil.isMcVersionAtLeast("1.16.2") ? MultiBlockChangeManager_1_16_2.class : MultiBlockChangeManager_Old.class)
            .build(IMultiBlockChangeManager.Factory.class)
        );
    }
}
