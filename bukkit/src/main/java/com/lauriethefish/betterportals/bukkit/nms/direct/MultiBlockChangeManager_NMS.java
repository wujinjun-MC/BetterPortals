package com.lauriethefish.betterportals.bukkit.nms.direct;

import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.lauriethefish.betterportals.bukkit.block.IViewableBlockInfo;
import com.lauriethefish.betterportals.bukkit.block.IMultiBlockChangeManager;
import com.lauriethefish.betterportals.shared.util.ReflectionUtil;
import it.unimi.dsi.fastutil.shorts.ShortArraySet;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.bukkit.craftbukkit.v1_18_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_18_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class MultiBlockChangeManager_NMS implements IMultiBlockChangeManager {
    private static final Field BLOCK_STATES_FIELD = ReflectionUtil.findFieldByType(ClientboundSectionBlocksUpdatePacket.class, BlockState[].class);
    private static final Field POSITIONS_FIELD = ReflectionUtil.findFieldByType(ClientboundSectionBlocksUpdatePacket.class, short[].class);

    private final Player player;
    private final HashMap<SectionPos, Map<Vector, BlockState>> changes = new HashMap<>();

    @Inject
    public MultiBlockChangeManager_NMS(@Assisted Player player) {
        this.player = player;
    }

    @Override
    public void addChangeOrigin(Vector position, IViewableBlockInfo newData) {
        addChange(position, ((NmsBlockInfo) newData).getBaseOriginData());
    }

    @Override
    public void addChangeDestination(Vector position, IViewableBlockInfo newData) {
        addChange(position, ((NmsBlockInfo) newData).getRenderedDestData());
    }

    public void addChange(Vector position, WrappedBlockData wrappedData) {
        addChange(position, (BlockState) wrappedData.getHandle());
    }

    protected void addChange(Vector position, BlockState newData) {
        SectionPos sectionPosition = SectionPos.of(
                position.getBlockX() >> 4,
                position.getBlockY() >> 4,
                position.getBlockZ() >> 4
        );

        // Create/get the list for this chunk section
        Map<Vector, BlockState> existingList = changes.computeIfAbsent(sectionPosition, k -> new HashMap<>());
        existingList.put(position, newData);
    }

    private short getShortLocation(Vector vec) {
        int x = vec.getBlockX() & 0xF;
        int y = vec.getBlockY() & 0xF;
        int z = vec.getBlockZ() & 0xF;

        return (short) (x << 8 | z << 4 | y);
    }

    @Override
    public void sendChanges() {
        ServerLevel playerLevel = ((CraftWorld) player.getWorld()).getHandle();
        ServerPlayerConnection playerConnection = ((CraftPlayer) player).getHandle().connection;

        // Each chunk position needs a different packet
        for(Map.Entry<SectionPos, Map<Vector, BlockState>> entry : changes.entrySet()) {
            SectionPos chunkPos = entry.getKey();
            if(chunkPos.getY() < 0 || chunkPos.getY() > 15) {
                continue;
            }
            LevelChunkSection chunkSection = playerLevel.getChunk(chunkPos.getX(), chunkPos.getZ()).getSections()[chunkPos.getY()];

            // First create a dummy packet
            ClientboundSectionBlocksUpdatePacket packet = new ClientboundSectionBlocksUpdatePacket(
                    SectionPos.of(chunkPos.getX(), chunkPos.getY(), chunkPos.getZ()),
                    new ShortArraySet(),
                    chunkSection,
                    false
            );

            // Add each changed block in the chunk
            int blockCount = entry.getValue().size();
            BlockState[] data = new BlockState[blockCount];
            short[] positions = new short[blockCount];
            int i = 0;
            for(Map.Entry<Vector, BlockState> blockEntry : entry.getValue().entrySet()) {
                positions[i] = getShortLocation(blockEntry.getKey());
                data[i] = blockEntry.getValue();
                i++;
            }

            // Set our data in the private fields
            ReflectionUtil.setField(packet, BLOCK_STATES_FIELD, data);
            ReflectionUtil.setField(packet, POSITIONS_FIELD, positions);

            // Now our packet is ready to send
            playerConnection.send(packet);
        }
    }
}
