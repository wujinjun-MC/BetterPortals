package com.lauriethefish.betterportals.velocity;

import com.velocitypowered.api.plugin.Plugin;
import org.slf4j.Logger;

import javax.inject.Inject;

@Plugin(id = "better-portals", name = "BetterPortals", version = "0.10.0", authors = "lauriethefish@outlook.com")
public class BetterPortals {
    @Inject
    public BetterPortals(Logger logger) {
        logger.info("BetterPortals loaded!");
    }
}
