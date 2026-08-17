package com.client.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoreBootstrap {
    public static final String MOD_ID = "clientcore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    public static String TELEGRAM_BOT_TOKEN = "";
    public static String TELEGRAM_ADMIN_ID = "";
    public static String TELEGRAM_GROUP_ID = "";
    public static String TELEGRAM_BOT_API = "";
    
    private static boolean initialized = false;
    
    public static void init() {
        if (initialized) return;
        initialized = true;
        
        LOGGER.info("CoreBootstrap Fabric 1.21.4 initialized!");
    }
}