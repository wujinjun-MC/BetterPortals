package com.lauriethefish.betterportals.bukkit.block.bukkit;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.lauriethefish.betterportals.bukkit.block.IMultiBlockChangeManager;
import com.lauriethefish.betterportals.bukkit.block.IBlockMap;
import com.lauriethefish.betterportals.bukkit.util.VersionUtil;

public class BukkitBlockMapModule extends AbstractModule {

    protected void configure() {
        install(new FactoryModuleBuilder()
                .implement(IBlockMap.class, BukkitBlockMap.class)
                .build(IBlockMap.Factory.class)
        );

        install(new FactoryModuleBuilder()
            .implement(IMultiBlockChangeManager.class, VersionUtil.isMcVersionAtLeast("1.16.2") ? MultiBlockChangeManager_1_16_2.class : MultiBlockChangeManager_Old.class)
            .build(IMultiBlockChangeManager.Factory.class)
        );
    }
}
