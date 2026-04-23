package com.robominecraft.client

import com.robominecraft.RoboMinecraft
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.KeyMapping
import net.minecraft.client.renderer.entity.NoopRenderer

object RoboMinecraftClient : ClientModInitializer {
	internal const val CLIENT_SHOT_COOLDOWN_TICKS = 1
	internal const val HEAT_RING_SEGMENTS = 56
	internal const val HEAT_RING_RADIUS = 16.0
	internal const val AIM_FRAME_WIDTH = 128
	internal const val AIM_FRAME_HEIGHT = 72
	internal const val AIM_ASSIST_RANGE_BLOCKS = 120.0

	internal lateinit var configKey: KeyMapping
	internal lateinit var robotSelectKey: KeyMapping
	internal lateinit var buyInfantryAmmoKey: KeyMapping
	internal lateinit var buyHeroAmmoKey: KeyMapping
	internal lateinit var aerialFlightToggleKey: KeyMapping
	internal lateinit var aerialAscendKey: KeyMapping
	internal lateinit var aerialDescendKey: KeyMapping

	internal var shotCooldownTicks = 0
	internal var autoAimHasTarget = false
	internal var localCollisionBoxApplied = false
	internal var localAerialFlightMode = false
	internal var lastSentAerialFlightMode = false
	internal var lastSentAerialAscending = false
	internal var lastSentAerialDescending = false

	override fun onInitializeClient() {
		@Suppress("DEPRECATION")
		EntityRendererRegistry.register(RoboMinecraft.ROBOT_VEHICLE_TYPE, ::NoopRenderer)
		registerKeyMappings()
		registerHudElements()
		registerWorldRendering()
		registerHudStateSync()
		ClientTickEvents.START_CLIENT_TICK.register(::tickLocalCollisionBox)
		ClientTickEvents.END_CLIENT_TICK.register(::tickAutoAim)
		ClientTickEvents.END_CLIENT_TICK.register(::tickAerialControls)
		ClientTickEvents.END_CLIENT_TICK.register(::tickShootingInput)
		ClientTickEvents.END_CLIENT_TICK.register(::tickScreenKeys)
	}
}
