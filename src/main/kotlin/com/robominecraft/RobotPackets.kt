package com.robominecraft

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

object FireBlasterPayload : CustomPacketPayload {
	val ID: CustomPacketPayload.Type<FireBlasterPayload> = CustomPacketPayload.Type(RobotConstants.id("fire_blaster"))
	val CODEC: StreamCodec<RegistryFriendlyByteBuf, FireBlasterPayload> = StreamCodec.unit(FireBlasterPayload)

	override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
		return ID
	}
}

data class RobotHudPayload(
	val heat: Int,
	val heatLimit: Int,
	val heroAmmo: Int,
	val infantryAmmo: Int,
	val robotKind: Int,
	val heroMode: Int,
	val infantryChassisMode: Int,
	val infantryLauncherMode: Int
) : CustomPacketPayload {
	override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
		return ID
	}

	companion object {
		val ID: CustomPacketPayload.Type<RobotHudPayload> = CustomPacketPayload.Type(RobotConstants.id("robot_hud"))
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, RobotHudPayload> = StreamCodec.composite(
			ByteBufCodecs.VAR_INT,
			RobotHudPayload::heat,
			ByteBufCodecs.VAR_INT,
			RobotHudPayload::heatLimit,
			ByteBufCodecs.VAR_INT,
			RobotHudPayload::heroAmmo,
			ByteBufCodecs.VAR_INT,
			RobotHudPayload::infantryAmmo,
			ByteBufCodecs.VAR_INT,
			RobotHudPayload::robotKind,
			ByteBufCodecs.VAR_INT,
			RobotHudPayload::heroMode,
			ByteBufCodecs.VAR_INT,
			RobotHudPayload::infantryChassisMode,
			ByteBufCodecs.VAR_INT,
			RobotHudPayload::infantryLauncherMode,
			::RobotHudPayload
		)
	}
}

data class RobotConfigPayload(
	val robotKind: Int,
	val heroMode: Int,
	val infantryChassisMode: Int,
	val infantryLauncherMode: Int
) : CustomPacketPayload {
	override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
		return ID
	}

	companion object {
		val ID: CustomPacketPayload.Type<RobotConfigPayload> = CustomPacketPayload.Type(RobotConstants.id("robot_config"))
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, RobotConfigPayload> = StreamCodec.composite(
			ByteBufCodecs.VAR_INT,
			RobotConfigPayload::robotKind,
			ByteBufCodecs.VAR_INT,
			RobotConfigPayload::heroMode,
			ByteBufCodecs.VAR_INT,
			RobotConfigPayload::infantryChassisMode,
			ByteBufCodecs.VAR_INT,
			RobotConfigPayload::infantryLauncherMode,
			::RobotConfigPayload
		)
	}
}

data class BuyAmmoPayload(
	val robotKind: Int,
	val amount: Int
) : CustomPacketPayload {
	override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
		return ID
	}

	companion object {
		val ID: CustomPacketPayload.Type<BuyAmmoPayload> = CustomPacketPayload.Type(RobotConstants.id("buy_ammo"))
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, BuyAmmoPayload> = StreamCodec.composite(
			ByteBufCodecs.VAR_INT,
			BuyAmmoPayload::robotKind,
			ByteBufCodecs.VAR_INT,
			BuyAmmoPayload::amount,
			::BuyAmmoPayload
		)
	}
}
