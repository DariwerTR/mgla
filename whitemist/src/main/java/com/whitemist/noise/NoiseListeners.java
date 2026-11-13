package com.whitemist.noise;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import com.whitemist.WhiteMist;
import com.whitemist.config.WhiteMistConfig;

/** Hooks that convert player actions into noise. Registered once from the main entrypoint. */
public final class NoiseListeners {
	/** Breaking a wool block is quieter than breaking stone. */
	private static final double DAMPENED_ACTION_FACTOR = 0.4;
	/** Using a block while sneaking is quieter. */
	private static final double SNEAK_ACTION_FACTOR = 0.5;

	private NoiseListeners() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer) {
				double loudness = WhiteMist.config().noiseBlockBreak;
				if (NoiseTags.dampens(state)) {
					loudness *= DAMPENED_ACTION_FACTOR;
				}
				emitAt(serverLevel, pos, loudness);
			}
		});

		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			// The callback fires once per hand; count only the main hand.
			if (hand == InteractionHand.MAIN_HAND && level instanceof ServerLevel serverLevel
					&& player instanceof ServerPlayer && !player.isSpectator()) {
				double loudness = WhiteMist.config().noiseBlockUse;
				if (player.isShiftKeyDown()) {
					loudness *= SNEAK_ACTION_FACTOR;
				}
				emitAt(serverLevel, hitResult.getBlockPos(), loudness);
			}
			return InteractionResult.PASS;
		});

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer && !player.isSpectator()) {
				NoiseManager.emit(serverLevel, entity.getX(), entity.getY(0.5), entity.getZ(),
						WhiteMist.config().noiseAttack);
			}
			return InteractionResult.PASS;
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (amount > 0.0F && entity instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
				NoiseManager.emit(serverLevel, player.getX(), player.getY(0.5), player.getZ(),
						WhiteMist.config().noiseHurt);
			}
			return true; // never cancel the damage, we only listen
		});

		// Static state must not leak from one world into the next one (single player).
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			NoiseManager.clearAll();
			PlayerNoiseTracker.clearAll();
		});
	}

	private static void emitAt(ServerLevel level, BlockPos pos, double loudness) {
		WhiteMistConfig config = WhiteMist.config();
		if (loudness > 0.0 && config != null) {
			NoiseManager.emit(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, loudness);
		}
	}
}
