package com.client.core;

import net.fabricmc.api.ClientModInitializer;

public class ClientBootstrap implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CoreBootstrap.LOGGER.info("CoreBootstrap Fabric 1.21.4 initialized!");
    }
}
