package com.robominecraft.client

import com.robominecraft.BuyAmmoPayload
import com.robominecraft.HeroMode
import com.robominecraft.HeroMobilityMode
import com.robominecraft.InfantryChassisMode
import com.robominecraft.InfantryLauncherMode
import com.robominecraft.InfantryMobilityMode
import com.robominecraft.RobotConfigPayload
import com.robominecraft.RobotKind
import com.robominecraft.RobotRules
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.max

class RobotSelectionScreen : Screen(Component.literal("Robot Select")) {
	private var robotKind: RobotKind = RobotClientState.robotKind
	private var heroMobilityMode: HeroMobilityMode = RobotClientState.heroMobilityMode
	private var infantryMobilityMode: InfantryMobilityMode = RobotClientState.infantryMobilityMode

	override fun init() {
		rebuildControls()
	}

	private fun rebuildControls() {
		clearWidgets()

		val centerX = width / 2
		var y = height / 2 - 72

		addRenderableWidget(toggleButton("Hero", robotKind == RobotKind.HERO, centerX - 124, y) {
			robotKind = RobotKind.HERO
			rebuildControls()
		})
		addRenderableWidget(toggleButton("Infantry", robotKind == RobotKind.INFANTRY, centerX + 4, y) {
			robotKind = RobotKind.INFANTRY
			rebuildControls()
		})

		y += 30
		addRenderableWidget(toggleButton("Aerial", robotKind == RobotKind.AERIAL, centerX - 60, y) {
			robotKind = RobotKind.AERIAL
			rebuildControls()
		})

		y += 40
		when (robotKind) {
			RobotKind.HERO -> {
				addRenderableWidget(toggleButton("Regular Hero", heroMobilityMode == HeroMobilityMode.REGULAR, centerX - 124, y) {
					heroMobilityMode = HeroMobilityMode.REGULAR
					rebuildControls()
				})
				addRenderableWidget(toggleButton("Wheel-Legged Hero", heroMobilityMode == HeroMobilityMode.WHEEL_LEGGED, centerX + 4, y) {
					heroMobilityMode = HeroMobilityMode.WHEEL_LEGGED
					rebuildControls()
				})
			}
			RobotKind.INFANTRY -> {
				addRenderableWidget(toggleButton("Regular Infantry", infantryMobilityMode == InfantryMobilityMode.REGULAR, centerX - 124, y) {
					infantryMobilityMode = InfantryMobilityMode.REGULAR
					rebuildControls()
				})
				addRenderableWidget(toggleButton("Wheel-Legged Infantry", infantryMobilityMode == InfantryMobilityMode.WHEEL_LEGGED, centerX + 4, y) {
					infantryMobilityMode = InfantryMobilityMode.WHEEL_LEGGED
					rebuildControls()
				})
			}
			RobotKind.AERIAL -> {
				addRenderableWidget(
					Button.builder(Component.literal("Aerial chassis")) {}
						.bounds(centerX - 124, y, 248, 20)
						.build()
				)
			}
		}

		addRenderableWidget(
			Button.builder(Component.literal("Apply")) {
				sendRobotConfig(
				robotKind = robotKind,
				heroMode = RobotClientState.heroMode,
				heroMobilityMode = heroMobilityMode,
				infantryMobilityMode = infantryMobilityMode,
				infantryChassisMode = RobotClientState.infantryChassisMode,
				infantryLauncherMode = RobotClientState.infantryLauncherMode
				)
				Minecraft.getInstance().setScreen(null)
			}.bounds(centerX - 124, height / 2 + 76, 120, 20).build()
		)
		addRenderableWidget(
			Button.builder(Component.literal("Close")) {
				Minecraft.getInstance().setScreen(null)
			}.bounds(centerX + 4, height / 2 + 76, 120, 20).build()
		)
	}

	override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
		renderOverlay(context, width, height)
		context.drawCenteredString(font, title, width / 2, height / 2 - 112, 0xFFFFFFFF.toInt())
		context.drawCenteredString(font, "Choose robot class and mobility.", width / 2, height / 2 - 96, 0xFFC8D8E8.toInt())
		super.render(context, mouseX, mouseY, partialTick)
	}

	override fun isPauseScreen(): Boolean = false
}

class RobotConfigScreen : Screen(Component.literal("Robot Config")) {
	private val robotKind: RobotKind = RobotClientState.robotKind
	private var heroMode: HeroMode = RobotClientState.heroMode
	private var infantryChassisMode: InfantryChassisMode = RobotClientState.infantryChassisMode
	private var infantryLauncherMode: InfantryLauncherMode = RobotClientState.infantryLauncherMode

	override fun init() {
		rebuildControls()
	}

	private fun rebuildControls() {
		clearWidgets()

		val centerX = width / 2
		var y = height / 2 - 64

		when (robotKind) {
			RobotKind.HERO -> {
				addRenderableWidget(toggleButton("Melee Priority", heroMode == HeroMode.MELEE, centerX - 124, y) {
					heroMode = HeroMode.MELEE
					rebuildControls()
				})
				addRenderableWidget(toggleButton("Ranged Priority", heroMode == HeroMode.RANGED, centerX + 4, y) {
					heroMode = HeroMode.RANGED
					rebuildControls()
				})
			}
			RobotKind.INFANTRY -> {
				addRenderableWidget(toggleButton("Power Priority", infantryChassisMode == InfantryChassisMode.POWER, centerX - 124, y) {
					infantryChassisMode = InfantryChassisMode.POWER
					rebuildControls()
				})
				addRenderableWidget(toggleButton("Health Priority", infantryChassisMode == InfantryChassisMode.HEALTH, centerX + 4, y) {
					infantryChassisMode = InfantryChassisMode.HEALTH
					rebuildControls()
				})

				y += 30
				addRenderableWidget(toggleButton("Burst Priority", infantryLauncherMode == InfantryLauncherMode.BURST, centerX - 124, y) {
					infantryLauncherMode = InfantryLauncherMode.BURST
					rebuildControls()
				})
				addRenderableWidget(toggleButton("Cooling Priority", infantryLauncherMode == InfantryLauncherMode.COOLING, centerX + 4, y) {
					infantryLauncherMode = InfantryLauncherMode.COOLING
					rebuildControls()
				})
			}
			RobotKind.AERIAL -> {
				addRenderableWidget(
					Button.builder(Component.literal("Restore aerial ammo")) {
						if (ClientPlayNetworking.canSend(BuyAmmoPayload.ID)) {
							ClientPlayNetworking.send(BuyAmmoPayload(RobotKind.AERIAL.ordinal, RobotRules.AERIAL_INITIAL_AMMO))
						}
					}.bounds(centerX - 124, y, 248, 20).build()
				)
			}
		}

		addRenderableWidget(
			Button.builder(Component.literal("Apply")) {
				sendRobotConfig(
				robotKind = robotKind,
				heroMode = heroMode,
				heroMobilityMode = RobotClientState.heroMobilityMode,
				infantryMobilityMode = RobotClientState.infantryMobilityMode,
				infantryChassisMode = infantryChassisMode,
				infantryLauncherMode = infantryLauncherMode
				)
				Minecraft.getInstance().setScreen(null)
			}.bounds(centerX - 124, height / 2 + 76, 120, 20).build()
		)
		addRenderableWidget(
			Button.builder(Component.literal("Close")) {
				Minecraft.getInstance().setScreen(null)
			}.bounds(centerX + 4, height / 2 + 76, 120, 20).build()
		)
	}

	override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
		renderOverlay(context, width, height)
		context.drawCenteredString(font, title, width / 2, height / 2 - 112, 0xFFFFFFFF.toInt())
		context.drawCenteredString(font, configHint(), width / 2, height / 2 - 96, 0xFFC8D8E8.toInt())
		context.drawCenteredString(font, ammoSummary(), width / 2, height / 2 + 44, 0xFFE8F7FF.toInt())
		super.render(context, mouseX, mouseY, partialTick)
	}

	override fun isPauseScreen(): Boolean = false

	private fun configHint(): String {
		return when (robotKind) {
			RobotKind.HERO -> "P changes hero combat behavior only. K changes robot class."
			RobotKind.INFANTRY -> "P changes infantry loadout only. K changes robot class."
			RobotKind.AERIAL -> "P contains aerial utility only. K changes robot class."
		}
	}

	private fun ammoSummary(): String {
		return "Ammo | Hero ${RobotClientState.heroAmmo} | Infantry ${RobotClientState.infantryAmmo} | Aerial ${RobotClientState.aerialAmmo}"
	}
}

class AmmoPurchaseScreen(private val robotKind: RobotKind) : Screen(Component.literal("${robotKind.displayName} Ammo")) {
	private val deltas = when (robotKind) {
		RobotKind.HERO -> intArrayOf(-10, -5, -2, -1, 1, 2, 5, 10)
		RobotKind.INFANTRY -> intArrayOf(-100, -50, -20, -10, 10, 20, 50, 100)
		RobotKind.AERIAL -> intArrayOf()
	}
	private var selectedAmount = 0

	override fun init() {
		rebuildControls()
	}

	private fun rebuildControls() {
		clearWidgets()

		if (robotKind == RobotKind.AERIAL) {
			val centerX = width / 2
			addRenderableWidget(
				Button.builder(Component.literal("Close")) {
					Minecraft.getInstance().setScreen(null)
				}.bounds(centerX - 50, height / 2 + 20, 100, 20).build()
			)
			return
		}

		val centerX = width / 2
		val y = height / 2 - 6
		var x = centerX - 222

		deltas.take(4).forEach { delta ->
			addRenderableWidget(deltaButton(delta, x, y))
			x += 48
		}

		x = centerX + 30
		deltas.drop(4).forEach { delta ->
			addRenderableWidget(deltaButton(delta, x, y))
			x += 48
		}

		val bottomY = height / 2 + 48
		addRenderableWidget(
			Button.builder(Component.literal("Buy")) {
				if (selectedAmount > 0 && ClientPlayNetworking.canSend(BuyAmmoPayload.ID)) {
					ClientPlayNetworking.send(BuyAmmoPayload(robotKind.ordinal, selectedAmount))
					selectedAmount = 0
				}
			}.bounds(centerX - 104, bottomY, 100, 20).build()
		)
		addRenderableWidget(
			Button.builder(Component.literal("Close")) {
				Minecraft.getInstance().setScreen(null)
			}.bounds(centerX + 4, bottomY, 100, 20).build()
		)
	}

	override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
		renderOverlay(context, width, height)
		if (robotKind == RobotKind.AERIAL) {
			context.drawCenteredString(font, "Aerial ammo is restored from the config screen.", width / 2, height / 2 - 10, 0xFFFFFFFF.toInt())
			super.render(context, mouseX, mouseY, partialTick)
			return
		}

		val bulletName = if (robotKind == RobotKind.HERO) "42mm" else "17mm"
		context.drawCenteredString(font, "${robotKind.displayName} $bulletName ammo", width / 2, height / 2 - 78, 0xFFFFFFFF.toInt())
		context.drawCenteredString(font, "Current ammo: ${RobotClientState.ammoFor(robotKind)}", width / 2, height / 2 - 58, 0xFFC8D8E8.toInt())

		val boxX = width / 2 - 24
		val boxY = height / 2 - 12
		context.fill(boxX, boxY, boxX + 48, boxY + 26, 0xAA000000.toInt())
		context.fill(boxX, boxY, boxX + 48, boxY + 1, 0xDDFFFFFF.toInt())
		context.fill(boxX, boxY + 25, boxX + 48, boxY + 26, 0xDDFFFFFF.toInt())
		context.fill(boxX, boxY, boxX + 1, boxY + 26, 0xDDFFFFFF.toInt())
		context.fill(boxX + 47, boxY, boxX + 48, boxY + 26, 0xDDFFFFFF.toInt())
		context.drawCenteredString(font, selectedAmount.toString(), width / 2, height / 2 - 1, 0xFFFFFFFF.toInt())

		super.render(context, mouseX, mouseY, partialTick)
	}

	override fun isPauseScreen(): Boolean = false

	private fun deltaButton(delta: Int, x: Int, y: Int): Button {
		val label = if (delta > 0) "+$delta" else delta.toString()
		return Button.builder(Component.literal(label)) {
			selectedAmount = max(0, selectedAmount + delta)
		}.bounds(x, y, 44, 20).build()
	}
}

private fun toggleButton(label: String, selected: Boolean, x: Int, y: Int, onPress: () -> Unit): Button {
	val text = if (selected) "[$label]" else label
	return Button.builder(Component.literal(text)) { onPress() }
		.bounds(x, y, 120, 20)
		.build()
}

private fun sendRobotConfig(
	robotKind: RobotKind,
	heroMode: HeroMode,
	heroMobilityMode: HeroMobilityMode,
	infantryMobilityMode: InfantryMobilityMode,
	infantryChassisMode: InfantryChassisMode,
	infantryLauncherMode: InfantryLauncherMode
) {
	if (ClientPlayNetworking.canSend(RobotConfigPayload.ID)) {
		ClientPlayNetworking.send(
			RobotConfigPayload(
				robotKind = robotKind.ordinal,
				heroMode = heroMode.ordinal,
				heroMobilityMode = heroMobilityMode.ordinal,
				infantryMobilityMode = infantryMobilityMode.ordinal,
				infantryChassisMode = infantryChassisMode.ordinal,
				infantryLauncherMode = infantryLauncherMode.ordinal
			)
		)
	}
}

private fun renderOverlay(context: GuiGraphics, width: Int, height: Int) {
	context.fill(0, 0, width, height, 0x99000000.toInt())
}
