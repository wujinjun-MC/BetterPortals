package com.lauriethefish.betterportals.bukkit.nms.direct;

import com.lauriethefish.betterportals.api.IntVector;
import com.lauriethefish.betterportals.api.PortalPosition;
import com.lauriethefish.betterportals.bukkit.config.RenderConfig;
import com.lauriethefish.betterportals.shared.logging.Logger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;

import java.util.Objects;

public class PortalChunkAccess {
    private LevelChunkSection[] sections = null;

    private final int minX;
    private final int minY;
    private final int minZ;

    private final int maxX;
    private final int maxY;
    private final int maxZ;

    private final int yMultip;
    private final int zMultip;
    private final int totalLength;

    private final ServerLevel level;

    public PortalChunkAccess(PortalPosition portalPosition, RenderConfig renderConfig, Logger logger) {
        level = ((CraftWorld) Objects.requireNonNull(portalPosition.getWorld())).getHandle();

        IntVector minPos = portalPosition.getIntVector().subtract(renderConfig.getHalfFullSize());
        IntVector maxPos = portalPosition.getIntVector().add(renderConfig.getHalfFullSize());

        minX = minPos.getX() >> 4;
        minY = minPos.getY() >> 4;
        minZ = minPos.getZ() >> 4;

        maxX = maxPos.getX() >> 4;
        maxY = maxPos.getY() >> 4;
        maxZ = maxPos.getZ() >> 4;

        int xSize = (maxX - minX) + 1;
        int ySize = (maxY - minY) + 1;
        int zSize = (maxZ - minZ) + 1;
        logger.fine("Chunk map sizes, X: %d, Y: %d, Z: %d", xSize, ySize, zSize);

        yMultip = xSize;
        zMultip = xSize * ySize;
        totalLength = xSize * ySize * zSize;
    }

    public final BlockState getBlock(int x, int y, int z) {
        int sectX = (x >> 4) - minX;
        int sectY = (y >> 4) - minY;
        int sectZ = (z >> 4) - minZ;
        int relX = x & 0xF;
        int relY = y & 0xF;
        int relZ = z & 0xF;

        int idx = sectX + sectY * yMultip + sectZ * zMultip;
        LevelChunkSection section = sections[idx];

        return section == null ? Blocks.AIR.defaultBlockState() : section.getBlockState(relX, relY, relZ);
    }

    public void prepare() {
        if(sections != null) {
            return;
        }

        sections = new LevelChunkSection[totalLength];
        int i = 0;
        for(int z = minZ; z <= maxZ; z++) {
            for(int y = minY; y <= maxY; y++) {
                for(int x = minX; x <= maxX; x++) {
                    // This may be null if a section is empty
                    sections[i] = level.getChunk(x, z).getSections()[y];
                    i++;
                }
            }
        }
    }

    public void clear() {
        sections = null;
    }
}
