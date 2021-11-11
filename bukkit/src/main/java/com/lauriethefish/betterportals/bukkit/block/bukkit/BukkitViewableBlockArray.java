package com.lauriethefish.betterportals.bukkit.block.bukkit;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.lauriethefish.betterportals.api.IntVector;
import com.lauriethefish.betterportals.bukkit.block.FloodFillViewableBlockArray;
import com.lauriethefish.betterportals.bukkit.block.IViewableBlockInfo;
import com.lauriethefish.betterportals.bukkit.block.fetch.BlockDataFetcherFactory;
import com.lauriethefish.betterportals.bukkit.block.fetch.IBlockDataFetcher;
import com.lauriethefish.betterportals.bukkit.block.rotation.IBlockRotator;
import com.lauriethefish.betterportals.bukkit.config.RenderConfig;
import com.lauriethefish.betterportals.bukkit.math.Matrix;
import com.lauriethefish.betterportals.bukkit.nms.BlockDataUtil;
import com.lauriethefish.betterportals.bukkit.portal.IPortal;
import com.lauriethefish.betterportals.bukkit.util.MaterialUtil;
import com.lauriethefish.betterportals.shared.logging.Logger;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

/**
 * After lots of testing, a flood-fill appears to be the most efficient way to find the blocks around the destination that aren't obscured.
 * <br>If you want, you can try to optimise more, but I'm not sure how to make this much better with the requirements it has.
 */
public class BukkitViewableBlockArray extends FloodFillViewableBlockArray {
    private final IBlockRotator blockRotator;
    private final BlockDataFetcherFactory dataFetcherFactory;
    private final Matrix rotateDestToOrigin;
    private IBlockDataFetcher dataFetcher;

    private final World originWorld;

    @Inject
    public BukkitViewableBlockArray(@Assisted IPortal portal, Logger logger, RenderConfig renderConfig, IBlockRotator blockRotator, BlockDataFetcherFactory dataFetcherFactory) {
        super(portal, logger, renderConfig);
        this.blockRotator = blockRotator;
        this.dataFetcherFactory = dataFetcherFactory;
        this.rotateDestToOrigin = portal.getTransformations().getRotateToOrigin();

        this.originWorld = portal.getOriginPos().getWorld();
        logger.fine("Origin pos: %s, Dest pos: %s", portalOriginPos, portalDestPos);
        logger.fine("Origin direction: %s, Dest Direction: %s", portal.getOriginPos().getDirection(), portal.getDestPos().getDirection());

        reset();
    }

    /**
     * Starts a flood fill from <code>start</code> out to the edges of the viewed portal area.
     * The fill stops when it reaches occluding blocks, as we don't need to render other blocks behind these.
     * The origin data is also fetched, and this is placed in {@link BukkitViewableBlockArray#viewableStates}.
     * <br>Some notes:
     * - There used to be a check to see if the origin and destination states are the same, but this added too much complexity when checking for changes, so I decided to remove it.
     * - That unfortunately reduces the performance of the threaded bit slightly, but I think it's worth it for the gains here.
     * @param start Start position of the flood fill, at the origin
     */
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

            BukkitBlockInfo blockInfo = new BukkitBlockInfo(originPos, originData, destData);
            boolean isEdge = renderConfig.isOutsideBounds(relX, relY, relZ);
            if(isEdge && !isOccluding) {
                blockInfo.setRenderedDestData(backgroundData);
            }   else    {
                blockInfo.setRenderedDestData(WrappedBlockData.createData(blockRotator.rotateByMatrix(rotateDestToOrigin, destData)));
            }
            nonObscuredStates.add(blockInfo);

            boolean canSkip = destData.equals(originData) && firstUpdate && !isEdge;
            boolean isInLine = isInLine(destRelPos);
            if (!isInLine && !canSkip) {
                viewableStates.add(blockInfo);
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

    /**
     * Checks the origin and destination blocks for changes.
     * At the origin, we only need to check the actually viewable blocks, since there is no need to re-flood-fill.
     * At the destination, we must check all blocks that were reached by the flood-fill, then do a re-flood-fill for any that have changed to add blocks in a newly revealed cavern, for instance.
     */
    @Override
    protected void checkForChanges() {
        for(IViewableBlockInfo entry : nonObscuredStates) {
            BukkitBlockInfo blockInfo = (BukkitBlockInfo) entry;

            IntVector destPos = rotateOriginToDest.transform(blockInfo.getOriginPos().subtract(portalOriginPos)).add(portalDestPos); // Avoid directly using the matrix to fix floating point precision issues
            BlockData newDestData = dataFetcher.getData(destPos);

            if(!newDestData.equals(blockInfo.getBaseDestData())) {
                logger.finer("Destination block change");
                searchFromBlock(blockInfo.getOriginPos());
            }

            if(!portal.isCrossServer()) {
                if (MaterialUtil.isTileEntity(newDestData.getMaterial())) {
                    logger.finer("Adding tile state to map . . .");
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
                logger.finer("Adding tile state to map . . .");
                PacketContainer updatePacket = BlockDataUtil.getUpdatePacket(originBlock.getState());
                if(updatePacket != null) {
                    originTileStates.put(blockInfo.getOriginPos(), updatePacket);
                }
            }

            if(!newOriginData.equals(blockInfo.getBaseOriginData())) {
                logger.finer("Origin block change");
                blockInfo.setOriginData(newOriginData);
                if(!newOriginData.equals(newDestData) && !portal.getOriginPos().isInLine(blockInfo.getOriginPos())) {
                    viewableStates.add(blockInfo);
                }
            }
        }

        updateTileStateMap(originTileStates, originWorld, false);
        if(!portal.isCrossServer()) {
            updateTileStateMap(destTileStates, portal.getDestPos().getWorld(), true);
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
        super.reset();
    }
}