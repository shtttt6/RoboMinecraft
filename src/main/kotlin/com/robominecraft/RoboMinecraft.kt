package com.robominecraft

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.Holder
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object RoboMinecraft : ModInitializer {
	const val MOD_ID = RobotConstants.MOD_ID

	val ROBOT_SCALE_ID: ResourceLocation = RobotAttributeIds.SCALE

	private const val COLLISION_DAMAGE = 2.0f
	private const val COLLISION_COOLDOWN_TICKS = 20
	private const val BASE_PLAYER_HEALTH = 20.0
	private const val ROBOT_FOOD_LEVEL = 17
	private const val ROBOT_SATURATION_LEVEL = 0.0f
	private const val MAX_SHOTS_PER_REQUEST = 3

	private val logger = LoggerFactory.getLogger(MOD_ID)
	private val pilotStates = mutableMapOf<UUID, PilotState>()
	private val nextShotTicks = mutableMapOf<UUID, Double>()
	private val lastCollisionTicks = mutableMapOf<UUID, Int>()

	override fun onInitialize() {
		registerNetworking()
		registerAttackOverrides()
		registerPlayerLifecycle()
		registerRobotModeMaintenance()
		registerCommands()
		logger.info("RoboMaster MVP initialized")
	}

	private fun registerNetworking() {
		PayloadTypeRegistry.playC2S().register(FireBlasterPayload.ID, FireBlasterPayload.CODEC)
		PayloadTypeRegistry.playC2S().register(RobotConfigPayload.ID, RobotConfigPayload.CODEC)
		PayloadTypeRegistry.playC2S().register(BuyAmmoPayload.ID, BuyAmmoPayload.CODEC)
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
	}

	private fun registerAttackOverrides() {
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

	private fun registerPlayerLifecycle() {
		ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ ->
			val state = stateFor(newPlayer)
			state.appliedMaxHp = 0

			if (state.enabled) {
				val stats = state.stats()
				applyRobotAttributes(newPlayer, state, stats)
				maintainRobotVitals(newPlayer)
			}
		}
	}

	private fun registerRobotModeMaintenance() {
		ServerTickEvents.END_SERVER_TICK.register { server ->
			server.playerList.players.forEach { player ->
				val state = stateFor(player)
				val stats = state.stats()

				state.heat = max(0.0, state.heat - stats.heatCoolingPerSecond / 20.0)

				if (state.enabled) {
					applyRobotAttributes(player, state, stats)
					maintainRobotVitals(player)
					applyCollisionDamage(player)
				} else {
					removeRobotAttributes(player)
				}

				syncRobotHud(player, state, stats)
			}
		}
	}

	private fun registerCommands() {
		CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
			dispatcher.register(
				Commands.literal("robomc")
					.executes { context -> sendRobotStatus(context) }
					.then(Commands.literal("on").executes { context -> setRobotMode(context, true) })
					.then(Commands.literal("off").executes { context -> setRobotMode(context, false) })
					.then(Commands.literal("toggle").executes { context ->
						val player = context.source.getPlayerOrException()
						setRobotMode(player, !isRobotPilot(player))
						1
					})
					.then(
						Commands.literal("hero")
							.then(Commands.literal("melee").executes { context -> setHero(context, HeroMode.MELEE) })
							.then(Commands.literal("ranged").executes { context -> setHero(context, HeroMode.RANGED) })
					)
					.then(
						Commands.literal("infantry")
							.then(
								Commands.literal("power")
									.then(Commands.literal("burst").executes { context ->
										setInfantry(context, InfantryChassisMode.POWER, InfantryLauncherMode.BURST)
									})
									.then(Commands.literal("cooling").executes { context ->
										setInfantry(context, InfantryChassisMode.POWER, InfantryLauncherMode.COOLING)
									})
							)
							.then(
								Commands.literal("health")
									.then(Commands.literal("burst").executes { context ->
										setInfantry(context, InfantryChassisMode.HEALTH, InfantryLauncherMode.BURST)
									})
									.then(Commands.literal("cooling").executes { context ->
										setInfantry(context, InfantryChassisMode.HEALTH, InfantryLauncherMode.COOLING)
									})
							)
					)
					.then(
						Commands.literal("level")
							.then(
								Commands.argument("value", IntegerArgumentType.integer(1, 10))
									.executes { context ->
										setLevel(context, IntegerArgumentType.getInteger(context, "value"))
									}
							)
					)
					.then(
						Commands.literal("xp")
							.then(
								Commands.literal("set")
									.then(
										Commands.argument("amount", IntegerArgumentType.integer(0, 5000))
											.executes { context ->
												setExperience(context, IntegerArgumentType.getInteger(context, "amount"))
											}
									)
							)
							.then(
								Commands.literal("add")
									.then(
										Commands.argument("amount", IntegerArgumentType.integer(0))
											.executes { context ->
												addExperience(context, IntegerArgumentType.getInteger(context, "amount"))
											}
									)
							)
					)
			)
		}
	}

	private fun handleFireRequest(player: ServerPlayer) {
		val state = stateFor(player)
		val stats = state.stats()
		val serverTick = player.level().server.tickCount.toDouble()
		val shotIntervalTicks = 20.0 / stats.fireRateHz
		var nextShotTick = nextShotTicks[player.uuid] ?: serverTick

		// Do not store a large fire backlog after the player stops holding the trigger.
		if (serverTick - nextShotTick > 2.0) {
			nextShotTick = serverTick
		}

		if (nextShotTick > serverTick) {
			return
		}

		val dueShots = min(
			MAX_SHOTS_PER_REQUEST,
			floor((serverTick - nextShotTick) / shotIntervalTicks).toInt() + 1
		)
		var firedShots = 0

		while (firedShots < dueShots) {
			if (!fireSingleShot(player, state, stats)) {
				break
			}

			nextShotTick += shotIntervalTicks
			firedShots++
		}

		nextShotTicks[player.uuid] = if (firedShots > 0) nextShotTick else serverTick + shotIntervalTicks
	}

	private fun fireSingleShot(player: ServerPlayer, state: PilotState, stats: RobotStats): Boolean {
		val robotKind = state.profile.kind

		if (state.ammoFor(robotKind) <= 0) {
			return false
		}

		if (state.heat + stats.shotHeat > stats.heatLimit) {
			return false
		}

		state.consumeAmmo(robotKind)
		state.heat += stats.shotHeat
		player.swing(InteractionHand.MAIN_HAND)

		val level = player.level()
		val shot = RobotProjectiles.simulate(player, stats.bullet) { target -> canHit(player, target) }
		val end = shot.end

		level.playSound(null, player.x, player.y, player.z, SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.7f, 1.55f)
		RobotProjectiles.spawnTrail(player, shot.trail)

		val target = shot.target
		if (target != null) {
			target.hurtServer(level, level.damageSources().playerAttack(player), stats.bullet.damage)

			if (target is LivingEntity) {
				val knockbackDirection = shot.impactVelocity.normalize()
				target.knockback(0.35, -knockbackDirection.x, -knockbackDirection.z)
			}

			level.playSound(null, end.x, end.y, end.z, SoundEvents.CROSSBOW_HIT, SoundSource.PLAYERS, 0.8f, 1.25f)
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 12, 0.2, 0.2, 0.2, 0.02)
		} else {
			level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 4, 0.08, 0.08, 0.08, 0.0)
		}

		return true
	}

	private fun canHit(player: ServerPlayer, target: Entity): Boolean {
		return target !== player && target.isAlive && !target.isSpectator && target.isPickable && target.canBeHitByProjectile()
	}

	private fun maintainRobotVitals(player: ServerPlayer) {
		player.foodData.setFoodLevel(ROBOT_FOOD_LEVEL)
		player.foodData.setSaturation(ROBOT_SATURATION_LEVEL)
		player.setSprinting(false)
	}

	private fun applyCollisionDamage(player: ServerPlayer) {
		val serverTick = player.level().server.tickCount
		val previousCollisionTick = lastCollisionTicks[player.uuid] ?: -COLLISION_COOLDOWN_TICKS

		if (serverTick - previousCollisionTick < COLLISION_COOLDOWN_TICKS) {
			return
		}

		val target = player.level()
			.getEntities(player, player.boundingBox.inflate(0.18)) { target -> canHit(player, target) }
			.firstOrNull()
			?: return

		lastCollisionTicks[player.uuid] = serverTick
		target.hurtServer(player.level(), player.level().damageSources().playerAttack(player), COLLISION_DAMAGE)
	}

	private fun syncRobotHud(player: ServerPlayer, state: PilotState, stats: RobotStats) {
		if (!ServerPlayNetworking.canSend(player, RobotHudPayload.ID)) {
			return
		}

		ServerPlayNetworking.send(
			player,
			RobotHudPayload(
				heat = if (state.enabled) state.heat.roundToInt() else 0,
				heatLimit = if (state.enabled) stats.heatLimit else 0,
				heroAmmo = state.heroAmmo,
				infantryAmmo = state.infantryAmmo,
				robotKind = state.profile.kind.ordinal,
				heroMode = state.profile.heroMode.ordinal,
				infantryChassisMode = state.profile.infantryChassisMode.ordinal,
				infantryLauncherMode = state.profile.infantryLauncherMode.ordinal
			)
		)
	}

	private fun applyRobotConfig(player: ServerPlayer, payload: RobotConfigPayload) {
		val state = stateFor(player)
		val previousKind = state.profile.kind
		val profile = RobotProfile(
			kind = enumByOrdinal(payload.robotKind, RobotKind.INFANTRY),
			heroMode = enumByOrdinal(payload.heroMode, HeroMode.MELEE),
			infantryChassisMode = enumByOrdinal(payload.infantryChassisMode, InfantryChassisMode.POWER),
			infantryLauncherMode = enumByOrdinal(payload.infantryLauncherMode, InfantryLauncherMode.BURST)
		)

		state.enabled = true
		state.profile = profile
		if (previousKind != profile.kind) {
			state.heroAmmo = 0
			state.infantryAmmo = 0
		}
		state.heat = 0.0
		state.appliedMaxHp = 0
		nextShotTicks.remove(player.uuid)
		applyRobotAttributes(player, state, state.stats())
		player.displayClientMessage(Component.literal("Robot configured: ${state.profile.displayName()}"), true)
	}

	private fun buyAmmo(player: ServerPlayer, payload: BuyAmmoPayload) {
		val kind = enumByOrdinal(payload.robotKind, RobotKind.INFANTRY)
		val amount = payload.amount.coerceIn(0, RobotConstants.MAX_AMMO_PER_TYPE)
		val state = stateFor(player)

		if (amount <= 0) {
			return
		}

		state.addAmmo(kind, amount)
		player.displayClientMessage(Component.literal("${kind.displayName} ammo +$amount | total ${state.ammoFor(kind)}"), true)
	}

	private fun sendRobotStatus(context: CommandContext<CommandSourceStack>): Int {
		val player = context.source.getPlayerOrException()
		player.displayClientMessage(Component.literal(statusLine(player)), false)
		return 1
	}

	private fun setRobotMode(context: CommandContext<CommandSourceStack>, enabled: Boolean): Int {
		setRobotMode(context.source.getPlayerOrException(), enabled)
		return 1
	}

	private fun setRobotMode(player: ServerPlayer, enabled: Boolean) {
		val state = stateFor(player)
		state.enabled = enabled

		if (enabled) {
			applyRobotAttributes(player, state, state.stats())
			player.displayClientMessage(Component.literal("${statusLine(player)} | empty-hand left click fires | right click auto-aims"), true)
		} else {
			removeRobotAttributes(player)
			player.displayClientMessage(Component.literal("RoboMaster chassis offline."), true)
		}
	}

	private fun setHero(context: CommandContext<CommandSourceStack>, mode: HeroMode): Int {
		val player = context.source.getPlayerOrException()
		val state = stateFor(player)
		val previousKind = state.profile.kind
		state.enabled = true
		state.profile = RobotProfile(kind = RobotKind.HERO, heroMode = mode)
		if (previousKind != RobotKind.HERO) {
			state.heroAmmo = 0
			state.infantryAmmo = 0
		}
		state.heat = 0.0
		state.appliedMaxHp = 0
		nextShotTicks.remove(player.uuid)
		applyRobotAttributes(player, state, state.stats())
		player.displayClientMessage(Component.literal(statusLine(player)), false)
		return 1
	}

	private fun setInfantry(
		context: CommandContext<CommandSourceStack>,
		chassisMode: InfantryChassisMode,
		launcherMode: InfantryLauncherMode
	): Int {
		val player = context.source.getPlayerOrException()
		val state = stateFor(player)
		val previousKind = state.profile.kind
		state.enabled = true
		state.profile = RobotProfile(
			kind = RobotKind.INFANTRY,
			infantryChassisMode = chassisMode,
			infantryLauncherMode = launcherMode
		)
		if (previousKind != RobotKind.INFANTRY) {
			state.heroAmmo = 0
			state.infantryAmmo = 0
		}
		state.heat = 0.0
		state.appliedMaxHp = 0
		nextShotTicks.remove(player.uuid)
		applyRobotAttributes(player, state, state.stats())
		player.displayClientMessage(Component.literal(statusLine(player)), false)
		return 1
	}

	private fun setLevel(context: CommandContext<CommandSourceStack>, level: Int): Int {
		val player = context.source.getPlayerOrException()
		val state = stateFor(player)
		state.level = level.coerceIn(1, 10)
		state.experience = max(state.experience, RobotRules.requiredExperienceForLevel(state.level))
		state.heat = min(state.heat, state.stats().heatLimit.toDouble())
		state.appliedMaxHp = 0
		applyRobotAttributes(player, state, state.stats())
		player.displayClientMessage(Component.literal(statusLine(player)), false)
		return 1
	}

	private fun setExperience(context: CommandContext<CommandSourceStack>, experience: Int): Int {
		val player = context.source.getPlayerOrException()
		val state = stateFor(player)
		state.experience = experience.coerceIn(0, 5000)
		state.level = RobotRules.levelForExperience(state.experience)
		state.heat = min(state.heat, state.stats().heatLimit.toDouble())
		state.appliedMaxHp = 0
		applyRobotAttributes(player, state, state.stats())
		player.displayClientMessage(Component.literal(statusLine(player)), false)
		return 1
	}

	private fun addExperience(context: CommandContext<CommandSourceStack>, amount: Int): Int {
		val state = stateFor(context.source.getPlayerOrException())
		return setExperience(context, state.experience + amount)
	}

	fun isRobotPilot(player: Entity): Boolean {
		return pilotStates[player.uuid]?.enabled ?: true
	}

	private fun stateFor(player: Entity): PilotState {
		return pilotStates.getOrPut(player.uuid) { PilotState() }
	}

	private fun statusLine(player: ServerPlayer): String {
		val state = stateFor(player)
		val stats = state.stats()
		val status = if (state.enabled) "online" else "offline"

		return "RoboMC $status | ${state.profile.displayName()} | Lv.${state.level} XP ${state.experience}/5000 | HP ${stats.maxHp} | Power ${stats.chassisPower}W | ${stats.physicalSpec.massKilograms.roundToInt()}kg | ${stats.movementSpeedMetersPerSecond.formatOneDecimal()}m/s | climb ${stats.physicalSpec.climbableStepHeightBlocks.formatOneDecimal()} blocks | Ammo ${state.ammoFor(state.profile.kind)} | Heat ${state.heat.roundToInt()}/${stats.heatLimit} | Cooling ${stats.heatCoolingPerSecond.roundToInt()}/s | Fire ${stats.fireRateHz.roundToInt()}Hz | Shot ${stats.bullet.name} ${stats.bullet.damage.roundToInt()}HP @ ${stats.bullet.muzzleVelocityMetersPerSecond.roundToInt()}m/s"
	}

	private fun applyRobotAttributes(player: LivingEntity, state: PilotState, stats: RobotStats) {
		val maxHealth = stats.maxHp.toDouble()
		val targetSpeedBlocksPerSecond = stats.movementSpeedMetersPerSecond * RobotConstants.WORLD_BLOCKS_PER_REAL_METER
		val baseSpeed = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)
		val targetMovementAttribute = targetSpeedBlocksPerSecond / RobotConstants.VANILLA_BASE_SPEED_BLOCKS_PER_SECOND * 0.1
		val movementBoost = targetMovementAttribute / baseSpeed - 1.0
		val scaleBoost = stats.physicalSpec.heightBlocks / RobotConstants.PLAYER_BASE_HEIGHT_BLOCKS - 1.0
		val stepHeightBoost = stats.physicalSpec.climbableStepHeightBlocks - player.getAttributeBaseValue(Attributes.STEP_HEIGHT)

		applyAttribute(player, Attributes.MAX_HEALTH, AttributeModifier(RobotAttributeIds.MAX_HEALTH, maxHealth - BASE_PLAYER_HEALTH, AttributeModifier.Operation.ADD_VALUE))
		applyAttribute(player, Attributes.MOVEMENT_SPEED, AttributeModifier(RobotAttributeIds.MOVEMENT, movementBoost, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))
		applyAttribute(player, Attributes.STEP_HEIGHT, AttributeModifier(RobotAttributeIds.STEP_HEIGHT, stepHeightBoost, AttributeModifier.Operation.ADD_VALUE))
		applyAttribute(player, Attributes.JUMP_STRENGTH, AttributeModifier(RobotAttributeIds.JUMP_STRENGTH, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))
		applyAttribute(player, Attributes.ARMOR, AttributeModifier(RobotAttributeIds.ARMOR, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))
		applyAttribute(player, Attributes.ARMOR_TOUGHNESS, AttributeModifier(RobotAttributeIds.ARMOR_TOUGHNESS, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))
		applyAttribute(player, Attributes.KNOCKBACK_RESISTANCE, AttributeModifier(RobotAttributeIds.KNOCKBACK, 0.65, AttributeModifier.Operation.ADD_VALUE))
		applyAttribute(player, Attributes.SCALE, AttributeModifier(RobotAttributeIds.SCALE, scaleBoost, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))

		if (state.appliedMaxHp != stats.maxHp) {
			player.health = stats.maxHp.toFloat()
			state.appliedMaxHp = stats.maxHp
		}
	}

	private fun removeRobotAttributes(player: LivingEntity) {
		removeAttribute(player, Attributes.MAX_HEALTH, RobotAttributeIds.MAX_HEALTH)
		removeAttribute(player, Attributes.MOVEMENT_SPEED, RobotAttributeIds.MOVEMENT)
		removeAttribute(player, Attributes.STEP_HEIGHT, RobotAttributeIds.STEP_HEIGHT)
		removeAttribute(player, Attributes.JUMP_STRENGTH, RobotAttributeIds.JUMP_STRENGTH)
		removeAttribute(player, Attributes.ARMOR, RobotAttributeIds.ARMOR)
		removeAttribute(player, Attributes.ARMOR_TOUGHNESS, RobotAttributeIds.ARMOR_TOUGHNESS)
		removeAttribute(player, Attributes.KNOCKBACK_RESISTANCE, RobotAttributeIds.KNOCKBACK)
		removeAttribute(player, Attributes.SCALE, RobotAttributeIds.SCALE)
	}

	private fun applyAttribute(
		player: LivingEntity,
		attribute: Holder<Attribute>,
		modifier: AttributeModifier
	) {
		player.getAttribute(attribute)?.addOrUpdateTransientModifier(modifier)
	}

	private fun removeAttribute(
		player: LivingEntity,
		attribute: Holder<Attribute>,
		modifierId: ResourceLocation
	) {
		player.getAttribute(attribute)?.removeModifier(modifierId)
	}

	private inline fun <reified T : Enum<T>> enumByOrdinal(ordinal: Int, fallback: T): T {
		return enumValues<T>().getOrElse(ordinal) { fallback }
	}
}
