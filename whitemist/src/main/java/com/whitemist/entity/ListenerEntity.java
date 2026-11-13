package com.whitemist.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.whitemist.WhiteMist;
import com.whitemist.config.WhiteMistConfig;
import com.whitemist.mist.MistController;
import com.whitemist.noise.NoiseEvent;
import com.whitemist.noise.NoiseManager;

/**
 * The Listener: blind, silent-footed and born from the mist. It cannot see anything, it only reacts to
 * sound (see {@link NoiseManager}). Anything that stands next to it is felt and struck.
 * <p>
 * All behaviour lives in {@link #tick()} on purpose: it keeps the dependency on vanilla AI classes
 * minimal (only {@code FloatGoal} and {@code RandomLookAroundGoal}).
 */
public class ListenerEntity extends Monster {
	// Vanilla sounds referenced by id, so there are no sound files to ship.
	private static final SoundEvent SOUND_CLICK = sound("block.sculk_sensor.clicking");
	private static final SoundEvent SOUND_HEARD = sound("entity.warden.listening");
	private static final SoundEvent SOUND_HEARTBEAT = sound("entity.warden.heartbeat");
	private static final SoundEvent SOUND_STRIKE = sound("entity.warden.attack_impact");

	private static final int SEARCH_TICKS = 80;
	private static final int ATTACK_COOLDOWN_TICKS = 25;
	private static final double ARRIVE_DISTANCE_SQ = 1.5 * 1.5;
	private static final double HUNT_SPEED = 1.25;
	private static final double WANDER_SPEED = 0.45;

	private boolean hasTarget;
	private double targetX;
	private double targetY;
	private double targetZ;
	private int searchTicks;
	private int attackCooldown;
	private int clickCooldown = 40;
	private int heartbeatCooldown;
	private int calmTicks;

	public ListenerEntity(EntityType<? extends ListenerEntity> type, Level level) {
		super(type, level);
	}

	private static SoundEvent sound(String vanillaPath) {
		return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("minecraft", vanillaPath));
	}

	public static AttributeSupplier.Builder createListenerAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 40.0)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.ATTACK_DAMAGE, 7.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
	}

	/** Listeners walk without a sound: you hear their clicking and heartbeat, never their steps. */
	@Override
	protected void playStepSound(BlockPos pos, BlockState blockState) {
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel serverLevel) {
			this.serverTick(serverLevel);
		}
	}

	private void serverTick(ServerLevel level) {
		WhiteMistConfig config = WhiteMist.config();

		// 1. The Listener belongs to the mist: when the mist is gone, it dissolves.
		if (MistController.isMistPresent(level)) {
			this.calmTicks = 0;
		} else if (++this.calmTicks > config.dissolveDelayTicks) {
			this.dissolve(level);
			return;
		}

		// 2. Hearing (4 times per second is plenty).
		boolean heardNow = false;
		if (this.tickCount % 5 == 0) {
			NoiseEvent heard = NoiseManager.findLoudestAudible(level, this.getX(), this.getEyeY(), this.getZ(),
					config.listenerHearingScale, config.woolBetweenFactor, config.noiseLifetimeTicks);
			if (heard != null) {
				heardNow = true;
				if (!this.hasTarget) {
					this.playSound(SOUND_HEARD, 1.2F, 0.8F + this.random.nextFloat() * 0.3F);
				}
				this.setTarget(heard.x(), heard.y(), heard.z());
				this.searchTicks = 0;
			}
		}

		// 3. Movement: investigate the last heard sound, then search around, then idle-wander.
		if (this.hasTarget) {
			double dx = this.targetX - this.getX();
			double dz = this.targetZ - this.getZ();
			boolean arrived = dx * dx + dz * dz < ARRIVE_DISTANCE_SQ;
			if (arrived && !heardNow) {
				// Nothing new to hear at the spot: stop and search around. While the sound is still
				// being made (heardNow) we keep walking, otherwise a noisy player standing right next
				// to the Listener would never be reached.
				this.hasTarget = false;
				this.searchTicks = SEARCH_TICKS;
				this.getNavigation().stop();
			} else if (heardNow || this.tickCount % 10 == 0) {
				this.walkTo(this.targetX, this.targetY, this.targetZ, HUNT_SPEED);
			}
		} else if (this.searchTicks > 0) {
			this.searchTicks--;
		} else if (this.tickCount % 120 == 0 && this.random.nextInt(3) == 0) {
			double angle = this.random.nextDouble() * Math.PI * 2.0;
			double distance = 3.0 + this.random.nextDouble() * 5.0;
			this.walkTo(this.getX() + Math.cos(angle) * distance, this.getY(),
					this.getZ() + Math.sin(angle) * distance, WANDER_SPEED);
		}

		// 4. Sound design: you hear it before you see it.
		this.tickSounds();

		// 5. Touch: it cannot see you, but whatever stands right next to it is felt and struck.
		this.tickStrike(level, config);
	}

	/**
	 * The ONLY place that asks the pathfinder to walk somewhere. {@code PathNavigation#moveTo(x, y, z, speed)}
	 * is a long-standing vanilla method, but it does not occur in any 26.3 reference code I could check
	 * (see docs/VERIFICATION_REPORT.md). If the compiler rejects this line, fix it here and nowhere else.
	 */
	private void walkTo(double x, double y, double z, double speedModifier) {
		this.getNavigation().moveTo(x, y, z, speedModifier);
	}

	private void setTarget(double x, double y, double z) {
		this.hasTarget = true;
		this.targetX = x;
		this.targetY = y;
		this.targetZ = z;
	}

	private void tickSounds() {
		boolean alert = this.hasTarget;
		if (--this.clickCooldown <= 0) {
			this.playSound(SOUND_CLICK, alert ? 1.6F : 1.0F, 0.55F + this.random.nextFloat() * 0.25F);
			this.clickCooldown = alert ? 8 + this.random.nextInt(6) : 45 + this.random.nextInt(50);
		}
		if (alert && --this.heartbeatCooldown <= 0) {
			this.playSound(SOUND_HEARTBEAT, 1.0F, 1.0F);
			this.heartbeatCooldown = 18;
		}
	}

	private void tickStrike(ServerLevel level, WhiteMistConfig config) {
		if (this.attackCooldown > 0) {
			this.attackCooldown--;
			return;
		}
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
				this.getBoundingBox().inflate(0.8, 0.3, 0.8))) {
			if (player.isSpectator() || player.isCreative()) {
				continue;
			}
			player.hurtServer(level, level.damageSources().mobAttack(this), config.listenerDamage);
			this.playSound(SOUND_STRIKE, 1.2F, 0.9F);
			this.attackCooldown = ATTACK_COOLDOWN_TICKS;
			break;
		}
	}

	/** A Listener that gets hurt feels where the blow came from. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && source.getEntity() != null) {
			this.setTarget(source.getEntity().getX(), source.getEntity().getY(), source.getEntity().getZ());
			this.searchTicks = 0;
		}
		return hurt;
	}

	private void dissolve(ServerLevel level) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(0.5), this.getZ(), 14, 0.3, 0.8, 0.3, 0.01);
		this.discard();
	}
}
