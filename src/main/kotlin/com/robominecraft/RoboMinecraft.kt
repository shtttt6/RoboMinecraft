package com.robominecraft

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory
import java.util.UUID

object RoboMinecraft : ModInitializer {
	const val MOD_ID = RobotConstants.MOD_ID

	val ROBOT_SCALE_ID: ResourceLocation = RobotAttributeIds.SCALE
	val ROBOT_VEHICLE_TYPE = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		RobotConstants.id("robot_vehicle"),
		RobotVehicleEntity.builder().build(ResourceKey.create(Registries.ENTITY_TYPE, RobotConstants.id("robot_vehicle")))
	)

	internal const val COLLISION_DAMAGE = 2.0f
	internal const val COLLISION_COOLDOWN_TICKS = 20
	internal const val BASE_PLAYER_HEALTH = 20.0
	internal const val ROBOT_FOOD_LEVEL = 17
	internal const val ROBOT_SATURATION_LEVEL = 0.0f
	internal const val MAX_SHOTS_PER_REQUEST = 3
	internal const val HEAT_SETTLEMENT_INTERVAL_TICKS = 2

	private val logger = LoggerFactory.getLogger(MOD_ID)
	internal val pilotStates = mutableMapOf<UUID, PilotState>()
	internal val robotVehicleIds = mutableMapOf<UUID, UUID>()
	internal val nextShotTicks = mutableMapOf<UUID, Double>()
	internal val lastCollisionTicks = mutableMapOf<UUID, Int>()

	override fun onInitialize() {
		FabricDefaultAttributeRegistry.register(ROBOT_VEHICLE_TYPE, RobotVehicleEntity.createAttributes())
		registerNetworking()
		registerAttackOverrides()
		registerDamageRules()
		registerPlayerLifecycle()
		registerRobotModeMaintenance()
		registerCommands()
		logger.info("RoboMaster MVP initialized")
	}
}
