package net.sneakyjobboard.advert

import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.commands.CommandListAdvert
import net.sneakyjobboard.util.ItemModelUtility
import net.sneakyjobboard.util.LegacyItemModels
import com.sneakyutils.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import com.sneakyutils.util.DisplayOnlyInventoryHolder
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * Manages the interface for selecting icons for advertisements.
 * Provides a paginated inventory of available icons based on configuration.
 *
 * @property category The category context for icon selection, if any
 * @property page The current page being displayed
 * @property callback Optional callback to handle icon selection
 */
class AdvertIconSelector(
    val category: AdvertCategory?, val page: Int = 0, val callback: ((Material, NamespacedKey) -> Unit)? = null
) : DisplayOnlyInventoryHolder {
    private val inventory: Inventory = Bukkit.createInventory(this, 54, TextUtility.convertToComponent("&6Select Icon"))
    private val icons = mutableListOf<IconData>()

    init {
        loadIcons()
        updateInventory()
    }

    override fun getInventory(): Inventory = inventory

    private fun loadIcons() {
        val iconsSection = SneakyJobBoard.getInstance().config.getConfigurationSection("advert-icons") ?: return
        icons.addAll(readIcons(iconsSection))
    }

    /**
     * Updates the inventory with icons and navigation buttons.
     * Handles pagination and button placement.
     */
    private fun updateInventory() {
        inventory.clear()

        // Add icons
        val startIndex = page * 50
        icons.drop(startIndex).take(50).forEachIndexed { index, icon ->
            if (index < 50) {
                inventory.setItem(index, createIconButton(icon))
            }
        }

        // Add navigation buttons
        val hasNextPage = icons.size > (page + 1) * 50
        val hasPrevPage = page > 0

        if (hasPrevPage) {
            val prevButton = ItemStack(Material.ARROW)
            val meta = prevButton.itemMeta
            meta.displayName(TextUtility.convertToComponent("&ePrevious Page"))
            meta.persistentDataContainer.set(
                SneakyJobBoard.getAdvertManager().IDKEY, PersistentDataType.STRING, "prev_page"
            )
            prevButton.itemMeta = meta
            inventory.setItem(51, prevButton)
        }

        if (hasNextPage) {
            val nextButton = ItemStack(Material.ARROW)
            val meta = nextButton.itemMeta
            meta.displayName(TextUtility.convertToComponent("&eNext Page"))
            meta.persistentDataContainer.set(
                SneakyJobBoard.getAdvertManager().IDKEY, PersistentDataType.STRING, "next_page"
            )
            nextButton.itemMeta = meta
            inventory.setItem(52, nextButton)
        }
    }

    /**
     * Creates a button representing an icon option.
     * @param icon The icon data to create a button for
     * @return An ItemStack representing the icon option
     */
    private fun createIconButton(icon: IconData): ItemStack {
        val itemStack = ItemStack(icon.material)
        val meta = itemStack.itemMeta

        meta.setItemModel(icon.itemModel)
        meta.isHideTooltip = true

        // Store icon data in persistent data container
        val container = meta.persistentDataContainer
        container.set(
            SneakyJobBoard.getAdvertManager().IDKEY,
            PersistentDataType.STRING,
            "${icon.material.name},${icon.itemModel}"
        )

        itemStack.itemMeta = meta
        return itemStack
    }

    companion object {
        /** `5` or `2001-2024`: the integer forms `advert-icons` used before item models. */
        private val LEGACY_INTEGER_ENTRY = Regex("\\d{1,9}(-\\d{1,9})?")

        /**
         * Reads the `advert-icons` section: each key is a carrier material and its list holds item-model ids.
         * Legacy integer entries (`5`, `"2001-2024"`) are still accepted and converted to the item model that
         * replaced them on that carrier.
         */
        internal fun readIcons(
            iconsSection: ConfigurationSection,
            legacy: LegacyItemModels = LegacyItemModels.bundled
        ): List<IconData> {
            val icons = mutableListOf<IconData>()
            for (materialKey in iconsSection.getKeys(false)) {
                val material = Material.matchMaterial(materialKey) ?: continue
                val source = "advert-icons.$materialKey"

                for (entry in iconsSection.getStringList(materialKey)) {
                    val models = if (LEGACY_INTEGER_ENTRY.matches(entry)) {
                        val bounds = entry.split("-").map(String::toInt)
                        legacy.resolveRange(material, bounds.first(), bounds.last()).also {
                            if (it.isEmpty()) SneakyJobBoard.log("Legacy integer icon '$entry' at $source has no item model")
                        }
                    } else {
                        listOfNotNull(ItemModelUtility.parseItemModel(entry, source))
                    }
                    models.mapTo(icons) { IconData(material, it) }
                }
            }
            return icons
        }

        /**
         * Opens the icon selector interface for a player.
         * @param player The player to show the interface to
         * @param category Optional category context for the selection
         * @param page The page number to display
         * @param callback Optional callback to handle the selection
         */
        fun open(
            player: Player,
            category: AdvertCategory?,
            page: Int = 0,
            callback: ((Material, NamespacedKey) -> Unit)? = null
        ) {
            val ui = AdvertIconSelector(category, page, callback)
            player.openInventory(ui.inventory)
        }
    }

    /**
     * Represents an icon option: the carrier material and the item model shown on it.
     */
    data class IconData(val material: Material, val itemModel: NamespacedKey)
}

/**
 * Handles inventory interaction events for the icon selector interface.
 * Processes icon selection and navigation actions.
 */
class AdvertIconSelectorListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? AdvertIconSelector ?: return
        event.isCancelled = true

        val clickedItem = event.currentItem ?: return
        val player = event.whoClicked as? Player ?: return

        val id = clickedItem.itemMeta?.persistentDataContainer?.get(
            SneakyJobBoard.getAdvertManager().IDKEY, PersistentDataType.STRING
        ) ?: return

        when (id) {
            "prev_page" -> {
                val currentPage = holder.page
                if (currentPage > 0) {
                    AdvertIconSelector.open(player, holder.category, currentPage - 1, holder.callback)
                }
            }

            "next_page" -> {
                val currentPage = holder.page
                AdvertIconSelector.open(player, holder.category, currentPage + 1, holder.callback)
            }

            else -> {
                // Parse icon data: "<MATERIAL>,<namespace:item_model>"
                val (materialName, modelId) = id.split(",", limit = 2)
                val material = Material.valueOf(materialName)
                val itemModel = NamespacedKey.fromString(modelId) ?: return

                if (holder.callback != null) {
                    holder.callback.invoke(material, itemModel)
                } else {
                    // Start the advert creation process with the selected icon and category
                    net.sneakyjobboard.dialog.TextInputDialogs.fromInventory(player, event.view) {
                        CommandListAdvert.startAdvertCreation(player, material, itemModel, holder.category)
                    }
                }
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory.holder !is AdvertIconSelector) return
        // Additional cleanup if needed
    }
} 