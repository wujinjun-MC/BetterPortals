package com.lauriethefish.betterportals.bukkit.util.nms;

import com.lauriethefish.betterportals.bukkit.util.VersionUtil;
import com.lauriethefish.betterportals.shared.util.ReflectionUtil;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

import static com.lauriethefish.betterportals.bukkit.util.nms.MinecraftReflectionUtil.findCraftBukkitClass;
import static com.lauriethefish.betterportals.shared.util.ReflectionUtil.newInstance;
import static com.lauriethefish.betterportals.shared.util.ReflectionUtil.runMethod;

/**
 * Used to add simple marker tags to items, for example, the portal wand
 */
public class NBTTagUtil {
    private static final String MARKER_PREFIX = "BetterPortals_marker_";
    private static final String MARKER_VALUE = "marked";
    private static final Class<?> CRAFT_ITEM_STACK = findCraftBukkitClass("inventory.CraftItemStack");

    private static final Class<?> NBT_TAG_STRING;
    private static final Class<?> NBT_BASE;
    private static final Class<?> NBT_TAG_COMPOUND;
    private static final Class<?> ITEM_STACK;

    private static final Method NBT_HAS_TAG;
    private static final Method NBT_GET_TAG;
    private static final Method NBT_TAG_SET;
    private static final Method NBT_TAG_GET_STRING;

    static  {
        if(VersionUtil.isMcVersionAtLeast("1.17.0")) {
            NBT_TAG_STRING = ReflectionUtil.findClass("net.minecraft.nbt.NBTTagString");
            NBT_BASE = ReflectionUtil.findClass("net.minecraft.nbt.NBTBase");
            NBT_TAG_COMPOUND = ReflectionUtil.findClass("net.minecraft.nbt.NBTTagCompound");
            ITEM_STACK = ReflectionUtil.findClass("net.minecraft.world.item.ItemStack");
        }   else    {
            NBT_TAG_STRING = MinecraftReflectionUtil.findVersionedNMSClass("NBTTagString");
            NBT_BASE = MinecraftReflectionUtil.findVersionedNMSClass("NBTBase");
            NBT_TAG_COMPOUND = MinecraftReflectionUtil.findVersionedNMSClass("NBTTagCompound");
            ITEM_STACK = MinecraftReflectionUtil.findVersionedNMSClass("ItemStack");
        }

        if (VersionUtil.isMcVersionAtLeast("1.18.0")) {
            NBT_HAS_TAG = ReflectionUtil.findMethod(ITEM_STACK, "r");
            NBT_GET_TAG = ReflectionUtil.findMethod(ITEM_STACK, "t");
            NBT_TAG_SET = ReflectionUtil.findMethod(NBT_TAG_COMPOUND, "a", new Class[]{String.class, NBT_BASE});
            NBT_TAG_GET_STRING = ReflectionUtil.findMethod(NBT_TAG_COMPOUND, "l", new Class[]{String.class});
        } else {
            NBT_HAS_TAG = ReflectionUtil.findMethod(ITEM_STACK, "hasTag");
            NBT_GET_TAG = ReflectionUtil.findMethod(ITEM_STACK, "getTag");
            NBT_TAG_SET = ReflectionUtil.findMethod(NBT_TAG_COMPOUND, "set", new Class[]{String.class, NBT_BASE});
            NBT_TAG_GET_STRING = ReflectionUtil.findMethod(NBT_TAG_COMPOUND, "getString", new Class[]{String.class});
        }
    }

    /**
     * Adds a marker NBT tag to <code>item</code>.
     * @param item Item to add the tag to
     * @param name Name of the tag
     * @return A new {@link ItemStack} with the tag. (original is unmodified)
     */
    @NotNull
    public static ItemStack addMarkerTag(@NotNull ItemStack item, @NotNull String name) {
        Object nmsItem = getNMSItemStack(item);

        try {
            // Get the NBT tag, or create one if the item doesn't have one
            Object itemTag = ((boolean) NBT_HAS_TAG.invoke(nmsItem)) ? NBT_GET_TAG.invoke(nmsItem) : newInstance(NBT_TAG_COMPOUND);
            Object stringValue = newInstance(NBT_TAG_STRING, new Class[]{String.class}, MARKER_VALUE);

            NBT_TAG_SET.invoke(itemTag,MARKER_PREFIX + name, stringValue); // Set the value
        } catch (ReflectiveOperationException e) {
            throw new ReflectionUtil.ReflectionException(e);
        }

        return getBukkitItemStack(nmsItem);
    }

    /**
     * Checks if <code>item</code> has a marker tag with <code>name</code>.
     * @param item The item to check
     * @param name The name of the NBT marker tag
     * @return Whether it has the tag
     */
    public static boolean hasMarkerTag(@NotNull ItemStack item, @NotNull String name)	{
        Object nmsItem = getNMSItemStack(item); // ItemStack

        String value;
        try {
            if(!(boolean) NBT_HAS_TAG.invoke(nmsItem)) {return false;} // Return null if it has no NBT data
            Object itemTag = NBT_GET_TAG.invoke(nmsItem); // Otherwise, get the item's NBT tag

            value = (String) NBT_TAG_GET_STRING.invoke(itemTag,MARKER_PREFIX + name);
        } catch (ReflectiveOperationException e) {
            throw new ReflectionUtil.ReflectionException(e);
        }

        return MARKER_VALUE.equals(value); // Return the value of the key
    }

    @NotNull
    private static Object getNMSItemStack(@NotNull ItemStack item) {
        return runMethod(null, CRAFT_ITEM_STACK, "asNMSCopy", new Class[]{ItemStack.class}, new Object[]{item});
    }

    @NotNull
    private static ItemStack getBukkitItemStack(@NotNull Object nmsItem) {
        return (ItemStack) runMethod(null, CRAFT_ITEM_STACK, "asBukkitCopy", new Class[]{ITEM_STACK}, new Object[]{nmsItem});
    }
}
