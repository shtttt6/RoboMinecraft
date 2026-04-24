package com.robominecraft

import net.minecraft.world.phys.AABB
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

data class BulletSpec(
	val name: String,
	val diameterMeters: Double,
	val massKilograms: Double,
	val material: String,
	val muzzleVelocityMetersPerSecond: Double,
	val damage: Float
)

data class RobotPhysicalSpec(
	val lengthMeters: Double,
	val widthMeters: Double,
	val heightMeters: Double,
	val massKilograms: Double,
	val viewHeightMeters: Double = heightMeters * 0.75
) {
	val heightBlocks: Double
		get() = heightMeters * RobotConstants.WORLD_BLOCKS_PER_REAL_METER

	val collisionHeightMeters: Double
		get() = max(0.1, heightMeters - 0.1)

	val collisionHeightBlocks: Double
		get() = collisionHeightMeters * RobotConstants.WORLD_BLOCKS_PER_REAL_METER

	val lengthBlocks: Double
		get() = lengthMeters * RobotConstants.WORLD_BLOCKS_PER_REAL_METER

	val widthBlocks: Double
		get() = widthMeters * RobotConstants.WORLD_BLOCKS_PER_REAL_METER

	val climbableStepHeightBlocks: Double
		get() = RobotConstants.CLIMBABLE_STEP_HEIGHT_BLOCKS

	val viewHeightBlocks: Double
		get() = viewHeightMeters * RobotConstants.WORLD_BLOCKS_PER_REAL_METER

	fun displayDimensions(): String {
		return "${lengthMeters}m x ${widthMeters}m x ${heightMeters}m"
	}

	fun collisionBoxAt(x: Double, y: Double, z: Double, yawDegrees: Float): AABB {
		val halfWidth = widthBlocks / 2.0
		val halfLength = lengthBlocks / 2.0
		val yawRadians = Math.toRadians(yawDegrees.toDouble())
		val cosYaw = abs(cos(yawRadians))
		val sinYaw = abs(sin(yawRadians))
		val halfExtentX = cosYaw * halfWidth + sinYaw * halfLength
		val halfExtentZ = sinYaw * halfWidth + cosYaw * halfLength

		return AABB(
			x - halfExtentX,
			y,
			z - halfExtentZ,
			x + halfExtentX,
			y + collisionHeightBlocks,
			z + halfExtentZ
		)
	}
}

data class RobotStats(
	val maxHp: Int,
	val chassisPower: Int,
	val heatLimit: Int,
	val heatCoolingPerSecond: Double,
	val shotHeat: Double,
	val fireRateHz: Double,
	val bullet: BulletSpec,
	val physicalSpec: RobotPhysicalSpec,
	val movementSpeedMetersPerSecond: Double,
	val stepHeightBlocks: Double,
	val jumpStrength: Double,
	val jumpHeightBlocks: Double,
	val stepPauseTicks: Int
)

data class RobotProfile(
	val kind: RobotKind = RobotKind.INFANTRY,
	val heroMode: HeroMode = HeroMode.RANGED,
	val heroMobilityMode: HeroMobilityMode = HeroMobilityMode.REGULAR,
	val infantryMobilityMode: InfantryMobilityMode = InfantryMobilityMode.REGULAR,
	val infantryChassisMode: InfantryChassisMode = InfantryChassisMode.HEALTH,
	val infantryLauncherMode: InfantryLauncherMode = InfantryLauncherMode.COOLING
) {
	fun displayName(): String {
		return when (kind) {
			RobotKind.HERO -> "${kind.displayName} ${heroMobilityMode.displayName} ${heroMode.displayName}"
			RobotKind.INFANTRY -> "${kind.displayName} ${infantryMobilityMode.displayName} ${infantryChassisMode.displayName}/${infantryLauncherMode.displayName}"
			RobotKind.AERIAL -> kind.displayName
		}
	}
}

fun Double.formatOneDecimal(): String {
	return ((this * 10.0).roundToInt() / 10.0).toString()
}
