package com.robominecraft.client

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.logging.LogUtils
import com.mojang.math.Axis
import com.robominecraft.HeroMobilityMode
import com.robominecraft.RobotConstants
import com.robominecraft.RobotKind
import com.robominecraft.RobotVehicleEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import org.joml.Vector3f
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

internal class RobotVehicleRenderer(context: EntityRendererProvider.Context) :
	EntityRenderer<RobotVehicleEntity, HeroRobotRenderState>(context) {

	init {
		shadowRadius = 0.9f
	}

	override fun createRenderState(): HeroRobotRenderState = HeroRobotRenderState()

	override fun extractRenderState(entity: RobotVehicleEntity, renderState: HeroRobotRenderState, partialTick: Float) {
		super.extractRenderState(entity, renderState, partialTick)
		val spec = entity.physicalSpec()
		renderState.robotKind = entity.robotKind()
		renderState.heroMobilityMode = entity.heroMobilityMode()
		renderState.bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)
		renderState.width = spec.widthBlocks.toFloat()
		renderState.length = spec.lengthBlocks.toFloat()
		renderState.height = spec.collisionHeightBlocks.toFloat()
	}

	override fun submit(
		renderState: HeroRobotRenderState,
		poseStack: PoseStack,
		submitNodeCollector: SubmitNodeCollector,
		cameraRenderState: CameraRenderState
	) {
		super.submit(renderState, poseStack, submitNodeCollector, cameraRenderState)

		poseStack.pushPose()
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - renderState.bodyYaw))

		val ordered = submitNodeCollector.order(0)
		if (renderState.robotKind == RobotKind.HERO && renderState.heroMobilityMode == HeroMobilityMode.REGULAR) {
			poseStack.scale(HeroRobotBlockbenchModel.MODEL_UNIT_SCALE, HeroRobotBlockbenchModel.MODEL_UNIT_SCALE, HeroRobotBlockbenchModel.MODEL_UNIT_SCALE)
			HeroRobotBlockbenchModel.submit(poseStack, ordered, renderState.lightCoords)
		} else {
			submitFallbackBox(poseStack, ordered, renderState)
		}

		poseStack.popPose()
	}

	private fun submitFallbackBox(
		poseStack: PoseStack,
		collector: OrderedSubmitNodeCollector,
		renderState: HeroRobotRenderState
	) {
		val halfWidth = renderState.width * 0.5f
		val halfLength = renderState.length * 0.5f
		val tint = when (renderState.robotKind) {
			RobotKind.HERO -> floatArrayOf(0.82f, 0.82f, 0.82f, 1.0f)
			RobotKind.INFANTRY -> floatArrayOf(0.58f, 0.76f, 0.88f, 1.0f)
			RobotKind.AERIAL -> floatArrayOf(0.94f, 0.88f, 0.50f, 1.0f)
		}

		collector.submitCustomGeometry(poseStack, RenderType.entityCutoutNoCull(TEXTURE)) { pose, consumer ->
			HeroRobotBlockbenchModel.emitBox(
				pose,
				consumer,
				renderState.lightCoords,
				-halfWidth,
				0.0f,
				-halfLength,
				halfWidth,
				renderState.height,
				halfLength,
				tint[0],
				tint[1],
				tint[2],
				tint[3]
			)
		}
	}

	companion object {
		private val TEXTURE: ResourceLocation = RobotConstants.id("textures/entity/hero_robot_atlas.png")
	}
}

internal class HeroRobotRenderState : EntityRenderState() {
	var robotKind: RobotKind = RobotKind.INFANTRY
	var heroMobilityMode: HeroMobilityMode = HeroMobilityMode.REGULAR
	var bodyYaw: Float = 0.0f
	var width: Float = 0.0f
	var length: Float = 0.0f
	var height: Float = 0.0f
}

private object HeroRobotBlockbenchModel {
	const val MODEL_UNIT_SCALE = 0.1f

	private val logger = LogUtils.getLogger()
	private val modelResource: ResourceLocation = RobotConstants.id("blockbench/hero.bbmodel")
	private val texture: ResourceLocation = RobotConstants.id("textures/entity/hero_robot_atlas.png")
	private var cachedModel: ParsedModel? = null
	private var loadFailed = false

	fun submit(poseStack: PoseStack, collector: OrderedSubmitNodeCollector, packedLight: Int) {
		val model = loadModel() ?: return
		model.cubes.forEach { submitCube(poseStack, collector, packedLight, model, it) }
		model.meshes.forEach { submitMesh(poseStack, collector, packedLight, model, it) }
	}

	fun emitBox(
		pose: PoseStack.Pose,
		consumer: VertexConsumer,
		packedLight: Int,
		minX: Float,
		minY: Float,
		minZ: Float,
		maxX: Float,
		maxY: Float,
		maxZ: Float,
		red: Float,
		green: Float,
		blue: Float,
		alpha: Float
	) {
		emitQuad(
			pose,
			consumer,
			packedLight,
			floatArrayOf(maxX, minY, minZ),
			floatArrayOf(maxX, maxY, minZ),
			floatArrayOf(minX, maxY, minZ),
			floatArrayOf(minX, minY, minZ),
			floatArrayOf(0.0f, 0.0f),
			floatArrayOf(1.0f, 0.0f),
			floatArrayOf(1.0f, 1.0f),
			floatArrayOf(0.0f, 1.0f),
			0.0f,
			0.0f,
			-1.0f,
			red,
			green,
			blue,
			alpha
		)
		emitQuad(
			pose,
			consumer,
			packedLight,
			floatArrayOf(minX, minY, maxZ),
			floatArrayOf(minX, maxY, maxZ),
			floatArrayOf(maxX, maxY, maxZ),
			floatArrayOf(maxX, minY, maxZ),
			floatArrayOf(0.0f, 0.0f),
			floatArrayOf(1.0f, 0.0f),
			floatArrayOf(1.0f, 1.0f),
			floatArrayOf(0.0f, 1.0f),
			0.0f,
			0.0f,
			1.0f,
			red,
			green,
			blue,
			alpha
		)
		emitQuad(
			pose,
			consumer,
			packedLight,
			floatArrayOf(maxX, minY, maxZ),
			floatArrayOf(maxX, maxY, maxZ),
			floatArrayOf(maxX, maxY, minZ),
			floatArrayOf(maxX, minY, minZ),
			floatArrayOf(0.0f, 0.0f),
			floatArrayOf(1.0f, 0.0f),
			floatArrayOf(1.0f, 1.0f),
			floatArrayOf(0.0f, 1.0f),
			1.0f,
			0.0f,
			0.0f,
			red,
			green,
			blue,
			alpha
		)
		emitQuad(
			pose,
			consumer,
			packedLight,
			floatArrayOf(minX, minY, minZ),
			floatArrayOf(minX, maxY, minZ),
			floatArrayOf(minX, maxY, maxZ),
			floatArrayOf(minX, minY, maxZ),
			floatArrayOf(0.0f, 0.0f),
			floatArrayOf(1.0f, 0.0f),
			floatArrayOf(1.0f, 1.0f),
			floatArrayOf(0.0f, 1.0f),
			-1.0f,
			0.0f,
			0.0f,
			red,
			green,
			blue,
			alpha
		)
		emitQuad(
			pose,
			consumer,
			packedLight,
			floatArrayOf(minX, maxY, minZ),
			floatArrayOf(maxX, maxY, minZ),
			floatArrayOf(maxX, maxY, maxZ),
			floatArrayOf(minX, maxY, maxZ),
			floatArrayOf(0.0f, 0.0f),
			floatArrayOf(1.0f, 0.0f),
			floatArrayOf(1.0f, 1.0f),
			floatArrayOf(0.0f, 1.0f),
			0.0f,
			1.0f,
			0.0f,
			red,
			green,
			blue,
			alpha
		)
		emitQuad(
			pose,
			consumer,
			packedLight,
			floatArrayOf(minX, minY, maxZ),
			floatArrayOf(maxX, minY, maxZ),
			floatArrayOf(maxX, minY, minZ),
			floatArrayOf(minX, minY, minZ),
			floatArrayOf(0.0f, 0.0f),
			floatArrayOf(1.0f, 0.0f),
			floatArrayOf(1.0f, 1.0f),
			floatArrayOf(0.0f, 1.0f),
			0.0f,
			-1.0f,
			0.0f,
			red,
			green,
			blue,
			alpha
		)
	}

	private fun submitCube(
		poseStack: PoseStack,
		collector: OrderedSubmitNodeCollector,
		packedLight: Int,
		model: ParsedModel,
		cube: ParsedCube
	) {
		poseStack.pushPose()
		applyAbsolutePivotRotation(poseStack, cube.origin, cube.rotation)
		collector.submitCustomGeometry(poseStack, RenderType.entityCutoutNoCull(texture)) { pose, consumer ->
			val x1 = cube.from[0]
			val y1 = cube.from[1]
			val z1 = cube.from[2]
			val x2 = cube.to[0]
			val y2 = cube.to[1]
			val z2 = cube.to[2]

			cube.faces["north"]?.let {
				emitQuad(pose, consumer, packedLight, floatArrayOf(x2, y1, z1), floatArrayOf(x2, y2, z1), floatArrayOf(x1, y2, z1), floatArrayOf(x1, y1, z1), uv(it, 3, model), uv(it, 0, model), uv(it, 1, model), uv(it, 2, model), 0.0f, 0.0f, -1.0f)
			}
			cube.faces["south"]?.let {
				emitQuad(pose, consumer, packedLight, floatArrayOf(x1, y1, z2), floatArrayOf(x1, y2, z2), floatArrayOf(x2, y2, z2), floatArrayOf(x2, y1, z2), uv(it, 3, model), uv(it, 0, model), uv(it, 1, model), uv(it, 2, model), 0.0f, 0.0f, 1.0f)
			}
			cube.faces["east"]?.let {
				emitQuad(pose, consumer, packedLight, floatArrayOf(x2, y1, z2), floatArrayOf(x2, y2, z2), floatArrayOf(x2, y2, z1), floatArrayOf(x2, y1, z1), uv(it, 3, model), uv(it, 0, model), uv(it, 1, model), uv(it, 2, model), 1.0f, 0.0f, 0.0f)
			}
			cube.faces["west"]?.let {
				emitQuad(pose, consumer, packedLight, floatArrayOf(x1, y1, z1), floatArrayOf(x1, y2, z1), floatArrayOf(x1, y2, z2), floatArrayOf(x1, y1, z2), uv(it, 3, model), uv(it, 0, model), uv(it, 1, model), uv(it, 2, model), -1.0f, 0.0f, 0.0f)
			}
			cube.faces["up"]?.let {
				emitQuad(pose, consumer, packedLight, floatArrayOf(x1, y2, z1), floatArrayOf(x2, y2, z1), floatArrayOf(x2, y2, z2), floatArrayOf(x1, y2, z2), uv(it, 0, model), uv(it, 1, model), uv(it, 2, model), uv(it, 3, model), 0.0f, 1.0f, 0.0f)
			}
			cube.faces["down"]?.let {
				emitQuad(pose, consumer, packedLight, floatArrayOf(x1, y1, z2), floatArrayOf(x2, y1, z2), floatArrayOf(x2, y1, z1), floatArrayOf(x1, y1, z1), uv(it, 0, model), uv(it, 1, model), uv(it, 2, model), uv(it, 3, model), 0.0f, -1.0f, 0.0f)
			}
		}
		poseStack.popPose()
	}

	private fun submitMesh(
		poseStack: PoseStack,
		collector: OrderedSubmitNodeCollector,
		packedLight: Int,
		model: ParsedModel,
		mesh: ParsedMesh
	) {
		poseStack.pushPose()
		poseStack.translate(mesh.origin[0].toDouble(), mesh.origin[1].toDouble(), mesh.origin[2].toDouble())
		applyLocalRotation(poseStack, mesh.rotation)
		collector.submitCustomGeometry(poseStack, RenderType.entityCutoutNoCull(texture)) { pose, consumer ->
			mesh.faces.forEach { face ->
				val points = face.vertexIds.mapNotNull { mesh.vertices[it] }
				if (points.size < 3) {
					return@forEach
				}
				val vertices = when (points.size) {
					3 -> listOf(points[0], points[1], points[2], points[2])
					else -> points.take(4)
				}
				val uvs = when (face.vertexUvs.size) {
					3 -> listOf(face.vertexUvs[0], face.vertexUvs[1], face.vertexUvs[2], face.vertexUvs[2])
					else -> face.vertexUvs.take(4)
				}
				val normal = faceNormal(vertices[0], vertices[1], vertices[2])
				emitQuad(
					pose,
					consumer,
					packedLight,
					vertices[0],
					vertices[1],
					vertices[2],
					vertices[3],
					normalizeUv(uvs[0], model),
					normalizeUv(uvs[1], model),
					normalizeUv(uvs[2], model),
					normalizeUv(uvs[3], model),
					normal.x,
					normal.y,
					normal.z
				)
			}
		}
		poseStack.popPose()
	}

	private fun applyAbsolutePivotRotation(poseStack: PoseStack, origin: FloatArray, rotation: FloatArray) {
		if (rotation.contentEquals(ZERO_ROTATION)) {
			return
		}
		poseStack.translate(origin[0].toDouble(), origin[1].toDouble(), origin[2].toDouble())
		applyLocalRotation(poseStack, rotation)
		poseStack.translate(-origin[0].toDouble(), -origin[1].toDouble(), -origin[2].toDouble())
	}

	private fun applyLocalRotation(poseStack: PoseStack, rotation: FloatArray) {
		if (rotation[0] != 0.0f) {
			poseStack.mulPose(Axis.XP.rotationDegrees(rotation[0]))
		}
		if (rotation[1] != 0.0f) {
			poseStack.mulPose(Axis.YP.rotationDegrees(rotation[1]))
		}
		if (rotation[2] != 0.0f) {
			poseStack.mulPose(Axis.ZP.rotationDegrees(rotation[2]))
		}
	}

	private fun loadModel(): ParsedModel? {
		if (loadFailed) {
			return null
		}
		cachedModel?.let { return it }

		return synchronized(this) {
			cachedModel?.let { return@synchronized it }
			try {
				val manager = Minecraft.getInstance().resourceManager
				manager.open(modelResource).use { stream ->
					InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
						val parsed = parse(JsonParser.parseReader(reader).asJsonObject)
						cachedModel = parsed
						parsed
					}
				}
			} catch (exception: Exception) {
				loadFailed = true
				logger.error("Failed to load Blockbench hero model {}", modelResource, exception)
				null
			}
		}
	}

	private fun parse(root: JsonObject): ParsedModel {
		val resolution = root.getAsJsonObject("resolution")
		val textureWidth = resolution?.get("width")?.asFloat ?: DEFAULT_TEXTURE_SIZE
		val textureHeight = resolution?.get("height")?.asFloat ?: DEFAULT_TEXTURE_SIZE
		val cubes = mutableListOf<ParsedCube>()
		val meshes = mutableListOf<ParsedMesh>()
		root.getAsJsonArray("elements")?.forEach { elementValue ->
			val element = elementValue.asJsonObject
			when (element.get("type")?.asString ?: "cube") {
				"cube" -> cubes += parseCube(element)
				"mesh" -> meshes += parseMesh(element)
			}
		}
		return ParsedModel(
			textureWidth = textureWidth,
			textureHeight = textureHeight,
			cubes = cubes,
			meshes = meshes
		)
	}

	private fun parseCube(element: JsonObject): ParsedCube {
		return ParsedCube(
			from = floatArray(element.getAsJsonArray("from")),
			to = floatArray(element.getAsJsonArray("to")),
			origin = floatArrayOrDefault(element.getAsJsonArray("origin"), ZERO_POINT),
			rotation = floatArrayOrDefault(element.getAsJsonArray("rotation"), ZERO_ROTATION),
			faces = parseCubeFaces(element.getAsJsonObject("faces"))
		)
	}

	private fun parseCubeFaces(facesObject: JsonObject?): Map<String, FaceUv> {
		if (facesObject == null) {
			return emptyMap()
		}

		val faces = linkedMapOf<String, FaceUv>()
		facesObject.entrySet().forEach { (direction, faceValue) ->
			val faceObject = faceValue.asJsonObject
			faces[direction] = FaceUv(floatArrayOrDefault(faceObject.getAsJsonArray("uv"), DEFAULT_CUBE_UV))
		}
		return faces
	}

	private fun parseMesh(element: JsonObject): ParsedMesh {
		val vertices = linkedMapOf<String, FloatArray>()
		element.getAsJsonObject("vertices").entrySet().forEach { (id, value) ->
			vertices[id] = floatArray(value.asJsonArray)
		}

		val faces = mutableListOf<MeshFace>()
		element.getAsJsonObject("faces").entrySet().forEach { (_, faceValue) ->
			val faceObject = faceValue.asJsonObject
			val vertexIds = faceObject.getAsJsonArray("vertices").map { it.asString }
			val uvObject = faceObject.getAsJsonObject("uv")
			val vertexUvs = vertexIds.map { vertexId ->
				floatArrayOrDefault(uvObject?.getAsJsonArray(vertexId), DEFAULT_VERTEX_UV)
			}
			faces += MeshFace(vertexIds, vertexUvs)
		}

		return ParsedMesh(
			origin = floatArrayOrDefault(element.getAsJsonArray("origin"), ZERO_POINT),
			rotation = floatArrayOrDefault(element.getAsJsonArray("rotation"), ZERO_ROTATION),
			vertices = vertices,
			faces = faces
		)
	}

	private fun floatArray(array: JsonArray): FloatArray {
		return FloatArray(array.size()) { index -> array[index].asFloat }
	}

	private fun floatArrayOrDefault(array: JsonArray?, fallback: FloatArray): FloatArray {
		if (array == null) {
			return fallback.copyOf()
		}
		return FloatArray(array.size()) { index -> array[index].asFloat }
	}

	private fun uv(face: FaceUv, index: Int, model: ParsedModel): FloatArray {
		val uv = face.uv
		return when (index) {
			0 -> floatArrayOf(uv[0] / model.textureWidth, uv[1] / model.textureHeight)
			1 -> floatArrayOf(uv[2] / model.textureWidth, uv[1] / model.textureHeight)
			2 -> floatArrayOf(uv[2] / model.textureWidth, uv[3] / model.textureHeight)
			else -> floatArrayOf(uv[0] / model.textureWidth, uv[3] / model.textureHeight)
		}
	}

	private fun normalizeUv(uv: FloatArray, model: ParsedModel): FloatArray {
		return floatArrayOf(uv[0] / model.textureWidth, uv[1] / model.textureHeight)
	}

	private fun faceNormal(a: FloatArray, b: FloatArray, c: FloatArray): Vector3f {
		val ab = Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2])
		val ac = Vector3f(c[0] - a[0], c[1] - a[1], c[2] - a[2])
		return ab.cross(ac).normalize()
	}

	private fun emitQuad(
		pose: PoseStack.Pose,
		consumer: VertexConsumer,
		packedLight: Int,
		v1: FloatArray,
		v2: FloatArray,
		v3: FloatArray,
		v4: FloatArray,
		uv1: FloatArray,
		uv2: FloatArray,
		uv3: FloatArray,
		uv4: FloatArray,
		nx: Float,
		ny: Float,
		nz: Float,
		red: Float = 1.0f,
		green: Float = 1.0f,
		blue: Float = 1.0f,
		alpha: Float = 1.0f
	) {
		addVertex(consumer, pose, packedLight, v1, uv1, nx, ny, nz, red, green, blue, alpha)
		addVertex(consumer, pose, packedLight, v2, uv2, nx, ny, nz, red, green, blue, alpha)
		addVertex(consumer, pose, packedLight, v3, uv3, nx, ny, nz, red, green, blue, alpha)
		addVertex(consumer, pose, packedLight, v4, uv4, nx, ny, nz, red, green, blue, alpha)
	}

	private fun addVertex(
		consumer: VertexConsumer,
		pose: PoseStack.Pose,
		packedLight: Int,
		position: FloatArray,
		uv: FloatArray,
		nx: Float,
		ny: Float,
		nz: Float,
		red: Float,
		green: Float,
		blue: Float,
		alpha: Float
	) {
		consumer.addVertex(pose, position[0], position[1], position[2])
			.setColor(red, green, blue, alpha)
			.setUv(uv[0], uv[1])
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(packedLight)
			.setNormal(pose, nx, ny, nz)
	}

	private data class ParsedModel(
		val textureWidth: Float,
		val textureHeight: Float,
		val cubes: List<ParsedCube>,
		val meshes: List<ParsedMesh>
	)

	private data class ParsedCube(
		val from: FloatArray,
		val to: FloatArray,
		val origin: FloatArray,
		val rotation: FloatArray,
		val faces: Map<String, FaceUv>
	)

	private data class FaceUv(val uv: FloatArray)

	private data class ParsedMesh(
		val origin: FloatArray,
		val rotation: FloatArray,
		val vertices: Map<String, FloatArray>,
		val faces: List<MeshFace>
	)

	private data class MeshFace(
		val vertexIds: List<String>,
		val vertexUvs: List<FloatArray>
	)

	private const val DEFAULT_TEXTURE_SIZE = 16.0f
	private val ZERO_POINT = floatArrayOf(0.0f, 0.0f, 0.0f)
	private val ZERO_ROTATION = floatArrayOf(0.0f, 0.0f, 0.0f)
	private val DEFAULT_CUBE_UV = floatArrayOf(0.0f, 0.0f, 16.0f, 16.0f)
	private val DEFAULT_VERTEX_UV = floatArrayOf(0.0f, 0.0f)
}
