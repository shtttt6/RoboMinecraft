package com.robominecraft

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity

internal fun RoboMinecraft.isJudgeMode(entity: Entity): Boolean {
	return pilotStates[entity.uuid]?.judgeMode == true
}

internal fun RoboMinecraft.toggleJudgeMode(player: ServerPlayer) {
	setJudgeMode(player, !stateFor(player).judgeMode)
}

internal fun RoboMinecraft.setJudgeMode(player: ServerPlayer, enabled: Boolean) {
	val state = stateFor(player)

	if (!state.enabled) {
		if (enabled) {
			player.displayClientMessage(Component.literal("Robot mode is offline. Enable the chassis before entering judge mode."), true)
		}
		return
	}
	if (state.judgeMode == enabled) {
		return
	}

	if (enabled) {
		enterJudgeMode(player, state)
	} else {
		exitJudgeMode(player, state)
	}
}

internal fun RoboMinecraft.maintainJudgeMode(player: ServerPlayer, state: PilotState) {
	if (!state.judgeMode) {
		return
	}

	if (player.vehicle is RobotVehicleEntity) {
		player.stopRiding()
	}
	player.abilities.mayfly = true
	player.abilities.flying = true
	player.noPhysics = true
	player.setNoGravity(true)
	player.fallDistance = 0.0
	player.isInvisible = true
	player.onUpdateAbilities()
}

internal fun RoboMinecraft.clearJudgeMode(player: ServerPlayer, state: PilotState) {
	if (!state.judgeMode) {
		return
	}

	state.judgeMode = false
	player.abilities.mayfly = state.storedMayfly
	player.abilities.flying = state.storedFlying
	player.noPhysics = state.storedNoPhysics
	player.setNoGravity(state.storedNoGravity)
	player.fallDistance = 0.0
	player.onUpdateAbilities()
	clearStoredJudgeAbilities(state)
}

private fun RoboMinecraft.enterJudgeMode(player: ServerPlayer, state: PilotState) {
	state.storedMayfly = player.abilities.mayfly
	state.storedFlying = player.abilities.flying
	state.storedNoPhysics = player.noPhysics
	state.storedNoGravity = player.isNoGravity
	state.judgeMode = true
	ensureRobotVehicle(player, state, state.stats())
	if (player.vehicle is RobotVehicleEntity) {
		player.stopRiding()
	}
	player.setDeltaMovement(0.0, 0.0, 0.0)
	maintainJudgeMode(player, state)
	player.displayClientMessage(Component.literal("Judge mode ON | F4 to return to the robot."), true)
}

private fun RoboMinecraft.exitJudgeMode(player: ServerPlayer, state: PilotState) {
	clearJudgeMode(player, state)
	val vehicle = ensureRobotVehicle(player, state, state.stats())
	player.isInvisible = vehicle != null
	player.displayClientMessage(Component.literal("Judge mode OFF | reconnected to the robot."), true)
}

private fun clearStoredJudgeAbilities(state: PilotState) {
	state.storedMayfly = false
	state.storedFlying = false
	state.storedNoPhysics = false
	state.storedNoGravity = false
}
