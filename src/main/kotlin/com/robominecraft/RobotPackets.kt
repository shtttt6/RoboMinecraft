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
	val judgeMode: Boolean,
	val heat: Int,
	val heatLimit: Int,
	val heroAmmo: Int,
	val infantryAmmo: Int,
	val aerialAmmo: Int,
	val aerialFlightMode: Boolean,
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
				ByteBufCodecs.BOOL.encode(buffer, payload.judgeMode)
				ByteBufCodecs.VAR_INT.encode(buffer, payload.heat)
				ByteBufCodecs.VAR_INT.encode(buffer, payload.heatLimit)
				ByteBufCodecs.VAR_INT.encode(buffer, payload.heroAmmo)
				ByteBufCodecs.VAR_INT.encode(buffer, payload.infantryAmmo)
				ByteBufCodecs.VAR_INT.encode(buffer, payload.aerialAmmo)
				ByteBufCodecs.BOOL.encode(buffer, payload.aerialFlightMode)
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
					judgeMode = ByteBufCodecs.BOOL.decode(buffer),
					heat = ByteBufCodecs.VAR_INT.decode(buffer),
					heatLimit = ByteBufCodecs.VAR_INT.decode(buffer),
					heroAmmo = ByteBufCodecs.VAR_INT.decode(buffer),
					infantryAmmo = ByteBufCodecs.VAR_INT.decode(buffer),
					aerialAmmo = ByteBufCodecs.VAR_INT.decode(buffer),
					aerialFlightMode = ByteBufCodecs.BOOL.decode(buffer),
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

object ToggleJudgeModePayload : CustomPacketPayload {
	val ID: CustomPacketPayload.Type<ToggleJudgeModePayload> = CustomPacketPayload.Type(RobotConstants.id("toggle_judge_mode"))
	val CODEC: StreamCodec<RegistryFriendlyByteBuf, ToggleJudgeModePayload> = StreamCodec.unit(ToggleJudgeModePayload)

	override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
		return ID
	}
}

data class AerialControlPayload(
	val flightMode: Boolean,
	val ascending: Boolean,
	val descending: Boolean
) : CustomPacketPayload {
	override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
		return ID
	}

	companion object {
		val ID: CustomPacketPayload.Type<AerialControlPayload> = CustomPacketPayload.Type(RobotConstants.id("aerial_control"))
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, AerialControlPayload> = StreamCodec.composite(
			ByteBufCodecs.BOOL,
			AerialControlPayload::flightMode,
			ByteBufCodecs.BOOL,
			AerialControlPayload::ascending,
			ByteBufCodecs.BOOL,
			AerialControlPayload::descending,
			::AerialControlPayload
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
