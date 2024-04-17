package net.sneakyjobboard.jobcategory

import java.util.UUID
import me.clip.placeholderapi.PlaceholderAPI
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Display.Brightness
import org.bukkit.entity.GlowItemFrame
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f

/** Manages job categories and their configurations. */
class JobCategoryManager {

    public val IDKEY: NamespacedKey = NamespacedKey(SneakyJobBoard.getInstance(), "id")

    private val jobCategories: MutableMap<String, JobCategory> = mutableMapOf()
    public val jobBoards: MutableList<JobBoard> = mutableListOf()

    /** Loads job categories from the configuration file on initialization. */
    init {
        parseConfig()
    }

    /**
     * Loads job categories from the configuration file. If an error occurs during loading, it's
     * logged and the categories are cleared.
     */
    public fun parseConfig() {
        try {
            val configFile = SneakyJobBoard.getConfigFile()
            if (!configFile.exists()) {
                throw IllegalStateException("config.yml not found")
            }

            val config = YamlConfiguration.loadConfiguration(configFile)
            val jobCategoriesSection = config.getConfigurationSection("job-categories") ?: return

            jobCategories.clear()

            for (key in jobCategoriesSection.getKeys(false)) {
                val name = jobCategoriesSection.getString("$key.name") ?: key
                val description = jobCategoriesSection.getString("$key.description") ?: key
                val iconMaterialString = jobCategoriesSection.getString("$key.icon-material") ?: ""
                val iconCustomModelData = jobCategoriesSection.getInt("$key.icon-custom-model-data")

                val iconMaterial =
                        Material.matchMaterial(iconMaterialString) ?: Material.MUSIC_DISC_CAT

                val dynmapMapIcon = jobCategoriesSection.getString("$key.dynmap-map-icon") ?: key

                val brightnessBlock =
                        jobCategoriesSection.getInt("$key.item-display-brightness.block")
                val brightnessSky = jobCategoriesSection.getInt("$key.item-display-brightness.sky")
                val brightness = Brightness(brightnessBlock, brightnessSky)

                val transformation =
                        with(jobCategoriesSection.getVectorTransformation(key)) {
                            Transformation(
                                    this.translation,
                                    this.leftRotation,
                                    this.scale,
                                    this.rightRotation
                            )
                        }

                jobCategories[key] =
                        JobCategory(
                                name,
                                description,
                                iconMaterial,
                                iconCustomModelData,
                                brightness,
                                transformation,
                                dynmapMapIcon
                        )
            }

            val mapCentralLocations = mutableListOf<Location?>()
            val mapInteractables = mutableListOf<Boolean?>()
            val mapScaleOverrides = mutableListOf<Int?>()
            val isometricAngles = mutableListOf<Double?>()
            val worldCentralLocations = mutableListOf<Location?>()

            val mapCentralVectorStrings: List<String> = config.getStringList("map-central-vectors")
            val worldCentralVectorStrings: List<String> =
                    config.getStringList("world-central-vectors")

            // Parse map central vectors
            mapCentralVectorStrings.forEach { vectorString ->
                val components = vectorString.split(",")
                if (components.size >= 4) {
                    val world = Bukkit.getWorld(components[0])
                    val x = components[1].toDouble()
                    val y = components[2].toDouble()
                    val z = components[3].toDouble()
                    val interactable = components.getOrNull(4)?.toBoolean() ?: true
                    val scaleOverride = components.getOrNull(5)?.toInt() ?: 0
                    val isometricAngle = components.getOrNull(6)?.toDouble() ?: 0.0

                    if (world != null) {
                        val location = Location(world, x, y, z)
                        mapCentralLocations.add(location)
                        mapInteractables.add(interactable)
                        mapScaleOverrides.add(scaleOverride)
                        isometricAngles.add(isometricAngle)
                    } else {
                        SneakyJobBoard.log(
                                "Error parsing map central vector: World '${components[1]}' not found."
                        )
                        mapCentralLocations.add(null)
                        mapInteractables.add(null)
                        mapScaleOverrides.add(null)
                        isometricAngles.add(null)
                    }
                } else {
                    mapCentralLocations.add(null)
                    mapInteractables.add(null)
                    mapScaleOverrides.add(null)
                    isometricAngles.add(null)
                }
            }

            // Parse world central vectors
            worldCentralVectorStrings.forEach { vectorString ->
                val components = vectorString.split(",")
                if (components.size == 4) {
                    val world = Bukkit.getWorld(components[0])
                    val x = components[1].toDouble()
                    val y = components[2].toDouble()
                    val z = components[3].toDouble()

                    if (world != null) {
                        val location = Location(world, x, y, z)
                        worldCentralLocations.add(location)
                    } else {
                        SneakyJobBoard.log(
                                "Error parsing world central vector: World '${components[0]}' not found."
                        )
                        worldCentralLocations.add(null)
                    }
                } else {
                    worldCentralLocations.add(null)
                }
            }

            if (mapCentralLocations.size > 0 && worldCentralLocations.size > 0) {
                for (i in 0 until mapCentralLocations.size) {
                    val mapLocation = mapCentralLocations[i]

                    if (mapLocation != null) {
                        var worldLocation: Location? = null
                        val interactable = mapInteractables.get(i)
                        val scaleOverride = mapScaleOverrides.get(i)
                        val isometricAngle = isometricAngles.get(i)

                        // Find the last non-null worldVector
                        for (j in i downTo 0) {
                            if (j < worldCentralLocations.size && worldCentralLocations[j] != null
                            ) {
                                worldLocation = worldCentralLocations[j]
                                break
                            }
                        }

                        if (worldLocation == null ||
                                        interactable == null ||
                                        scaleOverride == null ||
                                        isometricAngle == null
                        )
                                continue

                        jobBoards.add(
                                JobBoard(
                                        mapLocation,
                                        worldLocation,
                                        interactable,
                                        scaleOverride,
                                        isometricAngle
                                )
                        )
                    }
                }
            }
        } catch (e: IllegalStateException) {
            SneakyJobBoard.log("Error: ${e.message}")
        } catch (e: Exception) {
            SneakyJobBoard.log(
                    "An unexpected error occurred while loading job categories: ${e.message}"
            )
        }
    }

    /** Parse a Transformation from our config section. */
    fun ConfigurationSection.getVectorTransformation(key: String): Transformation {
        val leftRotationString =
                getString("$key.item-display-transformation.left-rotation")?.split(",")
                        ?: listOf("0", "0", "0", "1")
        val rightRotationString =
                getString("$key.item-display-transformation.right-rotation")?.split(",")
                        ?: listOf("0", "0", "0", "1")
        val translationString =
                getString("$key.item-display-transformation.translation")?.split(",")
                        ?: listOf("0.0", "0.0", "0.0")
        val scaleString =
                getString("$key.item-display-transformation.scale")?.split(",")
                        ?: listOf("0.1", "0.1", "0.1")

        val translation =
                Vector3f(
                        translationString[0].toFloat(),
                        translationString[1].toFloat(),
                        translationString[2].toFloat()
                )
        val leftRotation =
                Quaternionf(
                        leftRotationString[0].toFloat(),
                        leftRotationString[1].toFloat(),
                        leftRotationString[2].toFloat(),
                        leftRotationString[3].toFloat()
                )
        val rightRotation =
                Quaternionf(
                        rightRotationString[0].toFloat(),
                        rightRotationString[1].toFloat(),
                        rightRotationString[2].toFloat(),
                        rightRotationString[3].toFloat()
                )
        val scale =
                Vector3f(
                        scaleString[0].toFloat(),
                        scaleString[1].toFloat(),
                        scaleString[2].toFloat()
                )

        return Transformation(translation, leftRotation, scale, rightRotation)
    }

    /**
     * Retrieves a read-only map of job categories.
     * @return A map of job category keys to their corresponding JobCategory objects.
     */
    fun getJobCategories(): Map<String, JobCategory> {
        return jobCategories
    }
}

data class JobCategory(
        val name: String,
        val description: String,
        val iconMaterial: Material,
        val iconCustomModelData: Int,
        val brightness: Brightness,
        val transformation: Transformation,
        val dynmapMapIcon: String
)

data class Job(val category: JobCategory, val player: Player, val durationMilis: Long) {
    val uuid: String = UUID.randomUUID().toString()
    val location: Location = player.location
    val startTime: Long = System.currentTimeMillis()
    val itemDisplays: MutableList<ItemDisplay> = mutableListOf()
    val textDisplays: MutableList<TextDisplay> = mutableListOf()
    var name: String = category.name
        set(value) {
            field = value
            updateTextDisplays()
            if (SneakyJobBoard.isDynmapActive()) {
                val markerAPI = SneakyJobBoard.getInstance().markerAPI

                val markerSet =
                        markerAPI?.getMarkerSet("SneakyJobBoard")
                                ?: run {
                                    markerAPI?.createMarkerSet(
                                            "SneakyJobBoard",
                                            "SneakyJobBoard",
                                            null,
                                            false
                                    )
                                            ?: run {
                                                SneakyJobBoard.log(
                                                        "Failed to create a new marker set."
                                                )
                                                return
                                            }
                                }

                markerSet.findMarker(uuid)?.label = value
            }
        }
    var description: String = category.description
        set(value) {
            field = value
            updateTextDisplays()
        }

    fun isExpired(): Boolean {
        return (System.currentTimeMillis() >= startTime + durationMilis)
    }

    fun unlist() {
        itemDisplays.forEach { entity -> entity.remove() }
        textDisplays.forEach { entity -> entity.remove() }
        SneakyJobBoard.getJobManager().jobs.remove(uuid)

        if (SneakyJobBoard.isDynmapActive()) {
            val markerAPI = SneakyJobBoard.getInstance().markerAPI

            val markerSet =
                    markerAPI?.getMarkerSet("SneakyJobBoard")
                            ?: run {
                                markerAPI?.createMarkerSet(
                                        "SneakyJobBoard",
                                        "SneakyJobBoard",
                                        null,
                                        false
                                )
                                        ?: run {
                                            SneakyJobBoard.log("Failed to create a new marker set.")
                                            return
                                        }
                            }

            markerSet.findMarker(uuid)?.deleteMarker()
        }
    }

    fun updateTextDisplays() {
        for (textDisplayEntity in textDisplays) {
            val text: MutableList<String> = mutableListOf("&a${name}")

            for (line in TextUtility.splitIntoLines(description, 30)) {
                text.add("&e$line")
            }

            var posterString =
                    (SneakyJobBoard.getInstance().getConfig().getString("poster-string")
                                    ?: "&ePosted by: &b[playerName]").replace(
                            "[playerName]",
                            player.name
                    )

            if (SneakyJobBoard.isPapiActive()) {
                posterString = PlaceholderAPI.setPlaceholders(player, posterString)
            }

            text.add(posterString)

            textDisplayEntity.text(TextUtility.convertToComponent(text.joinToString("\n")))
            textDisplayEntity.setTransformation(
                    Transformation(
                            Vector3f(0F, 0.3F, 0.025F + (0.025F * text.size)),
                            Quaternionf(-1F, 0F, 0F, 1F),
                            Vector3f(0.1F, 0.1F, 0.1F),
                            Quaternionf(0F, 0F, 0F, 1F)
                    )
            )
        }
    }

    fun getIconItem(): ItemStack {
        var itemStack: ItemStack = ItemStack(category.iconMaterial)
        var customModelData: Int = category.iconCustomModelData

        val meta = itemStack.itemMeta

        // Set custom model data, display name, and lore.
        meta.setCustomModelData(customModelData)
        meta.displayName(TextUtility.convertToComponent("&a${name}"))

        val lore = mutableListOf<String>()

        // Split the description into lines of a maximum length
        val descriptionLines = TextUtility.splitIntoLines(description, 30)

        // Add each line of the description to the lore
        for (line in descriptionLines) {
            lore.add("&e$line")
        }

        // Add poster line
        var posterString =
                (SneakyJobBoard.getInstance().getConfig().getString("poster-string")
                                ?: "&ePosted by: &b[playerName]").replace(
                        "[playerName]",
                        player.name
                )

        if (SneakyJobBoard.isPapiActive()) {
            posterString = PlaceholderAPI.setPlaceholders(player, posterString)
        }

        lore.add(posterString)

        meta.lore(lore.map { TextUtility.convertToComponent(it) })

        // Set persistent data.
        val persistentData = meta.persistentDataContainer
        persistentData.set(
                SneakyJobBoard.getJobCategoryManager().IDKEY,
                PersistentDataType.STRING,
                uuid
        )

        itemStack.itemMeta = meta
        return itemStack
    }
}

data class JobBoard(
        val mapLocation: Location,
        val worldLocation: Location,
        val interactable: Boolean,
        val mapScaleOverride: Int,
        val isometricAngle: Double
) {
    /** Checks if an item frame is part of this job board. */
    fun isPartOfBoard(itemFrame: ItemFrame): Boolean {
        val frameLocation = itemFrame.location.block.location

        return checkAlignmentAndPath(frameLocation, mapLocation.block.location)
    }

    /** Checkss which axes to iterate over, and run those checks. */
    private fun checkAlignmentAndPath(start: Location, end: Location): Boolean {
        if (start.world != end.world) return false

        if (start.x == end.x) {
            return checkPath(start, end, 'y', 'z')
        } else if (start.y == end.y) {
            return checkPath(start, end, 'x', 'z')
        } else if (start.z == end.z) {
            return checkPath(start, end, 'x', 'y')
        }
        return false
    }

    /**
     * Iterate over both axes and ensure that there are item frames on every location in between.
     */
    private fun checkPath(start: Location, end: Location, axis1: Char, axis2: Char): Boolean {
        val increment1 = if (start.getBlock(axis1) < end.getBlock(axis1)) 1 else -1
        val increment2 = if (start.getBlock(axis2) < end.getBlock(axis2)) 1 else -1

        var currentPos1 = start.getBlock(axis1)
        var currentPos2 = start.getBlock(axis2)

        while (currentPos1 != end.getBlock(axis1)) {
            currentPos1 += increment1

            val currentLocation = start.clone()
            currentLocation.setBlock(axis1, currentPos1)

            if (!locationHasItemFrame(currentLocation)) {
                return false
            }
        }

        while (currentPos2 != end.getBlock(axis2)) {
            currentPos2 += increment2

            val currentLocation = start.clone()
            currentLocation.setBlock(axis2, currentPos2)

            if (!locationHasItemFrame(currentLocation)) {
                return false
            }
        }
        return true
    }

    /** Get the axis value of a block location. */
    private fun Location.getBlock(axis: Char): Int {
        return when (axis) {
            'x' -> this.blockX
            'y' -> this.blockY
            'z' -> this.blockZ
            else -> throw IllegalArgumentException("Invalid axis")
        }
    }

    /** Set the axis value of a block location. */
    private fun Location.setBlock(axis: Char, value: Int) {
        when (axis) {
            'x' -> this.x = value.toDouble()
            'y' -> this.y = value.toDouble()
            'z' -> this.z = value.toDouble()
            else -> throw IllegalArgumentException("Invalid axis")
        }
    }

    /** Check a specified location for item frames. */
    private fun locationHasItemFrame(location: Location): Boolean {
        val entitiesAtLocation =
                location.world.getNearbyEntities(location.clone().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5)
        return entitiesAtLocation.any { entity -> entity is ItemFrame || entity is GlowItemFrame }
    }
}
