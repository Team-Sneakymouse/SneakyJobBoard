package net.sneakyjobboard.advert

import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.util.TextUtility
import net.sneakyjobboard.commands.CommandListAdvert
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * Interface for selecting an icon for an advert.
 */
class AdvertIconSelector(val category: AdvertCategory?, val page: Int = 0) : InventoryHolder {
    private val inventory: Inventory = Bukkit.createInventory(this, 54, TextUtility.convertToComponent("&6Select Icon"))
    private val icons = mutableListOf<IconData>()

    init {
        loadIcons()
        updateInventory()
    }

    override fun getInventory(): Inventory = inventory

    private fun loadIcons() {
        val config = SneakyJobBoard.getInstance().config
        val iconsSection = config.getConfigurationSection("advert-icons") ?: return

        for (materialKey in iconsSection.getKeys(false)) {
            val material = Material.matchMaterial(materialKey) ?: continue
            val modelDataList = iconsSection.getStringList(materialKey)

            for (modelDataEntry in modelDataList) {
                if (modelDataEntry.contains("-")) {
                    // Handle range
                    val parts = modelDataEntry.split("-")
                    val start = parts[0].toIntOrNull() ?: continue
                    val end = parts[1].toIntOrNull() ?: continue
                    for (modelData in start..end) {
                        icons.add(IconData(material, modelData))
                    }
                } else {
                    // Handle single value
                    val modelData = modelDataEntry.toIntOrNull() ?: continue
                    icons.add(IconData(material, modelData))
                }
            }
        }
    }

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
                SneakyJobBoard.getAdvertManager().IDKEY,
                PersistentDataType.STRING,
                "prev_page"
            )
            prevButton.itemMeta = meta
            inventory.setItem(51, prevButton)
        }

        if (hasNextPage) {
            val nextButton = ItemStack(Material.ARROW)
            val meta = nextButton.itemMeta
            meta.displayName(TextUtility.convertToComponent("&eNext Page"))
            meta.persistentDataContainer.set(
                SneakyJobBoard.getAdvertManager().IDKEY,
                PersistentDataType.STRING,
                "next_page"
            )
            nextButton.itemMeta = meta
            inventory.setItem(52, nextButton)
        }
    }

    private fun createIconButton(icon: IconData): ItemStack {
        val itemStack = ItemStack(icon.material)
        val meta = itemStack.itemMeta

        meta.setCustomModelData(icon.modelData)
		meta.isHideTooltip = true
        
        // Store icon data in persistent data container
        val container = meta.persistentDataContainer
        container.set(
            SneakyJobBoard.getAdvertManager().IDKEY,
            PersistentDataType.STRING,
            "${icon.material.name},${icon.modelData}"
        )

        itemStack.itemMeta = meta
        return itemStack
    }

    companion object {
        fun open(player: Player, category: AdvertCategory?, page: Int = 0) {
            val ui = AdvertIconSelector(category, page)
            player.openInventory(ui.inventory)
        }
    }

    data class IconData(val material: Material, val modelData: Int)
}

/**
 * Listener for handling icon selector UI interactions.
 */
class AdvertIconSelectorListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder
        if (holder !is AdvertIconSelector) return

        event.isCancelled = true

        val clickedItem = event.currentItem ?: return
        val player = event.whoClicked as? Player ?: return

        val id = clickedItem.itemMeta?.persistentDataContainer?.get(
            SneakyJobBoard.getAdvertManager().IDKEY,
            PersistentDataType.STRING
        ) ?: return

        when (id) {
            "prev_page" -> {
                val currentPage = (holder as? AdvertIconSelector)?.page ?: 0
                if (currentPage > 0) {
                    AdvertIconSelector.open(player, holder.category, currentPage - 1)
                }
            }
            "next_page" -> {
                val currentPage = (holder as? AdvertIconSelector)?.page ?: 0
                AdvertIconSelector.open(player, holder.category, currentPage + 1)
            }
            else -> {
                // Parse icon data
                val (materialName, modelData) = id.split(",")
                val material = Material.valueOf(materialName)
                val customModelData = modelData.toInt()

                // Start the advert creation process with the selected icon and category
                player.closeInventory()
                CommandListAdvert.startAdvertCreation(player, material, customModelData, holder.category)
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory.holder !is AdvertIconSelector) return
        // Additional cleanup if needed
    }
} 