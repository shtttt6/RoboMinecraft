package com.robominecraft

import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.sin

class RobotVehicleEntity(
	type: EntityType<out PathfinderMob>,
	level: Level
) : PathfinderMob(type, level) {
	private var ownerUuid: java.util.UUID? = null
	private var profile: RobotProfile = RobotProfile()

	override fun registerGoals() {
		// Intentionally empty: movement comes from the rider.
	}

	override fun defineSynchedData(builder: SynchedEntityData.Builder) {
		super.defineSynchedData(builder)
		builder.define(DATA_ROBOT_KIND, RobotKind.INFANTRY.ordinal)
	}

	override fun getControllingPassenger(): LivingEntity? {
		return firstPassenger as? LivingEntity
	}

	override fun canAddPassenger(passenger: Entity): Boolean {
		return passengers.isEmpty() && passenger is Player
	}

	override fun getDefaultDimensions(pose: Pose): EntityDimensions {
		val spec = physicalSpec()
		val footprint = max(spec.lengthBlocks, spec.widthBlocks).toFloat()
		return EntityDimensions.fixed(footprint, spec.collisionHeightBlocks.toFloat())
	}

	override fun makeBoundingBox(position: Vec3): AABB {
		val spec = physicalSpec()
		return spec.collisionBoxAt(position.x, position.y, position.z, yBodyRot)
	}

	override fun getPassengerAttachmentPoint(passenger: Entity, dimensions: EntityDimensions, scaleFactor: Float): Vec3 {
		val spec = physicalSpec()
		val seatHeight = if (robotKind() == RobotKind.AERIAL) {
			spec.viewHeightBlocks
		} else {
			spec.collisionHeightBlocks * 0.7
		}
		return Vec3(0.0, seatHeight, 0.0)
	}

	override fun positionRider(passenger: Entity, moveFunction: Entity.MoveFunction) {
		if (robotKind() != RobotKind.AERIAL) {
			super.positionRider(passenger, moveFunction)
			return
		}

		val desiredEyeY = y + physicalSpec().viewHeightBlocks
		val riderBaseY = desiredEyeY - passenger.eyeHeight
		moveFunction.accept(passenger, x, riderBaseY, z)
	}

	override fun travel(travelVector: Vec3) {
		val rider = controllingPassenger as? Player
		if (rider == null || !isVehicle) {
			super.travel(travelVector)
			return
		}

		yRot = rider.yRot
		xRot = (rider.xRot * 0.5f).coerceIn(-45.0f, 45.0f)
		yRotO = yRot
		xRotO = xRot
		yBodyRot = yRot
		yHeadRot = yRot
		setSpeed(getAttributeValue(Attributes.MOVEMENT_SPEED).toFloat())

		if (robotKind() == RobotKind.AERIAL) {
			travelAerial(rider)
			return
		}

		val strafe = rider.xxa * 0.5f
		val forward = rider.zza
		if (rider.isJumping && onGround()) {
			jumpFromGround()
		}

		super.travel(Vec3(strafe.toDouble(), travelVector.y, forward.toDouble()))
	}

	override fun tick() {
		super.tick()

		if (level().isClientSide) {
			return
		}

		val aerial = robotKind() == RobotKind.AERIAL
		val flightMode = aerial && controllingPassenger != null && controllingPassenger?.let { RoboMinecraft.isAerialFlightMode(it) } == true
		setNoGravity(flightMode)
		if (flightMode) {
			resetFallDistance()
		}

		if (passengers.isEmpty()) {
			remove(RemovalReason.DISCARDED)
		}
	}

	override fun causeFallDamage(fallDistance: Double, damageMultiplier: Float, damageSource: DamageSource): Boolean {
		if (robotKind() != RobotKind.AERIAL) {
			return super.causeFallDamage(fallDistance, damageMultiplier, damageSource)
		}

		val rider = controllingPassenger as? net.minecraft.server.level.ServerPlayer
		if (rider != null && !RoboMinecraft.isAerialFlightMode(rider) && fallDistance > AERIAL_FATAL_FALL_DISTANCE_BLOCKS) {
			RoboMinecraft.killAerialPilot(rider)
		}

		resetFallDistance()
		return true
	}

	override fun canBeCollidedWith(entity: Entity?): Boolean {
		return true
	}

	override fun isPushable(): Boolean {
		return true
	}

	fun assignOwner(owner: Player) {
		ownerUuid = owner.uuid
	}

	fun ownerUuid(): java.util.UUID? {
		return ownerUuid
	}

	fun robotKind(): RobotKind {
		return enumValues<RobotKind>().getOrElse(entityData.get(DATA_ROBOT_KIND)) { RobotKind.INFANTRY }
	}

	fun syncFromPilotState(owner: Player, state: PilotState) {
		assignOwner(owner)
		if (profile.kind != state.profile.kind) {
			profile = state.profile
			entityData.set(DATA_ROBOT_KIND, profile.kind.ordinal)
			refreshDimensions()
		} else {
			profile = state.profile
		}

		val stats = state.stats()
		getAttribute(Attributes.MAX_HEALTH)?.baseValue = stats.maxHp.toDouble()
		getAttribute(Attributes.MOVEMENT_SPEED)?.baseValue = stats.movementAttributeValue()
		getAttribute(Attributes.STEP_HEIGHT)?.baseValue = stats.stepHeightBlocks
		getAttribute(Attributes.JUMP_STRENGTH)?.baseValue = stats.jumpStrength
		getAttribute(Attributes.KNOCKBACK_RESISTANCE)?.baseValue = 0.65
		setBoundingBox(makeBoundingBox(position()))
	}

	fun physicalSpec(): RobotPhysicalSpec {
		return RobotRules.physicalSpec(robotKind())
	}

	private fun travelAerial(rider: Player) {
		val flightMode = RoboMinecraft.isAerialFlightMode(rider)
		if (!flightMode) {
			setDeltaMovement(0.0, deltaMovement.y, 0.0)
			super.travel(Vec3.ZERO)
			return
		}

		val speed = getAttributeValue(Attributes.MOVEMENT_SPEED)
		val airborne = !onGround() || deltaMovement.y > 0.02
		val horizontalInputEnabled = airborne
		val forward = if (horizontalInputEnabled) rider.zza.toDouble() else 0.0
		val strafe = if (horizontalInputEnabled) (rider.xxa * 0.85f).toDouble() else 0.0
		val ascending = RoboMinecraft.isAerialAscending(rider)
		val descending = RoboMinecraft.isAerialDescending(rider)
		val verticalInput = when {
			ascending -> 1.0
			descending && !onGround() -> -1.0
			else -> 0.0
		}

		val yawRadians = Math.toRadians(yRot.toDouble())
		val forwardVec = Vec3(-sin(yawRadians), 0.0, cos(yawRadians))
		val rightVec = Vec3(cos(yawRadians), 0.0, sin(yawRadians))
		var movement = forwardVec.scale(forward).add(rightVec.scale(strafe)).add(0.0, verticalInput, 0.0)

		if (movement.lengthSqr() > 1.0E-6) {
			movement = movement.normalize().scale(speed)
		}

		setDeltaMovement(movement)
		move(MoverType.SELF, deltaMovement)
	}
	companion object {
		private const val AERIAL_FATAL_FALL_DISTANCE_BLOCKS = 20.0

		private val DATA_ROBOT_KIND: EntityDataAccessor<Int> =
			SynchedEntityData.defineId(RobotVehicleEntity::class.java, EntityDataSerializers.INT)

		fun createAttributes(): AttributeSupplier.Builder {
			return createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.1)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.JUMP_STRENGTH, 0.42)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.65)
		}

		fun builder(): EntityType.Builder<RobotVehicleEntity> {
			val maxSpec = RobotRules.physicalSpec(RobotKind.AERIAL)
			val footprint = max(maxSpec.lengthBlocks, maxSpec.widthBlocks).toFloat()
			return EntityType.Builder.of(::RobotVehicleEntity, MobCategory.MISC)
				.sized(footprint, maxSpec.collisionHeightBlocks.toFloat())
				.clientTrackingRange(10)
				.updateInterval(1)
		}
	}
}

fun RobotStats.movementAttributeValue(): Double {
	val targetSpeedBlocksPerSecond = movementSpeedMetersPerSecond * RobotConstants.WORLD_BLOCKS_PER_REAL_METER
	return targetSpeedBlocksPerSecond / RobotConstants.VANILLA_BASE_SPEED_BLOCKS_PER_SECOND * 0.1
}
