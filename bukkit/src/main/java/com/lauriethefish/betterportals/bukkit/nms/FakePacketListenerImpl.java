package com.lauriethefish.betterportals.bukkit.nms;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import sun.misc.Unsafe;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

public class FakePacketListenerImpl extends ServerGamePacketListenerImpl {
    public static FakePacketListenerImpl create() {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            return (FakePacketListenerImpl) unsafe.allocateInstance(FakePacketListenerImpl.class);
        } catch (InstantiationException | IllegalAccessException | NoSuchFieldException ex) {
            throw new Error(ex);
        }
    }

    // Unused, just used to make it compile
    private FakePacketListenerImpl(MinecraftServer minecraftserver, Connection networkmanager, ServerPlayer entityplayer) {
        super(minecraftserver, networkmanager, entityplayer);
    }

    @Override
    public void send(Packet<?> packet, @Nullable GenericFutureListener<? extends Future<? super Void>> genericfuturelistener) {
        // Do nothing, this is a fake implementation
    }
}
