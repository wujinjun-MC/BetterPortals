package com.lauriethefish.betterportals.bukkit.nms.direct;

import com.lauriethefish.betterportals.api.IntVector;
import com.lauriethefish.betterportals.bukkit.block.IViewableBlockInfo;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block info implementation that directly uses NMS to refer to the block states, used for performance, but fallback to the bukkit method is always available.
 */
public class NmsBlockInfo implements IViewableBlockInfo {
    @Getter private final IntVector originPos;

    @Getter private BlockState baseOriginData;
    @Getter @Setter private BlockState baseDestData;
    @Getter private BlockState renderedDestData;

    public NmsBlockInfo(IntVector originPos, BlockState baseOriginData, BlockState baseDestData) {
        this.originPos = originPos;
        this.baseOriginData = baseOriginData;
        this.baseDestData = baseDestData;
    }

    public void setOriginData(BlockState originData) {
        this.baseOriginData = originData;
    }

    public void setRenderedDestData(BlockState destData) {
        this.renderedDestData = destData;
    }
}
