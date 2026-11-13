package com.whitemist.mist;

import java.util.Random;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import com.whitemist.WhiteMist;
import com.whitemist.config.WhiteMistConfig;
import com.whitemist.core.MistCycle;
import com.whitemist.core.MistCycle.Phase;
import com.whitemist.core.MistCycle.Transition;
import com.whitemist.noise.NoiseManager;
import com.whitemist.noise.PlayerNoiseTracker;

/**
 * Server-side brain of the mist: advances the cycle in the Overworld, moves the world clock that drives
 * the fog, announces the phases, spawns Listeners and feeds the noise system.
 */
public final class MistController {
	private static final Random RNG = new Random();
	/** The fog follows the intensity in steps of one second; the timeline interpolates in between. */
	private static final int CLOCK_UPDATE_INTERVAL = 20;
	private static final int AUTOSAVE_INTERVAL = 600;
	/** Listeners start to appear when the fog is at least this thick. */
	private static final double SPAWN_INTENSITY = 0.5;

	private static MistSavedData data;
	private static int lastClockValue = -1;

	private MistController() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(MistController::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			data = null;
			lastClockValue = -1;
		});
		ServerTickEvents.END_LEVEL_TICK.register(MistController::onLevelTick);
	}

	// ---- public API -------------------------------------------------------------------------------

	public static boolean isMistPresent(ServerLevel level) {
		return data != null && level.dimension() == Level.OVERWORLD && data.cycle().isMistPresent();
	}

	/** The current cycle, or {@code null} while no world is running. */
	public static MistCycle cycle() {
		return data == null ? null : data.cycle();
	}

	public static void forceStart(MinecraftServer server) {
		if (data == null) {
			return;
		}
		boolean wasClear = data.cycle().phase() == Phase.CLEAR;
		data.cycle().forceStart(WhiteMist.config().mistTuning());
		data.setDirty();
		syncClock(server, true);
		if (wasClear) {
			announce(server, "message.whitemist.mist_rising");
		}
	}

	public static void forceStop(MinecraftServer server) {
		if (data == null) {
			return;
		}
		data.cycle().forceStop(WhiteMist.config().mistTuning());
		data.setDirty();
		syncClock(server, true);
	}

	// ---- lifecycle --------------------------------------------------------------------------------

	private static void onServerStarted(MinecraftServer server) {
		data = MistSavedData.get(server);
		lastClockValue = -1;
		ClockBridge.pause(server); // we move the clock by hand, it must not run on its own
		syncClock(server, true);
	}

	private static void onLevelTick(ServerLevel level) {
		if (data == null || level.dimension() != Level.OVERWORLD) {
			return;
		}

		WhiteMistConfig config = WhiteMist.config();
		MistCycle cycle = data.cycle();
		long now = level.getGameTime();

		Transition transition = cycle.tick(config.mistTuning(), RNG);
		if (transition != Transition.NONE) {
			data.setDirty();
			syncClock(level.getServer(), true);
			if (config.announce) {
				announceTransition(level, transition);
			}
		} else {
			Phase phase = cycle.phase();
			if ((phase == Phase.RISING || phase == Phase.FADING) && now % CLOCK_UPDATE_INTERVAL == 0) {
				syncClock(level.getServer(), false);
			}
			if (now % AUTOSAVE_INTERVAL == 0) {
				data.setDirty();
			}
		}

		PlayerNoiseTracker.tick(level, config);
		NoiseManager.prune(level, config.noiseLifetimeTicks);

		if (cycle.intensity() >= SPAWN_INTENSITY && now % config.spawnIntervalTicks == 0) {
			ListenerSpawner.trySpawn(level, config, cycle.escalationSteps());
		}
	}

	// ---- helpers ----------------------------------------------------------------------------------

	private static void syncClock(MinecraftServer server, boolean force) {
		int target = (int) Math.round(data.cycle().intensity() * ClockBridge.CLOCK_MAX);
		if (force || target != lastClockValue) {
			lastClockValue = target;
			ClockBridge.set(server, target);
		}
	}

	private static void announceTransition(ServerLevel level, Transition transition) {
		switch (transition) {
			case RISING_STARTED -> announce(level.getServer(), "message.whitemist.mist_rising");
			case PEAK_REACHED -> announce(level.getServer(), "message.whitemist.mist_peak");
			case FADING_STARTED -> announce(level.getServer(), "message.whitemist.mist_fading");
			case CLEARED -> announce(level.getServer(), "message.whitemist.mist_cleared");
			default -> { }
		}
	}

	/** Shows a short action-bar message to every player in the Overworld. */
	private static void announce(MinecraftServer server, String translationKey) {
		ServerLevel overworld = server.overworld();
		for (ServerPlayer player : overworld.players()) {
			player.sendSystemMessage(Component.translatable(translationKey), true);
		}
	}
}
