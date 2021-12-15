package com.lauriethefish.betterportals.proxy.net;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.lauriethefish.betterportals.proxy.IProxyConfig;
import com.lauriethefish.betterportals.proxy.IProxy;
import com.lauriethefish.betterportals.shared.net.IRequestHandler;
import com.lauriethefish.betterportals.shared.net.encryption.EncryptedObjectStream;
import com.lauriethefish.betterportals.shared.net.encryption.EncryptedObjectStreamFactory;
import com.lauriethefish.betterportals.shared.net.encryption.IEncryptedObjectStream;

/**
 * Configures the BetterPortals proxy server for viewing cross server portals
 * User will also need to bind an implementation of {@link IProxy} and {@link IProxyConfig}
 */
public class ProxyModule extends AbstractModule {
    protected void configure() {
        bind(IPortalServer.class).to(PortalServer.class);
        bind(IRequestHandler.class).to(ProxyRequestHandler.class);

        install(new FactoryModuleBuilder()
                .implement(IClientHandler.class, ClientHandler.class)
                .build(IClientHandler.Factory.class)
        );
        install(new FactoryModuleBuilder()
                .implement(IEncryptedObjectStream.class, EncryptedObjectStream.class)
                .build(EncryptedObjectStreamFactory.class)
        );
    }
}
