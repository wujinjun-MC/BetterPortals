package com.lauriethefish.betterportals.bukkit.nms.direct;

import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.lauriethefish.betterportals.api.IntVector;
import com.lauriethefish.betterportals.bukkit.block.FloodFillBlockMap;
import com.lauriethefish.betterportals.bukkit.block.IViewableBlockInfo;
import com.lauriethefish.betterportals.bukkit.config.RenderConfig;
import com.lauriethefish.betterportals.bukkit.portal.IPortal;
import com.lauriethefish.betterportals.shared.logging.Logger;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * After lots of testing, a flood-fill appears to be the most efficient way to find the blocks around the destination that aren't obscured.
 * <br>If you want, you can try to optimise more, but I'm not sure how to make this much better with the requirements it has.
 */
public class NmsBlockMap extends FloodFillBlockMap {
    private final PortalChunkAccess originAccess;
    private final PortalChunkAccess destAccess;

    @Inject
    public NmsBlockMap(@Assisted IPortal portal, Logger logger, RenderConfig renderConfig) {
        super(portal, logger, renderConfig);

        this.originAccess = new PortalChunkAccess(portal.getOriginPos(), renderConfig, logger);
        this.destAccess = new PortalChunkAccess(portal.getDestPos(), renderConfig, logger);
    }

    @Override
    protected void searchFromBlock(IntVector start, List<IViewableBlockInfo> statesOutput, IViewableBlockInfo firstBlockInfo) {
        WrappedBlockData backgroundData = getBackgroundData();

        int[] stack = new int[firstUpdate ? renderConfig.getTotalArrayLength() : 16];

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

            BlockState originData = originAccess.getBlock(originPos.getX(), originPos.getY(), originPos.getZ());
            BlockState destData = destAccess.getBlock(destPos.getX(), destPos.getY(), destPos.getZ());
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

            NmsBlockInfo blockInfo = firstBlockInfo == null ? new NmsBlockInfo(originPos, originData, destData) : (NmsBlockInfo) firstBlockInfo;
            boolean isEdge = renderConfig.isOutsideBounds(relX, relY, relZ);
            if(isEdge && !isOccluding) {
                blockInfo.setRenderedDestData((BlockState) backgroundData.getHandle());
            }   else    {
                // TODO: Reimplement rotation
                blockInfo.setRenderedDestData(destData);
            }

            // If the newly added block was not added previously (i.e. passed in via firstBlockInfo), then we need to add it to the non-obscured states
            // This list is used for incremental updates later
            if(firstBlockInfo == null) {
                nonObscuredStates.add(blockInfo);
            }
            firstBlockInfo = null;

            // If we're not on an edge block, and the origin and destination block are the exact same, then we can skip this block
            // This is because rendering it will do nothing - it's the same at both ends
            boolean canSkip = destData.equals(originData) && firstUpdate && !isEdge;

            boolean isInLine = isInLine(destRelPos);

            // Don't bother adding blocks which are in line with the portal window, as these will never be visible anyway, and just serve to made the block map larger
            // We also only add this block to the viewable states if it doesn't already exist there (this is the case if the alreadyReachedMap's value is 1)
            // The above check is only important on incremental updates, since on the first update the block must never have been added to the block map - it's being checked for the first time
            if (!isInLine && !canSkip && alreadyReachedMap[positionInt] < 2) {
                alreadyReachedMap[positionInt] = 2; // Make sure that this block will not be added multiple times
                statesOutput.add(blockInfo);
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
                if(alreadyReachedMap[newPos] == 0) {
                    alreadyReachedMap[newPos] = 1;

                    stackPos += 1;
                    stack[stackPos] = newPos;
                }
            }
        }
    }

    @Override
    protected void checkForChanges() {
        List<IViewableBlockInfo> newStates = new ArrayList<>();

        int statesLength = nonObscuredStates.size();
        for(int i = 0; i < statesLength; i++) { // We do not use foreach to avoid concurrent modifications
            NmsBlockInfo blockInfo = (NmsBlockInfo) nonObscuredStates.get(i);
            IntVector destPos = rotateOriginToDest.transform(blockInfo.getOriginPos().subtract(portalOriginPos)).add(portalDestPos); // Avoid directly using the matrix to fix floating point precision issues
            BlockState newDestData = destAccess.getBlock(destPos.getX(), destPos.getY(), destPos.getZ());

            if(newDestData != blockInfo.getBaseDestData()) {
                blockInfo.setBaseDestData(newDestData);

                logger.finer("Destination block change");
                searchFromBlock(blockInfo.getOriginPos(), newStates, blockInfo);
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
            IntVector originPos = blockInfo.getOriginPos();
            BlockState newOriginData = originAccess.getBlock(originPos.getX(), originPos.getY(), originPos.getZ());
            /*
            if(MaterialUtil.isTileEntity(originBlock.getType()))  {
                logger.finer("Adding tile state to map . . .");
                PacketContainer updatePacket = BlockDataUtil.getUpdatePacket(originBlock.getState());
                if(updatePacket != null) {
                    originTileStates.put(entry.getKey(), updatePacket);
                }
            }*/

            if(newOriginData != blockInfo.getBaseOriginData()) {
                // If the new origin data is different to the new dest data, then we might need to add this block to the viewable states
                // This is because it will not have been added previously if the origin and destination data are the same
                if(!newOriginData.equals(newDestData) && !portal.getOriginPos().isInLine(blockInfo.getOriginPos())) {
                    int position = getArrayMapIndex(blockInfo.getOriginPos().subtract(portalOriginPos));
                    // Only add this block as viewable if it is not already designated as viewable
                    if(alreadyReachedMap[position] < 2) {
                        alreadyReachedMap[position] = 2;
                        newStates.add(blockInfo);
                    }
                }

                blockInfo.setOriginData(newOriginData);
            }
        }

        /*
        updateTileStateMap(originTileStates, originWorld, false);
        if(!portal.isCrossServer()) {
            updateTileStateMap(destTileStates, portal.getDestPos().getWorld(), true);
        }*/

        if(newStates.size() > 0) {
            stateQueue.enqueueStates(newStates);
        }
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

    @Override
    public void updateInternal() {
        originAccess.prepare();
        destAccess.prepare();
        super.updateInternal();
    }

    @Override
    public void reset() {
        super.reset();

        if(originAccess != null) {
            originAccess.clear();
        }
        if(destAccess != null) {
            destAccess.clear();
        }
    }
}
