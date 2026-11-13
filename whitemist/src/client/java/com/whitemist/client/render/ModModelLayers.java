package com.whitemist.client.render;

import net.minecraft.client.model.geom.ModelLayerLocation;

import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import com.whitemist.WhiteMist;

public final class ModModelLayers {
	public static final ModelLayerLocation LISTENER = new ModelLayerLocation(WhiteMist.id("listener"), "main");

	private ModModelLayers() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LISTENER, ListenerModel::getTexturedModelData);
	}
}
