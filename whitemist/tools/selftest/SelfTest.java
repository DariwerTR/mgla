import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.whitemist.config.WhiteMistConfig;
import com.whitemist.core.MistCycle;
import com.whitemist.core.MistCycle.Phase;
import com.whitemist.core.MistCycle.Transition;
import com.whitemist.core.NoiseMath;

/** Dependency-free self test of the pure logic. Run: see tools/run_selftest.sh */
public class SelfTest {
	private static int passed = 0;
	private static int failed = 0;

	private static void check(boolean cond, String name) {
		if (cond) {
			passed++;
		} else {
			failed++;
			System.out.println("FAIL: " + name);
		}
	}

	private static void near(double a, double b, double eps, String name) {
		check(Math.abs(a - b) <= eps, name + " (got " + a + ", expected " + b + ")");
	}

	private static final MistCycle.Tuning T = new MistCycle.Tuning(100, 2000, 3000, 200, 400, 600, 200, 0.5);

	public static void main(String[] args) throws Exception {
		testCycleOrderAndIntensity();
		testCycleEscalation();
		testForceStartStopContinuity();
		testRestoreGarbage();
		testDeterminismAndLongRun();
		testNoiseMath();
		testConfig();
		System.out.println("passed=" + passed + " failed=" + failed);
		if (failed > 0) {
			System.exit(1);
		}
	}

	private static void testCycleOrderAndIntensity() {
		MistCycle c = new MistCycle(T);
		Random rng = new Random(1);
		List<Transition> seen = new ArrayList<>();
		double prev = 0;
		boolean monotonicRise = true;
		int guard = 0;
		while (seen.size() < 4 && guard++ < 100000) {
			Phase before = c.phase();
			Transition tr = c.tick(T, rng);
			if (tr != Transition.NONE) {
				seen.add(tr);
			}
			double i = c.intensity();
			check(i >= 0.0 && i <= 1.0, "intensity in [0,1]");
			if (before == Phase.RISING && c.phase() == Phase.RISING && i + 1e-9 < prev) {
				monotonicRise = false;
			}
			prev = i;
		}
		check(seen.equals(List.of(Transition.RISING_STARTED, Transition.PEAK_REACHED,
				Transition.FADING_STARTED, Transition.CLEARED)), "full cycle transition order: " + seen);
		check(monotonicRise, "intensity rises monotonically");
		check(c.mistCount() == 1, "mistCount increments after a full cycle");
		check(c.phase() == Phase.CLEAR && c.intensity() == 0.0, "back to clear");
	}

	private static void testCycleEscalation() {
		MistCycle c = MistCycle.restore(Phase.FADING.ordinal(), 199, 200, 0);
		Random rng = new Random(7);
		// finish many cycles; clear periods must shrink and never go below the minimum
		int lastClear = Integer.MAX_VALUE;
		boolean shrinks = true;
		boolean aboveMin = true;
		for (int n = 0; n < 12; n++) {
			int guard = 0;
			while (c.tick(T, rng) != Transition.CLEARED && guard++ < 1_000_000) { /* run */ }
			int len = c.phaseLength();
			if (len < MistCycle.MIN_CLEAR_TICKS) {
				aboveMin = false;
			}
			if (n > 1 && len > 3000) {
				shrinks = false; // a base roll can never exceed clearMax even without escalation
			}
			lastClear = len;
			// advance through the clear phase quickly
			c = MistCycle.restore(Phase.FADING.ordinal(), 199, 200, c.mistCount());
		}
		check(aboveMin, "clear period never below MIN_CLEAR_TICKS");
		check(shrinks, "clear period never above clearMax");
		check(c.escalationSteps() == MistCycle.MAX_ESCALATION_STEPS, "escalation steps are capped");
		check(lastClear >= MistCycle.MIN_CLEAR_TICKS, "last clear valid");

		// clear pauses shrink with escalation (same random seed, different mist count)
		MistCycle early = MistCycle.restore(Phase.FADING.ordinal(), 199, 200, 0);
		early.tick(T, new Random(5));
		MistCycle later = MistCycle.restore(Phase.FADING.ordinal(), 199, 200, 8);
		later.tick(T, new Random(5));
		check(early.phase() == Phase.CLEAR && later.phase() == Phase.CLEAR, "both reached clear");
		check(later.phaseLength() < early.phaseLength(),
				"later pauses are shorter (" + later.phaseLength() + " < " + early.phaseLength() + ")");

		// active phase grows with escalation
		MistCycle fresh = MistCycle.restore(Phase.RISING.ordinal(), 199, 200, 0);
		fresh.tick(T, new Random(3));
		int activeFresh = fresh.phaseLength();
		MistCycle late = MistCycle.restore(Phase.RISING.ordinal(), 199, 200, 10);
		late.tick(T, new Random(3));
		check(late.phaseLength() > activeFresh, "later mists last longer");
	}

	private static void testForceStartStopContinuity() {
		MistCycle c = MistCycle.restore(Phase.FADING.ordinal(), 50, 200, 2); // intensity 0.75
		double before = c.intensity();
		c.forceStart(T);
		check(c.phase() == Phase.RISING, "forceStart from fading -> rising");
		near(c.intensity(), before, 0.01, "forceStart keeps fog continuous");

		c = MistCycle.restore(Phase.RISING.ordinal(), 50, 200, 0); // intensity 0.25
		before = c.intensity();
		c.forceStop(T);
		check(c.phase() == Phase.FADING, "forceStop from rising -> fading");
		near(c.intensity(), before, 0.01, "forceStop keeps fog continuous");

		c = new MistCycle(T);
		c.forceStop(T);
		check(c.phase() == Phase.CLEAR, "forceStop in clear weather is a no-op");
		c.forceStart(T);
		check(c.phase() == Phase.RISING, "forceStart in clear weather begins rising");
		c.forceStart(T);
		check(c.phase() == Phase.RISING, "forceStart twice is a no-op");
	}

	private static void testRestoreGarbage() {
		MistCycle c = MistCycle.restore(-5, -100, 0, -3);
		check(c.phase() == Phase.CLEAR && c.phaseLength() >= 1 && c.ticksInPhase() >= 0 && c.mistCount() == 0,
				"restore sanitizes negative values");
		c = MistCycle.restore(99, 10_000, 100, 4);
		check(c.phase() == Phase.CLEAR && c.ticksInPhase() <= c.phaseLength(), "restore sanitizes huge values");
		c.tick(T, new Random(1)); // must not throw or loop forever
		check(true, "ticking a restored garbage cycle works");
	}

	private static void testDeterminismAndLongRun() {
		MistCycle a = new MistCycle(T);
		MistCycle b = new MistCycle(T);
		Random ra = new Random(42);
		Random rb = new Random(42);
		boolean same = true;
		int transitions = 0;
		for (int i = 0; i < 500_000; i++) {
			Transition ta = a.tick(T, ra);
			Transition tb = b.tick(T, rb);
			if (ta != tb || a.intensity() != b.intensity()) {
				same = false;
			}
			if (ta != Transition.NONE) {
				transitions++;
			}
			double in = a.intensity();
			if (in < 0 || in > 1 || Double.isNaN(in)) {
				same = false;
			}
		}
		check(same, "same seed -> same behaviour; intensity always valid");
		check(transitions > 50, "long run keeps cycling (" + transitions + " transitions)");
	}

	private static void testNoiseMath() {
		NoiseMath.Params p = new NoiseMath.Params(6, 1.5, 14, 0.25, 0.5);
		near(NoiseMath.movementLoudness(false, false, true, false, p), 0, 0, "standing still is silent");
		near(NoiseMath.movementLoudness(true, false, false, false, p), 6, 0, "walking");
		near(NoiseMath.movementLoudness(true, true, false, false, p), 1.5, 0, "sneaking");
		near(NoiseMath.movementLoudness(true, false, true, false, p), 14, 0, "sprinting");
		near(NoiseMath.movementLoudness(true, false, true, true, p), 3.5, 1e-9, "sprinting on wool");
		check(NoiseMath.movementLoudness(true, false, true, true, p)
				< NoiseMath.movementLoudness(true, false, false, false, p), "sprinting on wool quieter than walking on stone");
		near(NoiseMath.attenuate(16, 0, 0.5), 16, 1e-9, "no dampeners");
		near(NoiseMath.attenuate(16, 2, 0.5), 4, 1e-9, "two dampeners");
		near(NoiseMath.attenuate(16, -3, 0.5), 16, 1e-9, "negative count treated as zero");
		near(NoiseMath.attenuate(-1, 1, 0.5), 0, 0, "negative loudness is silent");
		check(NoiseMath.isAudible(10, 10), "boundary audible");
		check(!NoiseMath.isAudible(10, 10.01), "just out of range");
		check(!NoiseMath.isAudible(0, 0), "zero loudness is never audible");
		check(NoiseMath.audibilityScore(12, 3) > NoiseMath.audibilityScore(12, 9), "closer sound scores higher");
		near(NoiseMath.meterFraction(7, 14), 0.5, 1e-9, "meter half");
		near(NoiseMath.meterFraction(99, 14), 1.0, 0, "meter clamps high");
		near(NoiseMath.meterFraction(5, 0), 0.0, 0, "meter with zero max");
	}

	private static void testConfig() throws Exception {
		WhiteMistConfig c = new WhiteMistConfig();
		c.clearTicksMin = 50000;
		c.clearTicksMax = 1500;
		c.activeTicksMin = 9000;
		c.activeTicksMax = 300;
		c.spawnMinDistance = 60;
		c.spawnMaxDistance = 10;
		c.listenerHearingScale = Double.NaN;
		c.woolBetweenFactor = 7;
		c.maxListenersPerPlayer = -4;
		c.sanitize();
		check(c.clearTicksMin <= c.clearTicksMax, "sanitize swaps clear min/max");
		check(c.activeTicksMin <= c.activeTicksMax, "sanitize swaps active min/max");
		check(c.spawnMaxDistance > c.spawnMinDistance, "sanitize keeps spawn distances ordered");
		check(!Double.isNaN(c.listenerHearingScale) && c.listenerHearingScale >= 0.1, "NaN hearing scale fixed");
		check(c.woolBetweenFactor <= 1.0, "wool factor clamped");
		check(c.maxListenersPerPlayer >= 0, "negative cap clamped");
		MistCycle.Tuning t = c.mistTuning();
		check(t.clearMin() <= t.clearMax() && t.activeMin() <= t.activeMax(), "tuning is consistent");
		check(c.meterMax() >= 1.0, "meterMax positive");

		Path dir = Files.createTempDirectory("whitemist-test");
		Path file = dir.resolve("sub").resolve("whitemist.json");
		List<String> warnings = new ArrayList<>();
		WhiteMistConfig loaded = WhiteMistConfig.load(file, warnings::add);
		check(Files.exists(file), "missing config file is created (including parent dirs)");
		check(warnings.isEmpty(), "no warnings for a fresh config: " + warnings);
		check(loaded.firstMistDelayTicks == 24000, "defaults loaded");
	}
}
