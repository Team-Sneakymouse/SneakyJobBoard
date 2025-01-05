package net.sneakyjobboard.advert

import net.sneakyjobboard.SneakyJobBoard
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Display.Brightness
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Manages advert categories and their configurations, loading them from a configuration file.
 */
class AdvertCategoryManager {
    private val advertCategories = mutableMapOf<String, AdvertCategory>()

    /** Initializes the manager by loading advert categories from the configuration file. */
    init {
        parseConfig()
    }

    /**
     * Loads advert categories from the configuration file.
     * Throws an IllegalStateException if the configuration file is not found.
     */
    private fun parseConfig() {
        try {
            val configFile = SneakyJobBoard.getConfigFile()
            if (!configFile.exists()) {
                throw IllegalStateException("config.yml not found")
            }

            val config = YamlConfiguration.loadConfiguration(configFile)
            val advertCategoriesSection = config.getConfigurationSection("advert-categories") ?: return

            advertCategories.clear()

            for (key in advertCategoriesSection.getKeys(false)) {
                val name = advertCategoriesSection.getString("$key.name") ?: key
                val description = advertCategoriesSection.getString("$key.description") ?: key
                val iconMaterialString = advertCategoriesSection.getString("$key.icon-material") ?: ""
                val iconCustomModelData = advertCategoriesSection.getInt("$key.icon-custom-model-data")

                val iconMaterial = Material.matchMaterial(iconMaterialString) ?: Material.MUSIC_DISC_CAT

                val dynmapMapIcon = advertCategoriesSection.getString("$key.dynmap-map-icon") ?: ""
                val discordEmbedIcon = advertCategoriesSection.getString("$key.discord-embed-icon") ?: ""

                val brightnessBlock = advertCategoriesSection.getInt("$key.item-display-brightness.block")
                val brightnessSky = advertCategoriesSection.getInt("$key.item-display-brightness.sky")
                val brightness = Brightness(brightnessBlock, brightnessSky)

                val transformation = with(advertCategoriesSection.getVectorTransformation(key)) {
                    Transformation(
                        this.translation, this.leftRotation, this.scale, this.rightRotation
                    )
                }

                advertCategories[key] = AdvertCategory(
					id = key,
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
                "An unexpected error occurred while loading advert categories: ${e.message}"
            )
        }
    }

    /**
     * Parses a Transformation from the configuration section.
     * @param key The key for the advert category in the configuration.
     * @return The Transformation object created from the configuration values.
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
     * Retrieves a read-only map of advert categories.
     * @return A map where the key is the advert category ID and the value is the AdvertCategory object.
     */
    fun getAdvertCategories(): Map<String, AdvertCategory> {
        return advertCategories
    }

    fun getCategory(id: String): AdvertCategory? = advertCategories[id]
}

/**
 * Represents an advert category with associated properties for display and functionality.
 * @property name The display name of the advert category.
 * @property description A brief description of the advert category.
 * @property iconMaterial The material used for the icon representation.
 * @property iconCustomModelData Custom model data for the icon.
 * @property brightness The brightness settings for item display.
 * @property transformation The transformation applied to the item display.
 * @property dynmapMapIcon The icon used in the Dynmap integration.
 * @property discordEmbedIcon The icon used in Discord embeds.
 */
data class AdvertCategory(
    val id: String,
    val name: String,
    val description: String,
    val iconMaterial: Material,
    val iconCustomModelData: Int,
    val brightness: Brightness,
    val transformation: Transformation,
    val dynmapMapIcon: String,
    val discordEmbedIcon: String
) 