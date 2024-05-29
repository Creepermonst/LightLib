package net.tls.lightlib;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LightLib implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("Light Lib");
	public static final String MOD_ID = "lightlib";

	@Override
	public void onInitialize(ModContainer mod) {
		LOGGER.info("Lighting up your world with {}! ", mod.metadata().name());
	}
}
