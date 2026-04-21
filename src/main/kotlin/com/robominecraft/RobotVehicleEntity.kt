package com.robominecraft

import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.max

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
		val seatHeight = physicalSpec().collisionHeightBlocks * 0.7
		return Vec3(0.0, seatHeight, 0.0)
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

		val strafe = rider.xxa * 0.5f
		val forward = rider.zza
		setSpeed(getAttributeValue(Attributes.MOVEMENT_SPEED).toFloat())

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

		if (passengers.isEmpty()) {
			remove(RemovalReason.DISCARDED)
		}
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

	companion object {
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
			val maxSpec = RobotRules.physicalSpec(RobotKind.HERO)
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
