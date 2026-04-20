package com.robominecraft.client

import com.robominecraft.FireBlasterPayload
import com.robominecraft.HeroMode
import com.robominecraft.InfantryChassisMode
import com.robominecraft.InfantryLauncherMode
import com.robominecraft.RoboMinecraft
import com.robominecraft.RobotHudPayload
import com.robominecraft.RobotKind
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.player.LocalPlayer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.tan

object RoboMinecraftClient : ClientModInitializer {
	private const val CLIENT_SHOT_COOLDOWN_TICKS = 1
	private const val HEAT_RING_SEGMENTS = 56
	private const val HEAT_RING_RADIUS = 16.0
	private const val AIM_FRAME_WIDTH = 128
	private const val AIM_FRAME_HEIGHT = 72
	private const val AIM_ASSIST_RANGE_BLOCKS = 120.0

	private lateinit var configKey: KeyMapping
	private lateinit var buyInfantryAmmoKey: KeyMapping
	private lateinit var buyHeroAmmoKey: KeyMapping

	private var shotCooldownTicks = 0
	private var autoAimHasTarget = false

	override fun onInitializeClient() {
		registerKeyMappings()
		registerHudElements()
		ClientPlayNetworking.registerGlobalReceiver(RobotHudPayload.ID) { payload, _ ->
			RobotClientState.heat = payload.heat.coerceAtLeast(0)
			RobotClientState.heatLimit = payload.heatLimit.coerceAtLeast(1)
			RobotClientState.heroAmmo = payload.heroAmmo.coerceAtLeast(0)
			RobotClientState.infantryAmmo = payload.infantryAmmo.coerceAtLeast(0)
			RobotClientState.robotKind = enumByOrdinal(payload.robotKind, RobotKind.INFANTRY)
			RobotClientState.heroMode = enumByOrdinal(payload.heroMode, HeroMode.MELEE)
			RobotClientState.infantryChassisMode = enumByOrdinal(payload.infantryChassisMode, InfantryChassisMode.POWER)
			RobotClientState.infantryLauncherMode = enumByOrdinal(payload.infantryLauncherMode, InfantryLauncherMode.BURST)
		}
		ClientTickEvents.END_CLIENT_TICK.register(::tickAutoAim)
		ClientTickEvents.END_CLIENT_TICK.register(::tickShootingInput)
		ClientTickEvents.END_CLIENT_TICK.register(::tickScreenKeys)
	}

	private fun registerHudElements() {
		hideVanillaHudElementWhenRobotActive(VanillaHudElements.HEALTH_BAR)
		hideVanillaHudElementWhenRobotActive(VanillaHudElements.FOOD_BAR)
		HudElementRegistry.attachElementAfter(
			VanillaHudElements.CROSSHAIR,
			ResourceLocation.fromNamespaceAndPath(RoboMinecraft.MOD_ID, "robot_hud"),
			::renderRobotHud
		)
	}

	private fun registerKeyMappings() {
		configKey = KeyBindingHelper.registerKeyBinding(
			KeyMapping("key.robominecraft.config", InputConstants.Type.KEYSYM, InputConstants.KEY_P, KeyMapping.Category.MISC)
		)
		buyInfantryAmmoKey = KeyBindingHelper.registerKeyBinding(
			KeyMapping("key.robominecraft.buy_infantry_ammo", InputConstants.Type.KEYSYM, InputConstants.KEY_O, KeyMapping.Category.MISC)
		)
		buyHeroAmmoKey = KeyBindingHelper.registerKeyBinding(
			KeyMapping("key.robominecraft.buy_hero_ammo", InputConstants.Type.KEYSYM, InputConstants.KEY_I, KeyMapping.Category.MISC)
		)
	}

	private fun hideVanillaHudElementWhenRobotActive(id: ResourceLocation) {
		HudElementRegistry.replaceElement(id) { original ->
			HudElement { context, tickCounter ->
				if (!isRobotModeActive()) {
					original.render(context, tickCounter)
				}
			}
		}
	}

	private fun tickAutoAim(client: Minecraft) {
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

	private fun tickShootingInput(client: Minecraft) {
		if (shotCooldownTicks > 0) {
			shotCooldownTicks--
		}

		val player = client.player ?: return

		if (!isRobotModeActive() || !player.mainHandItem.isEmpty || !client.options.keyAttack.isDown || shotCooldownTicks > 0 || RobotClientState.currentAmmo() <= 0) {
			return
		}

		if (ClientPlayNetworking.canSend(FireBlasterPayload.ID)) {
			ClientPlayNetworking.send(FireBlasterPayload)
			shotCooldownTicks = CLIENT_SHOT_COOLDOWN_TICKS
		}
	}

	private fun tickScreenKeys(client: Minecraft) {
		if (client.screen != null) {
			return
		}

		while (configKey.consumeClick()) {
			client.setScreen(RobotConfigScreen())
		}
		while (buyInfantryAmmoKey.consumeClick()) {
			if (RobotClientState.robotKind == RobotKind.INFANTRY) {
				client.setScreen(AmmoPurchaseScreen(RobotKind.INFANTRY))
			}
		}
		while (buyHeroAmmoKey.consumeClick()) {
			if (RobotClientState.robotKind == RobotKind.HERO) {
				client.setScreen(AmmoPurchaseScreen(RobotKind.HERO))
			}
		}
	}

	private fun renderRobotHud(context: GuiGraphics, @Suppress("UNUSED_PARAMETER") tickCounter: net.minecraft.client.DeltaTracker) {
		val client = Minecraft.getInstance()
		val player = client.player ?: return
		if (!isRobotModeActive()) {
			return
		}

		val width = context.guiWidth()
		val height = context.guiHeight()
		val centerX = width / 2
		val centerY = height / 2
		val white = 0x99FFFFFF.toInt()

		renderHeatRing(context, centerX, centerY)
		renderAimAssistFrame(context, client, centerX, centerY)
		renderCompactCrosshair(context, centerX, centerY, white)
		renderHealthBar(context, client, player, height)
		renderAmmoCounter(context, client, width)
	}

	private fun renderAimAssistFrame(context: GuiGraphics, client: Minecraft, centerX: Int, centerY: Int) {
		val left = centerX - AIM_FRAME_WIDTH / 2
		val top = centerY - AIM_FRAME_HEIGHT / 2
		val right = left + AIM_FRAME_WIDTH
		val bottom = top + AIM_FRAME_HEIGHT
		val color = when {
			autoAimHasTarget -> 0xFF66FFAA.toInt()
			client.options.keyUse.isDown -> 0xCCFFFFFF.toInt()
			else -> 0x66FFFFFF
		}

		context.fill(left, top, right, top + 1, color)
		context.fill(left, bottom - 1, right, bottom, color)
		context.fill(left, top, left + 1, bottom, color)
		context.fill(right - 1, top, right, bottom, color)
	}

	private fun renderCompactCrosshair(context: GuiGraphics, centerX: Int, centerY: Int, color: Int) {
		context.fill(centerX - 8, centerY, centerX - 3, centerY + 1, color)
		context.fill(centerX + 4, centerY, centerX + 9, centerY + 1, color)
		context.fill(centerX, centerY - 8, centerX + 1, centerY - 3, color)
		context.fill(centerX, centerY + 4, centerX + 1, centerY + 9, color)
	}

	private fun renderHeatRing(context: GuiGraphics, centerX: Int, centerY: Int) {
		val heatProgress = (RobotClientState.heat.toDouble() / RobotClientState.heatLimit.toDouble()).coerceIn(0.0, 1.0)
		val activeSegments = (HEAT_RING_SEGMENTS * heatProgress).toInt()
		val background = 0x22FFFFFF
		val heatColor = 0xAAFFFFFF.toInt()

		for (index in 0 until HEAT_RING_SEGMENTS) {
			val angle = -Math.PI / 2.0 - (Math.PI * 2.0 * index / HEAT_RING_SEGMENTS)
			val x = centerX + kotlin.math.cos(angle) * HEAT_RING_RADIUS
			val y = centerY + kotlin.math.sin(angle) * HEAT_RING_RADIUS
			val color = if (index < activeSegments) heatColor else background
			context.fill(x.toInt(), y.toInt(), x.toInt() + 1, y.toInt() + 1, color)
		}
	}

	private fun renderAmmoCounter(context: GuiGraphics, client: Minecraft, width: Int) {
		val bulletName = if (RobotClientState.robotKind == RobotKind.HERO) "42mm" else "17mm"
		val text = "$bulletName x${RobotClientState.currentAmmo()}"
		val x = width - client.font.width(text) - 8
		val y = 8
		val color = if (RobotClientState.currentAmmo() > 0) 0xFFE8F7FF.toInt() else 0xFFFF6060.toInt()

		context.fill(x - 5, y - 3, width - 4, y + 12, 0x88000000.toInt())
		context.drawString(client.font, text, x, y, color, false)
	}

	private fun renderHealthBar(context: GuiGraphics, client: Minecraft, player: net.minecraft.client.player.LocalPlayer, height: Int) {
		val maxHealth = player.getAttributeValue(Attributes.MAX_HEALTH).toFloat().coerceAtLeast(1.0f)
		val healthProgress = (player.health / maxHealth).coerceIn(0.0f, 1.0f)
		val barX = 8
		val barY = height - 24
		val barWidth = 144
		val barHeight = 10
		val fillWidth = (barWidth * healthProgress).toInt()

		context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xAA220A0A.toInt())
		context.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFFE34141.toInt())
		context.fill(barX, barY, barX + barWidth, barY + 1, 0xDDFFFFFF.toInt())
		context.fill(barX, barY + barHeight - 1, barX + barWidth, barY + barHeight, 0xDDFFFFFF.toInt())
		context.fill(barX, barY, barX + 1, barY + barHeight, 0xDDFFFFFF.toInt())
		context.fill(barX + barWidth - 1, barY, barX + barWidth, barY + barHeight, 0xDDFFFFFF.toInt())
		context.drawString(
			client.font,
			"HP ${player.health.toInt()}/${maxHealth.toInt()}",
			barX,
			barY - 11,
			0xFFE8F7FF.toInt(),
			false
		)
	}

	private fun findAutoAimTarget(client: Minecraft, player: LocalPlayer): AimTarget? {
		val level = player.level()
		val searchBox = player.boundingBox.inflate(AIM_ASSIST_RANGE_BLOCKS)
		val eyePosition = player.eyePosition
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

	private fun canAutoAimAt(player: LocalPlayer, target: Entity): Boolean {
		return target is LivingEntity &&
			target !== player &&
			target.isAlive &&
			!target.isSpectator &&
			target.isPickable &&
			target.canBeHitByProjectile() &&
			player.hasLineOfSight(target)
	}

	private fun bestAimPointInFrame(
		player: LocalPlayer,
		eyePosition: Vec3,
		target: Entity,
		frameAngles: AimFrameAngles
	): AimTarget? {
		val box = target.boundingBox
		val center = box.center
		val aimPoint = Vec3(center.x, box.minY + (box.maxY - box.minY) * 0.75, center.z)
		val testPoints = arrayOf(
			center,
			aimPoint,
			Vec3(center.x, box.minY, center.z),
			Vec3(box.minX, box.minY, box.minZ),
			Vec3(box.minX, box.minY, box.maxZ),
			Vec3(box.maxX, box.minY, box.minZ),
			Vec3(box.maxX, box.minY, box.maxZ),
			Vec3(box.minX, box.maxY, box.minZ),
			Vec3(box.minX, box.maxY, box.maxZ),
			Vec3(box.maxX, box.maxY, box.minZ),
			Vec3(box.maxX, box.maxY, box.maxZ)
		)
		var bestTarget: AimTarget? = null

		testPoints.forEach { point ->
			val rotation = rotationFromTo(eyePosition, point) ?: return@forEach
			val yawOffset = wrapDegrees(rotation.yaw - player.getYRot().toDouble())
			val pitchOffset = rotation.pitch - player.getXRot().toDouble()

			if (abs(yawOffset) > frameAngles.halfYawDegrees || abs(pitchOffset) > frameAngles.halfPitchDegrees) {
				return@forEach
			}

			val distance = eyePosition.distanceTo(point)
			val score = abs(yawOffset) / frameAngles.halfYawDegrees +
				abs(pitchOffset) / frameAngles.halfPitchDegrees +
				distance / AIM_ASSIST_RANGE_BLOCKS * 0.05
			val aimTarget = AimTarget(aimPoint, score)
			if (aimTarget.score < (bestTarget?.score ?: Double.POSITIVE_INFINITY)) {
				bestTarget = aimTarget
			}
		}

		return bestTarget
	}

	private fun aimAt(player: LocalPlayer, point: Vec3) {
		val rotation = rotationFromTo(player.eyePosition, point) ?: return
		val yaw = rotation.yaw.toFloat()
		val pitch = rotation.pitch.coerceIn(-90.0, 90.0).toFloat()

		player.setYRot(yaw)
		player.setXRot(pitch)
		player.setYHeadRot(yaw)
		player.setYBodyRot(yaw)
	}

	private fun rotationFromTo(origin: Vec3, target: Vec3): AimRotation? {
		val delta = target.subtract(origin)
		val horizontalDistance = sqrt(delta.x * delta.x + delta.z * delta.z)
		if (horizontalDistance < 1.0e-6 && abs(delta.y) < 1.0e-6) {
			return null
		}

		val yaw = Math.toDegrees(atan2(delta.z, delta.x)) - 90.0
		val pitch = -Math.toDegrees(atan2(delta.y, horizontalDistance))
		return AimRotation(yaw, pitch)
	}

	private fun aimFrameAngles(client: Minecraft): AimFrameAngles {
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

	private fun wrapDegrees(degrees: Double): Double {
		var wrapped = degrees % 360.0
		if (wrapped >= 180.0) {
			wrapped -= 360.0
		}
		if (wrapped < -180.0) {
			wrapped += 360.0
		}
		return wrapped
	}

	private fun isRobotModeActive(): Boolean {
		val player = Minecraft.getInstance().player ?: return false
		val scale = player.getAttribute(Attributes.SCALE) ?: return false
		return scale.hasModifier(RoboMinecraft.ROBOT_SCALE_ID)
	}

	private inline fun <reified T : Enum<T>> enumByOrdinal(ordinal: Int, fallback: T): T {
		return enumValues<T>().getOrElse(ordinal) { fallback }
	}

	private data class AimRotation(val yaw: Double, val pitch: Double)

	private data class AimFrameAngles(val halfYawDegrees: Double, val halfPitchDegrees: Double)

	private data class AimTarget(val point: Vec3, val score: Double)
}

object RobotClientState {
	var heat: Int = 0
	var heatLimit: Int = 1
	var heroAmmo: Int = 0
	var infantryAmmo: Int = 0
	var robotKind: RobotKind = RobotKind.INFANTRY
	var heroMode: HeroMode = HeroMode.MELEE
	var infantryChassisMode: InfantryChassisMode = InfantryChassisMode.POWER
	var infantryLauncherMode: InfantryLauncherMode = InfantryLauncherMode.BURST

	fun currentAmmo(): Int {
		return ammoFor(robotKind)
	}

	fun ammoFor(kind: RobotKind): Int {
		return when (kind) {
			RobotKind.HERO -> heroAmmo
			RobotKind.INFANTRY -> infantryAmmo
		}
	}
}
