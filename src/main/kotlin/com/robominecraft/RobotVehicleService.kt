package com.robominecraft

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity

internal fun RoboMinecraft.ensureRobotVehicle(player: ServerPlayer, state: PilotState, @Suppress("UNUSED_PARAMETER") stats: RobotStats): RobotVehicleEntity? {
	val existing = robotVehicle(player)
	if (existing != null) {
		existing.syncFromPilotState(player, state)
		return existing
	}

	val level = player.level() as net.minecraft.server.level.ServerLevel
	val trackedVehicleId = robotVehicleIds[player.uuid]
	val trackedVehicle = trackedVehicleId?.let { level.getEntity(it) as? RobotVehicleEntity }
	if (trackedVehicle != null && trackedVehicle.isAlive) {
		trackedVehicle.syncFromPilotState(player, state)
		if (player.vehicle !== trackedVehicle) {
			player.startRiding(trackedVehicle, true, true)
		}
		return trackedVehicle
	}

	val vehicle = RobotVehicleEntity(RoboMinecraft.ROBOT_VEHICLE_TYPE, level)
	vehicle.snapTo(player.x, player.y, player.z, player.yRot, player.xRot)
	vehicle.syncFromPilotState(player, state)
	level.addFreshEntity(vehicle)
	player.startRiding(vehicle, true, true)
	robotVehicleIds[player.uuid] = vehicle.uuid
	return vehicle
}

internal fun RoboMinecraft.discardRobotVehicle(player: ServerPlayer) {
	val level = player.level() as net.minecraft.server.level.ServerLevel
	val vehicle = robotVehicle(player)
		?: robotVehicleIds[player.uuid]?.let { level.getEntity(it) as? RobotVehicleEntity }
		?: return

	if (player.vehicle === vehicle) {
		player.stopRiding()
	}
	vehicle.remove(Entity.RemovalReason.DISCARDED)
	robotVehicleIds.remove(player.uuid)
}

internal fun RoboMinecraft.robotVehicle(player: ServerPlayer): RobotVehicleEntity? {
	return player.vehicle as? RobotVehicleEntity
}
