package com.lauriethefish.betterportals;

/**
 * References entry points of different parts of the plugin to exclude them from minimisation
 */
public class MinimisationEntryPoint {
    private static final Class<?> BUKKIT = com.lauriethefish.betterportals.bukkit.BetterPortals.class;
    private static final Class<?> BUNGEE = com.lauriethefish.betterportals.bungee.BetterPortals.class;

    /**
     * Only referenced via Class.forName
     */
    private static final Class<?> DIRECT_NMS_ROOT = com.lauriethefish.betterportals.bukkit.nms.direct.NmsOptimisationModule.class;
}
