package net.sneakyjobboard.advert

import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * Manages the UI for browsing adverts, either by category or all at once.
 */
class AdvertBoardInterface(
    val category: AdvertCategory? = null,
    val page: Int = 0
) : InventoryHolder {
    private val inventory: Inventory = Bukkit.createInventory(
        this,
        54,
        TextUtility.convertToComponent(category?.name?.let { "&6Adverts - $it" } ?: "&6Adverts")
    )

    init {
        updateInventory()
    }

    override fun getInventory(): Inventory = inventory

    /**
     * Updates the inventory with either category buttons or adverts based on the selected category.
     */
    private fun updateInventory() {
        inventory.clear()

        if (category == null) {
            // Show category buttons and uncategorized adverts
            populateCategoryButtons()
        } else {
            // Show adverts for the selected category
            populateCategoryAdverts()
        }

        // Add navigation buttons
        addNavigationButtons()
    }

    /**
     * Populates the inventory with category buttons and uncategorized adverts.
     */
    private fun populateCategoryButtons() {
        val advertManager = SneakyJobBoard.getAdvertManager()
        val categoryManager = SneakyJobBoard.getAdvertCategoryManager()
        var slot = 0

        // Add category buttons for categories that have active adverts
        for (category in categoryManager.getAdvertCategories().values) {
            val categoryAdverts = advertManager.getAdverts().filter { it.category == category }
            
            if (categoryAdverts.isNotEmpty()) {
                val button = createCategoryButton(category, categoryAdverts.size)
                inventory.setItem(slot++, button)
            }
        }

        // Skip to the next row for uncategorized adverts
        slot = ((slot + 8) / 9) * 9

        // Add uncategorized adverts
        val uncategorizedAdverts = advertManager.getAdverts()
            .filter { it.category == null }
            .drop(page * 50)
            .take(50 - slot)

        uncategorizedAdverts.forEach { advert ->
            if (slot < 50) {
                inventory.setItem(slot++, advert.getIconItem())
            }
        }
    }

    /**
     * Populates the inventory with adverts from the selected category.
     */
    private fun populateCategoryAdverts() {
        val advertManager = SneakyJobBoard.getAdvertManager()
        val adverts = advertManager.getAdverts()
            .filter { it.category == category }
            .drop(page * 50)
            .take(50)

        adverts.forEachIndexed { index, advert ->
            inventory.setItem(index, advert.getIconItem())
        }
    }

    /**
     * Creates a button for a category showing how many active adverts it has.
     */
    private fun createCategoryButton(category: AdvertCategory, advertCount: Int): ItemStack {
        val itemStack = ItemStack(category.iconMaterial)
        val meta = itemStack.itemMeta

        meta.displayName(TextUtility.convertToComponent("&6${category.name}"))
        meta.setCustomModelData(category.iconCustomModelData)

        val lore = mutableListOf<String>()
        lore.add("&7${category.description}")
        lore.add("&e$advertCount active adverts")

        meta.lore(lore.map { TextUtility.convertToComponent(it) })

        // Store category ID in persistent data
        meta.persistentDataContainer.set(
            SneakyJobBoard.getAdvertManager().IDKEY,
            PersistentDataType.STRING,
            "category_${category.id}" // Prefix with category_ to distinguish from other IDs
        )

        itemStack.itemMeta = meta
        return itemStack
    }

    /**
     * Adds navigation buttons to the inventory.
     */
    private fun addNavigationButtons() {
        val advertManager = SneakyJobBoard.getAdvertManager()
        
        // Calculate if we need previous/next buttons
        val totalAdverts = if (category != null) {
            advertManager.getAdverts().count { it.category == category }
        } else {
            advertManager.getAdverts().count { it.category == null }
        }
        
        val hasNextPage = totalAdverts > (page + 1) * 50
        val hasPrevPage = page > 0

        // Add previous button if needed
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

        // Add next button if needed
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

        // Add back button if in category view
        if (category != null) {
            val backButton = ItemStack(Material.BARRIER)
            val meta = backButton.itemMeta
            meta.displayName(TextUtility.convertToComponent("&cBack"))
            meta.persistentDataContainer.set(
                SneakyJobBoard.getAdvertManager().IDKEY,
                PersistentDataType.STRING,
                "back"
            )
            backButton.itemMeta = meta
            inventory.setItem(53, backButton)
        }
    }

    companion object {
        fun open(player: Player, category: AdvertCategory? = null, page: Int = 0) {
            val ui = AdvertBoardInterface(category, page)
            player.openInventory(ui.inventory)
        }
    }
}

/**
 * Listener for handling advert board UI interactions.
 */
class AdvertBoardListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder
        if (holder !is AdvertBoardInterface) return

        event.isCancelled = true

        val clickedItem = event.currentItem ?: return
        val player = event.whoClicked as? Player ?: return

        val id = clickedItem.itemMeta?.persistentDataContainer?.get(
            SneakyJobBoard.getAdvertManager().IDKEY,
            PersistentDataType.STRING
        ) ?: return

        when {
            id == "prev_page" -> {
                val currentPage = (holder as? AdvertBoardInterface)?.page ?: 0
                if (currentPage > 0) {
                    AdvertBoardInterface.open(player, holder.category, currentPage - 1)
                }
            }
            id == "next_page" -> {
                val currentPage = (holder as? AdvertBoardInterface)?.page ?: 0
                AdvertBoardInterface.open(player, holder.category, currentPage + 1)
            }
            id == "back" -> {
                AdvertBoardInterface.open(player)
            }
            id.startsWith("category_") -> {
                // Extract category key from the ID
                val categoryKey = id.removePrefix("category_")
                val category = SneakyJobBoard.getAdvertCategoryManager().getCategory(categoryKey)
                if (category != null) {
                    AdvertBoardInterface.open(player, category)
                }
            }
            else -> {
                // It's an advert, handle invitation creation
                val advert = SneakyJobBoard.getAdvertManager().getAdverts().find { it.uuid == id }
                if (advert != null) {
					if (advert.player.uniqueId == player.uniqueId) {
						player.sendMessage(TextUtility.convertToComponent("&4You cannot invite yourself!"))
						return
					}

                    // Close the advert board first to prevent inventory issues
                    player.closeInventory()
                    
                    // Create the invitation
                    SneakyJobBoard.getAdvertManager().createInvitation(advert, player)
                    player.sendMessage(TextUtility.convertToComponent("&aInvitation sent!"))
                }
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory.holder !is AdvertBoardInterface) return
        // Additional cleanup if needed
    }
} 