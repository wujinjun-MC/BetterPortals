package com.lauriethefish.betterportals.bukkit.block.lighting;

import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.lauriethefish.betterportals.bukkit.portal.IPortal;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.type.Light;
import org.jetbrains.annotations.Nullable;

import javax.inject.Singleton;

@Singleton
public class LightDataManger implements ILightDataManager   {
    @Override
    public @Nullable WrappedBlockData getLightData(IPortal portal) {
        // TODO: Automatically determine light level depending on dimension, Y level, surface light, etc.
        Light lightBlockData = (Light) Bukkit.createBlockData(Material.LIGHT);
        lightBlockData.setLevel(10);
        return WrappedBlockData.createData(lightBlockData);
    }
}
