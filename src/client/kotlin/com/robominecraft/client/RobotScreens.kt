package com.robominecraft.client

import com.robominecraft.BuyAmmoPayload
import com.robominecraft.HeroMode
import com.robominecraft.HeroMobilityMode
import com.robominecraft.InfantryChassisMode
import com.robominecraft.InfantryLauncherMode
import com.robominecraft.InfantryMobilityMode
import com.robominecraft.RobotConfigPayload
import com.robominecraft.RobotKind
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.max

class RobotConfigScreen : Screen(Component.literal("RoboMC Config")) {
	private var robotKind: RobotKind = RobotClientState.robotKind
	private var heroMode: HeroMode = RobotClientState.heroMode
	private var heroMobilityMode: HeroMobilityMode = RobotClientState.heroMobilityMode
	private var infantryMobilityMode: InfantryMobilityMode = RobotClientState.infantryMobilityMode
	private var infantryChassisMode: InfantryChassisMode = RobotClientState.infantryChassisMode
	private var infantryLauncherMode: InfantryLauncherMode = RobotClientState.infantryLauncherMode

	override fun init() {
		rebuildControls()
	}

	private fun rebuildControls() {
		clearWidgets()

		val centerX = width / 2
		var y = height / 2 - 86
		addRenderableWidget(toggleButton("英雄机器人", robotKind == RobotKind.HERO, centerX - 124, y) {
			robotKind = RobotKind.HERO
			rebuildControls()
		})
		addRenderableWidget(toggleButton("步兵机器人", robotKind == RobotKind.INFANTRY, centerX + 4, y) {
			robotKind = RobotKind.INFANTRY
			rebuildControls()
		})

		y += 38
		if (robotKind == RobotKind.HERO) {
			addRenderableWidget(toggleButton("常规英雄", heroMobilityMode == HeroMobilityMode.REGULAR, centerX - 124, y) {
				heroMobilityMode = HeroMobilityMode.REGULAR
				rebuildControls()
			})
			addRenderableWidget(toggleButton("英雄步兵", heroMobilityMode == HeroMobilityMode.WHEEL_LEGGED, centerX + 4, y) {
				heroMobilityMode = HeroMobilityMode.WHEEL_LEGGED
				rebuildControls()
			})

			y += 30
			addRenderableWidget(toggleButton("近战优先", heroMode == HeroMode.MELEE, centerX - 124, y) {
				heroMode = HeroMode.MELEE
				rebuildControls()
			})
			addRenderableWidget(toggleButton("远程优先", heroMode == HeroMode.RANGED, centerX + 4, y) {
				heroMode = HeroMode.RANGED
				rebuildControls()
			})
		} else {
			addRenderableWidget(toggleButton("常规步兵", infantryMobilityMode == InfantryMobilityMode.REGULAR, centerX - 124, y) {
				infantryMobilityMode = InfantryMobilityMode.REGULAR
				rebuildControls()
			})
			addRenderableWidget(toggleButton("轮腿步兵", infantryMobilityMode == InfantryMobilityMode.WHEEL_LEGGED, centerX + 4, y) {
				infantryMobilityMode = InfantryMobilityMode.WHEEL_LEGGED
				rebuildControls()
			})

			y += 30
			addRenderableWidget(toggleButton("功率优先", infantryChassisMode == InfantryChassisMode.POWER, centerX - 124, y) {
				infantryChassisMode = InfantryChassisMode.POWER
				rebuildControls()
			})
			addRenderableWidget(toggleButton("血量优先", infantryChassisMode == InfantryChassisMode.HEALTH, centerX + 4, y) {
				infantryChassisMode = InfantryChassisMode.HEALTH
				rebuildControls()
			})

			y += 30
			addRenderableWidget(toggleButton("爆发优先", infantryLauncherMode == InfantryLauncherMode.BURST, centerX - 124, y) {
				infantryLauncherMode = InfantryLauncherMode.BURST
				rebuildControls()
			})
			addRenderableWidget(toggleButton("冷却优先", infantryLauncherMode == InfantryLauncherMode.COOLING, centerX + 4, y) {
				infantryLauncherMode = InfantryLauncherMode.COOLING
				rebuildControls()
			})
		}

		val bottomY = height / 2 + 70
		addRenderableWidget(
			Button.builder(Component.literal("应用配置")) {
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
				Minecraft.getInstance().setScreen(null)
			}.bounds(centerX - 124, bottomY, 120, 20).build()
		)
		addRenderableWidget(
			Button.builder(Component.literal("关闭")) {
				Minecraft.getInstance().setScreen(null)
			}.bounds(centerX + 4, bottomY, 120, 20).build()
		)
	}

	override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
		renderInGameOverlay(context)
		context.drawCenteredString(font, title, width / 2, height / 2 - 118, 0xFFFFFFFF.toInt())
		context.drawCenteredString(font, "P 打开配置界面，选择机器人类型和模式", width / 2, height / 2 - 104, 0xFFC8D8E8.toInt())

		val ammoText = "当前弹量：英雄 ${RobotClientState.heroAmmo} | 步兵 ${RobotClientState.infantryAmmo}"
		context.drawCenteredString(font, ammoText, width / 2, height / 2 + 42, 0xFFE8F7FF.toInt())
		super.render(context, mouseX, mouseY, partialTick)
	}

	override fun isPauseScreen(): Boolean {
		return false
	}

	private fun toggleButton(label: String, selected: Boolean, x: Int, y: Int, onPress: () -> Unit): Button {
		val text = if (selected) "[$label]" else label
		return Button.builder(Component.literal(text)) { onPress() }
			.bounds(x, y, 120, 20)
			.build()
	}

	private fun renderInGameOverlay(context: GuiGraphics) {
		context.fill(0, 0, width, height, 0x99000000.toInt())
	}
}

class AmmoPurchaseScreen(private val robotKind: RobotKind) : Screen(Component.literal("${robotKind.displayName} Ammo")) {
	private val deltas = when (robotKind) {
		RobotKind.HERO -> intArrayOf(-10, -5, -2, -1, 1, 2, 5, 10)
		RobotKind.INFANTRY -> intArrayOf(-100, -50, -20, -10, 10, 20, 50, 100)
	}
	private var selectedAmount = 0

	override fun init() {
		rebuildControls()
	}

	private fun rebuildControls() {
		clearWidgets()

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
			Button.builder(Component.literal("兑换")) {
				if (selectedAmount > 0 && ClientPlayNetworking.canSend(BuyAmmoPayload.ID)) {
					ClientPlayNetworking.send(BuyAmmoPayload(robotKind.ordinal, selectedAmount))
					selectedAmount = 0
				}
			}.bounds(centerX - 104, bottomY, 100, 20).build()
		)
		addRenderableWidget(
			Button.builder(Component.literal("关闭")) {
				Minecraft.getInstance().setScreen(null)
			}.bounds(centerX + 4, bottomY, 100, 20).build()
		)
	}

	override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
		renderInGameOverlay(context)
		val bulletName = if (robotKind == RobotKind.HERO) "42mm" else "17mm"
		context.drawCenteredString(font, "${robotKind.displayName} $bulletName 买弹", width / 2, height / 2 - 78, 0xFFFFFFFF.toInt())
		context.drawCenteredString(font, "已有弹量：${RobotClientState.ammoFor(robotKind)}", width / 2, height / 2 - 58, 0xFFC8D8E8.toInt())

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

	override fun isPauseScreen(): Boolean {
		return false
	}

	private fun deltaButton(delta: Int, x: Int, y: Int): Button {
		val label = if (delta > 0) "+$delta" else delta.toString()
		return Button.builder(Component.literal(label)) {
			selectedAmount = max(0, selectedAmount + delta)
		}.bounds(x, y, 44, 20).build()
	}

	private fun renderInGameOverlay(context: GuiGraphics) {
		context.fill(0, 0, width, height, 0x99000000.toInt())
	}
}
