package com.whitemist.noise;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.whitemist.config.WhiteMistConfig;
import com.whitemist.core.NoiseMath;

/** Turns player movement into noise events (every {@value #INTERVAL} ticks while moving). */
public final class PlayerNoiseTracker {
	private static final int INTERVAL = 8;
	/** Minimal horizontal speed (blocks per tick) that counts as "moving". Walking is ~0.2, sneaking ~0.065. */
	private static final double MOVING_SPEED = 0.03;
	/** Faster than this is a teleport / elytra, not footsteps. */
	private static final double MAX_FOOTSTEP_SPEED = 1.0;

	/** UUID -> {x, z, tick of the last sample}. */
	private static final Map<UUID, double[]> LAST = new HashMap<>();

	private PlayerNoiseTracker() {
	}

	public static void clearAll() {
		LAST.clear();
	}

	public static void tick(ServerLevel level, WhiteMistConfig config) {
		long now = level.getGameTime();
		NoiseMath.Params params = config.noiseParams();

		for (ServerPlayer player : level.players()) {
			UUID id = player.getUUID();
			double[] last = LAST.get(id);
			if (last == null) {
				LAST.put(id, new double[] {player.getX(), player.getZ(), now});
				continue;
			}

			long elapsed = now - (long) last[2];
			if (elapsed < INTERVAL) {
				continue;
			}

			double dx = player.getX() - last[0];
			double dz = player.getZ() - last[1];
			last[0] = player.getX();
			last[1] = player.getZ();
			last[2] = now;

			if (player.isSpectator() || player.isCreative()) {
				continue;
			}

			double speed = Math.sqrt(dx * dx + dz * dz) / (double) elapsed;
			boolean moving = speed > MOVING_SPEED && speed < MAX_FOOTSTEP_SPEED;

			BlockPos feet = player.blockPosition();
			boolean onDampener = NoiseTags.dampens(level.getBlockState(feet))
					|| NoiseTags.dampens(level.getBlockState(feet.below()));

			double loudness = NoiseMath.movementLoudness(moving, player.isShiftKeyDown(), player.isSprinting(),
					onDampener, params);
			NoiseManager.emit(level, player.getX(), player.getY() + 0.1, player.getZ(), loudness);
		}
	}
}
