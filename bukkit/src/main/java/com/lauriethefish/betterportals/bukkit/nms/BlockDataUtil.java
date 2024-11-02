package com.lauriethefish.betterportals.bukkit.nms;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.lauriethefish.betterportals.api.IntVector;
import com.lauriethefish.betterportals.shared.util.ReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public class BlockDataUtil {
    private static final Method GET_HANDLE;
    private static final Method GET_COMBINED_ID;
    private static final Method GET_FROM_COMBINED_ID;
    private static final Method FROM_HANDLE;
    private static final Method GET_TILE_ENTITY;
    private static final Method GET_UPDATE_PACKET;

    static {
        Class<?> nmsBlock = ReflectionUtil.findClass("net.minecraft.world.level.block.Block");
        Class<?> craftBlockData = CraftBukkitClassUtil.findCraftBukkitClass("block.data.CraftBlockData");
        Class<?> nmsBlockData = ReflectionUtil.findClass("net.minecraft.world.level.block.state.IBlockData");

        // Reflection to get necessary methods
        GET_HANDLE = ReflectionUtil.findMethod(craftBlockData, "getState");
        GET_COMBINED_ID = ReflectionUtil.findMethod(nmsBlock, "getCombinedId", nmsBlockData);
        GET_FROM_COMBINED_ID = ReflectionUtil.findMethod(nmsBlock, "getByCombinedId", int.class);
        FROM_HANDLE = ReflectionUtil.findMethod(craftBlockData, "fromData", nmsBlockData);

        Class<?> blockEntityState = CraftBukkitClassUtil.findCraftBukkitClass("block.CraftBlockEntityState");
        Class<?> nmsTileEntity = ReflectionUtil.findClass("net.minecraft.world.level.block.entity.TileEntity");
        GET_TILE_ENTITY = ReflectionUtil.findMethod(blockEntityState, "getTileEntity");
        GET_UPDATE_PACKET = ReflectionUtil.findMethod(nmsTileEntity, "getUpdatePacket");
    }

    /**
     * Converts <code>blockData</code> into a combined ID that stores all info about the block.
     * @param blockData The data to convert
     * @return The combined ID of the data
     */
    public static int getCombinedId(@NotNull BlockData blockData) {
        Object nmsData = ReflectionUtil.invokeMethod(blockData, GET_HANDLE);
        return (int) ReflectionUtil.invokeMethod(null, GET_COMBINED_ID, nmsData);
    }

    /**
     * Converts <code>combinedId</code> back into a {@link BlockData}.
     * @param combinedId The ID to convert
     * @return The Bukkit block data
     */
    public static BlockData getByCombinedId(int combinedId) {
        Object nmsData = ReflectionUtil.invokeMethod(null, GET_FROM_COMBINED_ID, combinedId);
        return (BlockData) ReflectionUtil.invokeMethod(null, FROM_HANDLE, nmsData);
    }

    /**
     * Finds the ProtocolLib wrapper around the <code>PacketPlayOutTileEntityData</code> which updates the tile entity data for <code>tileState</code>.
     * @param tileState The tile entity to get the packet of
     * @return The ProtocolLib wrapper, or null if not applicable
     */
    public static @Nullable PacketContainer getUpdatePacket(@NotNull BlockState tileState) {
        Object nmsTileEntity = ReflectionUtil.invokeMethod(tileState, GET_TILE_ENTITY);
        Object unwrappedPacket = ReflectionUtil.invokeMethod(nmsTileEntity, GET_UPDATE_PACKET);
        return unwrappedPacket != null ? PacketContainer.fromPacket(unwrappedPacket) : null;
    }

    /**
     * Sets the position of a <code>PacketPlayOutTileEntityData</code> in the packet itself.
     * @param packet The packet to modify the position of
     * @param position The new position
     */
    public static void setTileEntityPosition(@NotNull PacketContainer packet, @NotNull IntVector position) {
        BlockPosition blockPosition = new BlockPosition(position.getX(), position.getY(), position.getZ());
        packet.getBlockPositionModifier().write(0, blockPosition);
    }
}
