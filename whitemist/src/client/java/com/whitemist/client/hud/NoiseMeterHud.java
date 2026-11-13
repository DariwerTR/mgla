package com.whitemist.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import com.whitemist.WhiteMist;
import com.whitemist.config.WhiteMistConfig;
import com.whitemist.core.NoiseMath;
import com.whitemist.noise.NoiseTags;

/**
 * Small bar in the bottom-right corner that shows how loud the player is RIGHT NOW. It uses the same
 * maths and the same config as the server, so what you see is what the Listeners hear. Toggle it with
 * {@code showNoiseMeter} in config/whitemist.json.
 */
public final class NoiseMeterHud {
	private static final Identifier ID = WhiteMist.id("noise_meter");

	private static final int WIDTH = 40;
	private static final int HEIGHT = 6;
	private static final int MARGIN_X = 10;
	private static final int MARGIN_Y = 12;
	/** Horizontal speed (blocks per tick) from which the player counts as moving. Same as the server. */
	private static final double MOVING_SPEED = 0.03;

	private static final int COLOR_QUIET = 0xFF4CAF50;
	private static final int COLOR_MEDIUM = 0xFFFFC107;
	private static final int COLOR_LOUD = 0xFFE53935;

	/** Smoothed value in [0..1] so the bar does not flicker. */
	private static double shown;

	private NoiseMeterHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ID, NoiseMeterHud::extract);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		WhiteMistConfig config = WhiteMist.config();
		if (!config.showNoiseMeter) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || player.isSpectator()) {
			return;
		}

		Vec3 motion = player.getDeltaMovement();
		boolean moving = Math.sqrt(motion.x * motion.x + motion.z * motion.z) > MOVING_SPEED;
		BlockPos feet = player.blockPosition();
		boolean onDampener = NoiseTags.dampens(level.getBlockState(feet))
				|| NoiseTags.dampens(level.getBlockState(feet.below()));

		double loudness = NoiseMath.movementLoudness(moving, player.isShiftKeyDown(), player.isSprinting(),
				onDampener, config.noiseParams());
		double target = NoiseMath.meterFraction(loudness, config.meterMax());
		shown += (target - shown) * 0.25;

		int x = graphics.guiWidth() - MARGIN_X - WIDTH;
		int y = graphics.guiHeight() - MARGIN_Y;

		graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0xAA000000);
		graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF1B1B1B);

		int filled = (int) Math.round(shown * WIDTH);
		if (filled > 0) {
			graphics.fill(x, y, x + filled, y + HEIGHT, colorFor((float) shown));
		}
	}

	private static int colorFor(float fraction) {
		if (fraction < 0.5F) {
			return ARGB.linearLerp(fraction * 2.0F, COLOR_QUIET, COLOR_MEDIUM);
		}
		return ARGB.linearLerp((fraction - 0.5F) * 2.0F, COLOR_MEDIUM, COLOR_LOUD);
	}
}
