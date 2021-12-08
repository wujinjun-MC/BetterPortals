package com.lauriethefish.betterportals.bukkit.nms.direct;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import com.comphenix.protocol.wrappers.WrappedAttribute;
import com.google.inject.assistedinject.Assisted;
import com.lauriethefish.betterportals.bukkit.entity.faking.EntityInfo;
import com.lauriethefish.betterportals.bukkit.entity.faking.EntityTrackingManager;
import com.lauriethefish.betterportals.bukkit.entity.faking.IEntityTracker;
import com.lauriethefish.betterportals.bukkit.math.MathUtil;
import com.lauriethefish.betterportals.bukkit.nms.AnimationType;
import com.lauriethefish.betterportals.bukkit.nms.RotationUtil;
import com.lauriethefish.betterportals.bukkit.portal.IPortal;
import com.lauriethefish.betterportals.shared.logging.Logger;
import com.mojang.authlib.GameProfile;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_18_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_18_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class NMSEntityTracker implements IEntityTracker {
    /**
     * Delay in ticks between sending the player info for fake players to the client and removing it (it is removed to avoid showing them twice in the tab menu)
     */
    private static final int fakePlayerTabListRemoveDelay = 10;

    private final Map<UUID, ServerPlayerConnection> trackingPlayers = new HashMap<>();
    private final Entity nmsEntity;
    private final PacketProxyProvider proxyProvider;
    private ServerEntity serverEntity;
    @Getter private final EntityInfo entityInfo;
    @Getter private final IPortal portal;
    private final EntityTrackingManager trackingManager;
    private final JavaPlugin pl;

    private final Logger logger;
    private final Consumer<Packet<?>> broadcast = this::onEntityPacketBroadcast;


    @Inject
    public NMSEntityTracker(@Assisted org.bukkit.entity.Entity entity, @Assisted IPortal portal, Logger logger, PacketProxyProvider proxyProvider, EntityTrackingManager trackingManager, JavaPlugin pl) {
        nmsEntity = ((CraftEntity) entity).getHandle();
        this.logger = logger;
        this.entityInfo = new EntityInfo(portal.getTransformations(), entity);
        this.portal = portal;
        this.proxyProvider = proxyProvider;
        this.trackingManager = trackingManager;
        this.pl = pl;
    }

    /**
     * Intercepts the outgoing packets, and sends them to our viewing players.
     * @param packet Packet to intercept
     */
    private void onEntityPacketBroadcast(Packet<?> packet) {
        // Correct the entity ID and positioning
        Packet<?> modified = modifyPacket(packet);

        if(modified == null) {
            return;
        }

        for(ServerPlayerConnection player : trackingPlayers.values()) {
            player.send(modified);
        }
    }

    /**
     * Begins intercepting packets for the entity
     */
    private void begin() {
        this.serverEntity = proxyProvider.proxyFor(entityInfo.getEntity(), broadcast);
    }

    /**
     * Stops listening for packets to forward
     */
    private void stop() {
        proxyProvider.stopProxying(entityInfo.getEntity(), broadcast);
    }

    private PacketContainer getContainer(Class<?> packetClass) {
        return new PacketContainer(PacketType.fromClass(packetClass));
    }

    private Packet<?> getPacket(PacketContainer container) {
        return (Packet<?>) container.getHandle();
    }

    /**
     * Limits an angle to be between -180 and 180 degrees.
     * @param angle The angle to limit, in degrees
     * @return The angle between -180 and 180 degrees.
     */
    private float getLimitedAngle(float angle) {
        angle =  angle % 360;
        angle = (angle + 360) % 360;

        if (angle > 180) {
            angle -= 360;
        }

        return angle;
    }

    private Pair<Float, Float> getTransformedLocationFloats(byte yRot, byte xRot) {
        float pitch = (xRot * 360.0f) / 256.0f;
        float yaw = (yRot * 360.0f) / 256.0f;

        Vector direction = MathUtil.getDirection(yaw, pitch);
        direction = entityInfo.getRotation().transform(direction);

        Pair<Float, Float> result = MathUtil.getYawAndPitch(direction);
        result.setFirst(getLimitedAngle(result.getFirst()));
        result.setSecond(getLimitedAngle(result.getSecond()));


        //logger.fine("Transforming location. Oyaw: %f, Opitch: %f, nYaw: %f, nPitch: %f", yaw, pitch, result.getSecond(), result.getFirst());
        return result;
    }

    private Pair<Byte, Byte> getTransformedLocationBytes(byte yRot, byte xRot) {
        Pair<Float, Float> newAngles = getTransformedLocationFloats(yRot, xRot);
        return new Pair<>((byte) Math.floor(newAngles.getFirst() * 256.0f / 360.0f), (byte) Math.floor(newAngles.getSecond() * 256.0f / 360.0f));
    }

    /**
     * Modifies the given packet to correspond to the new entity ID, and to move the location of the entity
     * @param p Packet to modify
     * @return Modified packet, if the packet is unmodified it will be the same instance as <code>p</code>, otherwise it will be a new instance.
     */
    private @Nullable Packet<?> modifyPacket(Packet<?> p) {
        if(p instanceof ClientboundAnimatePacket packet) {
            PacketContainer container = getContainer(ClientboundAnimatePacket.class);

            container.getIntegers()
                    .write(0, getEntityInfo().getEntityId())
                    .write(1, packet.getAction());

            return getPacket(container);
        }
        if(p instanceof ClientboundTakeItemEntityPacket packet) {

            IEntityTracker pickerUpperTracker = trackingManager.getTracker(portal, Objects.requireNonNull(nmsEntity.level.getEntity(packet.getPlayerId())).getBukkitEntity());
            if(pickerUpperTracker == null) {
                return null;
            }

            return new ClientboundTakeItemEntityPacket(entityInfo.getEntityId(), pickerUpperTracker.getEntityInfo().getEntityId(), packet.getAmount());
        }
        if(p instanceof ClientboundSetPassengersPacket packet) {
            if(packet.getVehicle() != nmsEntity.getId()) {
                return null;
            }

            List<Integer> result = new ArrayList<>();
            for(int passengerId : packet.getPassengers()) {
                IEntityTracker tracker = trackingManager.getTracker(portal, Objects.requireNonNull(nmsEntity.level.getEntity(passengerId)).getBukkitEntity());
                if(tracker != null) {
                    result.add(tracker.getEntityInfo().getEntityId());
                }
            }

            PacketContainer container = getContainer(ClientboundSetPassengersPacket.class);
            container.getIntegers().write(0, entityInfo.getEntityId());
            container.getIntegerArrays().write(0, result.stream().mapToInt(i -> i).toArray());
            return getPacket(container);
        }
        if(p instanceof ClientboundEntityEventPacket packet) {

            PacketContainer container = getContainer(ClientboundEntityEventPacket.class);
            container.getIntegers().write(0, entityInfo.getEntityId());
            container.getBytes().write(0, packet.getEventId());

            return getPacket(container);
        }
        if(p instanceof ClientboundMoveEntityPacket packet) {

            byte xRot = packet.getxRot();
            byte yRot = packet.getyRot();
            Pair<Byte, Byte> transformedRot = getTransformedLocationBytes(yRot, xRot);
            byte tXRot = transformedRot.getFirst();
            byte tYRot = transformedRot.getSecond();

            if(packet.hasPosition()) {
                Vector movement = new Vector(
                        ClientboundMoveEntityPacket.packetToEntity(packet.getXa()),
                        ClientboundMoveEntityPacket.packetToEntity(packet.getYa()),
                        ClientboundMoveEntityPacket.packetToEntity(packet.getZa())
                );
                movement = entityInfo.getRotation().transform(movement);

                short aX = (short) ClientboundMoveEntityPacket.entityToPacket(movement.getX());
                short aY = (short) ClientboundMoveEntityPacket.entityToPacket(movement.getY());
                short aZ = (short) ClientboundMoveEntityPacket.entityToPacket(movement.getZ());


                if(packet.hasRotation()) {
                    return new ClientboundMoveEntityPacket.PosRot(entityInfo.getEntityId(), aX, aY, aZ, tYRot, tXRot, packet.isOnGround());
                }   else    {
                    return new ClientboundMoveEntityPacket.Pos(entityInfo.getEntityId(), aX, aY, aZ, packet.isOnGround());
                }
            }   else    {
                return new ClientboundMoveEntityPacket.Rot(entityInfo.getEntityId(), tYRot, tXRot, packet.isOnGround());
            }
        }
        if(p instanceof ClientboundRemoveEntitiesPacket) {
            return new ClientboundRemoveEntitiesPacket(entityInfo.getEntityId());
        }
        if(p instanceof ClientboundRotateHeadPacket packet) {
            PacketContainer container = getContainer(ClientboundRotateHeadPacket.class);
            byte originalHeadRot = packet.getYHeadRot();
            byte transformedHeadRot = getTransformedLocationBytes(originalHeadRot, (byte) 0).getSecond();

            container.getIntegers().write(0, entityInfo.getEntityId());
            container.getBytes().write(0, transformedHeadRot);
            return getPacket(container);
        }
        if(p instanceof ClientboundSetEntityDataPacket) {
            PacketContainer container = getContainer(ClientboundSetEntityDataPacket.class);
            container.getIntegers().write(0, entityInfo.getEntityId());
            container.getModifier().write(1, ((ClientboundSetEntityDataPacket) p).getUnpackedData());
            return getPacket(container);
        }
        if(p instanceof ClientboundSetEntityMotionPacket packet) {
            Vector motionVector = new Vector(
                    packet.getXa() / 8000.0,
                    packet.getYa() / 8000.0,
                    packet.getZa() / 8000.0
            );
            motionVector = entityInfo.getRotation().transform(motionVector);

            return new ClientboundSetEntityMotionPacket(entityInfo.getEntityId(), new Vec3(
               motionVector.getX(),
               motionVector.getY(),
               motionVector.getZ()
            ));
        }

        if(p instanceof ClientboundSetEquipmentPacket packet) {
            return new ClientboundSetEquipmentPacket(entityInfo.getEntityId(), packet.getSlots());
        }
        if(p instanceof ClientboundTeleportEntityPacket packet) {
            PacketContainer container = getContainer(ClientboundTeleportEntityPacket.class);

            Vector location = new Vector(packet.getX(), packet.getY(), packet.getZ());
            location = entityInfo.getTranslation().transform(location);

            byte xRot = packet.getxRot();
            byte yRot = packet.getyRot();
            Pair<Byte, Byte> transformedRot = getTransformedLocationBytes(yRot, xRot);
            byte tXRot = transformedRot.getFirst();
            byte tYRot = transformedRot.getSecond();

            container.getIntegers().write(0, entityInfo.getEntityId());
            container.getDoubles()
                    .write(0, location.getX())
                    .write(1, location.getY())
                    .write(2, location.getZ());

            container.getBytes()
                    .write(0, tYRot)
                    .write(1, tXRot);

            container.getBooleans().write(0, packet.isOnGround());
            return getPacket(container);
        }
        if(p instanceof ClientboundUpdateMobEffectPacket packet) {
            PacketContainer container = getContainer(ClientboundUpdateMobEffectPacket.class);

            byte flags = 0;
            if (packet.isEffectAmbient())
                flags = (byte)(flags | 0x1);
            if (packet.isEffectVisible())
                flags = (byte)(flags | 0x2);
            if (packet.effectShowsIcon())
                flags = (byte)(flags | 0x4);
            container.getIntegers()
                    .write(0, entityInfo.getEntityId())
                    .write(1, packet.getEffectDurationTicks());

            container.getBytes()
                    .write(0, packet.getEffectId())
                    .write(1, packet.getEffectAmplifier())
                    .write(2, flags);
            return getPacket(container);
        }
        if(p instanceof ClientboundRemoveMobEffectPacket packet) {
            return new ClientboundRemoveMobEffectPacket(entityInfo.getEntityId(), packet.getEffect());
        }
        if(p instanceof ClientboundAddEntityPacket packet) {
            Vector location = new Vector(
                    packet.getX(),
                    packet.getY(),
                    packet.getZ()
            );
            location = entityInfo.getTranslation().transform(location);

            Pair<Float, Float> transformedRot = getTransformedLocationFloats((byte) packet.getyRot(), (byte) packet.getxRot());
            float xRot = transformedRot.getFirst();
            float yRot = transformedRot.getSecond();

            Vector acceleration = new Vector(
                    packet.getXa(),
                    packet.getYa(),
                    packet.getZa()
            );
            acceleration = entityInfo.getRotation().transform(acceleration);

            return new ClientboundAddEntityPacket(
                entityInfo.getEntityId(),
                entityInfo.getEntityUniqueId(),
                location.getX(),
                location.getY(),
                location.getZ(),
                xRot,
                yRot,
                packet.getType(),
                packet.getData(),
                new Vec3(acceleration.getX(), acceleration.getY(), acceleration.getZ())
            );
        }
        if(p instanceof ClientboundAddExperienceOrbPacket packet) {
            PacketContainer container = getContainer(ClientboundAddExperienceOrbPacket.class);

            container.getIntegers()
                    .write(0, entityInfo.getEntityId())
                    .write(1, packet.getValue());

            Vector position = new Vector(
                    packet.getX(),
                    packet.getY(),
                    packet.getZ()
            );
            position = entityInfo.getTranslation().transform(position);

            container.getDoubles()
                    .write(0, position.getX())
                    .write(1, position.getY())
                    .write(2, position.getZ());

            return getPacket(container);
        }
        if(p instanceof ClientboundAddMobPacket packet) {
            Vector position = new Vector(
                    packet.getX(),
                    packet.getY(),
                    packet.getZ()
            );
            position = entityInfo.getTranslation().transform(position);

            Vector deltaMovement = new Vector(
                    packet.getXd() / 8000.0,
                    packet.getYd() / 8000.0,
                    packet.getZd() / 8000.0
            );
            deltaMovement = entityInfo.getRotation().transform(deltaMovement);

            byte xRot = packet.getxRot();
            byte yRot = packet.getyRot();
            // TODO: Does this need transformations? I haven't noticed any problems with it not being transformed
            byte originalHeadRot = packet.getyHeadRot();

            Pair<Byte, Byte> transformedRot = getTransformedLocationBytes(yRot, xRot);
            byte tXRot = transformedRot.getFirst();
            byte tYRot = transformedRot.getSecond();

            PacketContainer container = getContainer(ClientboundAddMobPacket.class);
            container.getIntegers()
                    .write(0, entityInfo.getEntityId())
                    .write(1, packet.getType())
                    .write(2, (int) (deltaMovement.getX() * 8000.0))
                    .write(3, (int) (deltaMovement.getY() * 8000.0))
                    .write(4, (int) (deltaMovement.getZ() * 8000.0));

            container.getBytes()
                    .write(0, tYRot)
                    .write(1, tXRot)
                    .write(2, originalHeadRot);

            container.getUUIDs()
                    .write(0, entityInfo.getEntityUniqueId());

            container.getDoubles()
                    .write(0, position.getX())
                    .write(1, position.getY())
                    .write(2, position.getZ());
            return getPacket(container);
        }
        if(p instanceof ClientboundAddPaintingPacket packet) {
            PacketContainer container = getContainer(ClientboundAddPaintingPacket.class);

            container.getIntegers()
                    .write(0, entityInfo.getEntityId())
                    .write(1, Registry.MOTIVE.getId(packet.getMotive()));

            container.getUUIDs().write(0, entityInfo.getEntityUniqueId());

            BlockPos paintingBlockPos = packet.getPos();
            Vector paintingPos = new Vector(paintingBlockPos.getX() + 0.5, paintingBlockPos.getY() + 0.5, paintingBlockPos.getZ() + 0.5);
            paintingPos = entityInfo.getTranslation().transform(paintingPos);

            EnumWrappers.Direction direction = nmsDirectionToWrapper(packet.getDirection());
            direction = RotationUtil.rotateBy(direction, entityInfo.getRotation());

            container.getBlockPositionModifier().write(0, new BlockPosition(paintingPos));
            container.getDirections().write(0, direction);
            return getPacket(container);
        }
        if(p instanceof ClientboundAddPlayerPacket packet) {
            PacketContainer container = getContainer(ClientboundAddPlayerPacket.class);
            container.getIntegers().write(0, entityInfo.getEntityId());

            Vector position = new Vector(
                packet.getX(),
                packet.getY(),
                packet.getZ()
            );
            position = entityInfo.getTranslation().transform(position);

            byte xRot = packet.getxRot();
            byte yRot = packet.getyRot();
            Pair<Byte, Byte> transformedRot = getTransformedLocationBytes(yRot, xRot);
            byte tXRot = transformedRot.getFirst();
            byte tYRot = transformedRot.getSecond();

            container.getDoubles()
                    .write(0, position.getX())
                    .write(1, position.getY())
                    .write(2, position.getZ());

            container.getBytes()
                    .write(0, tYRot)
                    .write(1, tXRot);

            container.getUUIDs().write(0, entityInfo.getEntityUniqueId());

            return getPacket(container);
        }
        if(p instanceof ClientboundUpdateAttributesPacket packet) {
            PacketContainer container = getContainer(ClientboundUpdateAttributesPacket.class);
            container.getIntegers().write(0, entityInfo.getEntityId());
            container.getAttributeCollectionModifier().write(0, packet.getValues().stream().map(WrappedAttribute::fromHandle).collect(Collectors.toList()));

            return getPacket(container);
        }

        logger.warning("Modification code was not implemented for the packet. This should not happen - the packet will not be redirected. Packet type: %s", p.getClass().getName());
        // Packet does not need to be modified
        // Interested to see which packets these will be.
        // Return null to indicate to not send the packet
        return null;
    }

    private EnumWrappers.Direction nmsDirectionToWrapper(Direction direction) {
        return switch (direction) {
            case NORTH -> EnumWrappers.Direction.NORTH;
            case SOUTH -> EnumWrappers.Direction.SOUTH;
            case EAST -> EnumWrappers.Direction.EAST;
            case WEST -> EnumWrappers.Direction.WEST;
            case UP -> EnumWrappers.Direction.UP;
            case DOWN -> EnumWrappers.Direction.DOWN;
        };
    }


    public void addTracking(@NotNull Player player) {
        if(trackingPlayers.containsKey(player.getUniqueId())) {throw new IllegalArgumentException("Player is already tracking this entity");}
        if(trackingPlayers.size() == 0) {
            // Assign our redirection consumer if no players were previously tracking this entity
            begin();
        }

        ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        ServerPlayerConnection playerConnection = serverPlayer.connection;
        trackingPlayers.put(player.getUniqueId(), playerConnection);

        ClientboundPlayerInfoPacket.PlayerUpdate entry = null;
        // If the entity is a player, then we need to send a player info packet to coerce the client into thinking that a player with this fake UUID exists, but has the same skin as the player
        if(entityInfo.getEntity() instanceof Player) {
            logger.finest("Sending player info packet!");
            ClientboundPlayerInfoPacket playerInfoPacket = new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER, serverPlayer);

            ServerPlayer entityPlayer = (ServerPlayer) nmsEntity;
            // We make a new profile for our fake user, with our fake UUID and the original name
            GameProfile profile = new GameProfile(entityInfo.getEntityUniqueId(), entityInfo.getEntity().getName());

            // We remove the existing textures (if any) and add the skin of the original player
            profile.getProperties().removeAll("textures");
            profile.getProperties().putAll("textures", entityPlayer.getGameProfile().getProperties().get("textures"));

            entry = new ClientboundPlayerInfoPacket.PlayerUpdate(profile, entityPlayer.latency, entityPlayer.gameMode.getGameModeForPlayer(), entityPlayer.getTabListDisplayName());
            playerInfoPacket.getEntries().set(0, entry);
            playerConnection.send(playerInfoPacket);
        }

        // Sends entity spawning packets, note that we need to make sure that they have been modified to get the correct entity ID and position
        serverEntity.sendPairingData(packet -> playerConnection.send(Objects.requireNonNull(modifyPacket(packet))), serverPlayer);
        nmsEntity.startSeenByPlayer(serverPlayer);

        // We also need to remove the player from the tab menu after it is spawned
        if(entityInfo.getEntity() instanceof Player) {
            ClientboundPlayerInfoPacket.PlayerUpdate finalEntry = entry;
            // This is scheduled one tick later, as otherwise the client will remove the player's skin, so they'll show as steve or alex
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                logger.finer("Sending player info remove packet!");
                ClientboundPlayerInfoPacket playerInfoPacket = new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER, serverPlayer);
                playerInfoPacket.getEntries().set(0, finalEntry);
                playerConnection.send(playerInfoPacket);
            }, fakePlayerTabListRemoveDelay);
        }
    }

    public void removeTracking(@NotNull Player player, boolean sendPackets) {
        if(!trackingPlayers.containsKey(player.getUniqueId())) {throw new IllegalArgumentException("Cannot stop player from tracking entity, they weren't viewing in the first place");}


        trackingPlayers.remove(player.getUniqueId());

        if(sendPackets) {
            ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();

            nmsEntity.stopSeenByPlayer(serverPlayer);

            PacketContainer container = getContainer(ClientboundRemoveEntitiesPacket.class);
            if(container.getIntegers().size() > 0) {
                container.getIntegers().write(0, entityInfo.getEntityId());
            }   else if(container.getIntLists().size() > 0) {
                List<Integer> idsList = new ArrayList<>();
                idsList.add(entityInfo.getEntityId());
                container.getIntLists().write(0, idsList);
            }

            serverPlayer.connection.send(getPacket(container));
        }

        if(trackingPlayers.size() == 0) {
            // Remove our redirection consumer
            stop();
        }
    }

    @Override
    public int getTrackingPlayerCount() {
        return trackingPlayers.size();
    }

    @Override
    public void update() { }

    @Override
    public void onAnimation(@NotNull AnimationType animationType) { }

    @Override
    public void onPickup(@NotNull EntityInfo pickedUp) { }
}
