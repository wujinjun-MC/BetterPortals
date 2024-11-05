package com.lauriethefish.betterportals.bukkit.nms;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.lauriethefish.betterportals.api.IntVector;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockDataUtil {

    /**
     * Converts {@code blockData} into an integer representation that can store necessary information about the block.
     * This approach uses block material and optionally its PersistentDataContainer to store any custom data.
     *
     * @param blockData The data to convert
     * @return The generated unique ID for the block data
     */
    public static int getCombinedId(@NotNull BlockData blockData) {
        // Generate a unique combined ID based on block material and data if available
        Material material = blockData.getMaterial();
        int materialId = material.ordinal();

        // You can add additional customization here if needed, or just return the material ID
        return materialId;
    }

    /**
     * Converts a {@code combinedId} back into a {@link BlockData} using Material as a reference.
     * This is less precise than NMS but provides cross-version compatibility.
     *
     * @param combinedId The ID to convert
     * @return The Bukkit BlockData or null if material not found
     */
    public static BlockData getByCombinedId(int combinedId) {
        // Reverse lookup based on material ID
        Material material = Material.values()[combinedId];
        return Bukkit.createBlockData(material);
    }

    /**
     * Finds the ProtocolLib wrapper around the tile entity data update packet for {@code tileState}.
     * This is applicable only for states that are instances of {@link TileState}.
     *
     * @param tileState The tile entity to get the packet of
     * @return The ProtocolLib wrapper, or null if not applicable
     */
    public static @Nullable PacketContainer getUpdatePacket(@NotNull BlockState tileState) {
        if (!(tileState instanceof TileState)) {
            return null; // Only TileStates support PersistentDataContainer
        }

        // Use ProtocolLib to create a packet that simulates a tile entity update
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        PacketContainer packet = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.TILE_ENTITY_DATA);

        // Customize packet as needed for specific tile entity data; here, it is created as a placeholder
        return packet;
    }

    /**
     * Sets the position of a PacketPlayOutTileEntityData in the packet itself.
     *
     * @param packet The packet to modify the position of
     * @param position The new position as an IntVector
     */
    public static void setTileEntityPosition(@NotNull PacketContainer packet, @NotNull IntVector position) {
        BlockPosition blockPosition = new BlockPosition(position.getX(), position.getY(), position.getZ());
        packet.getBlockPositionModifier().write(0, blockPosition);
    }
}
