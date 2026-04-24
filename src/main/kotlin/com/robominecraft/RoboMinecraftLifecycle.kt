package com.robominecraft

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerPlayer
import kotlin.math.max

internal fun RoboMinecraft.registerPlayerLifecycle() {
	ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ ->
		val state = stateFor(newPlayer)
		syncPilotProgressFromMinecraft(newPlayer, state)
		state.appliedMaxHp = 0
		resetRobotLocomotionState(state)
		discardRobotVehicle(newPlayer)

		if (state.enabled) {
			val stats = state.stats()
			applyRobotAttributes(newPlayer, state, stats)
			maintainRobotVitals(newPlayer)
		}
	}
}

internal fun RoboMinecraft.registerRobotModeMaintenance() {
	ServerTickEvents.START_SERVER_TICK.register { server ->
		server.playerList.players.forEach { player ->
			val state = stateFor(player)
			syncPilotProgressFromMinecraft(player, state)
			val stats = state.stats()

			if (state.enabled) {
				val vehicle = ensureRobotVehicle(player, state, stats)
				vehicle?.syncFromPilotState(player, state)
				if (vehicle != null) {
					redirectHostileMobs(player, vehicle)
				}
				keepFirstHotbarSlotEmpty(player)
				applyRobotAttributes(player, state, stats)
				player.isInvisible = vehicle != null
				maintainRobotVitals(player)
			} else {
				player.isInvisible = false
				discardRobotVehicle(player)
				resetRobotLocomotionState(state)
				restorePlayerCollisionBox(player)
				removeRobotAttributes(player)
			}
		}
	}

	ServerTickEvents.END_SERVER_TICK.register { server ->
		server.playerList.players.forEach { player ->
			val state = stateFor(player)
			val stats = state.stats()

			if (server.tickCount % HEAT_SETTLEMENT_INTERVAL_TICKS == 0) {
				state.heat = max(0.0, state.heat - stats.heatCoolingPerSecond / 10.0)
			}

			if (state.enabled) {
				applyCollisionDamage(player)
			}

			syncRobotHud(player, state, stats)
		}
	}
}
