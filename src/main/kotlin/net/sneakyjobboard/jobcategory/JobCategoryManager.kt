package net.sneakyjobboard.jobcategory

import java.util.UUID
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/** Manages job categories and their configurations. */
class JobCategoryManager {

    public val IDKEY: NamespacedKey = NamespacedKey(SneakyJobBoard.getInstance(), "id")
    private val jobCategories: MutableMap<String, JobCategory> = mutableMapOf()

    /** Loads job categories from the configuration file on initialization. */
    init {
        loadJobCategories()
    }

    /**
     * Loads job categories from the configuration file. If an error occurs during loading, it's
     * logged and the categories are cleared.
     */
    public fun loadJobCategories() {
        try {
            val configFile = SneakyJobBoard.getConfigFile()
            if (!configFile.exists()) {
                throw IllegalStateException("config.yml not found")
            }

            val config = YamlConfiguration.loadConfiguration(configFile)
            val jobCategoriesSection = config.getConfigurationSection("job-categories") ?: return

            jobCategories.clear()

            jobCategoriesSection.getKeys(false).forEach { key ->
                val name = jobCategoriesSection.getString("$key.name") ?: key
                val description = jobCategoriesSection.getString("$key.description") ?: key
                val iconMaterialString = jobCategoriesSection.getString("$key.icon-material") ?: ""

                var iconMaterial = Material.matchMaterial(iconMaterialString)
                if (iconMaterial == null) {
                    SneakyJobBoard.log(
                            "Invalid material '$iconMaterialString' specified for job-category '$key'. Using default."
                    )
                    iconMaterial = Material.MUSIC_DISC_CAT
                }
                val iconCustomModelData = jobCategoriesSection.getInt("$key.icon-custom-model-data")
                val dispatchCap = jobCategoriesSection.getInt("$key.dispatch-cap")
                val dispatchPar = jobCategoriesSection.getInt("$key.dispatch-par")
                val durationMillis = jobCategoriesSection.getInt("$key.duration-milis")

                jobCategories[key] =
                        JobCategory(
                                name,
                                description,
                                iconMaterial,
                                iconCustomModelData,
                                if (dispatchCap > 0) dispatchCap else 1,
                                dispatchPar,
                                if (durationMillis > 0) durationMillis else 600000
                        )
            }
        } catch (e: Exception) {
            SneakyJobBoard.log("An error occurred while loading job categories: ${e.message}")
        }
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
        val dispatchCap: Int,
        val dispatchPar: Int,
        val durationMillis: Int
)

data class Job(val category: JobCategory, val player: Player) {
    val uuid: String = UUID.randomUUID().toString()
    val location: Location = player.location
    var description: String = category.description
    var startTime: Long = System.currentTimeMillis()
    var dispatched: Int = 0

    fun isExpired(): Boolean {
        return (System.currentTimeMillis() >= startTime + category.durationMillis)
    }

    fun incrementDispatched() {
        dispatched++
    }

    fun getDispatchCap(): Int {
        return category.dispatchCap
    }

    fun isCapFulfilled(): Boolean {
        return (dispatched >= category.dispatchCap)
    }

    fun isParFulfilled(): Boolean {
        return (dispatched >= category.dispatchPar)
    }

    fun getName(): String {
        return category.name
    }

    fun getIconItem(): ItemStack {
        var itemStack: ItemStack = ItemStack(category.iconMaterial)
        var customModelData: Int = category.iconCustomModelData

        val meta = itemStack.itemMeta

        // Set custom model data, display name, and lore.
        meta.setCustomModelData(customModelData)
        meta.displayName(TextUtility.convertToComponent("&a${category.name}"))

        val lore = mutableListOf<String>()

        // Split the description into lines of a maximum length
        val descriptionLines = TextUtility.splitIntoLines(description, 30)

        // Add each line of the description to the lore
        for (line in descriptionLines) {
            lore.add("&e$line")
        }

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
