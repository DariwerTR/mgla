package com.whitemist.command;

import java.util.Locale;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import com.whitemist.WhiteMist;
import com.whitemist.core.MistCycle;
import com.whitemist.entity.ListenerEntity;
import com.whitemist.mist.MistController;

/**
 * /whitemist status          - anyone: current phase of the mist
 * /whitemist start | stop    - game masters: start or end the mist now
 * /whitemist reload          - game masters: reload config/whitemist.json
 */
public final class WhiteMistCommands {
	/** Listeners within this many blocks of the command source are counted by /whitemist status. */
	private static final double STATUS_RADIUS = 128.0;

	private WhiteMistCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("whitemist")
						.then(Commands.literal("status").executes(WhiteMistCommands::status))
						.then(Commands.literal("start").requires(WhiteMistCommands::isGameMaster)
								.executes(WhiteMistCommands::start))
						.then(Commands.literal("stop").requires(WhiteMistCommands::isGameMaster)
								.executes(WhiteMistCommands::stop))
						.then(Commands.literal("reload").requires(WhiteMistCommands::isGameMaster)
								.executes(WhiteMistCommands::reload))));
	}

	private static boolean isGameMaster(CommandSourceStack source) {
		return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	private static int status(CommandContext<CommandSourceStack> context) {
		MistCycle cycle = MistController.cycle();
		if (cycle == null) {
			context.getSource().sendFailure(Component.translatable("command.whitemist.not_running"));
			return 0;
		}

		ServerLevel level = context.getSource().getLevel();
		Vec3 pos = context.getSource().getPosition();
		AABB area = new AABB(pos.x - STATUS_RADIUS, pos.y - STATUS_RADIUS, pos.z - STATUS_RADIUS,
				pos.x + STATUS_RADIUS, pos.y + STATUS_RADIUS, pos.z + STATUS_RADIUS);
		int listeners = level.getEntitiesOfClass(ListenerEntity.class, area).size();
		int percent = (int) Math.round(cycle.intensity() * 100.0);
		int waves = cycle.mistCount();
		Component phase = Component.translatable("phase.whitemist." + cycle.phase().name().toLowerCase(Locale.ROOT));

		context.getSource().sendSuccess(
				() -> Component.translatable("command.whitemist.status", phase, percent, waves, listeners), false);
		return 1;
	}

	private static int start(CommandContext<CommandSourceStack> context) {
		MistController.forceStart(context.getSource().getServer());
		context.getSource().sendSuccess(() -> Component.translatable("command.whitemist.started"), true);
		return 1;
	}

	private static int stop(CommandContext<CommandSourceStack> context) {
		MistController.forceStop(context.getSource().getServer());
		context.getSource().sendSuccess(() -> Component.translatable("command.whitemist.stopped"), true);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		WhiteMist.reloadConfig();
		context.getSource().sendSuccess(() -> Component.translatable("command.whitemist.reloaded"), true);
		return 1;
	}
}
