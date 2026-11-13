package com.whitemist.mist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.whitemist.WhiteMist;
import com.whitemist.core.MistCycle;

/** Persists the mist cycle with the world, so a reload does not reset the timer. */
public final class MistSavedData extends SavedData {
	private static final Codec<MistSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("phase", 0).forGetter(data -> data.cycle.phase().ordinal()),
			Codec.INT.optionalFieldOf("ticks", 0).forGetter(data -> data.cycle.ticksInPhase()),
			Codec.INT.optionalFieldOf("length", 0).forGetter(data -> data.cycle.phaseLength()),
			Codec.INT.optionalFieldOf("count", 0).forGetter(data -> data.cycle.mistCount())
	).apply(instance, MistSavedData::new));

	private static final SavedDataType<MistSavedData> TYPE = new SavedDataType<>(
			WhiteMist.id("mist_cycle"),
			MistSavedData::new,
			CODEC,
			null
	);

	private final MistCycle cycle;

	/** Fresh world. */
	public MistSavedData() {
		this.cycle = new MistCycle(WhiteMist.config().mistTuning());
	}

	/** Loaded from disk (sanitized by {@link MistCycle#restore}). */
	public MistSavedData(int phase, int ticks, int length, int count) {
		this.cycle = MistCycle.restore(phase, ticks, length, count);
	}

	public MistCycle cycle() {
		return this.cycle;
	}

	public static MistSavedData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}
}
