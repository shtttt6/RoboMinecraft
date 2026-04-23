package com.robominecraft

import net.minecraft.resources.ResourceLocation

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
	val ARMOR_TOUGHNESS: ResourceLocation = RobotConstants.id("robot_armor_toughness")
	val KNOCKBACK: ResourceLocation = RobotConstants.id("robot_knockback_resistance")
}

enum class RobotKind(val displayName: String) {
	HERO("Hero"),
	INFANTRY("Infantry"),
	AERIAL("Aerial")
}

enum class HeroMode(val displayName: String) {
	MELEE("melee-priority"),
	RANGED("ranged-priority")
}

enum class HeroMobilityMode(val displayName: String) {
	REGULAR("regular"),
	WHEEL_LEGGED("wheel-legged")
}

enum class InfantryChassisMode(val displayName: String) {
	POWER("power-priority"),
	HEALTH("health-priority")
}

enum class InfantryLauncherMode(val displayName: String) {
	BURST("burst-priority"),
	COOLING("cooling-priority")
}

enum class InfantryMobilityMode(val displayName: String) {
	REGULAR("regular"),
	WHEEL_LEGGED("wheel-legged")
}
