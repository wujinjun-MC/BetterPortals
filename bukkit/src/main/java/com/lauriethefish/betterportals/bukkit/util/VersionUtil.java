package com.lauriethefish.betterportals.bukkit.util;

import org.bukkit.Bukkit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Various parts of the plugin need different implementations based on the Minecraft version.
 */
public class VersionUtil {
    // Cache since it's not super cheap to check if we're above a version
    private static final ConcurrentMap<String, Boolean> versionAtLeastCache = new ConcurrentHashMap<>();

    /**
     * Returns the current Minecraft version as a string (e.g., "1.20.1").
     * @return Current Minecraft version.
     */
    public static String getCurrentVersion() {
        return Bukkit.getServer().getMinecraftVersion();
    }

    /**
     * Checks if the version represented by <code>versionStr</code> is greater than or equal to <code>otherStr</code>.
     * @param versionStr Version to test if greater
     * @param otherStr Other version
     * @return Whether <code>versionStr</code> is greater than or equal.
     */
    public static boolean isVersionGreaterOrEq(String versionStr, String otherStr) {
        String[] version = splitVersionString(versionStr);
        String[] other = splitVersionString(otherStr);

        // If the other version has more numbers, it must be newer
        if (other.length > version.length) {
            return false;
        }

        for (int i = 0; i < version.length; i++) {
            int number = Integer.parseInt(version[i]);
            if (i >= other.length) {
                return true;
            }

            // If one of the numbers in the other version is greater than this one, return false
            int otherNumber = Integer.parseInt(other[i]);
            if (otherNumber > number) {
                return false;
            }

            // If one of the numbers in this version is greater than the other one, return true
            if (number > otherNumber) {
                return true;
            }
        }

        // If all numbers are equal, return true
        return true;
    }

    private static String[] splitVersionString(String version) {
        // Split version by dots and remove "R" characters
        return version.replace("R", "").split("\\.");
    }

    /**
     * Checks if the current Minecraft server version is greater than <code>version</code>.
     * @param version The version to test
     * @return Whether the current Minecraft server version is greater than <code>version</code>.
     */
    public static boolean isMcVersionAtLeast(String version) {
        return versionAtLeastCache.computeIfAbsent(version, v -> isVersionGreaterOrEq(getCurrentVersion(), v));
    }
}
