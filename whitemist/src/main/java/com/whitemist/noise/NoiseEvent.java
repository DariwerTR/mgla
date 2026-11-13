package com.whitemist.noise;

/** One audible event: where it happened, how loud it was (hearing radius in blocks) and when. */
public record NoiseEvent(double x, double y, double z, double loudness, long tick) {
}
