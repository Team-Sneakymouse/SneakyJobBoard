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
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

/**
 * Interface for managing your own advertisements.
 */
class AdvertManagementInterface(private val player: Player) : InventoryHolder {
    private val inventory: Inventory = createInventory()

    init {
        updateInventory()
    }

    override fun getInventory(): Inventory = inventory

    private fun createInventory(): Inventory {
        val adverts = SneakyJobBoard.getAdvertManager().adverts.values.filter { it.player == player }
        val rows = ((adverts.size + 8) / 9).coerceIn(1, 6)
        return Bukkit.createInventory(this, rows * 9, TextUtility.convertToComponent("&6Your Advertisements"))
    }

    private fun updateInventory() {
        inventory.clear()
        
        // Add all adverts belonging to the player
        val adverts = SneakyJobBoard.getAdvertManager().adverts.values.filter { it.player == player }
        adverts.forEachIndexed { index, advert ->
            if (index < inventory.size) {
                val itemStack = if (advert.enabled) {
                    advert.getIconItem().apply {
                        itemMeta = itemMeta?.also { meta ->
                            meta.lore(listOf(
                                TextUtility.convertToComponent("&7Category: &e${advert.category?.name ?: "None"}"),
                                TextUtility.convertToComponent("&7Description:"),
                                *TextUtility.splitIntoLines(advert.description, 30).map { 
                                    TextUtility.convertToComponent("&7$it") 
                                }.toTypedArray(),
                                TextUtility.convertToComponent(""),
                                TextUtility.convertToComponent("&eClick to disable"),
                                TextUtility.convertToComponent("&ePress Q to delete"),
                                TextUtility.convertToComponent("&ePress F to edit")
                            ))
                            // Store advert ID in persistent data
                            meta.persistentDataContainer.set(
                                SneakyJobBoard.getAdvertManager().IDKEY,
                                org.bukkit.persistence.PersistentDataType.STRING,
                                advert.uuid
                            )
                        }
                    }
                } else {
                    ItemStack(Material.RED_WOOL).apply {
                        itemMeta = itemMeta?.also { meta ->
                            meta.displayName(TextUtility.convertToComponent("&c${advert.name}"))
                            meta.lore(listOf(
                                TextUtility.convertToComponent("&7Category: &e${advert.category?.name ?: "None"}"),
                                TextUtility.convertToComponent("&7Description:"),
                                *TextUtility.splitIntoLines(advert.description, 30).map { 
                                    TextUtility.convertToComponent("&7$it") 
                                }.toTypedArray(),
                                TextUtility.convertToComponent(""),
                                TextUtility.convertToComponent("&eClick to enable"),
                                TextUtility.convertToComponent("&ePress Q to delete"),
                                TextUtility.convertToComponent("&ePress F to edit")
                            ))
                            // Store advert ID in persistent data
                            meta.persistentDataContainer.set(
                                SneakyJobBoard.getAdvertManager().IDKEY,
                                org.bukkit.persistence.PersistentDataType.STRING,
                                advert.uuid
                            )
                        }
                    }
                }
                inventory.setItem(index, itemStack)
            }
        }
    }

    companion object {
        fun open(player: Player) {
            player.openInventory(AdvertManagementInterface(player).inventory)
        }
    }
}

/**
 * Listener for handling advertisement management interface interactions.
 */
class AdvertManagementListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder
        if (holder !is AdvertManagementInterface) return

        event.isCancelled = true

        val clickedItem = event.currentItem ?: return
        val player = event.whoClicked as? Player ?: return

        val uuid = clickedItem.itemMeta?.persistentDataContainer?.get(
            SneakyJobBoard.getAdvertManager().IDKEY,
            org.bukkit.persistence.PersistentDataType.STRING
        ) ?: return

        val advert = SneakyJobBoard.getAdvertManager().adverts[uuid] ?: return

        when (event.click) {
            ClickType.LEFT -> {
                // Toggle enabled state
                advert.enabled = !advert.enabled
                SneakyJobBoard.getPocketbaseManager().updateAdvert(advert)
                player.closeInventory()
                AdvertManagementInterface.open(player)
            }
            ClickType.DROP -> {
                // Mark as deleted
                advert.deleted = true
                advert.enabled = false
                SneakyJobBoard.getPocketbaseManager().updateAdvert(advert)
                SneakyJobBoard.getAdvertManager().adverts.remove(advert.uuid)
                player.closeInventory()
                AdvertManagementInterface.open(player)
            }
            ClickType.SWAP_OFFHAND -> {
                // Open edit interface
                player.closeInventory()
                AdvertEditInterface.open(player, advert)
            }
            else -> {}
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is AdvertManagementInterface) {
            event.isCancelled = true
        }
    }
} 