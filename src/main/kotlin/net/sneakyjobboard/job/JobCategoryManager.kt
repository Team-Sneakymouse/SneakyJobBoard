package net.sneakyjobboard.job

import net.sneakyjobboard.SneakyJobBoard
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Display.Brightness
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Manages job categories and their configurations.
 * Handles loading and providing access to job categories defined in the config.
 */
class JobCategoryManager {

    private val jobCategories = mutableMapOf<String, JobCategory>()

    init {
        parseConfig()
    }

    /**
     * Loads job categories from the configuration file.
     * Each category can be configured with:
     * - Display name and description
     * - Icon material and model data
     * - Display brightness and transformation
     * - Integration icons for Dynmap and Discord
     */
    private fun parseConfig() {
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

                val iconMaterial = Material.matchMaterial(iconMaterialString) ?: Material.MUSIC_DISC_CAT

                val dynmapMapIcon = jobCategoriesSection.getString("$key.dynmap-map-icon") ?: ""
                val discordEmbedIcon = jobCategoriesSection.getString("$key.discord-embed-icon") ?: ""

                val brightnessBlock = jobCategoriesSection.getInt("$key.item-display-brightness.block")
                val brightnessSky = jobCategoriesSection.getInt("$key.item-display-brightness.sky")
                val brightness = Brightness(brightnessBlock, brightnessSky)

                val transformation = with(jobCategoriesSection.getVectorTransformation(key)) {
                    Transformation(
                        this.translation, this.leftRotation, this.scale, this.rightRotation
                    )
                }

                jobCategories[key] = JobCategory(
                    name,
                    description,
                    iconMaterial,
                    iconCustomModelData,
                    brightness,
                    transformation,
                    dynmapMapIcon,
                    discordEmbedIcon
                )
            }
        } catch (e: IllegalStateException) {
            SneakyJobBoard.log("Error: ${e.message}")
        } catch (e: Exception) {
            SneakyJobBoard.log(
                "An unexpected error occurred while loading job categories: ${e.message}"
            )
        }
    }

    /**
     * Parses transformation values for a job category from config.
     * @param key The category key in the configuration
     * @return A Transformation object with the parsed values
     */
    private fun ConfigurationSection.getVectorTransformation(key: String): Transformation {
        val leftRotationString =
            getString("$key.item-display-transformation.left-rotation")?.split(",") ?: listOf("0", "0", "0", "1")
        val rightRotationString =
            getString("$key.item-display-transformation.right-rotation")?.split(",") ?: listOf("0", "0", "0", "1")
        val translationString =
            getString("$key.item-display-transformation.translation")?.split(",") ?: listOf("0.0", "0.0", "0.0")
        val scaleString = getString("$key.item-display-transformation.scale")?.split(",") ?: listOf("0.1", "0.1", "0.1")

        val translation = Vector3f(
            translationString[0].toFloat(), translationString[1].toFloat(), translationString[2].toFloat()
        )
        val leftRotation = Quaternionf(
            leftRotationString[0].toFloat(),
            leftRotationString[1].toFloat(),
            leftRotationString[2].toFloat(),
            leftRotationString[3].toFloat()
        )
        val rightRotation = Quaternionf(
            rightRotationString[0].toFloat(),
            rightRotationString[1].toFloat(),
            rightRotationString[2].toFloat(),
            rightRotationString[3].toFloat()
        )
        val scale = Vector3f(
            scaleString[0].toFloat(), scaleString[1].toFloat(), scaleString[2].toFloat()
        )

        return Transformation(translation, leftRotation, scale, rightRotation)
    }

    /**
     * Gets all registered job categories.
     * @return An immutable map of category IDs to JobCategory objects
     */
    fun getJobCategories(): Map<String, JobCategory> {
        return jobCategories
    }
}

/**
 * Represents a job category with its display and functionality settings.
 *
 * @property name Display name of the category
 * @property description Brief description of the category
 * @property iconMaterial Material used for category icons
 * @property iconCustomModelData Custom model data for the icon
 * @property brightness Display brightness settings
 * @property transformation Display transformation settings
 * @property dynmapMapIcon Icon used on Dynmap
 * @property discordEmbedIcon Icon used in Discord embeds
 */
data class JobCategory(
    val name: String,
    val description: String,
    val iconMaterial: Material,
    val iconCustomModelData: Int,
    val brightness: Brightness,
    val transformation: Transformation,
    val dynmapMapIcon: String,
    val discordEmbedIcon: String
)
