package com.lauriethefish.betterportals.bukkit.block.bukkit;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.lauriethefish.betterportals.api.IntVector;
import com.lauriethefish.betterportals.bukkit.block.FloodFillBlockMap;
import com.lauriethefish.betterportals.bukkit.block.IViewableBlockInfo;
import com.lauriethefish.betterportals.bukkit.block.fetch.BlockDataFetcherFactory;
import com.lauriethefish.betterportals.bukkit.block.fetch.IBlockDataFetcher;
import com.lauriethefish.betterportals.bukkit.block.lighting.ILightDataManager;
import com.lauriethefish.betterportals.bukkit.block.rotation.IBlockRotator;
import com.lauriethefish.betterportals.bukkit.config.RenderConfig;
import com.lauriethefish.betterportals.bukkit.math.Matrix;
import com.lauriethefish.betterportals.bukkit.nms.BlockDataUtil;
import com.lauriethefish.betterportals.bukkit.portal.IPortal;
import com.lauriethefish.betterportals.bukkit.util.MaterialUtil;
import com.lauriethefish.betterportals.shared.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

/**
 * A bukkit implementation of a flood fill block map.
 */
public class BukkitBlockMap extends FloodFillBlockMap {
    private final IBlockRotator blockRotator;
    private final BlockDataFetcherFactory dataFetcherFactory;
    private final Matrix rotateDestToOrigin;
    private IBlockDataFetcher dataFetcher;

    private final World originWorld;
    private final ILightDataManager lightDataManager;

    private WrappedBlockData wrappedLightData;

    @Inject
    public BukkitBlockMap(@Assisted IPortal portal, Logger logger, RenderConfig renderConfig, IBlockRotator blockRotator, BlockDataFetcherFactory dataFetcherFactory, ILightDataManager lightDataManager) {
        super(portal, logger, renderConfig);
        this.blockRotator = blockRotator;
        this.dataFetcherFactory = dataFetcherFactory;
        this.rotateDestToOrigin = portal.getTransformations().getRotateToOrigin();
        this.lightDataManager = lightDataManager;

        this.originWorld = portal.getOriginPos().getWorld();
        logger.fine("Origin pos: %s, Dest pos: %s", portalOriginPos, portalDestPos);
        logger.fine("Origin direction: %s, Dest Direction: %s", portal.getOriginPos().getDirection(), portal.getDestPos().getDirection());
    }

    /**
     * Starts a flood fill from <code>start</code> out to the edges of the viewed portal area.
     * The fill stops when it reaches occluding blocks, as we don't need to render other blocks behind these.
     * The origin data is also fetched, and this is placed in <code>statesOutput</code>
     * <br>Some notes:
     * - There used to be a check to see if the origin and destination states are the same, but this added too much complexity when checking for changes, so I decided to remove it.
     * - That unfortunately reduces the performance of the threaded bit slightly, but I think it's worth it for the gains here.
     * @param start Start position of the flood fill, at the origin
     * @param statesOutput List to place the newly viewable states in
     * @param firstBlockInfo Optional, used instead of adding a new block to the map for the first block. Useful for incremental updates
     */
    protected void searchFromBlock(IntVector start, List<IViewableBlockInfo> statesOutput, @Nullable IViewableBlockInfo firstBlockInfo) {
        WrappedBlockData backgroundData = getBackgroundData();

        final int timeBetweenLightBlocks = renderConfig.getLightSimulationInterval();

        if(wrappedLightData == null) {
            wrappedLightData = lightDataManager.getLightData(portal);
        }

        boolean enableLightBlocks = wrappedLightData != null && timeBetweenLightBlocks >= 1;
        int airCount = 0;

        // We don't use a Stack<T> or ArrayList<T> since those are much too slow
        int[] stack = new int[firstUpdate ? renderConfig.getTotalArrayLength() : 16];

        stack[0] = getArrayMapIndex(start.subtract(centerPos));
        int stackPos = 0;
        while(stackPos >= 0) {
            int positionInt = stack[stackPos];
            stackPos--;

            // Convert our position integer into the origin relative coordinates
            int relX = (positionInt % renderConfig.getZMultip());
            int relY = Math.floorDiv(positionInt, renderConfig.getYMultip());
            int relZ = Math.floorDiv(positionInt - relY * renderConfig.getYMultip(), renderConfig.getZMultip());

            relX -= renderConfig.getMaxXZ();
            relY -= renderConfig.getMaxY();
            relZ -= renderConfig.getMaxXZ();

            IntVector originPos = new IntVector(relX + portalOriginPos.getX(), relY + portalOriginPos.getY(), relZ + portalOriginPos.getZ());
            IntVector destRelPos =  rotateOriginToDest.transform(relX, relY, relZ);
            IntVector destPos = destRelPos.add(portalDestPos);

            BlockData destData = dataFetcher.getData(destPos);
            boolean isOccluding = destData.getMaterial().isOccluding();

            Block originBlock = originPos.getBlock(originWorld);
            BlockData originData = originBlock.getBlockData();

            if(!portal.isCrossServer() && MaterialUtil.isTileEntity(destData.getMaterial())) {
                logger.finer("Adding tile state to map . . .");
                Block destBlock = destPos.getBlock(Objects.requireNonNull(portal.getDestPos().getWorld()));

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
            }

            // Use the existing first block if given, otherwise make a new block info
            BukkitBlockInfo blockInfo = firstBlockInfo == null ? new BukkitBlockInfo(originPos, originData, destData) : (BukkitBlockInfo) firstBlockInfo;
            boolean isEdge = renderConfig.isOutsideBounds(relX, relY, relZ);

            // If we're on a block on the edge of the portal view, and it is not a fully occluding material, then we must set it to the portal background
            // This avoids the real-world being visible through the edge of the projection
            if(isEdge && !isOccluding) {
                blockInfo.setRenderedDestData(backgroundData);
            }   else    {
                blockInfo.setRenderedDestData(WrappedBlockData.createData(blockRotator.rotateByMatrix(rotateDestToOrigin, destData)));
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

            Material currentBlock = destData.getMaterial();

            if (alreadyReachedMap[positionInt] < 2 && !isInLine) {
                if(enableLightBlocks) {
                    if (currentBlock.isAir() && !isEdge) {
                        airCount++;
                    }
                }

                if (enableLightBlocks && airCount == timeBetweenLightBlocks) {
                    airCount = 0;
                    blockInfo.setRenderedDestData(wrappedLightData);
                    alreadyReachedMap[positionInt] = 2; // Make sure that this block will not be added multiple times
                    statesOutput.add(blockInfo);
                }   else if (!canSkip) {
                    alreadyReachedMap[positionInt] = 2; // Make sure that this block will not be added multiple times
                    statesOutput.add(blockInfo);
                }
            }

            // Stop when we reach the edge, as otherwise the flood-fill will continue forever
            // Also stop when we reach an occluding block, as displaying a block behind an occluding block is useless - it will never be seen anyway
            if (isOccluding || isEdge) {continue;}


            // Resize the stack if there's a possibility there won't be enough room to fit our new items
            if(!firstUpdate && (stack.length - (stackPos + 1) < 5)) {
                int[] newStack = new int[stack.length * 2];
                System.arraycopy(stack, 0, newStack, 0, stack.length);
                stack = newStack;
            }

            // Continue for the surrounding blocks
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

    /**
     * Checks the origin and destination blocks for changes.
     * At the origin, we only need to check the actually viewable blocks, since there is no need to re-flood-fill.
     * At the destination, we must check all blocks that were reached by the flood-fill, then do a re-flood-fill for any that have changed to add blocks in a newly revealed cavern, for instance.
     */
    @Override
    protected void checkForChanges() {
        List<IViewableBlockInfo> newStates = new ArrayList<>();

        int statesLength = nonObscuredStates.size();

        //noinspection ForLoopReplaceableByForEach
        for(int i = 0; i < statesLength; i++) { // We do not use foreach to avoid concurrent modifications
            BukkitBlockInfo blockInfo = (BukkitBlockInfo) nonObscuredStates.get(i);

            IntVector destPos = rotateOriginToDest.transform(blockInfo.getOriginPos().subtract(portalOriginPos)).add(portalDestPos); // Avoid directly using the matrix to fix floating point precision issues
            BlockData newDestData = dataFetcher.getData(destPos);


            if(!newDestData.equals(blockInfo.getBaseDestData())) {
                logger.finer("Destination block change");
                blockInfo.setBaseDestData(newDestData); // Set the new base data, so that this update isn't detected next change check

                // Re-floodfill from this block, as if this block has changed from occluding to non-occluding, it may have revealed some new blocks
                // We pass in our existing blockInfo to avoid double-adding to the non obscured states
                searchFromBlock(blockInfo.getOriginPos(), newStates, blockInfo);
            }

            if(!portal.isCrossServer()) {
                if (MaterialUtil.isTileEntity(newDestData.getMaterial())) {
                    Block destBlock = destPos.getBlock(Objects.requireNonNull(portal.getDestPos().getWorld()));

                    PacketContainer updatePacket = BlockDataUtil.getUpdatePacket(destBlock.getState());
                    if(updatePacket != null) {
                        BlockDataUtil.setTileEntityPosition(updatePacket, blockInfo.getOriginPos());

                        destTileStates.put(blockInfo.getOriginPos(), updatePacket);
                    }
                }
            }

            Block originBlock = blockInfo.getOriginPos().getBlock(originWorld);
            BlockData newOriginData = originBlock.getBlockData();
            if(MaterialUtil.isTileEntity(originBlock.getType()))  {
                PacketContainer updatePacket = BlockDataUtil.getUpdatePacket(originBlock.getState());
                if(updatePacket != null) {
                    originTileStates.put(blockInfo.getOriginPos(), updatePacket);
                }
            }

            if(!newOriginData.equals(blockInfo.getBaseOriginData())) {
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


        updateTileStateMap(originTileStates, originWorld, false);
        if(!portal.isCrossServer()) {
            updateTileStateMap(destTileStates, portal.getDestPos().getWorld(), true);
        }

        if(newStates.size() > 0) {
            stateQueue.enqueueStates(newStates);
        }
    }

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
    }

    @Override
    protected void updateInternal() {
        if(dataFetcher == null) {
            dataFetcher = dataFetcherFactory.create(portal);
        }
        dataFetcher.update();

        // If fetching external blocks has not yet finished, we can't do the flood-fill.
        if(!dataFetcher.isReady()) {
            logger.fine("Not updating portal, data was not yet been fetched");
            return;
        }

        super.updateInternal();
    }

    @Override
    public void reset() {
        dataFetcher = null;
        wrappedLightData = null;
        super.reset();
    }
}