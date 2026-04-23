package com.robominecraft

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

object RobotRules {
	const val AERIAL_INITIAL_AMMO = 750

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
	private val aerialPhysicalSpec = RobotPhysicalSpec(
		lengthMeters = 1.4,
		widthMeters = 1.4,
		heightMeters = 0.6,
		massKilograms = 18.0,
		viewHeightMeters = 0.1
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
			RobotKind.HERO -> heroStats(level, profile.heroMobilityMode, profile.heroMode)
			RobotKind.INFANTRY -> infantryStats(level, profile.infantryMobilityMode, profile.infantryChassisMode, profile.infantryLauncherMode)
			RobotKind.AERIAL -> aerialStats(level)
		}
	}

	fun physicalSpec(kind: RobotKind): RobotPhysicalSpec {
		return when (kind) {
			RobotKind.HERO -> heroPhysicalSpec
			RobotKind.INFANTRY -> infantryPhysicalSpec
			RobotKind.AERIAL -> aerialPhysicalSpec
		}
	}

	fun inferKindFromScale(scale: Double): RobotKind {
		val heroScale = heroPhysicalSpec.heightBlocks / RobotConstants.PLAYER_BASE_HEIGHT_BLOCKS
		val infantryScale = infantryPhysicalSpec.heightBlocks / RobotConstants.PLAYER_BASE_HEIGHT_BLOCKS
		return if (abs(scale - heroScale) <= abs(scale - infantryScale)) RobotKind.HERO else RobotKind.INFANTRY
	}

	fun requiredExperienceForLevel(level: Int): Int {
		return interpolate(level.coerceIn(1, 10), 0, 2200, 5000)
	}

	fun levelForExperience(experience: Int): Int {
		return (1..10).last { requiredExperienceForLevel(it) <= experience.coerceAtLeast(0) }
	}

	private fun heroStats(level: Int, mobilityMode: HeroMobilityMode, mode: HeroMode): RobotStats {
		val maxHp = when (mode) {
			HeroMode.MELEE -> interpolate(level, 200, 300, 450)
			HeroMode.RANGED -> interpolate(level, 150, 210, 300)
		}
		val chassisPower = when (mode) {
			HeroMode.MELEE -> interpolate(level, 70, 90, 120)
			HeroMode.RANGED -> interpolate(level, 50, 70, 100)
		}
		val heatLimit = when (mode) {
			HeroMode.MELEE -> interpolate(level, 140, 180, 240)
			HeroMode.RANGED -> interpolate(level, 100, 115, 130)
		}
		val heatCooling = when (mode) {
			HeroMode.MELEE -> interpolateDouble(level, 5.0, 10.0, 20.0)
			HeroMode.RANGED -> interpolateDouble(level, 5.0, 10.0, 15.0)
		}
		val stepHeightBlocks = when (mobilityMode) {
			HeroMobilityMode.REGULAR -> RobotConstants.CLIMBABLE_STEP_HEIGHT_BLOCKS
			HeroMobilityMode.WHEEL_LEGGED -> 2.0
		}
		val jumpStrength = when (mobilityMode) {
			HeroMobilityMode.REGULAR -> 0.0
			HeroMobilityMode.WHEEL_LEGGED -> 0.6
		}
		val jumpHeightBlocks = when (mobilityMode) {
			HeroMobilityMode.REGULAR -> 0.0
			HeroMobilityMode.WHEEL_LEGGED -> 2.0
		}
		val stepPauseTicks = when (mobilityMode) {
			HeroMobilityMode.REGULAR -> 0
			HeroMobilityMode.WHEEL_LEGGED -> 10
		}

		return RobotStats(
			maxHp = maxHp,
			chassisPower = chassisPower,
			heatLimit = heatLimit,
			heatCoolingPerSecond = heatCooling,
			shotHeat = 100.0,
			fireRateHz = 5.0,
			bullet = heroBullet,
			physicalSpec = heroPhysicalSpec,
			movementSpeedMetersPerSecond = movementSpeedMetersPerSecond(chassisPower, heroPhysicalSpec.massKilograms),
			stepHeightBlocks = stepHeightBlocks,
			jumpStrength = jumpStrength,
			jumpHeightBlocks = jumpHeightBlocks,
			stepPauseTicks = stepPauseTicks
		)
	}

	private fun infantryStats(
		level: Int,
		mobilityMode: InfantryMobilityMode,
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
			InfantryLauncherMode.COOLING -> interpolate(level, 40, 80, 120)
		}
		val heatCooling = when (launcherMode) {
			InfantryLauncherMode.BURST -> interpolateDouble(level, 5.0, 10.0, 20.0)
			InfantryLauncherMode.COOLING -> interpolateDouble(level, 10.0, 20.0, 30.0)
		}
		val stepHeightBlocks = when (mobilityMode) {
			InfantryMobilityMode.REGULAR -> RobotConstants.CLIMBABLE_STEP_HEIGHT_BLOCKS
			InfantryMobilityMode.WHEEL_LEGGED -> 2.0
		}
		val jumpStrength = when (mobilityMode) {
			InfantryMobilityMode.REGULAR -> 0.0
			InfantryMobilityMode.WHEEL_LEGGED -> 0.6
		}
		val jumpHeightBlocks = when (mobilityMode) {
			InfantryMobilityMode.REGULAR -> 0.0
			InfantryMobilityMode.WHEEL_LEGGED -> 2.0
		}
		val stepPauseTicks = when (mobilityMode) {
			InfantryMobilityMode.REGULAR -> 0
			InfantryMobilityMode.WHEEL_LEGGED -> 10
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
			movementSpeedMetersPerSecond = movementSpeedMetersPerSecond(chassisPower, infantryPhysicalSpec.massKilograms),
			stepHeightBlocks = stepHeightBlocks,
			jumpStrength = jumpStrength,
			jumpHeightBlocks = jumpHeightBlocks,
			stepPauseTicks = stepPauseTicks
		)
	}

	private fun aerialStats(level: Int): RobotStats {
		val safeLevel = level.coerceIn(1, 10)
		val heatLimitByLevel = intArrayOf(100, 110, 120, 130, 140, 150, 160, 170, 180, 200)
		val coolingByLevel = doubleArrayOf(20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0, 120.0)

		return RobotStats(
			maxHp = 1,
			chassisPower = 220,
			heatLimit = heatLimitByLevel[safeLevel - 1],
			heatCoolingPerSecond = coolingByLevel[safeLevel - 1],
			shotHeat = 10.0,
			fireRateHz = 25.0,
			bullet = infantryBullet,
			physicalSpec = aerialPhysicalSpec,
			movementSpeedMetersPerSecond = 3.5,
			stepHeightBlocks = 0.0,
			jumpStrength = 0.0,
			jumpHeightBlocks = 0.0,
			stepPauseTicks = 0
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
