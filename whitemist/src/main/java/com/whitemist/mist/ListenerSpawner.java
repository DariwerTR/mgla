package com.whitemist.mist;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import com.whitemist.config.WhiteMistConfig;
import com.whitemist.entity.ListenerEntity;
import com.whitemist.entity.ModEntities;

/**
 * Spawns Listeners around players while the mist is thick. We do not use vanilla spawn rules:
 * the mist is a scripted event, so the spawner is explicit and easy to reason about.
 */
final class ListenerSpawner {
	private static final int ATTEMPTS = 8;
	/** Listeners only spawn where the surface is at about the player's height (no spawning on cliffs above caves). */
	private static final double MAX_HEIGHT_DIFFERENCE = 12.0;

	private ListenerSpawner() {
	}

	static void trySpawn(ServerLevel level, WhiteMistConfig config, int escalationSteps) {
		// +1 allowed Listener for every two completed mists, up to +4.
		int cap = config.maxListenersPerPlayer + Math.min(escalationSteps / 2, 4);
		if (cap <= 0) {
			return;
		}

		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative()) {
				continue;
			}

			AABB area = player.getBoundingBox().inflate(config.spawnMaxDistance + 24.0);
			List<ListenerEntity> nearby = level.getEntitiesOfClass(ListenerEntity.class, area);
			if (nearby.size() >= cap) {
				continue;
			}

			for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
				if (spawnOne(level, player, config, random)) {
					break;
				}
			}
		}
	}

	private static boolean spawnOne(ServerLevel level, ServerPlayer player, WhiteMistConfig config, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		double distance = config.spawnMinDistance
				+ random.nextDouble() * (config.spawnMaxDistance - config.spawnMinDistance);
		int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
		int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);

		BlockPos column = new BlockPos(x, Mth.floor(player.getY()), z);
		if (!level.hasChunkAt(column)) {
			return false; // never force-load chunks just to spawn something
		}

		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		if (Math.abs(top.getY() - player.getY()) > MAX_HEIGHT_DIFFERENCE) {
			return false;
		}
		if (!level.getBlockState(top.below()).getFluidState().isEmpty()) {
			return false; // do not spawn on water
		}

		ListenerEntity listener = ModEntities.LISTENER.create(level, EntitySpawnReason.TRIGGERED);
		if (listener == null) {
			return false;
		}
		listener.setPos(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
		if (!level.noCollision(listener.getBoundingBox())) {
			listener.discard();
			return false;
		}
		return level.addFreshEntity(listener);
	}
}
