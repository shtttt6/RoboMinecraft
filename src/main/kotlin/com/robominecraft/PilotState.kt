package com.robominecraft

data class PilotState(
	var enabled: Boolean = true,
	var profile: RobotProfile = RobotProfile(),
	var level: Int = 1,
	var experience: Int = 0,
	var heat: Double = 0.0,
	var appliedMaxHp: Int = 0,
	var movementPauseTicks: Int = 0,
	var lastMovementY: Double? = null,
	var lastMovementOnGround: Boolean = false,
	var lastSafeX: Double? = null,
	var lastSafeY: Double? = null,
	var lastSafeZ: Double? = null,
	var pauseLockX: Double? = null,
	var pauseLockY: Double? = null,
	var pauseLockZ: Double? = null,
	var heroAmmo: Int = 0,
	var infantryAmmo: Int = 0,
	var aerialAmmo: Int = 0,
	var aerialFlightMode: Boolean = false,
	var aerialAscending: Boolean = false,
	var aerialDescending: Boolean = false
) {
	fun stats(): RobotStats {
		return RobotRules.stats(profile, level)
	}

	fun ammoFor(kind: RobotKind): Int {
		return when (kind) {
			RobotKind.HERO -> heroAmmo
			RobotKind.INFANTRY -> infantryAmmo
			RobotKind.AERIAL -> aerialAmmo
		}
	}

	fun addAmmo(kind: RobotKind, amount: Int) {
		if (amount <= 0) {
			return
		}

		when (kind) {
			RobotKind.HERO -> heroAmmo = (heroAmmo + amount).coerceAtMost(RobotConstants.MAX_AMMO_PER_TYPE)
			RobotKind.INFANTRY -> infantryAmmo = (infantryAmmo + amount).coerceAtMost(RobotConstants.MAX_AMMO_PER_TYPE)
			RobotKind.AERIAL -> aerialAmmo = (aerialAmmo + amount).coerceAtMost(RobotConstants.MAX_AMMO_PER_TYPE)
		}
	}

	fun consumeAmmo(kind: RobotKind): Boolean {
		return when (kind) {
			RobotKind.HERO -> {
				if (heroAmmo <= 0) {
					false
				} else {
					heroAmmo--
					true
				}
			}
			RobotKind.INFANTRY -> {
				if (infantryAmmo <= 0) {
					false
				} else {
					infantryAmmo--
					true
				}
			}
			RobotKind.AERIAL -> {
				if (aerialAmmo <= 0) {
					false
				} else {
					aerialAmmo--
					true
				}
			}
		}
	}
}
