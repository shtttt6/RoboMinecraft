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
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.ai.attributes.Attributes

object RoboMinecraftClient : ClientModInitializer {
	private const val CLIENT_SHOT_COOLDOWN_TICKS = 1
	private const val HEAT_RING_SEGMENTS = 56

	private lateinit var configKey: KeyMapping
	private lateinit var buyInfantryAmmoKey: KeyMapping
	private lateinit var buyHeroAmmoKey: KeyMapping

	private var shotCooldownTicks = 0

	override fun onInitializeClient() {
		registerKeyMappings()
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
		ClientTickEvents.END_CLIENT_TICK.register(::tickShootingInput)
		ClientTickEvents.END_CLIENT_TICK.register(::tickScreenKeys)
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

	private fun tickShootingInput(client: Minecraft) {
		if (shotCooldownTicks > 0) {
			shotCooldownTicks--
		}

		val player = client.player ?: return

		if (!isRobotModeActive() || !player.mainHandItem.isEmpty || !client.options.keyUse.isDown || shotCooldownTicks > 0 || RobotClientState.currentAmmo() <= 0) {
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
		val white = 0xDDFFFFFF.toInt()

		renderHeatRing(context, centerX, centerY)
		renderCompactCrosshair(context, centerX, centerY, white)
		renderHealthBar(context, client, player, height)
		renderAmmoCounter(context, client, width)
	}

	private fun renderCompactCrosshair(context: GuiGraphics, centerX: Int, centerY: Int, color: Int) {
		context.fill(centerX - 11, centerY - 1, centerX - 4, centerY + 1, color)
		context.fill(centerX + 5, centerY - 1, centerX + 12, centerY + 1, color)
		context.fill(centerX - 1, centerY - 11, centerX + 1, centerY - 4, color)
		context.fill(centerX - 1, centerY + 5, centerX + 1, centerY + 12, color)
	}

	private fun renderHeatRing(context: GuiGraphics, centerX: Int, centerY: Int) {
		val heatProgress = (RobotClientState.heat.toDouble() / RobotClientState.heatLimit.toDouble()).coerceIn(0.0, 1.0)
		val activeSegments = (HEAT_RING_SEGMENTS * heatProgress).toInt()
		val radius = 19.0
		val background = 0x44FFFFFF
		val heatColor = 0xFFFFFFFF.toInt()

		for (index in 0 until HEAT_RING_SEGMENTS) {
			val angle = -Math.PI / 2.0 - (Math.PI * 2.0 * index / HEAT_RING_SEGMENTS)
			val x = centerX + kotlin.math.cos(angle) * radius
			val y = centerY + kotlin.math.sin(angle) * radius
			val color = if (index < activeSegments) heatColor else background
			context.fill(x.toInt() - 1, y.toInt() - 1, x.toInt() + 2, y.toInt() + 2, color)
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

	private fun isRobotModeActive(): Boolean {
		val player = Minecraft.getInstance().player ?: return false
		val scale = player.getAttribute(Attributes.SCALE) ?: return false
		return scale.hasModifier(RoboMinecraft.ROBOT_SCALE_ID)
	}

	private inline fun <reified T : Enum<T>> enumByOrdinal(ordinal: Int, fallback: T): T {
		return enumValues<T>().getOrElse(ordinal) { fallback }
	}
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
