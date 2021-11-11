package com.lauriethefish.betterportals.bukkit.block;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.lauriethefish.betterportals.api.IntVector;
import com.lauriethefish.betterportals.api.PortalDirection;
import com.lauriethefish.betterportals.bukkit.config.RenderConfig;
import com.lauriethefish.betterportals.bukkit.math.MathUtil;
import com.lauriethefish.betterportals.bukkit.math.Matrix;
import com.lauriethefish.betterportals.bukkit.portal.IPortal;
import com.lauriethefish.betterportals.bukkit.util.MaterialUtil;
import com.lauriethefish.betterportals.bukkit.util.performance.OperationTimer;
import com.lauriethefish.betterportals.shared.logging.Logger;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public abstract class FloodFillViewableBlockArray implements IViewableBlockArray {
    protected final Logger logger;
    protected final RenderConfig renderConfig;

    protected final ConcurrentHashMap<IntVector, PacketContainer> originTileStates = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<IntVector, PacketContainer> destTileStates = new ConcurrentHashMap<>();

    protected List<IViewableBlockInfo> viewableStates = new ArrayList<>();
    protected List<IViewableBlockInfo> queuedViewableStates = new ArrayList<>();
    protected final ReentrantLock queuedViewableStatesLock = new ReentrantLock();

    protected List<IViewableBlockInfo> nonObscuredStates = new ArrayList<>();

    protected boolean[] alreadyReachedMap;

    protected final IPortal portal;
    protected final Matrix rotateOriginToDest;
    protected final IntVector portalOriginPos;
    protected final IntVector portalDestPos;

    protected final IntVector centerPos;
    protected final PortalDirection destDirection;
    protected boolean firstUpdate;

    public FloodFillViewableBlockArray(IPortal portal, Logger logger, RenderConfig renderConfig) {
        this.portal = portal;
        this.logger = logger;
        this.renderConfig = renderConfig;
        this.centerPos = new IntVector(portal.getOriginPos().getVector());
        this.rotateOriginToDest = portal.getTransformations().getRotateToDestination();
        this.destDirection = portal.getDestPos().getDirection();
        this.portalOriginPos = new IntVector(portal.getOriginPos().getVector());
        this.portalDestPos = roundBasedOnDirection(portal);
        logger.fine("Origin pos: %s, Dest pos: %s", portalOriginPos, portalDestPos);
        logger.fine("Origin direction: %s, Dest Direction: %s", portal.getOriginPos().getDirection(), portal.getDestPos().getDirection());

        reset();
    }

    private IntVector roundBasedOnDirection(IPortal portal) {
        Vector originPosVec = portal.getOriginPos().getVector();
        Vector originPosCenter = MathUtil.moveToCenterOfBlock(originPosVec);
        Vector relOriginPos = originPosCenter.clone().subtract(originPosVec);
        Vector relDestPos = portal.getTransformations().rotateToDestination(relOriginPos);
        Vector destPosCenter = portal.getDestPos().getVector().add(relDestPos);

        return new IntVector(destPosCenter);
    }

    protected boolean isInLine(IntVector relPos) {
        return destDirection.swapVector(relPos).getZ() == 0;
    }

    protected final int getArrayMapIndex(IntVector relPos) {
        return ((relPos.getX() + (int) renderConfig.getMaxXZ()) + (relPos.getZ() + (int) renderConfig.getMaxXZ()) * renderConfig.getZMultip() + (relPos.getY() + (int) renderConfig.getMaxY()) * renderConfig.getYMultip());
    }

    /**
     * Starts a flood fill from <code>start</code> out to the edges of the viewed portal area.
     * The fill stops when it reaches occluding blocks, as we don't need to render other blocks behind these.
     * The origin data is also fetched, and this is placed in the viewable states
     * @param originPos Start position of the flood fill, at the origin (non-relative)
     */
    protected abstract void searchFromBlock(IntVector originPos);

    /**
     * Checks the origin and destination blocks for changes.
     * At the origin, we only need to check the actually viewable blocks, since there is no need to re-flood-fill.
     * At the destination, we must check all blocks that were reached by the flood-fill, then do a re-flood-fill for any that have changed to add blocks in a newly revealed cavern, for instance.
     */
    protected abstract void checkForChanges();

    protected final WrappedBlockData getBackgroundData() {
        WrappedBlockData backgroundData = renderConfig.getBackgroundBlockData();
        if(backgroundData == null) {
            backgroundData = MaterialUtil.PORTAL_EDGE_DATA; // Use the default if not overridden in the config
        }

        return backgroundData;
    }

    @Override
    public void update(int ticksSinceActivated) {
        if(ticksSinceActivated % renderConfig.getBlockUpdateInterval() != 0) {return;}

        updateInternal();
    }

    protected void updateInternal() {
        // TODO: Figure out deadlocking problem with very large render distances


        OperationTimer timer = new OperationTimer();
        if(firstUpdate) {
            queuedViewableStatesLock.lock();
            try {
                searchFromBlock(centerPos);
            }   finally {
                queuedViewableStatesLock.unlock();
            }
        }   else    {
            checkForChanges();
        }
        firstUpdate = false;
        logger.fine("Viewable block array update took: %.3f ms. Block count: %d. Viewable count: %d", timer.getTimeTakenMillis(), nonObscuredStates.size(), viewableStates.size());
    }

    @Override
    public void reset() {
        logger.finer("Clearing block array to save memory");

        // In practise there should be no block update ongoing when this happens, but to be on the safe side
        queuedViewableStatesLock.lock();
        try {
            queuedViewableStates = new ArrayList<>();
        }   finally {
            queuedViewableStatesLock.unlock();
        }
        viewableStates = new ArrayList<>();
        nonObscuredStates = new ArrayList<>();
        originTileStates.clear();
        destTileStates.clear();
        firstUpdate = true;
        alreadyReachedMap = new boolean[renderConfig.getTotalArrayLength()];
    }

    @Override
    public List<IViewableBlockInfo> getViewableStates() {
        if(queuedViewableStatesLock.tryLock()) {
            try {
                viewableStates.addAll(queuedViewableStates);
                queuedViewableStates.clear();
            }   finally {
                queuedViewableStatesLock.unlock();
            }
        }

        return viewableStates;
    }

    @Override
    public @Nullable PacketContainer getOriginTileEntityPacket(@NotNull IntVector position) {
        return originTileStates.get(position);
    }

    @Override
    public @Nullable PacketContainer getDestinationTileEntityPacket(@NotNull IntVector position) {
        return destTileStates.get(position);
    }
}
