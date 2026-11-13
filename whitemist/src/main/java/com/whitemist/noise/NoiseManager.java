package com.whitemist.noise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import com.whitemist.core.NoiseMath;

/**
 * Server-side registry of recent sounds. Listeners query it to find what they can hear.
 * Only touched from the server thread. Cleared when the server stops so nothing leaks between worlds.
 */
public final class NoiseManager {
	private static final int MAX_EVENTS = 256;
	private static final double SAMPLE_STEP = 0.5;
	private static final int MAX_SAMPLES = 96;
	private static final int MAX_DAMPENERS = 6;

	private static final Map<ResourceKey<Level>, List<NoiseEvent>> EVENTS = new HashMap<>();

	private NoiseManager() {
	}

	public static void emit(ServerLevel level, double x, double y, double z, double loudness) {
		if (loudness <= 0.0) {
			return;
		}
		List<NoiseEvent> list = EVENTS.computeIfAbsent(level.dimension(), key -> new ArrayList<>());
		list.add(new NoiseEvent(x, y, z, loudness, level.getGameTime()));
		if (list.size() > MAX_EVENTS) {
			list.remove(0);
		}
	}

	/** Drops sounds that are too old to be heard. */
	public static void prune(ServerLevel level, int lifetimeTicks) {
		List<NoiseEvent> list = EVENTS.get(level.dimension());
		if (list == null || list.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		list.removeIf(event -> now - event.tick() > lifetimeTicks);
	}

	public static void clearAll() {
		EVENTS.clear();
	}

	/**
	 * Finds the sound a listener at the given position would react to: the audible one with the best
	 * score (loud and close). Returns {@code null} if the listener hears nothing.
	 */
	public static NoiseEvent findLoudestAudible(ServerLevel level, double x, double y, double z,
												double hearingScale, double betweenFactor, int lifetimeTicks) {
		List<NoiseEvent> list = EVENTS.get(level.dimension());
		if (list == null || list.isEmpty()) {
			return null;
		}

		long now = level.getGameTime();
		NoiseEvent best = null;
		double bestScore = Double.NEGATIVE_INFINITY;

		for (NoiseEvent event : list) {
			if (now - event.tick() > lifetimeTicks) {
				continue;
			}
			double dx = event.x() - x;
			double dy = event.y() - y;
			double dz = event.z() - z;
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			double scaled = event.loudness() * hearingScale;
			if (!NoiseMath.isAudible(scaled, distance)) {
				continue; // cheap rejection before the block sampling
			}
			int dampeners = countDampeners(level, event.x(), event.y(), event.z(), x, y, z);
			double effective = NoiseMath.attenuate(scaled, dampeners, betweenFactor);
			if (!NoiseMath.isAudible(effective, distance)) {
				continue;
			}
			double score = NoiseMath.audibilityScore(effective, distance);
			if (score > bestScore) {
				bestScore = score;
				best = event;
			}
		}
		return best;
	}

	/** Counts muffling blocks on the straight line between two points (each block once). */
	static int countDampeners(ServerLevel level, double x1, double y1, double z1, double x2, double y2, double z2) {
		double dx = x2 - x1;
		double dy = y2 - y1;
		double dz = z2 - z1;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		int steps = Math.min(MAX_SAMPLES, (int) Math.ceil(length / SAMPLE_STEP));
		int count = 0;
		long lastKey = Long.MIN_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int i = 1; i < steps; i++) {
			double f = (double) i / (double) steps;
			pos.set(Mth.floor(x1 + dx * f), Mth.floor(y1 + dy * f), Mth.floor(z1 + dz * f));
			long key = pos.asLong();
			if (key == lastKey) {
				continue;
			}
			lastKey = key;
			if (level.hasChunkAt(pos) && NoiseTags.dampens(level.getBlockState(pos))) {
				count++;
				if (count >= MAX_DAMPENERS) {
					break;
				}
			}
		}
		return count;
	}
}
