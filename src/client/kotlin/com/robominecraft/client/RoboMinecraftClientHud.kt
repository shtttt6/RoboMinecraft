package com.robominecraft.client

import com.robominecraft.RoboMinecraft
import com.robominecraft.RobotKind
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.ai.attributes.Attributes

internal fun RoboMinecraftClient.registerHudElements() {
	hideVanillaHudElementWhenRobotActive(VanillaHudElements.HEALTH_BAR)
	hideVanillaHudElementWhenRobotActive(VanillaHudElements.FOOD_BAR)
	hideVanillaHudElementWhenRobotActive(VanillaHudElements.MOUNT_HEALTH)
	HudElementRegistry.attachElementAfter(
		VanillaHudElements.CROSSHAIR,
		ResourceLocation.fromNamespaceAndPath(RoboMinecraft.MOD_ID, "robot_hud"),
		::renderRobotHud
	)
}

private fun RoboMinecraftClient.hideVanillaHudElementWhenRobotActive(id: ResourceLocation) {
	HudElementRegistry.replaceElement(id) { original ->
		HudElement { context, tickCounter ->
			if (!isRobotModeActive()) {
				original.render(context, tickCounter)
			}
		}
	}
}

internal fun RoboMinecraftClient.renderRobotHud(context: GuiGraphics, @Suppress("UNUSED_PARAMETER") tickCounter: net.minecraft.client.DeltaTracker) {
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

private fun RoboMinecraftClient.renderAimAssistFrame(context: GuiGraphics, client: Minecraft, centerX: Int, centerY: Int) {
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

private fun RoboMinecraftClient.renderCompactCrosshair(context: GuiGraphics, centerX: Int, centerY: Int, color: Int) {
	context.fill(centerX - 8, centerY, centerX - 3, centerY + 1, color)
	context.fill(centerX + 4, centerY, centerX + 9, centerY + 1, color)
	context.fill(centerX, centerY - 8, centerX + 1, centerY - 3, color)
	context.fill(centerX, centerY + 4, centerX + 1, centerY + 9, color)
}

private fun RoboMinecraftClient.renderHeatRing(context: GuiGraphics, centerX: Int, centerY: Int) {
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

private fun RoboMinecraftClient.renderAmmoCounter(context: GuiGraphics, client: Minecraft, width: Int) {
	val bulletName = if (RobotClientState.robotKind == RobotKind.HERO) "42mm" else "17mm"
	val text = if (RobotClientState.robotKind == RobotKind.AERIAL) {
		"$bulletName x${RobotClientState.currentAmmo()} | ${if (localAerialFlightMode) "FLIGHT" else "FALL"}"
	} else {
		"$bulletName x${RobotClientState.currentAmmo()}"
	}
	val x = width - client.font.width(text) - 8
	val y = 8
	val color = if (RobotClientState.currentAmmo() > 0) 0xFFE8F7FF.toInt() else 0xFFFF6060.toInt()

	context.fill(x - 5, y - 3, width - 4, y + 12, 0x88000000.toInt())
	context.drawString(client.font, text, x, y, color, false)
}

private fun RoboMinecraftClient.renderHealthBar(context: GuiGraphics, client: Minecraft, player: net.minecraft.client.player.LocalPlayer, height: Int) {
	if (RobotClientState.robotKind == RobotKind.AERIAL) {
		return
	}

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
	context.drawString(client.font, "HP ${player.health.toInt()}/${maxHealth.toInt()}", barX, barY - 11, 0xFFE8F7FF.toInt(), false)
}
