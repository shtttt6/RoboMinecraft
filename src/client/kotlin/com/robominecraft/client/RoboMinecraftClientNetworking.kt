package com.robominecraft.client

import com.robominecraft.HeroMode
import com.robominecraft.HeroMobilityMode
import com.robominecraft.InfantryChassisMode
import com.robominecraft.InfantryLauncherMode
import com.robominecraft.InfantryMobilityMode
import com.robominecraft.RobotHudPayload
import com.robominecraft.RobotKind
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

internal fun RoboMinecraftClient.registerHudStateSync() {
	ClientPlayNetworking.registerGlobalReceiver(RobotHudPayload.ID) { payload, _ ->
		RobotClientState.enabled = payload.enabled
		RobotClientState.heat = payload.heat.coerceAtLeast(0)
		RobotClientState.heatLimit = payload.heatLimit.coerceAtLeast(1)
		RobotClientState.heroAmmo = payload.heroAmmo.coerceAtLeast(0)
		RobotClientState.infantryAmmo = payload.infantryAmmo.coerceAtLeast(0)
		RobotClientState.aerialAmmo = payload.aerialAmmo.coerceAtLeast(0)
		RobotClientState.aerialFlightMode = payload.aerialFlightMode
		RobotClientState.robotKind = enumByOrdinal(payload.robotKind, RobotKind.INFANTRY)
		if (RobotClientState.robotKind != RobotKind.AERIAL) {
			localAerialFlightMode = false
		} else if (payload.aerialFlightMode == lastSentAerialFlightMode) {
			localAerialFlightMode = payload.aerialFlightMode
		}
		RobotClientState.heroMode = enumByOrdinal(payload.heroMode, HeroMode.MELEE)
		RobotClientState.heroMobilityMode = enumByOrdinal(payload.heroMobilityMode, HeroMobilityMode.REGULAR)
		RobotClientState.infantryMobilityMode = enumByOrdinal(payload.infantryMobilityMode, InfantryMobilityMode.REGULAR)
		RobotClientState.infantryChassisMode = enumByOrdinal(payload.infantryChassisMode, InfantryChassisMode.POWER)
		RobotClientState.infantryLauncherMode = enumByOrdinal(payload.infantryLauncherMode, InfantryLauncherMode.BURST)
	}
}

internal inline fun <reified T : Enum<T>> enumByOrdinal(ordinal: Int, fallback: T): T {
	return enumValues<T>().getOrElse(ordinal) { fallback }
}
