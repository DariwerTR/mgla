package com.whitemist.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A tall, gaunt, faceless figure with huge ears and arms that hang below the knees.
 * Texture: 64x64 (assets/whitemist/textures/entity/listener.png). The model is 36 px (2.25 blocks) high.
 * <p>
 * UV layout (every cuboid occupies a 2*(w+d) by (d+h) net):
 * head (0,0) 32x16 | ears (32,0) 8x7 | body (0,16) 20x18 | arms (20,16) 8x20 | legs (28,16) 12x17
 */
public final class ListenerModel extends EntityModel<ListenerRenderState> {
	public static final int TEXTURE_WIDTH = 64;
	public static final int TEXTURE_HEIGHT = 64;

	private static final String HEAD = "head";
	private static final String BODY = "body";
	private static final String LEFT_ARM = "left_arm";
	private static final String RIGHT_ARM = "right_arm";
	private static final String LEFT_LEG = "left_leg";
	private static final String RIGHT_LEG = "right_leg";

	private static final float DEG_TO_RAD = (float) Math.PI / 180.0F;

	private final ModelPart head;
	private final ModelPart leftArm;
	private final ModelPart rightArm;
	private final ModelPart leftLeg;
	private final ModelPart rightLeg;

	public ListenerModel(ModelPart root) {
		super(root);
		this.head = root.getChild(HEAD);
		this.leftArm = root.getChild(LEFT_ARM);
		this.rightArm = root.getChild(RIGHT_ARM);
		this.leftLeg = root.getChild(LEFT_LEG);
		this.rightLeg = root.getChild(RIGHT_LEG);
	}

	public static LayerDefinition getTexturedModelData() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition head = root.addOrReplaceChild(HEAD,
				CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
				PartPose.offset(0.0F, -4.0F, 0.0F));
		// Huge flat ears. They are children of the head, so they turn with it.
		head.addOrReplaceChild("left_ear",
				CubeListBuilder.create().texOffs(32, 0).addBox(4.0F, -7.0F, -0.5F, 3.0F, 6.0F, 1.0F),
				PartPose.ZERO);
		head.addOrReplaceChild("right_ear",
				CubeListBuilder.create().texOffs(32, 0).addBox(-7.0F, -7.0F, -0.5F, 3.0F, 6.0F, 1.0F),
				PartPose.ZERO);

		root.addOrReplaceChild(BODY,
				CubeListBuilder.create().texOffs(0, 16).addBox(-3.0F, 0.0F, -2.0F, 6.0F, 14.0F, 4.0F),
				PartPose.offset(0.0F, -4.0F, 0.0F));
		root.addOrReplaceChild(RIGHT_ARM,
				CubeListBuilder.create().texOffs(20, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 18.0F, 2.0F),
				PartPose.offset(-4.0F, -3.0F, 0.0F));
		root.addOrReplaceChild(LEFT_ARM,
				CubeListBuilder.create().texOffs(20, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 18.0F, 2.0F),
				PartPose.offset(4.0F, -3.0F, 0.0F));
		root.addOrReplaceChild(RIGHT_LEG,
				CubeListBuilder.create().texOffs(28, 16).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 14.0F, 3.0F),
				PartPose.offset(-1.5F, 10.0F, 0.0F));
		root.addOrReplaceChild(LEFT_LEG,
				CubeListBuilder.create().texOffs(28, 16).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 14.0F, 3.0F),
				PartPose.offset(1.5F, 10.0F, 0.0F));

		return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
	}

	@Override
	public void setupAnim(ListenerRenderState state) {
		super.setupAnim(state);

		this.head.xRot = state.xRot * DEG_TO_RAD;
		this.head.yRot = state.yRot * DEG_TO_RAD;

		float swingSpeed = state.walkAnimationSpeed;
		float swingPos = state.walkAnimationPos;
		this.rightLeg.xRot = Mth.cos(swingPos * 0.6662F) * 1.2F * swingSpeed;
		this.leftLeg.xRot = Mth.cos(swingPos * 0.6662F + Mth.PI) * 1.2F * swingSpeed;

		// The long arms hang almost still and sway slowly, which looks wrong in a good way.
		float sway = Mth.sin(state.ageInTicks * 0.05F) * 0.04F;
		this.rightArm.xRot = Mth.cos(swingPos * 0.6662F + Mth.PI) * 0.3F * swingSpeed;
		this.leftArm.xRot = Mth.cos(swingPos * 0.6662F) * 0.3F * swingSpeed;
		this.rightArm.zRot = 0.05F + sway;
		this.leftArm.zRot = -0.05F - sway;
	}
}
