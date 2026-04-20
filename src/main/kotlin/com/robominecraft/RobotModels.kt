package com.robominecraft

import net.minecraft.resources.ResourceLocation
import kotlin.math.roundToInt
import kotlin.math.sqrt

object RobotConstants {
	const val MOD_ID = "robominecraft"
	const val WORLD_BLOCKS_PER_REAL_METER = 10.0
	const val VANILLA_BASE_SPEED_BLOCKS_PER_SECOND = 4.317
	const val PLAYER_BASE_HEIGHT_BLOCKS = 1.8
	const val ROBOT_SPEED_COEFFICIENT = 1.0
	const val CLIMBABLE_STEP_HEIGHT_BLOCKS = 1.0
	const val MAX_AMMO_PER_TYPE = 99999

	fun id(path: String): ResourceLocation {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
	}
}

object RobotAttributeIds {
	val SCALE: ResourceLocation = RobotConstants.id("robot_scale")
	val MAX_HEALTH: ResourceLocation = RobotConstants.id("robot_max_health")
	val MOVEMENT: ResourceLocation = RobotConstants.id("robot_movement_speed")
	val STEP_HEIGHT: ResourceLocation = RobotConstants.id("robot_step_height")
	val JUMP_STRENGTH: ResourceLocation = RobotConstants.id("robot_jump_strength")
	val ARMOR: ResourceLocation = RobotConstants.id("robot_armor")
	val KNOCKBACK: ResourceLocation = RobotConstants.id("robot_knockback_resistance")
}

enum class RobotKind(val displayName: String) {
	HERO("Hero"),
	INFANTRY("Infantry")
}

enum class HeroMode(val displayName: String) {
	MELEE("melee-priority"),
	RANGED("ranged-priority")
}

enum class InfantryChassisMode(val displayName: String) {
	POWER("power-priority"),
	HEALTH("health-priority")
}

enum class InfantryLauncherMode(val displayName: String) {
	BURST("burst-priority"),
	COOLING("cooling-priority")
}

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
	val massKilograms: Double
) {
	val heightBlocks: Double
		get() = heightMeters * RobotConstants.WORLD_BLOCKS_PER_REAL_METER

	val climbableStepHeightBlocks: Double
		get() = RobotConstants.CLIMBABLE_STEP_HEIGHT_BLOCKS

	fun displayDimensions(): String {
		return "${lengthMeters}m x ${widthMeters}m x ${heightMeters}m"
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
	val movementSpeedMetersPerSecond: Double
)

data class RobotProfile(
	val kind: RobotKind = RobotKind.INFANTRY,
	val heroMode: HeroMode = HeroMode.MELEE,
	val infantryChassisMode: InfantryChassisMode = InfantryChassisMode.POWER,
	val infantryLauncherMode: InfantryLauncherMode = InfantryLauncherMode.BURST
) {
	fun displayName(): String {
		return when (kind) {
			RobotKind.HERO -> "${kind.displayName} ${heroMode.displayName}"
			RobotKind.INFANTRY -> "${kind.displayName} ${infantryChassisMode.displayName}/${infantryLauncherMode.displayName}"
		}
	}
}

data class PilotState(
	var enabled: Boolean = true,
	var profile: RobotProfile = RobotProfile(),
	var level: Int = 1,
	var experience: Int = 0,
	var heat: Double = 0.0,
	var appliedMaxHp: Int = 0,
	var heroAmmo: Int = 0,
	var infantryAmmo: Int = 0
) {
	fun stats(): RobotStats {
		return RobotRules.stats(profile, level)
	}

	fun ammoFor(kind: RobotKind): Int {
		return when (kind) {
			RobotKind.HERO -> heroAmmo
			RobotKind.INFANTRY -> infantryAmmo
		}
	}

	fun addAmmo(kind: RobotKind, amount: Int) {
		if (amount <= 0) {
			return
		}

		when (kind) {
			RobotKind.HERO -> heroAmmo = (heroAmmo + amount).coerceAtMost(RobotConstants.MAX_AMMO_PER_TYPE)
			RobotKind.INFANTRY -> infantryAmmo = (infantryAmmo + amount).coerceAtMost(RobotConstants.MAX_AMMO_PER_TYPE)
		}
	}

	fun consumeAmmo(kind: RobotKind): Boolean {
		return when (kind) {
			RobotKind.HERO -> {
				if (heroAmmo <= 0) {
					false
				} else {
					heroAmmo--
					true
				}
			}
			RobotKind.INFANTRY -> {
				if (infantryAmmo <= 0) {
					false
				} else {
					infantryAmmo--
					true
				}
			}
		}
	}
}

object RobotRules {
	private val infantryPhysicalSpec = RobotPhysicalSpec(
		lengthMeters = 0.6,
		widthMeters = 0.5,
		heightMeters = 0.5,
		massKilograms = 20.0
	)
	private val heroPhysicalSpec = RobotPhysicalSpec(
		lengthMeters = 0.7,
		widthMeters = 0.6,
		heightMeters = 0.6,
		massKilograms = 25.0
	)
	private val infantryBullet = BulletSpec(
		name = "17mm",
		diameterMeters = 0.017,
		massKilograms = 0.0032,
		material = "TPU",
		muzzleVelocityMetersPerSecond = 30.0,
		damage = 20.0f
	)
	private val heroBullet = BulletSpec(
		name = "42mm",
		diameterMeters = 0.042,
		massKilograms = 0.0445,
		material = "TPE",
		muzzleVelocityMetersPerSecond = 16.0,
		damage = 200.0f
	)

	fun stats(profile: RobotProfile, level: Int): RobotStats {
		return when (profile.kind) {
			RobotKind.HERO -> heroStats(level, profile.heroMode)
			RobotKind.INFANTRY -> infantryStats(level, profile.infantryChassisMode, profile.infantryLauncherMode)
		}
	}

	fun requiredExperienceForLevel(level: Int): Int {
		return interpolate(level.coerceIn(1, 10), 0, 2200, 5000)
	}

	fun levelForExperience(experience: Int): Int {
		return (1..10).last { requiredExperienceForLevel(it) <= experience.coerceAtLeast(0) }
	}

	private fun heroStats(level: Int, mode: HeroMode): RobotStats {
		val maxHp = when (mode) {
			HeroMode.MELEE -> interpolate(level, 200, 300, 450)
			HeroMode.RANGED -> interpolate(level, 150, 210, 300)
		}
		val chassisPower = when (mode) {
			HeroMode.MELEE -> interpolate(level, 70, 90, 120)
			HeroMode.RANGED -> interpolate(level, 50, 70, 100)
		}
		val heatLimit = when (mode) {
			HeroMode.MELEE -> interpolate(level, 140, 184, 240)
			HeroMode.RANGED -> interpolate(level, 100, 113, 130)
		}

		return RobotStats(
			maxHp = maxHp,
			chassisPower = chassisPower,
			heatLimit = heatLimit,
			heatCoolingPerSecond = if (mode == HeroMode.MELEE) 18.0 else 14.0,
			shotHeat = 100.0,
			fireRateHz = 5.0,
			bullet = heroBullet,
			physicalSpec = heroPhysicalSpec,
			movementSpeedMetersPerSecond = movementSpeedMetersPerSecond(chassisPower, heroPhysicalSpec.massKilograms)
		)
	}

	private fun infantryStats(
		level: Int,
		chassisMode: InfantryChassisMode,
		launcherMode: InfantryLauncherMode
	): RobotStats {
		val maxHp = when (chassisMode) {
			InfantryChassisMode.POWER -> interpolate(level, 150, 250, 400)
			InfantryChassisMode.HEALTH -> interpolate(level, 200, 300, 400)
		}
		val chassisPower = when (chassisMode) {
			InfantryChassisMode.POWER -> interpolate(level, 60, 80, 100)
			InfantryChassisMode.HEALTH -> interpolate(level, 45, 65, 100)
		}
		val heatLimit = when (launcherMode) {
			InfantryLauncherMode.BURST -> interpolate(level, 170, 210, 260)
			InfantryLauncherMode.COOLING -> interpolate(level, 40, 76, 120)
		}
		val heatCooling = when (launcherMode) {
			InfantryLauncherMode.BURST -> interpolateDouble(level, 5.0, 11.7, 20.0)
			InfantryLauncherMode.COOLING -> interpolateDouble(level, 15.0, 21.7, 30.0)
		}

		return RobotStats(
			maxHp = maxHp,
			chassisPower = chassisPower,
			heatLimit = heatLimit,
			heatCoolingPerSecond = heatCooling,
			shotHeat = 10.0,
			fireRateHz = 25.0,
			bullet = infantryBullet,
			physicalSpec = infantryPhysicalSpec,
			movementSpeedMetersPerSecond = movementSpeedMetersPerSecond(chassisPower, infantryPhysicalSpec.massKilograms)
		)
	}

	private fun movementSpeedMetersPerSecond(chassisPowerWatts: Int, massKilograms: Double): Double {
		return RobotConstants.ROBOT_SPEED_COEFFICIENT * sqrt(chassisPowerWatts / massKilograms)
	}

	private fun interpolate(level: Int, levelOne: Int, levelFive: Int, levelTen: Int): Int {
		return interpolateDouble(level, levelOne.toDouble(), levelFive.toDouble(), levelTen.toDouble()).roundToInt()
	}

	private fun interpolateDouble(level: Int, levelOne: Double, levelFive: Double, levelTen: Double): Double {
		val safeLevel = level.coerceIn(1, 10)
		return if (safeLevel <= 5) {
			val progress = (safeLevel - 1) / 4.0
			levelOne + (levelFive - levelOne) * progress
		} else {
			val progress = (safeLevel - 5) / 5.0
			levelFive + (levelTen - levelFive) * progress
		}
	}
}

fun Double.formatOneDecimal(): String {
	return ((this * 10.0).roundToInt() / 10.0).toString()
}
