package net.sneakyjobboard.util

import me.clip.placeholderapi.PlaceholderAPI
import net.sneakyjobboard.SneakyJobBoard
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.ItemMeta

data class ItemModelDefinition(
    val itemModel: NamespacedKey?,
    val customModelData: List<ConfiguredCustomModelData>
) {
    val legacyCustomModelData: Int
        get() = customModelData.filterIsInstance<ConfiguredCustomModelData.Number>().firstOrNull()?.value?.toInt() ?: 0
}

sealed interface ConfiguredCustomModelData {
    data class Number(val value: Float) : ConfiguredCustomModelData
    data class String(val value: kotlin.String) : ConfiguredCustomModelData
}

object ItemModelUtility {

    /** Applies an item model with one string custom-model-data value. */
    fun applyModel(meta: ItemMeta, itemModel: String, customModelData: String) {
        applyModel(
            meta,
            ItemModelDefinition(
                parseItemModel(itemModel, "internal item"),
                listOf(ConfiguredCustomModelData.String(customModelData))
            ),
            null
        )
    }

    /** Applies optional item-model and custom-model-data values from a configured button. */
    fun applyConfiguredModel(meta: ItemMeta, section: ConfigurationSection, key: String, player: Player) {
        applyModel(meta, readConfiguredModel(section, key), player)
    }

    /** Reads an optional item model and scalar or list custom-model-data definition. */
    fun readConfiguredModel(section: ConfigurationSection, key: String): ItemModelDefinition {
        val source = "${section.currentPath}.$key"
        val itemModel = section.getString("$key.icon-item-model")
            ?.takeIf { it.isNotBlank() }
            ?.let { parseItemModel(it, source) }

        val configuredData = section.get("$key.icon-custom-model-data")
        val configuredValues = when (configuredData) {
            null -> emptyList()
            is List<*> -> configuredData
            else -> listOf(configuredData)
        }
        val values = configuredValues.mapNotNull { configuredValue ->
            when (configuredValue) {
                is Number -> ConfiguredCustomModelData.Number(configuredValue.toFloat())
                is String -> ConfiguredCustomModelData.String(configuredValue)
                else -> {
                    SneakyJobBoard.log(
                        "Invalid icon-custom-model-data entry at $source: expected a number or string"
                    )
                    null
                }
            }
        }

        return ItemModelDefinition(itemModel, values)
    }

    /** Applies a previously parsed item model, resolving placeholders for the supplied player. */
    fun applyModel(meta: ItemMeta, definition: ItemModelDefinition, player: Player?) {
        definition.itemModel?.let(meta::setItemModel)

        val floats = mutableListOf<Float>()
        val strings = mutableListOf<String>()

        definition.customModelData.forEach { configuredValue ->
            when (configuredValue) {
                is ConfiguredCustomModelData.Number -> floats.add(configuredValue.value)
                is ConfiguredCustomModelData.String -> {
                    val containsPlaceholder = PLACEHOLDER_PATTERN.containsMatchIn(configuredValue.value)
                    val resolvedValue = if (SneakyJobBoard.isPapiActive()) {
                        PlaceholderAPI.setPlaceholders(player, configuredValue.value)
                    } else {
                        configuredValue.value
                    }

                    val resolvedNumber = if (containsPlaceholder) resolvedValue.toFloatOrNull() else null
                    if (resolvedNumber == null) {
                        strings.add(resolvedValue)
                    } else {
                        floats.add(resolvedNumber)
                    }
                }
            }
        }

        if (floats.isNotEmpty() || strings.isNotEmpty()) {
            val component = meta.customModelDataComponent
            component.setFloats(floats)
            component.setStrings(strings)
            meta.setCustomModelDataComponent(component)
        }
    }

    private fun parseItemModel(configuredItemModel: String, source: String): NamespacedKey? {
        val itemModel = runCatching { NamespacedKey.fromString(configuredItemModel) }.getOrNull()
        if (itemModel == null) {
            SneakyJobBoard.log("Invalid icon-item-model '$configuredItemModel' at $source")
        }
        return itemModel
    }

    private val PLACEHOLDER_PATTERN = Regex("%[^%]+%")
}
