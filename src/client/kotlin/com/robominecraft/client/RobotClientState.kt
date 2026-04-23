package com.robominecraft.client

import com.robominecraft.HeroMode
import com.robominecraft.HeroMobilityMode
import com.robominecraft.InfantryChassisMode
import com.robominecraft.InfantryLauncherMode
import com.robominecraft.InfantryMobilityMode
import com.robominecraft.RobotKind

object RobotClientState {
	var enabled: Boolean = true
	var heat: Int = 0
	var heatLimit: Int = 1
	var heroAmmo: Int = 0
	var infantryAmmo: Int = 0
	var aerialAmmo: Int = 0
	var aerialFlightMode: Boolean = false
	var robotKind: RobotKind = RobotKind.INFANTRY
	var heroMode: HeroMode = HeroMode.MELEE
	var heroMobilityMode: HeroMobilityMode = HeroMobilityMode.REGULAR
	var infantryMobilityMode: InfantryMobilityMode = InfantryMobilityMode.REGULAR
	var infantryChassisMode: InfantryChassisMode = InfantryChassisMode.POWER
	var infantryLauncherMode: InfantryLauncherMode = InfantryLauncherMode.BURST

	fun currentAmmo(): Int {
		return ammoFor(robotKind)
	}

	fun ammoFor(kind: RobotKind): Int {
		return when (kind) {
			RobotKind.HERO -> heroAmmo
			RobotKind.INFANTRY -> infantryAmmo
			RobotKind.AERIAL -> aerialAmmo
		}
	}
}
