package com.whitemist.core;

/**
 * Pure maths of the noise model. Loudness is expressed as a hearing radius in blocks:
 * a sound of loudness 10 can be heard by a Listener at most 10 blocks away (before dampening).
 */
public final class NoiseMath {
	private NoiseMath() {
	}

	/** Loudness values and dampening factors, taken from the config. */
	public record Params(double walk, double sneak, double sprint, double underfootFactor, double betweenFactor) {
	}

	/** Loudness of a moving player. A player who does not move makes no footstep noise at all. */
	public static double movementLoudness(boolean moving, boolean sneaking, boolean sprinting,
										boolean onDampener, Params p) {
		if (!moving) {
			return 0.0;
		}
		double base = sprinting ? p.sprint() : (sneaking ? p.sneak() : p.walk());
		return onDampener ? base * p.underfootFactor() : base;
	}

	/** Every dampening block (wool, carpet) between the source and the listener scales the loudness. */
	public static double attenuate(double loudness, int dampenersBetween, double perBlockFactor) {
		if (loudness <= 0.0) {
			return 0.0;
		}
		return loudness * Math.pow(perBlockFactor, Math.max(0, dampenersBetween));
	}

	public static boolean isAudible(double loudness, double distance) {
		return loudness > 0.0 && distance <= loudness;
	}

	/** Higher is better. Used to pick the most "interesting" sound among several audible ones. */
	public static double audibilityScore(double loudness, double distance) {
		return loudness - distance;
	}

	/** Linear mapping of a loudness to [0..1] for the HUD meter. */
	public static double meterFraction(double loudness, double maxLoudness) {
		if (maxLoudness <= 0.0) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, loudness / maxLoudness));
	}
}
