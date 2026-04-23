package com.robominecraft

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Enemy
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

internal fun RoboMinecraft.registerDamageRules() {
	ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ ->
		entity !is ServerPlayer || !isAerialRobot(entity)
	}
}

internal fun RoboMinecraft.handleFireRequest(player: ServerPlayer) {
	val state = stateFor(player)
	val stats = state.stats()
	val serverTick = player.level().server.tickCount.toDouble()
	val shotIntervalTicks = 20.0 / stats.fireRateHz
	var nextShotTick = nextShotTicks[player.uuid] ?: serverTick

	if (serverTick - nextShotTick > 2.0) {
		nextShotTick = serverTick
	}

	if (nextShotTick > serverTick) {
		return
	}

	val dueShots = min(
		RoboMinecraft.MAX_SHOTS_PER_REQUEST,
		floor((serverTick - nextShotTick) / shotIntervalTicks).toInt() + 1
	)
	var firedShots = 0

	while (firedShots < dueShots) {
		if (!fireSingleShot(player, state, stats)) {
			break
		}

		nextShotTick += shotIntervalTicks
		firedShots++
	}

	nextShotTicks[player.uuid] = if (firedShots > 0) nextShotTick else serverTick + shotIntervalTicks
}

internal fun RoboMinecraft.syncRobotHud(player: ServerPlayer, state: PilotState, stats: RobotStats) {
	if (!ServerPlayNetworking.canSend(player, RobotHudPayload.ID)) {
		return
	}

	ServerPlayNetworking.send(
		player,
		RobotHudPayload(
			enabled = state.enabled,
			heat = if (state.enabled) state.heat.roundToInt() else 0,
			heatLimit = if (state.enabled) stats.heatLimit else 0,
			heroAmmo = state.heroAmmo,
			infantryAmmo = state.infantryAmmo,
			aerialAmmo = state.aerialAmmo,
			aerialFlightMode = state.aerialFlightMode,
			robotKind = state.profile.kind.ordinal,
			heroMode = state.profile.heroMode.ordinal,
			heroMobilityMode = state.profile.heroMobilityMode.ordinal,
			infantryMobilityMode = state.profile.infantryMobilityMode.ordinal,
			infantryChassisMode = state.profile.infantryChassisMode.ordinal,
			infantryLauncherMode = state.profile.infantryLauncherMode.ordinal
		)
	)
}

internal fun RoboMinecraft.killAerialPilot(player: ServerPlayer) {
	if (player.isPassenger) {
		player.stopRiding()
	}
	player.isInvisible = false
	discardRobotVehicle(player)
	player.health = 0.0f
	val level = player.level() as net.minecraft.server.level.ServerLevel
	player.kill(level)
}

internal fun RoboMinecraft.maintainRobotVitals(player: ServerPlayer) {
	player.foodData.setFoodLevel(RoboMinecraft.ROBOT_FOOD_LEVEL)
	player.foodData.setSaturation(RoboMinecraft.ROBOT_SATURATION_LEVEL)
	player.setSprinting(false)
}

internal fun RoboMinecraft.applyCollisionDamage(player: ServerPlayer) {
	if (isAerialRobot(player)) {
		return
	}

	val serverTick = player.level().server.tickCount
	val previousCollisionTick = lastCollisionTicks[player.uuid] ?: -RoboMinecraft.COLLISION_COOLDOWN_TICKS

	if (serverTick - previousCollisionTick < RoboMinecraft.COLLISION_COOLDOWN_TICKS) {
		return
	}

	val collider = robotVehicle(player) ?: player
	val target = player.level()
		.getEntities(collider, collider.boundingBox.inflate(0.18)) { target -> canHit(player, target) }
		.firstOrNull()
		?: return

	if (target is Enemy) {
		return
	}

	lastCollisionTicks[player.uuid] = serverTick
	target.hurtServer(player.level(), player.level().damageSources().playerAttack(player), RoboMinecraft.COLLISION_DAMAGE)
}

internal fun RoboMinecraft.redirectHostileMobs(player: ServerPlayer, vehicle: RobotVehicleEntity) {
	val searchBox = vehicle.boundingBox.inflate(32.0)
	val hostiles = player.level().getEntitiesOfClass(Mob::class.java, searchBox) { mob ->
		mob is Enemy && mob.isAlive && mob !is RobotVehicleEntity
	}

	hostiles.forEach { mob ->
		val followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE).coerceAtLeast(16.0)
		if (mob.distanceToSqr(vehicle) > followRange * followRange) {
			return@forEach
		}
		if (!mob.hasLineOfSight(vehicle) && mob.target !== vehicle) {
			return@forEach
		}

		val currentTarget = mob.target
		if (currentTarget == null || currentTarget === player || currentTarget === vehicle) {
			mob.setTarget(vehicle)
		}
	}
}

private fun RoboMinecraft.fireSingleShot(player: ServerPlayer, state: PilotState, stats: RobotStats): Boolean {
	val robotKind = state.profile.kind

	if (state.ammoFor(robotKind) <= 0) {
		return false
	}

	if (state.heat + stats.shotHeat > stats.heatLimit) {
		return false
	}

	state.consumeAmmo(robotKind)
	state.heat += stats.shotHeat
	player.swing(InteractionHand.MAIN_HAND)

	val level = player.level()
	val shot = RobotProjectiles.simulate(player, stats.bullet) { target -> canHit(player, target) }
	val end = shot.end

	level.playSound(null, player.x, player.y, player.z, SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.7f, 1.55f)
	RobotProjectiles.spawnTrail(player, shot.trail)

	val target = shot.target
	if (target != null) {
		target.hurtServer(level, level.damageSources().playerAttack(player), stats.bullet.damage)

		if (target is LivingEntity) {
			val knockbackDirection = shot.impactVelocity.normalize()
			target.knockback(0.35, -knockbackDirection.x, -knockbackDirection.z)
		}

		level.playSound(null, end.x, end.y, end.z, SoundEvents.CROSSBOW_HIT, SoundSource.PLAYERS, 0.8f, 1.25f)
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 12, 0.2, 0.2, 0.2, 0.02)
	} else {
		level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 4, 0.08, 0.08, 0.08, 0.0)
	}

	return true
}

private fun RoboMinecraft.canHit(player: ServerPlayer, target: Entity): Boolean {
	return target !== player &&
		target !is RobotVehicleEntity &&
		target.isAlive &&
		!target.isSpectator &&
		target.isPickable &&
		target.canBeHitByProjectile()
}
