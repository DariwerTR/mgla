package com.whitemist.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import com.whitemist.core.MistCycle;
import com.whitemist.core.NoiseMath;

/**
 * User configuration ({@code config/whitemist.json}). Plain data class: Gson fills public fields,
 * missing fields keep their defaults, unknown fields are ignored, a broken file never crashes the game.
 * Time values are in game ticks (20 ticks = 1 second, 24000 ticks = 1 Minecraft day).
 */
public final class WhiteMistConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// ---- mist cycle -------------------------------------------------------------------------
	/** Ticks of clear weather in a new world before the very first mist. */
	public int firstMistDelayTicks = 24000;
	public int clearTicksMin = 24000;
	public int clearTicksMax = 48000;
	public int riseTicks = 2400;
	public int activeTicksMin = 6000;
	public int activeTicksMax = 12000;
	public int fadeTicks = 2400;
	/** Each completed mist makes the next one this much longer (0.1 = +10%) and the pauses shorter. */
	public double escalationPerMist = 0.10;
	/** Show the overlay messages when the mist starts and ends. */
	public boolean announce = true;

	// ---- Listeners --------------------------------------------------------------------------
	public int maxListenersPerPlayer = 2;
	public int spawnIntervalTicks = 200;
	public int spawnMinDistance = 24;
	public int spawnMaxDistance = 40;
	/** Damage of one Listener strike (2.0 = one heart). */
	public float listenerDamage = 7.0F;
	/** Ticks of calm (no mist) after which a Listener fades away. */
	public int dissolveDelayTicks = 200;
	/** Multiplies the hearing radius of every Listener. */
	public double listenerHearingScale = 1.0;
	/** Listeners can only hear sounds that are at most this old (ticks). */
	public int noiseLifetimeTicks = 30;

	// ---- noise (hearing radius in blocks) ---------------------------------------------------
	public double noiseWalk = 6.0;
	public double noiseSneak = 1.5;
	public double noiseSprint = 14.0;
	public double noiseBlockBreak = 16.0;
	public double noiseBlockUse = 9.0;
	public double noiseAttack = 12.0;
	public double noiseHurt = 18.0;
	/** Footsteps on wool / carpet are multiplied by this. */
	public double woolUnderfootFactor = 0.25;
	/** Every wool block between a sound and a Listener multiplies the loudness by this. */
	public double woolBetweenFactor = 0.5;

	// ---- client -----------------------------------------------------------------------------
	public boolean showNoiseMeter = true;

	/** Brings every value into a safe range. Never throws. */
	public void sanitize() {
		firstMistDelayTicks = clamp(firstMistDelayTicks, 20, 24000 * 365);
		clearTicksMin = clamp(clearTicksMin, 1200, 24000 * 365);
		clearTicksMax = clamp(clearTicksMax, 1200, 24000 * 365);
		if (clearTicksMax < clearTicksMin) {
			int tmp = clearTicksMin;
			clearTicksMin = clearTicksMax;
			clearTicksMax = tmp;
		}
		riseTicks = clamp(riseTicks, 20, 24000 * 7);
		fadeTicks = clamp(fadeTicks, 20, 24000 * 7);
		activeTicksMin = clamp(activeTicksMin, 200, 24000 * 30);
		activeTicksMax = clamp(activeTicksMax, 200, 24000 * 30);
		if (activeTicksMax < activeTicksMin) {
			int tmp = activeTicksMin;
			activeTicksMin = activeTicksMax;
			activeTicksMax = tmp;
		}
		escalationPerMist = clamp(escalationPerMist, 0.0, 2.0);

		maxListenersPerPlayer = clamp(maxListenersPerPlayer, 0, 12);
		spawnIntervalTicks = clamp(spawnIntervalTicks, 20, 24000);
		spawnMinDistance = clamp(spawnMinDistance, 8, 96);
		spawnMaxDistance = clamp(spawnMaxDistance, spawnMinDistance + 1, 128);
		listenerDamage = (float) clamp(listenerDamage, 0.0, 200.0);
		dissolveDelayTicks = clamp(dissolveDelayTicks, 20, 24000);
		listenerHearingScale = clamp(listenerHearingScale, 0.1, 4.0);
		noiseLifetimeTicks = clamp(noiseLifetimeTicks, 5, 200);

		noiseWalk = clamp(noiseWalk, 0.0, 64.0);
		noiseSneak = clamp(noiseSneak, 0.0, 64.0);
		noiseSprint = clamp(noiseSprint, 0.0, 64.0);
		noiseBlockBreak = clamp(noiseBlockBreak, 0.0, 64.0);
		noiseBlockUse = clamp(noiseBlockUse, 0.0, 64.0);
		noiseAttack = clamp(noiseAttack, 0.0, 64.0);
		noiseHurt = clamp(noiseHurt, 0.0, 64.0);
		woolUnderfootFactor = clamp(woolUnderfootFactor, 0.0, 1.0);
		woolBetweenFactor = clamp(woolBetweenFactor, 0.0, 1.0);
	}

	public MistCycle.Tuning mistTuning() {
		return new MistCycle.Tuning(firstMistDelayTicks, clearTicksMin, clearTicksMax, riseTicks,
				activeTicksMin, activeTicksMax, fadeTicks, escalationPerMist);
	}

	public NoiseMath.Params noiseParams() {
		return new NoiseMath.Params(noiseWalk, noiseSneak, noiseSprint, woolUnderfootFactor, woolBetweenFactor);
	}

	/** Loudest sound the HUD meter can show. */
	public double meterMax() {
		return Math.max(1.0, Math.max(noiseSprint, noiseHurt));
	}

	/**
	 * Loads the config. If the file is missing it is created with defaults; if it is unreadable the
	 * defaults are used and a warning is passed to {@code warn}.
	 */
	public static WhiteMistConfig load(Path file, Consumer<String> warn) {
		WhiteMistConfig config = new WhiteMistConfig();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				WhiteMistConfig parsed = GSON.fromJson(reader, WhiteMistConfig.class);
				if (parsed != null) {
					config = parsed;
				}
			} catch (IOException | JsonParseException e) {
				warn.accept("Could not read " + file.getFileName() + ", using defaults: " + e.getMessage());
				config = new WhiteMistConfig();
			}
		}
		config.sanitize();
		config.save(file, warn);
		return config;
	}

	/** Writes the (sanitized) config back so that new options appear in the file after updates. */
	public void save(Path file, Consumer<String> warn) {
		try {
			Path parent = file.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			warn.accept("Could not write " + file.getFileName() + ": " + e.getMessage());
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		if (Double.isNaN(value)) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}
}
