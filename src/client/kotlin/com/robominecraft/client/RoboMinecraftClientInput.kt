package com.robominecraft.client

import com.robominecraft.AerialControlPayload
import com.robominecraft.FireBlasterPayload
import com.robominecraft.RobotKind
import com.robominecraft.RobotVehicleEntity
import com.robominecraft.ToggleJudgeModePayload
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

internal fun RoboMinecraftClient.registerKeyMappings() {
	configKey = KeyBindingHelper.registerKeyBinding(
		KeyMapping("key.robominecraft.config", InputConstants.Type.KEYSYM, InputConstants.KEY_P, KeyMapping.Category.MISC)
	)
	robotSelectKey = KeyBindingHelper.registerKeyBinding(
		KeyMapping("key.robominecraft.select_robot", InputConstants.Type.KEYSYM, InputConstants.KEY_K, KeyMapping.Category.MISC)
	)
	buyInfantryAmmoKey = KeyBindingHelper.registerKeyBinding(
		KeyMapping("key.robominecraft.buy_infantry_ammo", InputConstants.Type.KEYSYM, InputConstants.KEY_O, KeyMapping.Category.MISC)
	)
	buyHeroAmmoKey = KeyBindingHelper.registerKeyBinding(
		KeyMapping("key.robominecraft.buy_hero_ammo", InputConstants.Type.KEYSYM, InputConstants.KEY_I, KeyMapping.Category.MISC)
	)
	judgeModeKey = KeyBindingHelper.registerKeyBinding(
		KeyMapping("key.robominecraft.judge_mode", InputConstants.Type.KEYSYM, InputConstants.KEY_F4, KeyMapping.Category.MISC)
	)
	aerialFlightToggleKey = KeyBindingHelper.registerKeyBinding(
		KeyMapping("key.robominecraft.aerial_flight_toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_G, KeyMapping.Category.MISC)
	)
	aerialAscendKey = KeyBindingHelper.registerKeyBinding(
		KeyMapping("key.robominecraft.aerial_ascend", InputConstants.Type.KEYSYM, InputConstants.KEY_SPACE, KeyMapping.Category.MISC)
	)
	aerialDescendKey = KeyBindingHelper.registerKeyBinding(
		KeyMapping("key.robominecraft.aerial_descend", InputConstants.Type.KEYSYM, InputConstants.KEY_LALT, KeyMapping.Category.MISC)
	)
}

internal fun RoboMinecraftClient.tickShootingInput(client: Minecraft) {
	if (shotCooldownTicks > 0) {
		shotCooldownTicks--
	}

	val player = client.player ?: return

	if (!isRobotModeActive() || RobotClientState.judgeMode || !player.mainHandItem.isEmpty || !client.options.keyAttack.isDown || shotCooldownTicks > 0 || RobotClientState.currentAmmo() <= 0) {
		return
	}

	if (ClientPlayNetworking.canSend(FireBlasterPayload.ID)) {
		ClientPlayNetworking.send(FireBlasterPayload)
		shotCooldownTicks = CLIENT_SHOT_COOLDOWN_TICKS
	}
}

internal fun RoboMinecraftClient.tickScreenKeys(client: Minecraft) {
	if (client.screen != null) {
		return
	}

	while (configKey.consumeClick()) {
		client.setScreen(RobotConfigScreen())
	}
	while (robotSelectKey.consumeClick()) {
		client.setScreen(RobotSelectionScreen())
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

internal fun RoboMinecraftClient.tickRobotShiftSuppression(client: Minecraft) {
	val player = client.player ?: return
	if (!isRobotModeActive(player)) {
		return
	}
	if (RobotClientState.judgeMode) {
		return
	}

	client.options.keyShift.setDown(false)
	if (player.vehicle is RobotVehicleEntity) {
		client.gui.setOverlayMessage(Component.empty(), false)
	}
}

internal fun RoboMinecraftClient.tickAerialControls(client: Minecraft) {
	val player = client.player
	val active = player != null &&
		isRobotModeActive(player) &&
		!RobotClientState.judgeMode &&
		RobotClientState.robotKind == RobotKind.AERIAL
	if (active) {
		while (aerialFlightToggleKey.consumeClick()) {
			localAerialFlightMode = !localAerialFlightMode
		}
	} else {
		localAerialFlightMode = false
	}
	val ascending = active && localAerialFlightMode && aerialAscendKey.isDown
	val descending = active && localAerialFlightMode && aerialDescendKey.isDown

	if (
		localAerialFlightMode == lastSentAerialFlightMode &&
		ascending == lastSentAerialAscending &&
		descending == lastSentAerialDescending
	) {
		return
	}

	if (ClientPlayNetworking.canSend(AerialControlPayload.ID)) {
		ClientPlayNetworking.send(AerialControlPayload(localAerialFlightMode, ascending, descending))
		lastSentAerialFlightMode = localAerialFlightMode
		lastSentAerialAscending = ascending
		lastSentAerialDescending = descending
	}
}

internal fun RoboMinecraftClient.tickJudgeModeToggle(client: Minecraft) {
	if (client.screen != null || !isRobotModeActive()) {
		return
	}

	while (judgeModeKey.consumeClick()) {
		if (ClientPlayNetworking.canSend(ToggleJudgeModePayload.ID)) {
			ClientPlayNetworking.send(ToggleJudgeModePayload)
		}
	}
}
