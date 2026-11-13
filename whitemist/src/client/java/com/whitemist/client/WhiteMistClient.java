package com.whitemist.client;

import net.minecraft.client.renderer.entity.EntityRenderers;

import net.fabricmc.api.ClientModInitializer;

import com.whitemist.client.hud.NoiseMeterHud;
import com.whitemist.client.render.ListenerRenderer;
import com.whitemist.client.render.ModModelLayers;
import com.whitemist.entity.ModEntities;

public class WhiteMistClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModModelLayers.register();
		EntityRenderers.register(ModEntities.LISTENER, ListenerRenderer::new);
		NoiseMeterHud.register();
	}
}
