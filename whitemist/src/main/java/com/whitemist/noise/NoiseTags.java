package com.whitemist.noise;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks that muffle sound. We reuse the vanilla tag that makes sculk sensors ignore vibrations
 * (wool, carpets, wool slabs and stairs), referenced by id so it does not depend on Java constants.
 */
public final class NoiseTags {
	public static final TagKey<Block> DAMPENS = TagKey.create(Registries.BLOCK,
			Identifier.fromNamespaceAndPath("minecraft", "dampens_vibrations"));

	private NoiseTags() {
	}

	public static boolean dampens(BlockState state) {
		return state.is(DAMPENS);
	}
}
