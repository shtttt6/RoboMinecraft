package com.robominecraft.client

import com.robominecraft.RoboMinecraft
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.ai.attributes.Attributes

object RoboMinecraftClient : ClientModInitializer {
	private const val CLIENT_SHOT_COOLDOWN_TICKS = 6
	private const val HEAT_RING_SEGMENTS = 56

	private var shotCooldownTicks = 0
	private var hudHeat = 0
	private var hudHeatLimit = 1

	override fun onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(RoboMinecraft.RobotHudPayload.ID) { payload, _ ->
			hudHeat = payload.heat.coerceAtLeast(0)
			hudHeatLimit = payload.heatLimit.coerceAtLeast(1)
		}
		ClientTickEvents.END_CLIENT_TICK.register(::tickShootingInput)
		HudElementRegistry.attachElementAfter(
			VanillaHudElements.CROSSHAIR,
			ResourceLocation.fromNamespaceAndPath(RoboMinecraft.MOD_ID, "robot_hud"),
			::renderRobotHud
		)
	}

	private fun tickShootingInput(client: Minecraft) {
		if (shotCooldownTicks > 0) {
			shotCooldownTicks--
		}

		val player = client.player ?: return

		if (!isRobotModeActive() || !player.mainHandItem.isEmpty || !client.options.keyUse.isDown || shotCooldownTicks > 0) {
			return
		}

		if (ClientPlayNetworking.canSend(RoboMinecraft.FireBlasterPayload.ID)) {
			ClientPlayNetworking.send(RoboMinecraft.FireBlasterPayload)
			shotCooldownTicks = CLIENT_SHOT_COOLDOWN_TICKS
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
		val white = 0xDDFFFFFF.toInt()

		renderHeatRing(context, centerX, centerY)
		renderCompactCrosshair(context, centerX, centerY, white)
		renderHealthBar(context, client, player, height)
	}

	private fun renderCompactCrosshair(context: GuiGraphics, centerX: Int, centerY: Int, color: Int) {
		context.fill(centerX - 11, centerY - 1, centerX - 4, centerY + 1, color)
		context.fill(centerX + 5, centerY - 1, centerX + 12, centerY + 1, color)
		context.fill(centerX - 1, centerY - 11, centerX + 1, centerY - 4, color)
		context.fill(centerX - 1, centerY + 5, centerX + 1, centerY + 12, color)
	}

	private fun renderHeatRing(context: GuiGraphics, centerX: Int, centerY: Int) {
		val heatProgress = (hudHeat.toDouble() / hudHeatLimit.toDouble()).coerceIn(0.0, 1.0)
		val activeSegments = (HEAT_RING_SEGMENTS * heatProgress).toInt()
		val radius = 19.0
		val background = 0x44FFFFFF
		val heatColor = 0xFFFFFFFF.toInt()

		for (index in 0 until HEAT_RING_SEGMENTS) {
			val angle = Math.PI / 2.0 + (Math.PI * 2.0 * index / HEAT_RING_SEGMENTS)
			val x = centerX + kotlin.math.cos(angle) * radius
			val y = centerY + kotlin.math.sin(angle) * radius
			val color = if (index < activeSegments) heatColor else background
			context.fill(x.toInt() - 1, y.toInt() - 1, x.toInt() + 2, y.toInt() + 2, color)
		}
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

	private fun isRobotModeActive(): Boolean {
		val player = Minecraft.getInstance().player ?: return false
		val scale = player.getAttribute(Attributes.SCALE) ?: return false
		return scale.hasModifier(RoboMinecraft.ROBOT_SCALE_ID)
	}
}
