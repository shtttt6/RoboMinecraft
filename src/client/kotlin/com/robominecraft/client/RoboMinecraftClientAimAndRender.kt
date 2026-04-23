package com.robominecraft.client

import com.robominecraft.RoboMinecraft
import com.robominecraft.RobotKind
import com.robominecraft.RobotRules
import com.robominecraft.RobotVehicleEntity
import com.mojang.math.Axis
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.ShapeRenderer
import net.minecraft.client.renderer.debug.DebugRenderer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.tan

internal fun RoboMinecraftClient.registerWorldRendering() {
	WorldRenderEvents.AFTER_ENTITIES.register { context ->
		val client = Minecraft.getInstance()
		val level = client.level ?: return@register
		val matrices = context.matrices()
		val consumers = context.consumers() ?: return@register
		val cameraPos = client.gameRenderer.mainCamera.position
		val firstPerson = client.options.cameraType.isFirstPerson

		level.players().forEach { player ->
			if (!isRobotModeActive(player)) {
				return@forEach
			}
			if (player == client.player && firstPerson) {
				return@forEach
			}

			val spec = robotPhysicalSpec(player) ?: return@forEach
			renderRobotHull(matrices, consumers, player, cameraPos, spec, player == client.player)
		}
	}
}

internal fun RoboMinecraftClient.tickLocalCollisionBox(client: Minecraft) {
	val player = client.player ?: return
	val spec = if (isRobotModeActive(player)) robotPhysicalSpec(player) else null

	if (spec == null) {
		if (localCollisionBoxApplied) {
			player.refreshDimensions()
			localCollisionBoxApplied = false
		}
		return
	}

	val collider = player.vehicle as? RobotVehicleEntity ?: player
	player.setBoundingBox(spec.collisionBoxAt(collider.x, collider.y, collider.z, collider.yBodyRot))
	localCollisionBoxApplied = true
}

internal fun RoboMinecraftClient.tickAutoAim(client: Minecraft) {
	autoAimHasTarget = false
	if (client.screen != null || !client.options.keyUse.isDown) {
		return
	}

	val player = client.player ?: return
	if (!isRobotModeActive() || !player.mainHandItem.isEmpty) {
		return
	}

	val target = findAutoAimTarget(client, player) ?: return
	aimAt(player, target.point)
	autoAimHasTarget = true
}

private fun RoboMinecraftClient.renderRobotHull(
	matrices: com.mojang.blaze3d.vertex.PoseStack,
	consumers: net.minecraft.client.renderer.MultiBufferSource,
	player: Player,
	cameraPos: Vec3,
	spec: com.robominecraft.RobotPhysicalSpec,
	isLocalPlayer: Boolean
) {
	val halfWidth = spec.widthBlocks / 2.0
	val halfLength = spec.lengthBlocks / 2.0
	val modelBox = AABB(-halfWidth, 0.0, -halfLength, halfWidth, spec.collisionHeightBlocks, halfLength)
	val kind = robotKindFor(player)
	val fillColor = when (kind) {
		RobotKind.HERO -> floatArrayOf(0.75f, 0.26f, 0.22f, if (isLocalPlayer) 0.35f else 0.55f)
		RobotKind.INFANTRY -> floatArrayOf(0.18f, 0.64f, 0.82f, if (isLocalPlayer) 0.35f else 0.55f)
		RobotKind.AERIAL -> floatArrayOf(0.95f, 0.84f, 0.28f, if (isLocalPlayer) 0.35f else 0.55f)
	}

	matrices.pushPose()
	matrices.translate(player.x - cameraPos.x, player.y - cameraPos.y, player.z - cameraPos.z)
	matrices.mulPose(Axis.YP.rotationDegrees(-player.yBodyRot))
	DebugRenderer.renderFilledBox(matrices, consumers, modelBox, fillColor[0], fillColor[1], fillColor[2], fillColor[3])
	ShapeRenderer.renderLineBox(
		matrices.last(),
		consumers.getBuffer(RenderType.lines()),
		modelBox,
		0.95f,
		0.95f,
		0.95f,
		if (isLocalPlayer) 0.55f else 0.9f
	)
	matrices.popPose()
}

private fun RoboMinecraftClient.findAutoAimTarget(client: Minecraft, player: LocalPlayer): AimTarget? {
	val level = player.level()
	val searchBox = player.boundingBox.inflate(AIM_ASSIST_RANGE_BLOCKS)
	val eyePosition = pilotViewPosition(player)
	val frameAngles = aimFrameAngles(client)
	var bestTarget: AimTarget? = null

	level.getEntities(player, searchBox) { target -> canAutoAimAt(player, target) }.forEach { target ->
		val aimTarget = bestAimPointInFrame(player, eyePosition, target, frameAngles) ?: return@forEach
		if (aimTarget.score < (bestTarget?.score ?: Double.POSITIVE_INFINITY)) {
			bestTarget = aimTarget
		}
	}

	return bestTarget
}

private fun RoboMinecraftClient.canAutoAimAt(player: LocalPlayer, target: Entity): Boolean {
	return target is LivingEntity &&
		target !== player &&
		target.isAlive &&
		!target.isSpectator &&
		target.isPickable &&
		target.canBeHitByProjectile() &&
		player.hasLineOfSight(target)
}

private fun RoboMinecraftClient.bestAimPointInFrame(
	player: LocalPlayer,
	eyePosition: Vec3,
	target: Entity,
	frameAngles: AimFrameAngles
): AimTarget? {
	val box = target.boundingBox
	var bestTarget: AimTarget? = null

	candidateAimPoints(box).forEach { point ->
		val rotation = rotationFromTo(eyePosition, point) ?: return@forEach
		val yawOffset = wrapDegrees(rotation.yaw - player.yRot.toDouble())
		val pitchOffset = rotation.pitch - player.xRot.toDouble()

		if (abs(yawOffset) > frameAngles.halfYawDegrees || abs(pitchOffset) > frameAngles.halfPitchDegrees) {
			return@forEach
		}

		val distance = eyePosition.distanceTo(point)
		val score = abs(yawOffset) / frameAngles.halfYawDegrees +
			abs(pitchOffset) / frameAngles.halfPitchDegrees +
			distance / AIM_ASSIST_RANGE_BLOCKS * 0.05
		val aimTarget = AimTarget(point, score)
		if (aimTarget.score < (bestTarget?.score ?: Double.POSITIVE_INFINITY)) {
			bestTarget = aimTarget
		}
	}

	return bestTarget
}

private fun RoboMinecraftClient.aimAt(player: LocalPlayer, point: Vec3) {
	val rotation = rotationFromTo(pilotViewPosition(player), point) ?: return
	val yaw = rotation.yaw.toFloat()
	val pitch = rotation.pitch.coerceIn(-90.0, 90.0).toFloat()

	player.yRot = yaw
	player.xRot = pitch
	player.yHeadRot = yaw
	player.yBodyRot = yaw
}

private fun RoboMinecraftClient.rotationFromTo(origin: Vec3, target: Vec3): AimRotation? {
	val delta = target.subtract(origin)
	val horizontalDistance = sqrt(delta.x * delta.x + delta.z * delta.z)
	if (horizontalDistance < 0.15 || abs(delta.y) < 1.0e-6 && horizontalDistance < 1.0e-6) {
		return null
	}

	val yaw = Math.toDegrees(atan2(delta.z, delta.x)) - 90.0
	val pitch = -Math.toDegrees(atan2(delta.y, horizontalDistance))
	return AimRotation(yaw, pitch)
}

private fun RoboMinecraftClient.aimFrameAngles(client: Minecraft): AimFrameAngles {
	val window = client.window
	val guiWidth = window.guiScaledWidth.coerceAtLeast(1).toDouble()
	val guiHeight = window.guiScaledHeight.coerceAtLeast(1).toDouble()
	val aspectRatio = guiWidth / guiHeight
	val verticalFovRadians = Math.toRadians(client.options.fov().get().toDouble())
	val horizontalFovRadians = 2.0 * atan(tan(verticalFovRadians / 2.0) * aspectRatio)
	val halfYaw = atan(tan(horizontalFovRadians / 2.0) * (AIM_FRAME_WIDTH.toDouble() / guiWidth))
	val halfPitch = atan(tan(verticalFovRadians / 2.0) * (AIM_FRAME_HEIGHT.toDouble() / guiHeight))

	return AimFrameAngles(
		halfYawDegrees = Math.toDegrees(halfYaw),
		halfPitchDegrees = Math.toDegrees(halfPitch)
	)
}

private fun RoboMinecraftClient.candidateAimPoints(box: AABB): Array<Vec3> {
	val centerX = (box.minX + box.maxX) * 0.5
	val centerZ = (box.minZ + box.maxZ) * 0.5
	val widthInset = (box.maxX - box.minX) * 0.2
	val depthInset = (box.maxZ - box.minZ) * 0.2
	val lowerY = box.minY + (box.maxY - box.minY) * 0.45
	val upperY = box.minY + (box.maxY - box.minY) * 0.75

	return arrayOf(
		Vec3(centerX, upperY, centerZ),
		Vec3(centerX, lowerY, centerZ),
		Vec3(box.minX + widthInset, upperY, centerZ),
		Vec3(box.maxX - widthInset, upperY, centerZ),
		Vec3(centerX, upperY, box.minZ + depthInset),
		Vec3(centerX, upperY, box.maxZ - depthInset)
	)
}

internal fun RoboMinecraftClient.isRobotModeActive(): Boolean {
	val player = Minecraft.getInstance().player ?: return false
	return isRobotModeActive(player)
}

internal fun RoboMinecraftClient.isRobotModeActive(player: Player): Boolean {
	if (player == Minecraft.getInstance().player) {
		return RobotClientState.enabled
	}
	val scale = player.getAttribute(Attributes.SCALE) ?: return false
	return scale.hasModifier(RoboMinecraft.ROBOT_SCALE_ID)
}

internal fun RoboMinecraftClient.robotPhysicalSpec(player: Player): com.robominecraft.RobotPhysicalSpec? {
	if (!isRobotModeActive(player)) {
		return null
	}

	return RobotRules.physicalSpec(robotKindFor(player))
}

internal fun RoboMinecraftClient.robotKindFor(player: Player): RobotKind {
	if (player == Minecraft.getInstance().player) {
		return RobotClientState.robotKind
	}

	val vehicleKind = (player.vehicle as? RobotVehicleEntity)?.robotKind()
	if (vehicleKind != null) {
		return vehicleKind
	}

	return RobotRules.inferKindFromScale(player.getAttributeValue(Attributes.SCALE))
}

internal fun RoboMinecraftClient.pilotViewPosition(player: Player): Vec3 {
	if (robotKindFor(player) != RobotKind.AERIAL) {
		return player.eyePosition
	}
	val vehicle = player.vehicle as? RobotVehicleEntity ?: return player.eyePosition
	val spec = robotPhysicalSpec(player) ?: return player.eyePosition
	return Vec3(vehicle.x, vehicle.y + spec.viewHeightBlocks, vehicle.z)
}

private fun RoboMinecraftClient.wrapDegrees(degrees: Double): Double {
	var wrapped = degrees % 360.0
	if (wrapped >= 180.0) {
		wrapped -= 360.0
	}
	if (wrapped < -180.0) {
		wrapped += 360.0
	}
	return wrapped
}

internal data class AimRotation(val yaw: Double, val pitch: Double)

internal data class AimFrameAngles(val halfYawDegrees: Double, val halfPitchDegrees: Double)

internal data class AimTarget(val point: Vec3, val score: Double)
