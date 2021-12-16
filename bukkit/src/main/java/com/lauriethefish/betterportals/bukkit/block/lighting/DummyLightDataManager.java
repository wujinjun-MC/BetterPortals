package com.lauriethefish.betterportals.bukkit.block.lighting;

import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.lauriethefish.betterportals.bukkit.portal.IPortal;
import org.jetbrains.annotations.Nullable;

import javax.inject.Singleton;

/**
 * Used on versions where light blocks are not available
 */
@Singleton
public class DummyLightDataManager implements ILightDataManager {
    @Override
    public @Nullable WrappedBlockData getLightData(IPortal portal) {
        return null;
    }
}
