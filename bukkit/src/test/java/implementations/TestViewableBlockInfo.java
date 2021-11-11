package implementations;

import com.lauriethefish.betterportals.api.IntVector;
import com.lauriethefish.betterportals.bukkit.block.IViewableBlockInfo;

public class TestViewableBlockInfo implements IViewableBlockInfo {
    @Override
    public IntVector getOriginPos() {
        return new IntVector(0, 0, 0);
    }
}
