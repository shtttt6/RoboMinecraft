package com.robominecraft

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import kotlin.math.min
import kotlin.math.roundToInt

internal fun RoboMinecraft.registerCommands() {
	CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
		dispatcher.register(
			Commands.literal("robomc")
				.executes { context -> sendRobotStatus(context) }
				.then(Commands.literal("on").executes { context -> setRobotMode(context, true) })
				.then(Commands.literal("off").executes { context -> setRobotMode(context, false) })
				.then(Commands.literal("toggle").executes { context ->
					val player = context.source.getPlayerOrException()
					setRobotMode(player, !isRobotPilot(player))
					1
				})
				.then(
					Commands.literal("hero")
						.then(
							Commands.literal("regular")
								.then(Commands.literal("melee").executes { context -> setHero(context, HeroMobilityMode.REGULAR, HeroMode.MELEE) })
								.then(Commands.literal("ranged").executes { context -> setHero(context, HeroMobilityMode.REGULAR, HeroMode.RANGED) })
						)
						.then(
							Commands.literal("wheellegged")
								.then(Commands.literal("melee").executes { context -> setHero(context, HeroMobilityMode.WHEEL_LEGGED, HeroMode.MELEE) })
								.then(Commands.literal("ranged").executes { context -> setHero(context, HeroMobilityMode.WHEEL_LEGGED, HeroMode.RANGED) })
						)
						.then(Commands.literal("melee").executes { context -> setHero(context, HeroMobilityMode.REGULAR, HeroMode.MELEE) })
						.then(Commands.literal("ranged").executes { context -> setHero(context, HeroMobilityMode.REGULAR, HeroMode.RANGED) })
				)
				.then(
					Commands.literal("infantry")
						.then(
							Commands.literal("regular")
								.then(
									Commands.literal("power")
										.then(Commands.literal("burst").executes { context ->
											setInfantry(context, InfantryMobilityMode.REGULAR, InfantryChassisMode.POWER, InfantryLauncherMode.BURST)
										})
										.then(Commands.literal("cooling").executes { context ->
											setInfantry(context, InfantryMobilityMode.REGULAR, InfantryChassisMode.POWER, InfantryLauncherMode.COOLING)
										})
								)
								.then(
									Commands.literal("health")
										.then(Commands.literal("burst").executes { context ->
											setInfantry(context, InfantryMobilityMode.REGULAR, InfantryChassisMode.HEALTH, InfantryLauncherMode.BURST)
										})
										.then(Commands.literal("cooling").executes { context ->
											setInfantry(context, InfantryMobilityMode.REGULAR, InfantryChassisMode.HEALTH, InfantryLauncherMode.COOLING)
										})
								)
						)
						.then(
							Commands.literal("wheellegged")
								.then(
									Commands.literal("power")
										.then(Commands.literal("burst").executes { context ->
											setInfantry(context, InfantryMobilityMode.WHEEL_LEGGED, InfantryChassisMode.POWER, InfantryLauncherMode.BURST)
										})
										.then(Commands.literal("cooling").executes { context ->
											setInfantry(context, InfantryMobilityMode.WHEEL_LEGGED, InfantryChassisMode.POWER, InfantryLauncherMode.COOLING)
										})
								)
								.then(
									Commands.literal("health")
										.then(Commands.literal("burst").executes { context ->
											setInfantry(context, InfantryMobilityMode.WHEEL_LEGGED, InfantryChassisMode.HEALTH, InfantryLauncherMode.BURST)
										})
										.then(Commands.literal("cooling").executes { context ->
											setInfantry(context, InfantryMobilityMode.WHEEL_LEGGED, InfantryChassisMode.HEALTH, InfantryLauncherMode.COOLING)
										})
								)
						)
						.then(
							Commands.literal("power")
								.then(Commands.literal("burst").executes { context ->
									setInfantry(context, InfantryMobilityMode.REGULAR, InfantryChassisMode.POWER, InfantryLauncherMode.BURST)
								})
								.then(Commands.literal("cooling").executes { context ->
									setInfantry(context, InfantryMobilityMode.REGULAR, InfantryChassisMode.POWER, InfantryLauncherMode.COOLING)
								})
						)
						.then(
							Commands.literal("health")
								.then(Commands.literal("burst").executes { context ->
									setInfantry(context, InfantryMobilityMode.REGULAR, InfantryChassisMode.HEALTH, InfantryLauncherMode.BURST)
								})
								.then(Commands.literal("cooling").executes { context ->
									setInfantry(context, InfantryMobilityMode.REGULAR, InfantryChassisMode.HEALTH, InfantryLauncherMode.COOLING)
								})
						)
				)
				.then(Commands.literal("aerial").executes { context -> setAerial(context) })
				.then(
					Commands.literal("level")
						.then(
							Commands.argument("value", IntegerArgumentType.integer(1, 10))
								.executes { context -> setLevel(context, IntegerArgumentType.getInteger(context, "value")) }
						)
				)
				.then(
					Commands.literal("xp")
						.then(
							Commands.literal("set")
								.then(
									Commands.argument("amount", IntegerArgumentType.integer(0, 5000))
										.executes { context -> setExperience(context, IntegerArgumentType.getInteger(context, "amount")) }
								)
						)
						.then(
							Commands.literal("add")
								.then(
									Commands.argument("amount", IntegerArgumentType.integer(0))
										.executes { context -> addExperience(context, IntegerArgumentType.getInteger(context, "amount")) }
								)
						)
				)
		)
	}
}

internal fun RoboMinecraft.applyRobotConfig(player: ServerPlayer, payload: RobotConfigPayload) {
	val profile = RobotProfile(
		kind = enumByOrdinal(payload.robotKind, RobotKind.INFANTRY),
		heroMode = enumByOrdinal(payload.heroMode, HeroMode.RANGED),
		heroMobilityMode = enumByOrdinal(payload.heroMobilityMode, HeroMobilityMode.REGULAR),
		infantryMobilityMode = enumByOrdinal(payload.infantryMobilityMode, InfantryMobilityMode.REGULAR),
		infantryChassisMode = enumByOrdinal(payload.infantryChassisMode, InfantryChassisMode.HEALTH),
		infantryLauncherMode = enumByOrdinal(payload.infantryLauncherMode, InfantryLauncherMode.COOLING)
	)
	activateProfile(player, profile)
	player.displayClientMessage(Component.literal("Robot configured: ${stateFor(player).profile.displayName()}"), true)
}

internal fun RoboMinecraft.buyAmmo(player: ServerPlayer, payload: BuyAmmoPayload) {
	val kind = enumByOrdinal(payload.robotKind, RobotKind.INFANTRY)
	val amount = payload.amount.coerceIn(0, RobotConstants.MAX_AMMO_PER_TYPE)
	val state = stateFor(player)

	if (kind == RobotKind.AERIAL) {
		player.displayClientMessage(Component.literal("Aerial ammo cannot be replenished during the match."), true)
		return
	}

	if (amount <= 0) {
		return
	}

	state.addAmmo(kind, amount)
	player.displayClientMessage(Component.literal("${kind.displayName} ammo +$amount | total ${state.ammoFor(kind)}"), true)
}

private fun RoboMinecraft.sendRobotStatus(context: CommandContext<CommandSourceStack>): Int {
	val player = context.source.getPlayerOrException()
	player.displayClientMessage(Component.literal(statusLine(player)), false)
	return 1
}

private fun RoboMinecraft.setRobotMode(context: CommandContext<CommandSourceStack>, enabled: Boolean): Int {
	setRobotMode(context.source.getPlayerOrException(), enabled)
	return 1
}

internal fun RoboMinecraft.setRobotMode(player: ServerPlayer, enabled: Boolean) {
	val state = stateFor(player)
	state.enabled = enabled

	if (enabled) {
		resetRobotLocomotionState(state)
		applyRobotAttributes(player, state, state.stats())
		player.displayClientMessage(Component.literal("${statusLine(player)} | empty-hand left click fires | right click auto-aims"), true)
	} else {
		player.isInvisible = false
		discardRobotVehicle(player)
		resetRobotLocomotionState(state)
		removeRobotAttributes(player)
		player.displayClientMessage(Component.literal("RoboMaster chassis offline."), true)
	}
}

private fun RoboMinecraft.setHero(context: CommandContext<CommandSourceStack>, mobilityMode: HeroMobilityMode, mode: HeroMode): Int {
	val player = context.source.getPlayerOrException()
	activateProfile(player, RobotProfile(kind = RobotKind.HERO, heroMode = mode, heroMobilityMode = mobilityMode))
	player.displayClientMessage(Component.literal(statusLine(player)), false)
	return 1
}

private fun RoboMinecraft.setInfantry(
	context: CommandContext<CommandSourceStack>,
	mobilityMode: InfantryMobilityMode,
	chassisMode: InfantryChassisMode,
	launcherMode: InfantryLauncherMode
): Int {
	val player = context.source.getPlayerOrException()
	activateProfile(
		player,
		RobotProfile(
			kind = RobotKind.INFANTRY,
			infantryMobilityMode = mobilityMode,
			infantryChassisMode = chassisMode,
			infantryLauncherMode = launcherMode
		)
	)
	player.displayClientMessage(Component.literal(statusLine(player)), false)
	return 1
}

private fun RoboMinecraft.setAerial(context: CommandContext<CommandSourceStack>): Int {
	val player = context.source.getPlayerOrException()
	activateProfile(player, RobotProfile(kind = RobotKind.AERIAL))
	player.displayClientMessage(Component.literal("${statusLine(player)} | jump ascends | Left Alt descends"), false)
	return 1
}

private fun RoboMinecraft.setLevel(context: CommandContext<CommandSourceStack>, level: Int): Int {
	val player = context.source.getPlayerOrException()
	val state = stateFor(player)
	setMinecraftLevel(player, level)
	syncPilotProgressFromMinecraft(player, state)
	state.heat = min(state.heat, state.stats().heatLimit.toDouble())
	refreshPilotStats(player, state)
	player.displayClientMessage(Component.literal(statusLine(player)), false)
	return 1
}

private fun RoboMinecraft.setExperience(context: CommandContext<CommandSourceStack>, experience: Int): Int {
	val player = context.source.getPlayerOrException()
	val state = stateFor(player)
	setMinecraftProgressFromRobotExperience(player, experience)
	syncPilotProgressFromMinecraft(player, state)
	state.heat = min(state.heat, state.stats().heatLimit.toDouble())
	refreshPilotStats(player, state)
	player.displayClientMessage(Component.literal(statusLine(player)), false)
	return 1
}

private fun RoboMinecraft.addExperience(context: CommandContext<CommandSourceStack>, amount: Int): Int {
	val state = stateFor(context.source.getPlayerOrException())
	return setExperience(context, state.experience + amount)
}

private fun RoboMinecraft.applyProfileAmmoReset(state: PilotState, previousKind: RobotKind, currentKind: RobotKind) {
	if (previousKind != currentKind) {
		state.heroAmmo = 0
		state.infantryAmmo = 0
		state.aerialAmmo = 0
	}
	if (currentKind == RobotKind.AERIAL) {
		state.aerialAmmo = RobotRules.AERIAL_INITIAL_AMMO
	}
}

internal fun RoboMinecraft.activateProfile(player: ServerPlayer, profile: RobotProfile) {
	val state = stateFor(player)
	val previousKind = state.profile.kind
	state.enabled = true
	state.profile = profile
	applyProfileAmmoReset(state, previousKind, profile.kind)
	state.heat = 0.0
	refreshPilotStats(player, state)
	nextShotTicks.remove(player.uuid)
}

internal fun RoboMinecraft.refreshPilotStats(player: ServerPlayer, state: PilotState) {
	state.appliedMaxHp = 0
	resetRobotLocomotionState(state)
	applyRobotAttributes(player, state, state.stats())
}

internal fun RoboMinecraft.statusLine(player: ServerPlayer): String {
	val state = stateFor(player)
	val stats = state.stats()
	val status = if (state.enabled) "online" else "offline"
	val hpText = if (state.profile.kind == RobotKind.AERIAL) "N/A" else stats.maxHp.toString()

	return "RoboMC $status | ${state.profile.displayName()} | Lv.${state.level} XP ${state.experience}/5000 | HP $hpText | Power ${stats.chassisPower}W | ${stats.physicalSpec.massKilograms.roundToInt()}kg | ${stats.movementSpeedMetersPerSecond.formatOneDecimal()}m/s | climb ${stats.stepHeightBlocks.formatOneDecimal()} blocks | jump ${stats.jumpHeightBlocks.formatOneDecimal()} blocks | Ammo ${state.ammoFor(state.profile.kind)} | Heat ${state.heat.roundToInt()}/${stats.heatLimit} | Cooling ${stats.heatCoolingPerSecond.roundToInt()}/s | Fire ${stats.fireRateHz.roundToInt()}Hz | Shot ${stats.bullet.name} ${stats.bullet.damage.roundToInt()}HP @ ${stats.bullet.muzzleVelocityMetersPerSecond.roundToInt()}m/s"
}
