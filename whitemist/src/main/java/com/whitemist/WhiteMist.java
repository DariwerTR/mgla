package com.whitemist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import com.whitemist.command.WhiteMistCommands;
import com.whitemist.config.WhiteMistConfig;
import com.whitemist.entity.ModEntities;
import com.whitemist.mist.MistController;
import com.whitemist.noise.NoiseListeners;

public class WhiteMist implements ModInitializer {
	public static final String MOD_ID = "whitemist";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static WhiteMistConfig config = new WhiteMistConfig();

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static WhiteMistConfig config() {
		return config;
	}

	/** (Re)loads {@code config/whitemist.json}. Never throws. */
	public static void reloadConfig() {
		config = WhiteMistConfig.load(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"), LOGGER::warn);
	}

	@Override
	public void onInitialize() {
		reloadConfig();
		ModEntities.initialize();
		NoiseListeners.register();
		MistController.register();
		WhiteMistCommands.register();
		LOGGER.info("White Mist is rising. Stay quiet.");
	}
}
