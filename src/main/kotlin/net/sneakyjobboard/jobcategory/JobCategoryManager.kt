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
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
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
                                transformation
                        )
            }

            val mapCentralLocations = mutableListOf<Location?>()
            val worldCentralLocations = mutableListOf<Location?>()

            val mapCentralVectorStrings: List<String> = config.getStringList("map-central-vectors")
            val worldCentralVectorStrings: List<String> =
                    config.getStringList("world-central-vectors")

            // Parse map central vectors
            mapCentralVectorStrings.forEach { vectorString ->
                val components = vectorString.split(",")
                if (components.size == 4) {
                    val world = Bukkit.getWorld(components[0])
                    val x = components[1].toDouble()
                    val y = components[2].toDouble()
                    val z = components[3].toDouble()

                    if (world != null) {
                        val location = Location(world, x, y, z)
                        mapCentralLocations.add(location)
                    } else {
                        SneakyJobBoard.log(
                                "Error parsing map central vector: World '${components[1]}' not found."
                        )
                        mapCentralLocations.add(null)
                    }
                } else {
                    mapCentralLocations.add(null)
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

                        // Find the last non-null worldVector
                        for (j in i downTo 0) {
                            if (j < worldCentralLocations.size && worldCentralLocations[j] != null
                            ) {
                                worldLocation = worldCentralLocations[j]
                                break
                            }
                        }

                        if (worldLocation == null) continue

                        jobBoards.add(JobBoard(mapLocation, worldLocation))
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
        val transformation: Transformation
)

data class Job(val category: JobCategory, val player: Player, val durationMilis: Long) {
    val uuid: String = UUID.randomUUID().toString()
    val location: Location = player.location
    var name: String = category.name
    var description: String = category.description
    val startTime: Long = System.currentTimeMillis()
    val itemDisplays: MutableList<ItemDisplay> = mutableListOf()

    fun isExpired(): Boolean {
        return (System.currentTimeMillis() >= startTime + durationMilis)
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

        if (SneakyJobBoard.getInstance().papiActive) {
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

data class JobBoard(val mapLocation: Location, val worldLocation: Location)
