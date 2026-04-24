package com.robominecraft

import kotlin.math.abs
import kotlin.math.sqrt

object RobotRules {
	const val AERIAL_INITIAL_AMMO = 750
	private const val HERO_FIRE_RATE_HZ = 5.0
	private const val AUTO_17MM_FIRE_RATE_HZ = 20.0

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
		diameterMeters = 0.0168,
		massKilograms = 0.0032,
		material = "TPU",
		muzzleVelocityMetersPerSecond = 25.0,
		damage = 20.0f
	)
	private val heroBullet = BulletSpec(
		name = "42mm",
		diameterMeters = 0.0425,
		massKilograms = 0.0445,
		material = "TPE",
		muzzleVelocityMetersPerSecond = 12.0,
		damage = 200.0f
	)

	private val experienceByLevel = intArrayOf(0, 550, 1100, 1650, 2200, 2750, 3300, 3850, 4400, 5000)

	private val heroMeleeHp = intArrayOf(200, 225, 250, 275, 300, 325, 350, 375, 400, 450)
	private val heroMeleePower = intArrayOf(70, 75, 80, 85, 90, 95, 100, 105, 110, 120)
	private val heroMeleeHeat = intArrayOf(140, 150, 160, 170, 180, 190, 200, 210, 220, 240)
	private val heroMeleeCooling = doubleArrayOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 24.0, 26.0, 28.0, 30.0)

	private val heroRangedHp = intArrayOf(150, 165, 180, 195, 210, 225, 240, 255, 270, 300)
	private val heroRangedPower = intArrayOf(50, 55, 60, 65, 70, 75, 80, 85, 90, 100)
	private val heroRangedHeat = intArrayOf(100, 102, 104, 106, 108, 110, 115, 120, 125, 130)
	private val heroRangedCooling = doubleArrayOf(20.0, 23.0, 26.0, 29.0, 32.0, 35.0, 38.0, 41.0, 44.0, 50.0)

	private val infantryPowerHp = intArrayOf(150, 175, 200, 225, 250, 275, 300, 325, 350, 400)
	private val infantryPowerPower = intArrayOf(60, 65, 70, 75, 80, 85, 90, 95, 100, 100)
	private val infantryHealthHp = intArrayOf(200, 225, 250, 275, 300, 325, 350, 375, 400, 400)
	private val infantryHealthPower = intArrayOf(45, 50, 55, 60, 65, 70, 75, 80, 90, 100)

	private val infantryBurstHeat = intArrayOf(170, 180, 190, 200, 210, 220, 230, 240, 250, 260)
	private val infantryBurstCooling = doubleArrayOf(5.0, 7.0, 9.0, 11.0, 12.0, 13.0, 14.0, 16.0, 18.0, 20.0)
	private val infantryCoolingHeat = intArrayOf(40, 48, 56, 64, 72, 80, 88, 96, 114, 120)
	private val infantryCoolingCooling = doubleArrayOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 24.0, 26.0, 28.0, 30.0)

	private val aerialHeat = intArrayOf(100, 110, 120, 130, 140, 150, 160, 170, 180, 200)
	private val aerialCooling = doubleArrayOf(20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0, 120.0)

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
		return statForLevel(experienceByLevel, level)
	}

	fun levelForExperience(experience: Int): Int {
		val clampedExperience = experience.coerceAtLeast(0)
		return (1..experienceByLevel.size).last { requiredExperienceForLevel(it) <= clampedExperience }
	}

	private fun heroStats(level: Int, mobilityMode: HeroMobilityMode, mode: HeroMode): RobotStats {
		val spec = when (mode) {
			HeroMode.MELEE -> RobotLevelSpec(
				maxHp = statForLevel(heroMeleeHp, level),
				chassisPower = statForLevel(heroMeleePower, level),
				heatLimit = statForLevel(heroMeleeHeat, level),
				heatCooling = statForLevel(heroMeleeCooling, level)
			)
			HeroMode.RANGED -> RobotLevelSpec(
				maxHp = statForLevel(heroRangedHp, level),
				chassisPower = statForLevel(heroRangedPower, level),
				heatLimit = statForLevel(heroRangedHeat, level),
				heatCooling = statForLevel(heroRangedCooling, level)
			)
		}
		val mobility = heroMobilitySpec(mobilityMode)

		return RobotStats(
			maxHp = spec.maxHp,
			chassisPower = spec.chassisPower,
			heatLimit = spec.heatLimit,
			heatCoolingPerSecond = spec.heatCooling,
			shotHeat = 100.0,
			fireRateHz = HERO_FIRE_RATE_HZ,
			bullet = heroBullet,
			physicalSpec = heroPhysicalSpec,
			movementSpeedMetersPerSecond = movementSpeedMetersPerSecond(spec.chassisPower, heroPhysicalSpec.massKilograms),
			stepHeightBlocks = mobility.stepHeightBlocks,
			jumpStrength = mobility.jumpStrength,
			jumpHeightBlocks = mobility.jumpHeightBlocks,
			stepPauseTicks = mobility.stepPauseTicks
		)
	}

	private fun infantryStats(
		level: Int,
		mobilityMode: InfantryMobilityMode,
		chassisMode: InfantryChassisMode,
		launcherMode: InfantryLauncherMode
	): RobotStats {
		val chassis = when (chassisMode) {
			InfantryChassisMode.POWER -> Pair(statForLevel(infantryPowerHp, level), statForLevel(infantryPowerPower, level))
			InfantryChassisMode.HEALTH -> Pair(statForLevel(infantryHealthHp, level), statForLevel(infantryHealthPower, level))
		}
		val launcher = when (launcherMode) {
			InfantryLauncherMode.BURST -> Pair(statForLevel(infantryBurstHeat, level), statForLevel(infantryBurstCooling, level))
			InfantryLauncherMode.COOLING -> Pair(statForLevel(infantryCoolingHeat, level), statForLevel(infantryCoolingCooling, level))
		}
		val mobility = infantryMobilitySpec(mobilityMode)

		return RobotStats(
			maxHp = chassis.first,
			chassisPower = chassis.second,
			heatLimit = launcher.first,
			heatCoolingPerSecond = launcher.second,
			shotHeat = 10.0,
			fireRateHz = AUTO_17MM_FIRE_RATE_HZ,
			bullet = infantryBullet,
			physicalSpec = infantryPhysicalSpec,
			movementSpeedMetersPerSecond = movementSpeedMetersPerSecond(chassis.second, infantryPhysicalSpec.massKilograms),
			stepHeightBlocks = mobility.stepHeightBlocks,
			jumpStrength = mobility.jumpStrength,
			jumpHeightBlocks = mobility.jumpHeightBlocks,
			stepPauseTicks = mobility.stepPauseTicks
		)
	}

	private fun aerialStats(level: Int): RobotStats {
		return RobotStats(
			maxHp = 1,
			chassisPower = 0,
			heatLimit = statForLevel(aerialHeat, level),
			heatCoolingPerSecond = statForLevel(aerialCooling, level),
			shotHeat = 10.0,
			fireRateHz = AUTO_17MM_FIRE_RATE_HZ,
			bullet = infantryBullet,
			physicalSpec = aerialPhysicalSpec,
			movementSpeedMetersPerSecond = 3.5,
			stepHeightBlocks = 0.0,
			jumpStrength = 0.0,
			jumpHeightBlocks = 0.0,
			stepPauseTicks = 0
		)
	}

	private fun heroMobilitySpec(mode: HeroMobilityMode): MobilitySpec {
		return when (mode) {
			HeroMobilityMode.REGULAR -> MobilitySpec(RobotConstants.CLIMBABLE_STEP_HEIGHT_BLOCKS, 0.0, 0.0, 0)
			HeroMobilityMode.WHEEL_LEGGED -> MobilitySpec(2.0, 0.6, 2.0, 10)
		}
	}

	private fun infantryMobilitySpec(mode: InfantryMobilityMode): MobilitySpec {
		return when (mode) {
			InfantryMobilityMode.REGULAR -> MobilitySpec(RobotConstants.CLIMBABLE_STEP_HEIGHT_BLOCKS, 0.0, 0.0, 0)
			InfantryMobilityMode.WHEEL_LEGGED -> MobilitySpec(2.0, 0.6, 2.0, 10)
		}
	}

	private fun movementSpeedMetersPerSecond(chassisPowerWatts: Int, massKilograms: Double): Double {
		if (chassisPowerWatts <= 0) {
			return 0.0
		}
		return RobotConstants.ROBOT_SPEED_COEFFICIENT * sqrt(chassisPowerWatts / massKilograms)
	}

	private fun statForLevel(values: IntArray, level: Int): Int {
		return values[level.coerceIn(1, values.size) - 1]
	}

	private fun statForLevel(values: DoubleArray, level: Int): Double {
		return values[level.coerceIn(1, values.size) - 1]
	}
}

private data class RobotLevelSpec(
	val maxHp: Int,
	val chassisPower: Int,
	val heatLimit: Int,
	val heatCooling: Double
)

private data class MobilitySpec(
	val stepHeightBlocks: Double,
	val jumpStrength: Double,
	val jumpHeightBlocks: Double,
	val stepPauseTicks: Int
)
