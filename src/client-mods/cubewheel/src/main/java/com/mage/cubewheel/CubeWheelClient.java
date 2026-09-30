package com.mage.cubewheel;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CubeWheelClient implements ClientModInitializer {
	public static final String MOD_ID = "cubewheel";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOG.info("[cubewheel] initialised");
	}
}
