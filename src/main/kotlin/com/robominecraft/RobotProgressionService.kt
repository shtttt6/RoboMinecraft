package com.robominecraft

import net.minecraft.server.level.ServerPlayer
import kotlin.math.roundToInt

internal const val MIN_BOUND_MINECRAFT_LEVEL = 1
internal const val MAX_BOUND_MINECRAFT_LEVEL = 10
internal val MAX_BOUND_ROBOT_EXPERIENCE: Int = RobotRules.requiredExperienceForLevel(MAX_BOUND_MINECRAFT_LEVEL)

internal fun RoboMinecraft.syncPilotProgressFromMinecraft(player: ServerPlayer, state: PilotState): Boolean {
	enforceMinecraftLevelBounds(player)

	val syncedLevel = player.experienceLevel.coerceIn(MIN_BOUND_MINECRAFT_LEVEL, MAX_BOUND_MINECRAFT_LEVEL)
	val syncedExperience = robotExperienceFromMinecraft(player)
	val changed = state.level != syncedLevel || state.experience != syncedExperience

	state.level = syncedLevel
	state.experience = syncedExperience
	return changed
}

internal fun RoboMinecraft.setMinecraftLevel(player: ServerPlayer, level: Int) {
	setMinecraftExperienceState(player, level.coerceIn(MIN_BOUND_MINECRAFT_LEVEL, MAX_BOUND_MINECRAFT_LEVEL), 0.0f)
}

internal fun RoboMinecraft.setMinecraftProgressFromRobotExperience(player: ServerPlayer, robotExperience: Int) {
	val clampedExperience = robotExperience.coerceIn(0, MAX_BOUND_ROBOT_EXPERIENCE)
	val targetLevel = RobotRules.levelForExperience(clampedExperience).coerceIn(MIN_BOUND_MINECRAFT_LEVEL, MAX_BOUND_MINECRAFT_LEVEL)
	val progress = if (targetLevel >= MAX_BOUND_MINECRAFT_LEVEL) {
		0.0f
	} else {
		val levelStart = RobotRules.requiredExperienceForLevel(targetLevel)
		val nextLevelStart = RobotRules.requiredExperienceForLevel(targetLevel + 1)
		if (nextLevelStart <= levelStart) {
			0.0f
		} else {
			((clampedExperience - levelStart).toFloat() / (nextLevelStart - levelStart).toFloat()).coerceIn(0.0f, 1.0f)
		}
	}

	setMinecraftExperienceState(player, targetLevel, progress)
}

private fun RoboMinecraft.enforceMinecraftLevelBounds(player: ServerPlayer) {
	val boundedLevel = player.experienceLevel.coerceIn(MIN_BOUND_MINECRAFT_LEVEL, MAX_BOUND_MINECRAFT_LEVEL)
	val boundedProgress = if (boundedLevel >= MAX_BOUND_MINECRAFT_LEVEL) 0.0f else player.experienceProgress.coerceIn(0.0f, 1.0f)
	setMinecraftExperienceState(player, boundedLevel, boundedProgress)
}

private fun RoboMinecraft.robotExperienceFromMinecraft(player: ServerPlayer): Int {
	val currentLevel = player.experienceLevel.coerceIn(MIN_BOUND_MINECRAFT_LEVEL, MAX_BOUND_MINECRAFT_LEVEL)
	if (currentLevel >= MAX_BOUND_MINECRAFT_LEVEL) {
		return MAX_BOUND_ROBOT_EXPERIENCE
	}

	val currentLevelExp = RobotRules.requiredExperienceForLevel(currentLevel)
	val nextLevelExp = RobotRules.requiredExperienceForLevel(currentLevel + 1)
	return (currentLevelExp + (nextLevelExp - currentLevelExp) * player.experienceProgress.coerceIn(0.0f, 1.0f)).roundToInt()
}

private fun RoboMinecraft.setMinecraftExperienceState(player: ServerPlayer, level: Int, progress: Float) {
	val boundedLevel = level.coerceIn(MIN_BOUND_MINECRAFT_LEVEL, MAX_BOUND_MINECRAFT_LEVEL)
	val boundedProgress = if (boundedLevel >= MAX_BOUND_MINECRAFT_LEVEL) 0.0f else progress.coerceIn(0.0f, 1.0f)
	val boundedTotalExperience = totalMinecraftExperienceAtLevel(boundedLevel) +
		if (boundedLevel >= MAX_BOUND_MINECRAFT_LEVEL) 0
		else (minecraftExperienceNeededForNextLevel(boundedLevel) * boundedProgress).roundToInt()

	if (
		player.experienceLevel == boundedLevel &&
		player.experienceProgress == boundedProgress &&
		player.totalExperience == boundedTotalExperience
	) {
		return
	}

	player.experienceLevel = boundedLevel
	player.experienceProgress = boundedProgress
	player.totalExperience = boundedTotalExperience
}

private fun totalMinecraftExperienceAtLevel(level: Int): Int {
	return when {
		level <= 16 -> level * level + 6 * level
		level <= 31 -> (2.5 * level * level - 40.5 * level + 360.0).roundToInt()
		else -> (4.5 * level * level - 162.5 * level + 2220.0).roundToInt()
	}
}

private fun minecraftExperienceNeededForNextLevel(level: Int): Int {
	return when {
		level <= 14 -> 2 * level + 7
		level <= 29 -> 5 * level - 38
		else -> 9 * level - 158
	}
}
