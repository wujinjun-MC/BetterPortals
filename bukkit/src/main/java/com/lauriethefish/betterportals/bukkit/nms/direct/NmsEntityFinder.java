package com.lauriethefish.betterportals.bukkit.nms.direct;

import com.lauriethefish.betterportals.bukkit.entity.IEntityFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.entity.Entity;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * This NMS entity finder implementation improves performance by avoiding placing the entities into a {@link ArrayList} unless actually necessary. (i.e. for the consumer methods, it doesn't need to collect the entities first)
 */
@Singleton
public class NmsEntityFinder implements IEntityFinder {
    @Override
    public Collection<Entity> getNearbyEntities(Collection<Entity> list, Location location, double xRadius, double yRadius, double zRadius) {
        // Use an existing list if there is one
        if(list instanceof ArrayList) {
            list.clear(); // Retains the original array within the arraylist to avoid reallocating one
        }   else    {
            list = new ArrayList<>();
        }

        getNearbyEntities(location, xRadius, yRadius, zRadius, list::add);
        return list;
    }

    @Override
    public void getNearbyEntities(Location location, double xRadius, double yRadius, double zRadius, Consumer<Entity> consumer) {
        ServerLevel nmsWorld = ((CraftWorld) Objects.requireNonNull(location.getWorld())).getHandle();

        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        AABB box = new AABB(x - xRadius, y - yRadius, z - zRadius,x + xRadius,  y + yRadius,  z + zRadius);
        // We don't use the ServerLevel method directly to avoid having to pass a predicate, performance is important here
        nmsWorld.getEntities().get(box, entity -> {
            // Make sure to separate out the ender dragon parts
            if (entity instanceof EnderDragon) {
                EnderDragonPart[] dragonParts = ((EnderDragon) entity).getSubEntities();

                for (EnderDragonPart dragonPart : dragonParts) {
                    consumer.accept(dragonPart.getBukkitEntity());
                }
            }

            consumer.accept(entity.getBukkitEntity());
        });
    }
}
