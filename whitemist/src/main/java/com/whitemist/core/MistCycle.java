package com.whitemist.core;

import java.util.Random;

/**
 * Pure state machine of the White Mist cycle. Contains NO Minecraft code so it can be unit-tested.
 *
 * <pre>
 * CLEAR --(timer)--> RISING --> ACTIVE --> FADING --> CLEAR ...
 * </pre>
 * Every completed mist makes the next ones longer and the pauses between them shorter.
 */
public final class MistCycle {
	/** Cap of the escalation steps so that numbers stay sane in very long worlds. */
	public static final int MAX_ESCALATION_STEPS = 10;
	/** The pause between two mists never gets shorter than this (1 minute). */
	public static final int MIN_CLEAR_TICKS = 1200;

	public enum Phase { CLEAR, RISING, ACTIVE, FADING }

	public enum Transition { NONE, RISING_STARTED, PEAK_REACHED, FADING_STARTED, CLEARED }

	/** All timings in ticks. */
	public record Tuning(int firstDelay, int clearMin, int clearMax, int riseTicks,
						int activeMin, int activeMax, int fadeTicks, double escalation) {
	}

	private Phase phase;
	private int ticksInPhase;
	private int phaseLength;
	private int mistCount;

	/** A brand new world: a clear period of {@code firstDelay} ticks, then the first mist. */
	public MistCycle(Tuning tuning) {
		this.phase = Phase.CLEAR;
		this.ticksInPhase = 0;
		this.phaseLength = Math.max(1, tuning.firstDelay());
		this.mistCount = 0;
	}

	private MistCycle(Phase phase, int ticksInPhase, int phaseLength, int mistCount) {
		this.phase = phase;
		this.ticksInPhase = ticksInPhase;
		this.phaseLength = phaseLength;
		this.mistCount = mistCount;
	}

	/** Restores a saved cycle; tolerant to garbage (corrupt data never crashes the world). */
	public static MistCycle restore(int phaseOrdinal, int ticksInPhase, int phaseLength, int mistCount) {
		Phase[] all = Phase.values();
		Phase p = (phaseOrdinal >= 0 && phaseOrdinal < all.length) ? all[phaseOrdinal] : Phase.CLEAR;
		int length = Math.max(1, phaseLength);
		int ticks = Math.min(Math.max(0, ticksInPhase), length);
		return new MistCycle(p, ticks, length, Math.max(0, mistCount));
	}

	/** Advances the cycle by exactly one game tick. */
	public Transition tick(Tuning t, Random rng) {
		this.ticksInPhase++;
		if (this.ticksInPhase < this.phaseLength) {
			return Transition.NONE;
		}

		switch (this.phase) {
			case CLEAR -> {
				enter(Phase.RISING, Math.max(1, t.riseTicks()));
				return Transition.RISING_STARTED;
			}
			case RISING -> {
				enter(Phase.ACTIVE, rollActive(t, rng));
				return Transition.PEAK_REACHED;
			}
			case ACTIVE -> {
				enter(Phase.FADING, Math.max(1, t.fadeTicks()));
				return Transition.FADING_STARTED;
			}
			default -> {
				this.mistCount++;
				enter(Phase.CLEAR, rollClear(t, rng));
				return Transition.CLEARED;
			}
		}
	}

	private void enter(Phase next, int length) {
		this.phase = next;
		this.ticksInPhase = 0;
		this.phaseLength = Math.max(1, length);
	}

	private double escalationFactor(Tuning t) {
		return 1.0 + Math.max(0.0, t.escalation()) * Math.min(this.mistCount, MAX_ESCALATION_STEPS);
	}

	private int rollClear(Tuning t, Random rng) {
		int min = Math.min(t.clearMin(), t.clearMax());
		int max = Math.max(t.clearMin(), t.clearMax());
		int base = min + rng.nextInt(max - min + 1);
		return Math.max(MIN_CLEAR_TICKS, (int) Math.round(base / escalationFactor(t)));
	}

	private int rollActive(Tuning t, Random rng) {
		int min = Math.min(t.activeMin(), t.activeMax());
		int max = Math.max(t.activeMin(), t.activeMax());
		int base = min + rng.nextInt(max - min + 1);
		return Math.max(1, (int) Math.round(base * escalationFactor(t)));
	}

	/** Fog density in [0..1]: 0 = clear sky, 1 = full white mist. */
	public double intensity() {
		double progress = Math.min(1.0, (double) this.ticksInPhase / (double) this.phaseLength);
		return switch (this.phase) {
			case CLEAR -> 0.0;
			case RISING -> progress;
			case ACTIVE -> 1.0;
			case FADING -> 1.0 - progress;
		};
	}

	/** True while there is any fog at all (rising, active or fading). */
	public boolean isMistPresent() {
		return this.phase != Phase.CLEAR;
	}

	/** Starts the mist now (admin command). Keeps the fog continuous if it was fading. */
	public void forceStart(Tuning t) {
		switch (this.phase) {
			case CLEAR -> enter(Phase.RISING, Math.max(1, t.riseTicks()));
			case FADING -> {
				double current = intensity();
				enter(Phase.RISING, Math.max(1, t.riseTicks()));
				this.ticksInPhase = (int) Math.round(current * this.phaseLength);
			}
			default -> { /* already rising or active */ }
		}
	}

	/** Ends the mist now (admin command) with a smooth fade-out. */
	public void forceStop(Tuning t) {
		switch (this.phase) {
			case RISING, ACTIVE -> {
				double current = intensity();
				enter(Phase.FADING, Math.max(1, t.fadeTicks()));
				this.ticksInPhase = (int) Math.round((1.0 - current) * this.phaseLength);
			}
			default -> { /* already clear or fading */ }
		}
	}

	public Phase phase() {
		return this.phase;
	}

	public int ticksInPhase() {
		return this.ticksInPhase;
	}

	public int phaseLength() {
		return this.phaseLength;
	}

	public int mistCount() {
		return this.mistCount;
	}

	/** How many escalation steps (extra Listeners, longer mists) are currently in effect. */
	public int escalationSteps() {
		return Math.min(this.mistCount, MAX_ESCALATION_STEPS);
	}
}
