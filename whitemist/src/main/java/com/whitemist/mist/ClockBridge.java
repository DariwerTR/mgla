package com.whitemist.mist;

import net.minecraft.server.MinecraftServer;

/**
 * The only place that drives the visual fog. The fog itself is data-driven
 * (data/whitemist/timeline/mist.json reads the world clock {@code whitemist:mist}); the server moves
 * that clock with the vanilla {@code /time of <clock> ...} command, silently.
 * <p>
 * Isolated in one class because it contains the one call that is not confirmed in the 26.3 corpus
 * ({@code withSuppressedOutput}). If it ever fails to compile, fix this single line.
 */
final class ClockBridge {
	static final String CLOCK_ID = "whitemist:mist";
	/** Must equal the last keyframe tick in data/whitemist/timeline/mist.json. */
	static final int CLOCK_MAX = 2400;

	private ClockBridge() {
	}

	static void pause(MinecraftServer server) {
		run(server, "time of " + CLOCK_ID + " pause");
	}

	static void set(MinecraftServer server, int ticks) {
		int clamped = Math.max(0, Math.min(CLOCK_MAX, ticks));
		run(server, "time of " + CLOCK_ID + " set " + clamped);
	}

	private static void run(MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}
}
