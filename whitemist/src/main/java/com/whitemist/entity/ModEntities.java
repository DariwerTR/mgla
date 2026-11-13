package com.whitemist.entity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import com.whitemist.WhiteMist;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> LISTENER_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, WhiteMist.id("listener"));

	public static final EntityType<ListenerEntity> LISTENER = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			LISTENER_KEY,
			EntityType.Builder.of(ListenerEntity::new, MobCategory.MONSTER)
					.sized(0.7F, 2.3F)
					.clientTrackingRange(10)
					.build(LISTENER_KEY)
	);

	private ModEntities() {
	}

	/** Forces the class to load (which registers the entity type) and registers the attributes. */
	public static void initialize() {
		FabricDefaultAttributeRegistry.register(LISTENER, ListenerEntity.createListenerAttributes());
	}
}
