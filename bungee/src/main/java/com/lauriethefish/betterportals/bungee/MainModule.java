package com.lauriethefish.betterportals.bungee;

import com.google.inject.AbstractModule;
import com.lauriethefish.betterportals.proxy.IProxyConfig;
import com.lauriethefish.betterportals.proxy.IProxy;
import com.lauriethefish.betterportals.proxy.net.*;
import com.lauriethefish.betterportals.shared.logging.Logger;
import com.lauriethefish.betterportals.shared.logging.OverrideLogger;

import net.md_5.bungee.api.plugin.Plugin;

public class MainModule extends AbstractModule {
    private final BetterPortals pl;
    public MainModule(BetterPortals pl) {
        this.pl = pl;
    }

    @Override
    public void configure() {
        bind(Plugin.class).toInstance(pl);
        bind(IProxyConfig.class).to(Config.class);
        bind(IProxy.class).to(BungeeProxy.class);

        install(new ProxyModule());

        bind(Logger.class).toInstance(new OverrideLogger(pl.getLogger()));
    }
}
