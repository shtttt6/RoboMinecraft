package com.robominecraft

import net.minecraft.core.Holder
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack

fun RoboMinecraft.isRobotPilot(player: Entity): Boolean {
	return pilotStates[player.uuid]?.enabled ?: true
}

fun RoboMinecraft.isAerialRobot(entity: Entity): Boolean {
	return isRobotPilot(entity) && stateFor(entity).profile.kind == RobotKind.AERIAL
}

fun RoboMinecraft.isAerialDescending(entity: Entity): Boolean {
	return isAerialRobot(entity) && stateFor(entity).aerialDescending
}

fun RoboMinecraft.isAerialFlightMode(entity: Entity): Boolean {
	return isAerialRobot(entity) && stateFor(entity).aerialFlightMode
}

fun RoboMinecraft.isAerialAscending(entity: Entity): Boolean {
	return isAerialRobot(entity) && stateFor(entity).aerialAscending
}

fun RoboMinecraft.robotStatsOrNull(entity: Entity): RobotStats? {
	return if (isRobotPilot(entity)) stateFor(entity).stats() else null
}

internal fun RoboMinecraft.stateFor(player: Entity): PilotState {
	return pilotStates.getOrPut(player.uuid) { PilotState() }
}

internal fun RoboMinecraft.applyRobotAttributes(player: LivingEntity, state: PilotState, stats: RobotStats) {
	val maxHealth = stats.maxHp.toDouble()
	val targetSpeedBlocksPerSecond = stats.movementSpeedMetersPerSecond * RobotConstants.WORLD_BLOCKS_PER_REAL_METER
	val baseSpeed = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)
	val baseJumpStrength = player.getAttributeBaseValue(Attributes.JUMP_STRENGTH).coerceAtLeast(1.0E-6)
	val targetMovementAttribute = targetSpeedBlocksPerSecond / RobotConstants.VANILLA_BASE_SPEED_BLOCKS_PER_SECOND * 0.1
	val movementBoost = targetMovementAttribute / baseSpeed - 1.0
	val jumpStrengthBoost = stats.jumpStrength / baseJumpStrength - 1.0
	val scaleBoost = stats.physicalSpec.heightBlocks / RobotConstants.PLAYER_BASE_HEIGHT_BLOCKS - 1.0
	val stepHeightBoost = stats.stepHeightBlocks - player.getAttributeBaseValue(Attributes.STEP_HEIGHT)

	applyAttribute(player, Attributes.MAX_HEALTH, AttributeModifier(RobotAttributeIds.MAX_HEALTH, maxHealth - RoboMinecraft.BASE_PLAYER_HEALTH, AttributeModifier.Operation.ADD_VALUE))
	applyAttribute(player, Attributes.MOVEMENT_SPEED, AttributeModifier(RobotAttributeIds.MOVEMENT, movementBoost, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))
	applyAttribute(player, Attributes.STEP_HEIGHT, AttributeModifier(RobotAttributeIds.STEP_HEIGHT, stepHeightBoost, AttributeModifier.Operation.ADD_VALUE))
	applyAttribute(player, Attributes.JUMP_STRENGTH, AttributeModifier(RobotAttributeIds.JUMP_STRENGTH, jumpStrengthBoost, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))
	applyAttribute(player, Attributes.ARMOR, AttributeModifier(RobotAttributeIds.ARMOR, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))
	applyAttribute(player, Attributes.ARMOR_TOUGHNESS, AttributeModifier(RobotAttributeIds.ARMOR_TOUGHNESS, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))
	applyAttribute(player, Attributes.KNOCKBACK_RESISTANCE, AttributeModifier(RobotAttributeIds.KNOCKBACK, 0.65, AttributeModifier.Operation.ADD_VALUE))
	applyAttribute(player, Attributes.SCALE, AttributeModifier(RobotAttributeIds.SCALE, scaleBoost, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))

	if (state.appliedMaxHp != stats.maxHp) {
		player.health = stats.maxHp.toFloat()
		state.appliedMaxHp = stats.maxHp
	}
}

internal fun RoboMinecraft.restorePlayerCollisionBox(player: ServerPlayer) {
	player.refreshDimensions()
}

internal fun RoboMinecraft.keepFirstHotbarSlotEmpty(player: ServerPlayer) {
	val inventory = player.inventory
	val reservedStack = inventory.getItem(0)
	if (reservedStack.isEmpty) {
		return
	}

	for (slot in 1 until 36) {
		if (inventory.getItem(slot).isEmpty) {
			inventory.setItem(slot, reservedStack.copy())
			inventory.setItem(0, ItemStack.EMPTY)
			return
		}
	}
}

internal fun RoboMinecraft.removeRobotAttributes(player: LivingEntity) {
	removeAttribute(player, Attributes.MAX_HEALTH, RobotAttributeIds.MAX_HEALTH)
	removeAttribute(player, Attributes.MOVEMENT_SPEED, RobotAttributeIds.MOVEMENT)
	removeAttribute(player, Attributes.STEP_HEIGHT, RobotAttributeIds.STEP_HEIGHT)
	removeAttribute(player, Attributes.JUMP_STRENGTH, RobotAttributeIds.JUMP_STRENGTH)
	removeAttribute(player, Attributes.ARMOR, RobotAttributeIds.ARMOR)
	removeAttribute(player, Attributes.ARMOR_TOUGHNESS, RobotAttributeIds.ARMOR_TOUGHNESS)
	removeAttribute(player, Attributes.KNOCKBACK_RESISTANCE, RobotAttributeIds.KNOCKBACK)
	removeAttribute(player, Attributes.SCALE, RobotAttributeIds.SCALE)
}

internal inline fun <reified T : Enum<T>> RoboMinecraft.enumByOrdinal(ordinal: Int, fallback: T): T {
	return enumValues<T>().getOrElse(ordinal) { fallback }
}

internal fun RoboMinecraft.resetRobotLocomotionState(state: PilotState) {
	state.movementPauseTicks = 0
	state.lastMovementY = null
	state.lastMovementOnGround = false
	state.lastSafeX = null
	state.lastSafeY = null
	state.lastSafeZ = null
	state.pauseLockX = null
	state.pauseLockY = null
	state.pauseLockZ = null
	state.aerialFlightMode = false
	state.aerialAscending = false
	state.aerialDescending = false
}

private fun RoboMinecraft.applyAttribute(
	player: LivingEntity,
	attribute: Holder<Attribute>,
	modifier: AttributeModifier
) {
	player.getAttribute(attribute)?.addOrUpdateTransientModifier(modifier)
}

private fun RoboMinecraft.removeAttribute(
	player: LivingEntity,
	attribute: Holder<Attribute>,
	modifierId: ResourceLocation
) {
	player.getAttribute(attribute)?.removeModifier(modifierId)
}
