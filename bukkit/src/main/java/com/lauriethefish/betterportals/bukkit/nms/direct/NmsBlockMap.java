package com.lauriethefish.betterportals.bukkit.nms.direct;

import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.lauriethefish.betterportals.api.IntVector;
import com.lauriethefish.betterportals.bukkit.block.FloodFillBlockMap;
import com.lauriethefish.betterportals.bukkit.config.RenderConfig;
import com.lauriethefish.betterportals.bukkit.portal.IPortal;
import com.lauriethefish.betterportals.shared.logging.Logger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;

/**
 * After lots of testing, a flood-fill appears to be the most efficient way to find the blocks around the destination that aren't obscured.
 * <br>If you want, you can try to optimise more, but I'm not sure how to make this much better with the requirements it has.
 */
public class NmsBlockMap extends FloodFillBlockMap {
    private final ServerLevel originLevel;
    private final ServerLevel destinationLevel;

    @Inject
    public NmsBlockMap(@Assisted IPortal portal, Logger logger, RenderConfig renderConfig) {
        super(portal, logger, renderConfig);

        this.originLevel = getLevel(portal.getOriginPos().getWorld());
        this.destinationLevel = getLevel(portal.getDestPos().getWorld());
    }

    private ServerLevel getLevel(World bukkitWorld) {
        return ((CraftWorld) bukkitWorld).getHandle();
    }

    private BlockState getNmsBlockState(ServerLevel world, IntVector pos) {
        int sectX = pos.getX() >> 4;
        int sectY = pos.getY() >> 4;
        int sectZ = pos.getZ() >> 4;
        int relX = pos.getX() & 0xF;
        int relY = pos.getY() & 0xF;
        int relZ = pos.getZ() & 0xF;

        if(sectY < 0 || sectY > 15) {
            return null;
        }

        LevelChunkSection chunkSection =  world.getChunk(sectX, sectZ).getSections()[sectY];
        return chunkSection == null ? Blocks.AIR.defaultBlockState() : chunkSection.getBlockState(relX, relY, relZ);
    }

    @Override
    protected void searchFromBlock(IntVector start) {
        WrappedBlockData backgroundData = getBackgroundData();

        int[] stack = new int[firstUpdate ? renderConfig.getTotalArrayLength() : 16];

        logger.fine("Starting at %s", start.subtract(centerPos));
        stack[0] = getArrayMapIndex(start.subtract(centerPos));
        int stackPos = 0;
        while(stackPos >= 0) {
            int positionInt = stack[stackPos];
            stackPos--;

            int relX = (positionInt % renderConfig.getZMultip());
            int relY = Math.floorDiv(positionInt, renderConfig.getYMultip());
            int relZ = Math.floorDiv(positionInt - relY * renderConfig.getYMultip(), renderConfig.getZMultip());

            relX -= renderConfig.getMaxXZ();
            relY -= renderConfig.getMaxY();
            relZ -= renderConfig.getMaxXZ();

            IntVector originPos = new IntVector(relX + portalOriginPos.getX(), relY + portalOriginPos.getY(), relZ + portalOriginPos.getZ());
            IntVector destRelPos =  rotateOriginToDest.transform(relX, relY, relZ);
            IntVector destPos = destRelPos.add(portalDestPos);

            BlockState originData = getNmsBlockState(originLevel, originPos);
            BlockState destData = getNmsBlockState(destinationLevel, destPos);
            if(originData == null || destData == null) {
                // We are outside the world
                continue;
            }

            boolean isOccluding = destData.getMaterial().isSolidBlocking();

            /*
            TODO: Reimplement
            if(!portal.isCrossServer() && MaterialUtil.isTileEntity(destData.getMaterial())) {
                logger.finer("Adding tile state to map . . .");
                Block destBlock = destPos.getBlock(portal.getDestPos().getWorld());

                PacketContainer updatePacket = BlockDataUtil.getUpdatePacket(destBlock.getState());
                if(updatePacket != null) {
                    BlockDataUtil.setTileEntityPosition(updatePacket, originPos);

                    destTileStates.put(originPos, updatePacket);
                }
            }

            if(MaterialUtil.isTileEntity(originBlock.getType()))  {
                logger.finer("Adding tile state to map . . .");
                PacketContainer updatePacket = BlockDataUtil.getUpdatePacket(originBlock.getState());
                if(updatePacket != null) {
                    originTileStates.put(originPos, updatePacket);
                }
            }*/

            NmsBlockInfo blockInfo = new NmsBlockInfo(originPos, originData, destData);
            boolean isEdge = renderConfig.isOutsideBounds(relX, relY, relZ);
            if(isEdge && !isOccluding) {
                blockInfo.setRenderedDestData((BlockState) backgroundData.getHandle());
            }   else    {
                // TODO: Reimplement rotation
                blockInfo.setRenderedDestData(destData);
            }
            nonObscuredStates.add(blockInfo);

            boolean canSkip = destData.equals(originData) && firstUpdate && !isEdge;
            boolean isInLine = isInLine(destRelPos);
            if (!isInLine && !canSkip) {
                queuedViewableStates.add(blockInfo);
            }

            // Stop when we reach the edge or an occluding block, since we don't want to show blocks outside the view area
            if (isOccluding || isEdge) {continue;}

            // Continue for any surrounding blocks that haven't been checked yet

            // Resize the stack if there's a possibility there won't be enough room to fit our new items
            if(!firstUpdate && (stack.length - (stackPos + 1) < 5)) {
                int[] newStack = new int[stack.length * 2];
                System.arraycopy(stack, 0, newStack, 0, stack.length);
                stack = newStack;
            }

            for(int offset : renderConfig.getIntOffsets()) {
                int newPos = positionInt + offset;
                if(!alreadyReachedMap[newPos]) {
                    alreadyReachedMap[newPos] = true;

                    stackPos += 1;
                    stack[stackPos] = newPos;
                }
            }
        }
    }

    @Override
    protected void checkForChanges() {
        int statesLength = nonObscuredStates.size();
        for(int i = 0; i < statesLength; i++) { // We do not use foreach to avoid concurrent modifications
            NmsBlockInfo blockInfo = (NmsBlockInfo) nonObscuredStates.get(i);
            IntVector destPos = rotateOriginToDest.transform(blockInfo.getOriginPos().subtract(portalOriginPos)).add(portalDestPos); // Avoid directly using the matrix to fix floating point precision issues
            BlockState newDestData = getNmsBlockState(destinationLevel, destPos);

            if(newDestData != blockInfo.getBaseDestData()) {
                logger.finer("Destination block change");
                queuedViewableStatesLock.lock();
                try {
                    searchFromBlock(blockInfo.getOriginPos());
                }   finally {
                    queuedViewableStatesLock.unlock();
                }
            }

            /*
            if(!portal.isCrossServer()) {
                if (MaterialUtil.isTileEntity(newDestData.getMaterial())) {
                    logger.finer("Adding tile state to map . . .");
                    Block destBlock = destPos.getBlock(portal.getDestPos().getWorld());

                    PacketContainer updatePacket = BlockDataUtil.getUpdatePacket(destBlock.getState());
                    if(updatePacket != null) {
                        BlockDataUtil.setTileEntityPosition(updatePacket, entry.getKey());

                        destTileStates.put(entry.getKey(), updatePacket);
                    }
                }
            }*/

            //Block originBlock = entry.getKey().getBlock(originWorld);
            BlockState newOriginData = getNmsBlockState(originLevel, blockInfo.getOriginPos());
            /*
            if(MaterialUtil.isTileEntity(originBlock.getType()))  {
                logger.finer("Adding tile state to map . . .");
                PacketContainer updatePacket = BlockDataUtil.getUpdatePacket(originBlock.getState());
                if(updatePacket != null) {
                    originTileStates.put(entry.getKey(), updatePacket);
                }
            }*/

            if(newOriginData != blockInfo.getBaseOriginData()) {
                logger.finer("Origin block change");
                blockInfo.setOriginData(newOriginData);
                if(!newOriginData.equals(newDestData) && !portal.getOriginPos().isInLine(blockInfo.getOriginPos())) {
                    queuedViewableStates.add(blockInfo);
                }
            }
        }

        /*
        updateTileStateMap(originTileStates, originWorld, false);
        if(!portal.isCrossServer()) {
            updateTileStateMap(destTileStates, portal.getDestPos().getWorld(), true);
        }*/
    }

    /*
    private void updateTileStateMap(ConcurrentMap<IntVector, PacketContainer> map, World world, boolean isDestination) {
        for(Map.Entry<IntVector, PacketContainer> entry : map.entrySet()) {
            IntVector position;
            if(isDestination) {
                IntVector portalRelativePos = entry.getKey().subtract(portalOriginPos);
                position = rotateOriginToDest.transform(portalRelativePos).add(portalDestPos);
            }   else    {
                position = entry.getKey();
            }

            Block block = position.getBlock(world);
            BlockState state = block.getState();
            if(!MaterialUtil.isTileEntity(state.getType())) {
                logger.finer("Removing tile state from map . . . %b", isDestination);
                map.remove(entry.getKey());
            }
        }
    }*/
}
