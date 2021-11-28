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

    private int minX;
    private int minY;
    private int minZ;

    private int yMultip;
    private int zMultip;

    private final PortalPosition portalPosition;
    private final Logger logger;
    private final RenderConfig renderConfig;

    private final ServerLevel level;

    public PortalChunkAccess(PortalPosition portalPosition, RenderConfig renderConfig, Logger logger) {
        this.portalPosition = portalPosition;
        this.logger = logger;
        this.renderConfig = renderConfig;

        level = ((CraftWorld) Objects.requireNonNull(portalPosition.getWorld())).getHandle();
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

        IntVector minPos = portalPosition.getIntVector().subtract(renderConfig.getHalfFullSize());
        IntVector maxPos = portalPosition.getIntVector().add(renderConfig.getHalfFullSize());
        minX = minPos.getX() >> 4;
        minY = minPos.getY() >> 4;
        minZ = minPos.getZ() >> 4;

        int maxX = maxPos.getX() >> 4;
        int maxY = maxPos.getY() >> 4;
        int maxZ = maxPos.getZ() >> 4;

        int xSize = (maxX - minX) + 1;
        int ySize = (maxY - minY) + 1;
        int zSize = (maxZ - minZ) + 1;
        logger.fine("Chunk map sizes, X: %d, Y: %d, Z: %d", xSize, ySize, zSize);

        yMultip = xSize;
        zMultip = xSize * ySize;
        int totalLength = xSize * ySize * zSize;

        sections = new LevelChunkSection[totalLength];
        int i = 0;
        for(int z = minZ; z <= maxZ; z++) {
            for(int y = minY; y <= maxY; y++) {
                for(int x = minX; x <= maxX; x++) {
                    if(y > 15 || y < 0) {
                        sections[i] = null;
                    }   else    {
                        // This may be null if a section is empty
                        sections[i] = level.getChunk(x, z).getSections()[y];
                    }
                    i++;
                }
            }
        }
    }

    public void clear() {
        sections = null;
    }
}
