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

                val brightnessBlock = advertCategoriesSection.getInt("$key.item-display-brightness.block")
                val brightnessSky = advertCategoriesSection.getInt("$key.item-display-brightness.sky")

                advertCategories[key] = AdvertCategory(
                    id = key,
                    name,
                    description,
                    iconMaterial,
                    iconCustomModelData
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
 */
data class AdvertCategory(
    val id: String,
    val name: String,
    val description: String,
    val iconMaterial: Material,
    val iconCustomModelData: Int
) 