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
	val enabled: Boolean,
	val heat: Int,
	val heatLimit: Int,
	val heroAmmo: Int,
	val infantryAmmo: Int,
	val robotKind: Int,
	val heroMode: Int,
	val heroMobilityMode: Int,
	val infantryMobilityMode: Int,
	val infantryChassisMode: Int,
	val infantryLauncherMode: Int
) : CustomPacketPayload {
	override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
		return ID
	}

	companion object {
		val ID: CustomPacketPayload.Type<RobotHudPayload> = CustomPacketPayload.Type(RobotConstants.id("robot_hud"))
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, RobotHudPayload> = StreamCodec.of(
			{ buffer, payload ->
				ByteBufCodecs.BOOL.encode(buffer, payload.enabled)
				ByteBufCodecs.VAR_INT.encode(buffer, payload.heat)
				ByteBufCodecs.VAR_INT.encode(buffer, payload.heatLimit)
				ByteBufCodecs.VAR_INT.encode(buffer, payload.heroAmmo)
				ByteBufCodecs.VAR_INT.encode(buffer, payload.infantryAmmo)
				ByteBufCodecs.VAR_INT.encode(buffer, payload.robotKind)
				ByteBufCodecs.VAR_INT.encode(buffer, payload.heroMode)
				ByteBufCodecs.VAR_INT.encode(buffer, payload.heroMobilityMode)
				ByteBufCodecs.VAR_INT.encode(buffer, payload.infantryMobilityMode)
				ByteBufCodecs.VAR_INT.encode(buffer, payload.infantryChassisMode)
				ByteBufCodecs.VAR_INT.encode(buffer, payload.infantryLauncherMode)
			},
			{ buffer ->
				RobotHudPayload(
					enabled = ByteBufCodecs.BOOL.decode(buffer),
					heat = ByteBufCodecs.VAR_INT.decode(buffer),
					heatLimit = ByteBufCodecs.VAR_INT.decode(buffer),
					heroAmmo = ByteBufCodecs.VAR_INT.decode(buffer),
					infantryAmmo = ByteBufCodecs.VAR_INT.decode(buffer),
					robotKind = ByteBufCodecs.VAR_INT.decode(buffer),
					heroMode = ByteBufCodecs.VAR_INT.decode(buffer),
					heroMobilityMode = ByteBufCodecs.VAR_INT.decode(buffer),
					infantryMobilityMode = ByteBufCodecs.VAR_INT.decode(buffer),
					infantryChassisMode = ByteBufCodecs.VAR_INT.decode(buffer),
					infantryLauncherMode = ByteBufCodecs.VAR_INT.decode(buffer)
				)
			}
		)
	}
}

data class RobotConfigPayload(
	val robotKind: Int,
	val heroMode: Int,
	val heroMobilityMode: Int,
	val infantryMobilityMode: Int,
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
			RobotConfigPayload::heroMobilityMode,
			ByteBufCodecs.VAR_INT,
			RobotConfigPayload::infantryMobilityMode,
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
