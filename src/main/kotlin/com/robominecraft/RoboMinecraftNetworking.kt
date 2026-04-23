package com.robominecraft

import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult

internal fun RoboMinecraft.registerNetworking() {
	PayloadTypeRegistry.playC2S().register(FireBlasterPayload.ID, FireBlasterPayload.CODEC)
	PayloadTypeRegistry.playC2S().register(RobotConfigPayload.ID, RobotConfigPayload.CODEC)
	PayloadTypeRegistry.playC2S().register(BuyAmmoPayload.ID, BuyAmmoPayload.CODEC)
	PayloadTypeRegistry.playC2S().register(AerialControlPayload.ID, AerialControlPayload.CODEC)
	PayloadTypeRegistry.playS2C().register(RobotHudPayload.ID, RobotHudPayload.CODEC)

	ServerPlayNetworking.registerGlobalReceiver(FireBlasterPayload.ID) { _, context ->
		val player = context.player()
		if (isRobotPilot(player) && player.mainHandItem.isEmpty) {
			handleFireRequest(player)
		}
	}
	ServerPlayNetworking.registerGlobalReceiver(RobotConfigPayload.ID) { payload, context ->
		applyRobotConfig(context.player(), payload)
	}
	ServerPlayNetworking.registerGlobalReceiver(BuyAmmoPayload.ID) { payload, context ->
		buyAmmo(context.player(), payload)
	}
	ServerPlayNetworking.registerGlobalReceiver(AerialControlPayload.ID) { payload, context ->
		val state = stateFor(context.player())
		state.aerialFlightMode = payload.flightMode
		state.aerialAscending = payload.ascending
		state.aerialDescending = payload.descending
	}
}

internal fun RoboMinecraft.registerAttackOverrides() {
	AttackBlockCallback.EVENT.register { player, _, hand, _, _ ->
		if (hand == InteractionHand.MAIN_HAND && isRobotPilot(player) && player.mainHandItem.isEmpty) {
			InteractionResult.FAIL
		} else {
			InteractionResult.PASS
		}
	}
	AttackEntityCallback.EVENT.register { player, _, hand, _, _ ->
		if (hand == InteractionHand.MAIN_HAND && isRobotPilot(player) && player.mainHandItem.isEmpty) {
			InteractionResult.FAIL
		} else {
			InteractionResult.PASS
		}
	}
}
