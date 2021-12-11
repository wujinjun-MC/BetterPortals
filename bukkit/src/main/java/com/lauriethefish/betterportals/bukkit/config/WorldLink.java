package com.lauriethefish.betterportals.bukkit.config;

import com.lauriethefish.betterportals.bukkit.util.VersionUtil;
import com.lauriethefish.betterportals.shared.util.ReflectionUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Stores the link between two worlds, for instance the overworld and the nether
 * There can be multiple links, for instance to link multiverses together
 * The overworld and the nether will have two links, one for each direction
 * This is to allow one way connections
 */
@Getter
public class WorldLink  {
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

    private final String originWorldName;
    private final String destWorldName;

    private final World originWorld;
    private final World destinationWorld;

    private final int minSpawnY;
    private final int maxSpawnY;

    private final double coordinateRescalingFactor;

    public WorldLink(ConfigurationSection config)    {
        originWorldName = Objects.requireNonNull(config.getString("originWorld"), "Missing originWorld key in world link");
        destWorldName = Objects.requireNonNull(config.getString("destinationWorld"), "Missing destinationWorld key in world link");

        originWorld = Bukkit.getWorld(originWorldName);
        destinationWorld = Bukkit.getWorld(destWorldName);

        minSpawnY = config.getInt("minSpawnY");
        maxSpawnY = config.getInt("maxSpawnY");
        coordinateRescalingFactor = config.getDouble("coordinateRescalingFactor");
    }

    public WorldLink(World originWorld, World destinationWorld, double coordinateRescalingFactor, int minSpawnY, int maxSpawnY) {
        originWorldName = originWorld.getName();
        destWorldName = destinationWorld.getName();
        this.originWorld = originWorld;
        this.destinationWorld = destinationWorld;
        this.minSpawnY = minSpawnY;
        this.maxSpawnY = maxSpawnY;
        this.coordinateRescalingFactor = coordinateRescalingFactor;
    }

    public WorldLink(World originWorld, World destinationWorld, double coordinateRescalingFactor, int yMinSpace) {
        this(originWorld, destinationWorld, coordinateRescalingFactor, getMinHeight(destinationWorld) + yMinSpace, getMaxHeight(destinationWorld) - yMinSpace);
    }

    private static int getMaxHeight(World world) {
        int actualMaxHeight = GET_MAX_HEIGHT == null ? 0 : (int) ReflectionUtil.invokeMethod(world, GET_MAX_HEIGHT);

        return world.getEnvironment() == World.Environment.NETHER ? 128 : actualMaxHeight;
    }

    private static int getMinHeight(World world) {
        return GET_MIN_HEIGHT == null ? 0 : (int) ReflectionUtil.invokeMethod(world, GET_MIN_HEIGHT);
    }

    public boolean isValid()    {
        return originWorld != null && destinationWorld != null;
    }

    @NotNull
    public Location moveFromOriginWorld(@NotNull Location loc) {
        assert loc.getWorld() == originWorld;
        loc = loc.clone();

        // Avoid multiplying the Y coordinate
        loc.setX(loc.getX() * coordinateRescalingFactor);
        loc.setZ(loc.getZ() * coordinateRescalingFactor);
        loc.setWorld(destinationWorld);
        return loc;
    }
}