package com.lauriethefish.betterportals.velocity;

import com.lauriethefish.betterportals.shared.logging.Logger;

import java.util.logging.Level;
import java.util.logging.LogRecord;

public class VelocityLogger extends Logger {
    private final org.slf4j.Logger logger;

    protected VelocityLogger(org.slf4j.Logger logger) {
        super(logger.getName(), null);
        setLevel(Level.INFO);

        this.logger = logger;
    }

    @Override
    public void log(LogRecord record) {
        // Return if the log level is too low
        if(record.getLevel().intValue() < super.getLevel().intValue()) {return;}

        logger.info(record.getMessage());
    }
}
