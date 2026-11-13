package com.whitemist.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

import com.whitemist.WhiteMist;
import com.whitemist.entity.ListenerEntity;

public class ListenerRenderer extends MobRenderer<ListenerEntity, ListenerRenderState, ListenerModel> {
	private static final Identifier TEXTURE = WhiteMist.id("textures/entity/listener.png");

	public ListenerRenderer(EntityRendererProvider.Context context) {
		super(context, new ListenerModel(context.bakeLayer(ModModelLayers.LISTENER)), 0.4F);
	}

	@Override
	public ListenerRenderState createRenderState() {
		return new ListenerRenderState();
	}

	@Override
	public Identifier getTextureLocation(ListenerRenderState state) {
		return TEXTURE;
	}
}
