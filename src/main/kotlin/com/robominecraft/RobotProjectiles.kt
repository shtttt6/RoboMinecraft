package com.robominecraft

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.min

data class ProjectileShot(
	val end: Vec3,
	val target: Entity?,
	val impactVelocity: Vec3,
	val trail: List<Vec3>
)

object RobotProjectiles {
	private const val GRAVITY_METERS_PER_SECOND_SQUARED = 9.80665
	private const val AIR_DENSITY_KG_PER_CUBIC_METER = 1.225
	private const val SPHERE_DRAG_COEFFICIENT = 0.47
	private const val PROJECTILE_STEP_SECONDS = 0.005
	private const val PROJECTILE_FAILSAFE_MAX_DISTANCE_BLOCKS = 1800.0
	private const val TRAIL_MAX_POINTS = 8

	fun simulate(
		player: ServerPlayer,
		bullet: BulletSpec,
		canHit: (Entity) -> Boolean
	): ProjectileShot {
		val level = player.level()
		val radius = bullet.diameterMeters / 2.0
		val radiusBlocks = radius * RobotConstants.WORLD_BLOCKS_PER_REAL_METER
		val crossSectionArea = Math.PI * radius * radius
		val dragFactor = 0.5 * AIR_DENSITY_KG_PER_CUBIC_METER * SPHERE_DRAG_COEFFICIENT * crossSectionArea / bullet.massKilograms
		val origin = muzzleOrigin(player)
		var previousPosition = origin
		var velocity = player.lookAngle.normalize().scale(bullet.muzzleVelocityMetersPerSecond)
		val trail = mutableListOf(previousPosition)

		while (previousPosition.distanceTo(origin) < PROJECTILE_FAILSAFE_MAX_DISTANCE_BLOCKS) {
			val speed = velocity.length()
			val dragAcceleration = if (speed > 0.0) {
				velocity.normalize().scale(-dragFactor * speed * speed)
			} else {
				Vec3.ZERO
			}
			val acceleration = dragAcceleration.add(0.0, -GRAVITY_METERS_PER_SECOND_SQUARED, 0.0)
			velocity = velocity.add(acceleration.scale(PROJECTILE_STEP_SECONDS))
			val nextPosition = previousPosition.add(velocity.scale(PROJECTILE_STEP_SECONDS * RobotConstants.WORLD_BLOCKS_PER_REAL_METER))
			val blockHit = level.clip(
				ClipContext(previousPosition, nextPosition, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
			)
			val segmentEnd = if (blockHit.type == HitResult.Type.MISS) nextPosition else blockHit.location
			val entityHit = ProjectileUtil.getEntityHitResult(
				player,
				previousPosition,
				segmentEnd,
				boxBetween(previousPosition, segmentEnd).inflate(radiusBlocks + 0.18),
				canHit,
				PROJECTILE_FAILSAFE_MAX_DISTANCE_BLOCKS * PROJECTILE_FAILSAFE_MAX_DISTANCE_BLOCKS
			)

			if (entityHit != null) {
				trail.add(entityHit.location)
				return ProjectileShot(entityHit.location, entityHit.entity, velocity, trail)
			}

			trail.add(segmentEnd)

			if (blockHit.type != HitResult.Type.MISS) {
				return ProjectileShot(segmentEnd, null, velocity, trail)
			}

			previousPosition = nextPosition
		}

		return ProjectileShot(previousPosition, null, velocity, trail)
	}

	private fun muzzleOrigin(player: ServerPlayer): Vec3 {
		val stats = RoboMinecraft.robotStatsOrNull(player) ?: return player.eyePosition
		if (!RoboMinecraft.isAerialRobot(player)) {
			return player.eyePosition
		}
		val vehicle = player.vehicle as? RobotVehicleEntity ?: return player.eyePosition
		return Vec3(vehicle.x, vehicle.y + stats.physicalSpec.viewHeightBlocks, vehicle.z)
	}

	fun spawnTrail(player: ServerPlayer, trail: List<Vec3>) {
		val level = player.level()
		trail.asSequence()
			.take(TRAIL_MAX_POINTS)
			.forEach { point ->
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
			}
	}

	private fun boxBetween(start: Vec3, end: Vec3): AABB {
		return AABB(
			min(start.x, end.x),
			min(start.y, end.y),
			min(start.z, end.z),
			max(start.x, end.x),
			max(start.y, end.y),
			max(start.z, end.z)
		)
	}
}
