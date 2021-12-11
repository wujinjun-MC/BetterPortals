package com.lauriethefish.betterportals.bukkit.util;

import com.lauriethefish.betterportals.shared.util.ReflectionUtil;
import org.bukkit.World;

import java.lang.reflect.Method;

public class HeightUtil {
    private static final Method GET_MIN_HEIGHT;
    private static final Method GET_MAX_HEIGHT;

    static {
        if(VersionUtil.isMcVersionAtLeast("1.18.0")) {
            GET_MIN_HEIGHT = ReflectionUtil.findMethod(World.class, "getMinHeight");
            GET_MAX_HEIGHT = ReflectionUtil.findMethod(World.class, "getMaxHeight");
        }   else    {
            GET_MIN_HEIGHT = null;
            GET_MAX_HEIGHT = null;
        }
    }

    public static int getMaxHeight(World world) {
        return GET_MAX_HEIGHT == null ? 255 : (int) ReflectionUtil.invokeMethod(world, GET_MAX_HEIGHT);
    }

    public static int getMinHeight(World world) {
        return GET_MIN_HEIGHT == null ? 0 : (int) ReflectionUtil.invokeMethod(world, GET_MIN_HEIGHT);
    }
}
