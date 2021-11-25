package com.lauriethefish.betterportals.bukkit.nms;

import com.lauriethefish.betterportals.shared.logging.Logger;
import com.lauriethefish.betterportals.shared.util.NewReflectionUtil;
import com.mojang.authlib.GameProfile;
import lombok.Getter;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerPlayerConnection;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftEntity;
import org.bukkit.entity.Entity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;

/**
 * Handles proxying entity update packets to a consumer.
 */
@Singleton
public class PacketProxyProvider {
    private static final Field serverEntityField;
    private static final Field seenByField;

    static {
        serverEntityField = NewReflectionUtil.findFieldByType(ChunkMap.TrackedEntity.class, ServerEntity.class);
        seenByField = NewReflectionUtil.findFieldByType(ChunkMap.TrackedEntity.class, Set.class);
    }

    private static class EntityProxy implements ServerPlayerConnection  {
        private final List<Consumer<Packet<?>>> proxyingTo = new ArrayList<>();
        private final Set<ServerPlayerConnection> seenBy;
        @Getter private final ServerEntity serverEntity;
        private final ServerPlayer fakePlayer;

        @SuppressWarnings("unchecked")
        public EntityProxy(ServerPlayer fakePlayer, Entity entity) {
            this.fakePlayer = fakePlayer;

            net.minecraft.world.entity.Entity nmsEntity = ((CraftEntity) entity).getHandle();
            ServerChunkCache chunkCache = (ServerChunkCache) nmsEntity.level.getChunkSource();
            ChunkMap levelChunkMap = chunkCache.chunkMap;
            ChunkMap.TrackedEntity entityTracker = levelChunkMap.G.get(nmsEntity.getId());

            try {
                // TODO: Try to speed up this reflection


                serverEntity = (ServerEntity) serverEntityField.get(entityTracker);

                // Find the set of player connections that are subscribed to the tracker
                seenBy = (Set<ServerPlayerConnection>) seenByField.get(entityTracker);

                // Add ourselves to the subscribed players
                seenBy.add(this);
            }   catch(IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }

        public void add(Consumer<Packet<?>> consumer) {
            proxyingTo.add(consumer);
        }

        public boolean remove(Consumer<Packet<?>> consumer) {
            proxyingTo.remove(consumer);
            if(proxyingTo.size() == 0) {
                // Stop redirecting packets to us
                seenBy.remove(this);
                return false;
            }
            return true;
        }

        @Override
        public ServerPlayer getPlayer() {
            return fakePlayer;
        }

        @Override
        public void send(Packet<?> packet) {
            // Send the packet to each of our proxy receivers
            proxyingTo.forEach(consumer -> consumer.accept(packet));
        }
    }

    private final Map<Entity, EntityProxy> proxiedEntities = new HashMap<>();
    private final Logger logger;
    private final ServerPlayer fakePlayer;

    @Inject
    public PacketProxyProvider(Logger logger) {
        this.logger = logger;

        ServerLevel level = ((CraftWorld) Bukkit.getWorlds().get(0)).getHandle();
        GameProfile fakeProfile = new GameProfile(UUID.randomUUID(), "BetterPortals test player");
        fakePlayer = new ServerPlayer(level.getServer(), level, fakeProfile);
        fakePlayer.connection = FakePacketListenerImpl.create();
    }

    /**
     * Begins proxying update packets for the given entity to the given consumer.
     * @param entity Entity to proxy packets for
     * @param sendTo Consumer to send the packets to
     * @return NMS ServerEntity instance for this entity, returned for convenience
     */
    public ServerEntity proxyFor(Entity entity, Consumer<Packet<?>> sendTo) {
        EntityProxy proxy = proxiedEntities.computeIfAbsent(entity, entityKey -> {
            logger.finer("Adding packet proxy for entity");
            return new EntityProxy(fakePlayer, entity);
        });
        proxy.add(sendTo);
        return proxy.getServerEntity();
    }

    /**
     * Stops proxying update packets for the given entity to the given consumer.
     * @param entity Entity to stop proxying packets for
     * @param sendTo Consumer to stop proxying packets to
     */
    public void stopProxying(Entity entity, Consumer<Packet<?>> sendTo) {
        EntityProxy proxy = proxiedEntities.get(entity);
        if(!proxy.remove(sendTo)) {
            // Remove the proxy if nobody is subscribed to it
            logger.finer("Removing packet proxy for entity");
            proxiedEntities.remove(entity);
        }
    }
}
