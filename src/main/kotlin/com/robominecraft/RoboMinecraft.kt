package com.robominecraft

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.api.ModInitializer
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.Holder
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

object RoboMinecraft : ModInitializer {
	const val MOD_ID = "robominecraft"

	val ROBOT_SCALE_ID: ResourceLocation = id("robot_scale")
	private val ROBOT_MAX_HEALTH_ID: ResourceLocation = id("robot_max_health")
	private val ROBOT_MOVEMENT_ID: ResourceLocation = id("robot_movement_speed")
	private val ROBOT_STEP_HEIGHT_ID: ResourceLocation = id("robot_step_height")
	private val ROBOT_JUMP_STRENGTH_ID: ResourceLocation = id("robot_jump_strength")
	private val ROBOT_ARMOR_ID: ResourceLocation = id("robot_armor")
	private val ROBOT_KNOCKBACK_ID: ResourceLocation = id("robot_knockback_resistance")

	private const val SHOT_COOLDOWN_TICKS = 6
	private const val COLLISION_DAMAGE = 2.0f
	private const val COLLISION_COOLDOWN_TICKS = 20
	private const val BASE_PLAYER_HEALTH = 20.0
	private const val PLAYER_BASE_HEIGHT_BLOCKS = 1.8
	private const val WORLD_BLOCKS_PER_REAL_METER = 10.0
	private const val VANILLA_BASE_SPEED_BLOCKS_PER_SECOND = 4.317
	private const val ROBOT_SPEED_COEFFICIENT = 1.0
	private const val GRAVITY_METERS_PER_SECOND_SQUARED = 9.80665
	private const val AIR_DENSITY_KG_PER_CUBIC_METER = 1.225
	private const val SPHERE_DRAG_COEFFICIENT = 0.47
	private const val PROJECTILE_STEP_SECONDS = 0.005
	private const val PROJECTILE_FAILSAFE_MAX_DISTANCE_BLOCKS = 1800.0
	private const val TRAIL_MAX_POINTS = 8
	private const val MAX_CLIMB_SLOPE_DEGREES = 40.0
	private val MAX_CLIMB_SLOPE_RADIANS = Math.toRadians(MAX_CLIMB_SLOPE_DEGREES)

	private val logger = LoggerFactory.getLogger(MOD_ID)
	private val pilotStates = mutableMapOf<UUID, PilotState>()
	private val lastShotTicks = mutableMapOf<UUID, Int>()
	private val lastCollisionTicks = mutableMapOf<UUID, Int>()
	private val infantryPhysicalSpec = RobotPhysicalSpec(
		lengthMeters = 0.6,
		widthMeters = 0.5,
		heightMeters = 0.5,
		massKilograms = 20.0
	)
	private val heroPhysicalSpec = RobotPhysicalSpec(
		lengthMeters = 0.7,
		widthMeters = 0.6,
		heightMeters = 0.6,
		massKilograms = 25.0
	)
	private val infantryBullet = BulletSpec(
		name = "17mm",
		diameterMeters = 0.017,
		massKilograms = 0.0032,
		material = "TPU",
		muzzleVelocityMetersPerSecond = 30.0,
		damage = 20.0f
	)
	private val heroBullet = BulletSpec(
		name = "42mm",
		diameterMeters = 0.042,
		massKilograms = 0.0445,
		material = "TPE",
		muzzleVelocityMetersPerSecond = 16.0,
		damage = 200.0f
	)

	enum class RobotKind(val displayName: String) {
		HERO("Hero"),
		INFANTRY("Infantry")
	}

	enum class HeroMode(val displayName: String) {
		MELEE("melee-priority"),
		RANGED("ranged-priority")
	}

	enum class InfantryChassisMode(val displayName: String) {
		POWER("power-priority"),
		HEALTH("health-priority")
	}

	enum class InfantryLauncherMode(val displayName: String) {
		BURST("burst-priority"),
		COOLING("cooling-priority")
	}

	data class RobotStats(
		val maxHp: Int,
		val chassisPower: Int,
		val heatLimit: Int,
		val heatCoolingPerSecond: Double,
		val shotHeat: Double,
		val bullet: BulletSpec,
		val physicalSpec: RobotPhysicalSpec,
		val movementSpeedMetersPerSecond: Double
	)

	data class BulletSpec(
		val name: String,
		val diameterMeters: Double,
		val massKilograms: Double,
		val material: String,
		val muzzleVelocityMetersPerSecond: Double,
		val damage: Float
	)

	data class RobotPhysicalSpec(
		val lengthMeters: Double,
		val widthMeters: Double,
		val heightMeters: Double,
		val massKilograms: Double
	) {
		val heightBlocks: Double
			get() = heightMeters * WORLD_BLOCKS_PER_REAL_METER

		val climbableStepHeightBlocks: Double
			get() = lengthMeters * tan(MAX_CLIMB_SLOPE_RADIANS) * WORLD_BLOCKS_PER_REAL_METER

		fun displayDimensions(): String {
			return "${lengthMeters}m x ${widthMeters}m x ${heightMeters}m"
		}
	}

	data class RobotProfile(
		val kind: RobotKind = RobotKind.INFANTRY,
		val heroMode: HeroMode = HeroMode.MELEE,
		val infantryChassisMode: InfantryChassisMode = InfantryChassisMode.POWER,
		val infantryLauncherMode: InfantryLauncherMode = InfantryLauncherMode.BURST
	) {
		fun displayName(): String {
			return when (kind) {
				RobotKind.HERO -> "${kind.displayName} ${heroMode.displayName}"
				RobotKind.INFANTRY -> "${kind.displayName} ${infantryChassisMode.displayName}/${infantryLauncherMode.displayName}"
			}
		}
	}

	data class PilotState(
		var enabled: Boolean = true,
		var profile: RobotProfile = RobotProfile(),
		var level: Int = 1,
		var experience: Int = 0,
		var heat: Double = 0.0,
		var appliedMaxHp: Int = 0
	) {
		fun stats(): RobotStats {
			return profile.stats(level)
		}
	}

	override fun onInitialize() {
		registerNetworking()
		registerRobotModeMaintenance()
		registerCommands()
		logger.info("RoboMaster MVP initialized")
	}

	private fun registerNetworking() {
		PayloadTypeRegistry.playC2S().register(FireBlasterPayload.ID, FireBlasterPayload.CODEC)
		PayloadTypeRegistry.playS2C().register(RobotHudPayload.ID, RobotHudPayload.CODEC)
		ServerPlayNetworking.registerGlobalReceiver(FireBlasterPayload.ID) { _, context ->
			val player = context.player()

			if (isRobotPilot(player) && player.mainHandItem.isEmpty) {
				fireBlaster(player)
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
					applyCollisionDamage(player)
					syncRobotHud(player, state, stats)
				} else {
					removeRobotAttributes(player)
					syncRobotHud(player, state, stats)
				}
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

	private fun fireBlaster(player: ServerPlayer) {
		val state = stateFor(player)
		val stats = state.stats()
		val serverTick = player.level().server.tickCount
		val previousShotTick = lastShotTicks[player.uuid] ?: -SHOT_COOLDOWN_TICKS

		if (serverTick - previousShotTick < SHOT_COOLDOWN_TICKS) {
			return
		}

		if (state.heat + stats.shotHeat > stats.heatLimit) {
			player.displayClientMessage(
				Component.literal("Blaster overheated: ${state.heat.roundToInt()}/${stats.heatLimit} heat"),
				true
			)
			return
		}

		state.heat += stats.shotHeat
		lastShotTicks[player.uuid] = serverTick
		player.swing(InteractionHand.MAIN_HAND)

		val level = player.level()
		val shot = simulateProjectile(player, stats.bullet)
		val end = shot.end

		level.playSound(null, player.x, player.y, player.z, SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.7f, 1.55f)
		spawnTrajectoryTrail(player, shot.trail)

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

		player.displayClientMessage(
			Component.literal("${stats.bullet.name} fired | heat ${state.heat.roundToInt()}/${stats.heatLimit}"),
			true
		)
	}

	private fun simulateProjectile(player: ServerPlayer, bullet: BulletSpec): ProjectileShot {
		val level = player.level()
		val radius = bullet.diameterMeters / 2.0
		val radiusBlocks = radius * WORLD_BLOCKS_PER_REAL_METER
		val crossSectionArea = Math.PI * radius * radius
		val dragFactor = 0.5 * AIR_DENSITY_KG_PER_CUBIC_METER * SPHERE_DRAG_COEFFICIENT * crossSectionArea / bullet.massKilograms
		var previousPosition = player.eyePosition
		var velocity = player.lookAngle.normalize().scale(bullet.muzzleVelocityMetersPerSecond)
		val trail = mutableListOf(previousPosition)

		while (previousPosition.distanceTo(player.eyePosition) < PROJECTILE_FAILSAFE_MAX_DISTANCE_BLOCKS) {
			val speed = velocity.length()
			val dragAcceleration = if (speed > 0.0) {
				velocity.normalize().scale(-dragFactor * speed * speed)
			} else {
				Vec3.ZERO
			}
			val acceleration = dragAcceleration.add(0.0, -GRAVITY_METERS_PER_SECOND_SQUARED, 0.0)
			velocity = velocity.add(acceleration.scale(PROJECTILE_STEP_SECONDS))
			val nextPosition = previousPosition.add(velocity.scale(PROJECTILE_STEP_SECONDS * WORLD_BLOCKS_PER_REAL_METER))
			val blockHit = level.clip(
				ClipContext(previousPosition, nextPosition, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
			)
			val segmentEnd = if (blockHit.type == HitResult.Type.MISS) nextPosition else blockHit.location
			val entityHit = ProjectileUtil.getEntityHitResult(
				player,
				previousPosition,
				segmentEnd,
				boxBetween(previousPosition, segmentEnd).inflate(radiusBlocks + 0.18),
				{ target -> canHit(player, target) },
				PROJECTILE_FAILSAFE_MAX_DISTANCE_BLOCKS * PROJECTILE_FAILSAFE_MAX_DISTANCE_BLOCKS
			)

			if (entityHit != null) {
				trail.add(entityHit.location)
				return ProjectileShot(entityHit.location, entityHit.entity, velocity, trail)
			}

			trail.add(segmentEnd)

			if (blockHit.type != HitResult.Type.MISS) {
				return ProjectileShot(segmentEnd, null, velocity, trail)
			}

			previousPosition = nextPosition
		}

		return ProjectileShot(previousPosition, null, velocity, trail)
	}

	private fun boxBetween(start: Vec3, end: Vec3): AABB {
		return AABB(
			min(start.x, end.x),
			min(start.y, end.y),
			min(start.z, end.z),
			max(start.x, end.x),
			max(start.y, end.y),
			max(start.z, end.z)
		)
	}

	private fun spawnTrajectoryTrail(player: ServerPlayer, trail: List<Vec3>) {
		val level = player.level()
		if (trail.isEmpty()) {
			return
		}

		trail.asSequence()
			.take(TRAIL_MAX_POINTS)
			.forEach { point ->
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
			}
	}

	data class ProjectileShot(
		val end: Vec3,
		val target: Entity?,
		val impactVelocity: Vec3,
		val trail: List<Vec3>
	)

	private fun canHit(player: ServerPlayer, target: Entity): Boolean {
		return target !== player && target.isAlive && !target.isSpectator && target.isPickable && target.canBeHitByProjectile()
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
				heatLimit = if (state.enabled) stats.heatLimit else 0
			)
		)
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
			player.displayClientMessage(Component.literal("${statusLine(player)} | empty-hand right click fires"), true)
		} else {
			removeRobotAttributes(player)
			player.displayClientMessage(Component.literal("RoboMaster chassis offline."), true)
		}
	}

	private fun setHero(context: CommandContext<CommandSourceStack>, mode: HeroMode): Int {
		val player = context.source.getPlayerOrException()
		val state = stateFor(player)
		state.enabled = true
		state.profile = RobotProfile(kind = RobotKind.HERO, heroMode = mode)
		state.heat = 0.0
		state.appliedMaxHp = 0
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
		state.enabled = true
		state.profile = RobotProfile(
			kind = RobotKind.INFANTRY,
			infantryChassisMode = chassisMode,
			infantryLauncherMode = launcherMode
		)
		state.heat = 0.0
		state.appliedMaxHp = 0
		applyRobotAttributes(player, state, state.stats())
		player.displayClientMessage(Component.literal(statusLine(player)), false)
		return 1
	}

	private fun setLevel(context: CommandContext<CommandSourceStack>, level: Int): Int {
		val player = context.source.getPlayerOrException()
		val state = stateFor(player)
		state.level = level.coerceIn(1, 10)
		state.experience = max(state.experience, requiredExperienceForLevel(state.level))
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
		state.level = levelForExperience(state.experience)
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

	private fun RobotProfile.stats(level: Int): RobotStats {
		return when (kind) {
			RobotKind.HERO -> heroStats(level, heroMode)
			RobotKind.INFANTRY -> infantryStats(level, infantryChassisMode, infantryLauncherMode)
		}
	}

	private fun heroStats(level: Int, mode: HeroMode): RobotStats {
		val maxHp = when (mode) {
			HeroMode.MELEE -> interpolate(level, 200, 300, 450)
			HeroMode.RANGED -> interpolate(level, 150, 210, 300)
		}
		val chassisPower = when (mode) {
			HeroMode.MELEE -> interpolate(level, 70, 90, 120)
			HeroMode.RANGED -> interpolate(level, 50, 70, 100)
		}
		val heatLimit = when (mode) {
			HeroMode.MELEE -> interpolate(level, 140, 184, 240)
			HeroMode.RANGED -> interpolate(level, 100, 113, 130)
		}

		return RobotStats(
			maxHp = maxHp,
			chassisPower = chassisPower,
			heatLimit = heatLimit,
			heatCoolingPerSecond = if (mode == HeroMode.MELEE) 18.0 else 14.0,
			shotHeat = 100.0,
			bullet = heroBullet,
			physicalSpec = heroPhysicalSpec,
			movementSpeedMetersPerSecond = movementSpeedMetersPerSecond(chassisPower, heroPhysicalSpec.massKilograms)
		)
	}

	private fun infantryStats(
		level: Int,
		chassisMode: InfantryChassisMode,
		launcherMode: InfantryLauncherMode
	): RobotStats {
		val maxHp = when (chassisMode) {
			InfantryChassisMode.POWER -> interpolate(level, 150, 250, 400)
			InfantryChassisMode.HEALTH -> interpolate(level, 200, 300, 400)
		}
		val chassisPower = when (chassisMode) {
			InfantryChassisMode.POWER -> interpolate(level, 60, 80, 100)
			InfantryChassisMode.HEALTH -> interpolate(level, 45, 65, 100)
		}
		val heatLimit = when (launcherMode) {
			InfantryLauncherMode.BURST -> interpolate(level, 170, 210, 260)
			InfantryLauncherMode.COOLING -> interpolate(level, 40, 76, 120)
		}
		val heatCooling = when (launcherMode) {
			InfantryLauncherMode.BURST -> interpolateDouble(level, 5.0, 11.7, 20.0)
			InfantryLauncherMode.COOLING -> interpolateDouble(level, 15.0, 21.7, 30.0)
		}

		return RobotStats(
			maxHp = maxHp,
			chassisPower = chassisPower,
			heatLimit = heatLimit,
			heatCoolingPerSecond = heatCooling,
			shotHeat = 10.0,
			bullet = infantryBullet,
			physicalSpec = infantryPhysicalSpec,
			movementSpeedMetersPerSecond = movementSpeedMetersPerSecond(chassisPower, infantryPhysicalSpec.massKilograms)
		)
	}

	private fun movementSpeedMetersPerSecond(chassisPowerWatts: Int, massKilograms: Double): Double {
		return ROBOT_SPEED_COEFFICIENT * sqrt(chassisPowerWatts / massKilograms)
	}

	private fun Double.formatOneDecimal(): String {
		return ((this * 10.0).roundToInt() / 10.0).toString()
	}

	private fun interpolate(level: Int, levelOne: Int, levelFive: Int, levelTen: Int): Int {
		return interpolateDouble(level, levelOne.toDouble(), levelFive.toDouble(), levelTen.toDouble()).roundToInt()
	}

	private fun interpolateDouble(level: Int, levelOne: Double, levelFive: Double, levelTen: Double): Double {
		val safeLevel = level.coerceIn(1, 10)
		return if (safeLevel <= 5) {
			val progress = (safeLevel - 1) / 4.0
			levelOne + (levelFive - levelOne) * progress
		} else {
			val progress = (safeLevel - 5) / 5.0
			levelFive + (levelTen - levelFive) * progress
		}
	}

	private fun requiredExperienceForLevel(level: Int): Int {
		return interpolate(level.coerceIn(1, 10), 0, 2200, 5000)
	}

	private fun levelForExperience(experience: Int): Int {
		return (1..10).last { requiredExperienceForLevel(it) <= experience.coerceAtLeast(0) }
	}

	private fun statusLine(player: ServerPlayer): String {
		val state = stateFor(player)
		val stats = state.stats()
		val status = if (state.enabled) "online" else "offline"

		return "RoboMC $status | ${state.profile.displayName()} | Lv.${state.level} XP ${state.experience}/5000 | HP ${stats.maxHp} | Power ${stats.chassisPower}W | ${stats.physicalSpec.massKilograms.roundToInt()}kg | ${stats.movementSpeedMetersPerSecond.formatOneDecimal()}m/s | climb ${stats.physicalSpec.climbableStepHeightBlocks.formatOneDecimal()} blocks | Heat ${state.heat.roundToInt()}/${stats.heatLimit} | Cooling ${stats.heatCoolingPerSecond.roundToInt()}/s | Shot ${stats.bullet.name} ${stats.bullet.damage.roundToInt()}HP @ ${stats.bullet.muzzleVelocityMetersPerSecond.roundToInt()}m/s"
	}

	private fun applyRobotAttributes(player: LivingEntity, state: PilotState, stats: RobotStats) {
		val maxHealth = stats.maxHp.toDouble()
		val targetSpeedBlocksPerSecond = stats.movementSpeedMetersPerSecond * WORLD_BLOCKS_PER_REAL_METER
		val baseSpeed = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)
		val targetMovementAttribute = targetSpeedBlocksPerSecond / VANILLA_BASE_SPEED_BLOCKS_PER_SECOND * 0.1
		val movementBoost = targetMovementAttribute / baseSpeed - 1.0
		val scaleBoost = stats.physicalSpec.heightBlocks / PLAYER_BASE_HEIGHT_BLOCKS - 1.0
		val stepHeightBoost = stats.physicalSpec.climbableStepHeightBlocks - player.getAttributeBaseValue(Attributes.STEP_HEIGHT)

		applyAttribute(player, Attributes.MAX_HEALTH, AttributeModifier(ROBOT_MAX_HEALTH_ID, maxHealth - BASE_PLAYER_HEALTH, AttributeModifier.Operation.ADD_VALUE))
		applyAttribute(player, Attributes.MOVEMENT_SPEED, AttributeModifier(ROBOT_MOVEMENT_ID, movementBoost, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))
		applyAttribute(player, Attributes.STEP_HEIGHT, AttributeModifier(ROBOT_STEP_HEIGHT_ID, stepHeightBoost, AttributeModifier.Operation.ADD_VALUE))
		applyAttribute(player, Attributes.JUMP_STRENGTH, AttributeModifier(ROBOT_JUMP_STRENGTH_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))
		applyAttribute(player, Attributes.ARMOR, AttributeModifier(ROBOT_ARMOR_ID, 8.0, AttributeModifier.Operation.ADD_VALUE))
		applyAttribute(player, Attributes.KNOCKBACK_RESISTANCE, AttributeModifier(ROBOT_KNOCKBACK_ID, 0.65, AttributeModifier.Operation.ADD_VALUE))
		applyAttribute(player, Attributes.SCALE, AttributeModifier(ROBOT_SCALE_ID, scaleBoost, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))

		if (state.appliedMaxHp != stats.maxHp) {
			player.health = stats.maxHp.toFloat()
			state.appliedMaxHp = stats.maxHp
		}
	}

	private fun removeRobotAttributes(player: LivingEntity) {
		removeAttribute(player, Attributes.MAX_HEALTH, ROBOT_MAX_HEALTH_ID)
		removeAttribute(player, Attributes.MOVEMENT_SPEED, ROBOT_MOVEMENT_ID)
		removeAttribute(player, Attributes.STEP_HEIGHT, ROBOT_STEP_HEIGHT_ID)
		removeAttribute(player, Attributes.JUMP_STRENGTH, ROBOT_JUMP_STRENGTH_ID)
		removeAttribute(player, Attributes.ARMOR, ROBOT_ARMOR_ID)
		removeAttribute(player, Attributes.KNOCKBACK_RESISTANCE, ROBOT_KNOCKBACK_ID)
		removeAttribute(player, Attributes.SCALE, ROBOT_SCALE_ID)
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

	private fun modifier(path: String, amount: Double, operation: AttributeModifier.Operation): AttributeModifier {
		return AttributeModifier(id(path), amount, operation)
	}

	private fun id(path: String): ResourceLocation {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
	}

	object FireBlasterPayload : CustomPacketPayload {
		val ID: CustomPacketPayload.Type<FireBlasterPayload> = CustomPacketPayload.Type(id("fire_blaster"))
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, FireBlasterPayload> = StreamCodec.unit(FireBlasterPayload)

		override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
			return ID
		}
	}

	data class RobotHudPayload(
		val heat: Int,
		val heatLimit: Int
	) : CustomPacketPayload {
		override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
			return ID
		}

		companion object {
			val ID: CustomPacketPayload.Type<RobotHudPayload> = CustomPacketPayload.Type(id("robot_hud"))
			val CODEC: StreamCodec<RegistryFriendlyByteBuf, RobotHudPayload> = StreamCodec.composite(
				ByteBufCodecs.VAR_INT,
				RobotHudPayload::heat,
				ByteBufCodecs.VAR_INT,
				RobotHudPayload::heatLimit,
				::RobotHudPayload
			)
		}
	}
}
