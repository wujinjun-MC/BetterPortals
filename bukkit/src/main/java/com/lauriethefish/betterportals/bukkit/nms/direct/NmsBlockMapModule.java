package com.lauriethefish.betterportals.bukkit.nms.direct;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.lauriethefish.betterportals.bukkit.block.IMultiBlockChangeManager;
import com.lauriethefish.betterportals.bukkit.block.IBlockMap;

public class NmsBlockMapModule extends AbstractModule  {
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
    }
}
