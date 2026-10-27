package net.sneakyjobboard.advert

import net.sneakyjobboard.dialog.TextInputDialogs
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.util.ItemModelUtility
import com.sneakyutils.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

/**
 * Interface for editing existing advertisements.
 * Provides options to modify category, icon, name, and description.
 *
 * @property player The player editing the advertisement
 * @property advert The advertisement being edited
 */
class AdvertEditInterface(private val player: Player, val advert: Advert) : InventoryHolder {
    private val inventory: Inventory = createInventory()

    init {
        updateInventory()
    }

    override fun getInventory(): Inventory = inventory

    /**
     * Creates the base inventory for the edit interface.
     * @return A new inventory with the edit title
     */
    private fun createInventory(): Inventory {
        return Bukkit.createInventory(this, 9, TextUtility.convertToComponent("&6Edit Advertisement"))
    }

    /**
     * Updates the inventory with current edit options.
     * Shows buttons for category, icon, and text editing.
     */
    private fun updateInventory() {
        inventory.clear()
/*
        // Category change button
        inventory.setItem(2, ItemStack(Material.NAME_TAG).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(TextUtility.convertToComponent("&eChange Category"))
                meta.lore(
                    listOf(
                        TextUtility.convertToComponent("&7Current: &b${advert.category?.name ?: "None"}"),
                        TextUtility.convertToComponent("&7Click to change")
                    )
                )
            }
        })
*/
        // Icon change button
        inventory.setItem(4, ItemStack(Material.ITEM_FRAME).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(TextUtility.convertToComponent("&eChange Icon"))
                meta.lore(
                    listOf(
                        TextUtility.convertToComponent("&7Click to select a new icon")
                    )
                )
            }
        })

        // Name/Description change button
        inventory.setItem(6, ItemStack(Material.WRITABLE_BOOK).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(TextUtility.convertToComponent("&eChange Text"))
                meta.lore(
                    listOf(
                        TextUtility.convertToComponent("&7Click to change name and description")
                    )
                )
            }
        })

        // Close button
        inventory.setItem(8, ItemStack(Material.BARRIER).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(TextUtility.convertToComponent("&cClose"))
                meta.lore(
                    listOf(
                        TextUtility.convertToComponent("&7Click to close the interface")
                    )
                )
            }
        })

		// Add UI button
		inventory.setItem(0, ItemStack(Material.JIGSAW).apply {
			itemMeta = itemMeta?.also { meta ->
				ItemModelUtility.applyModel(meta, "lom:jobboard", "manage_ads")
				meta.setHideTooltip(true)
			}
		})
    }

    companion object {
        /**
         * Opens the edit interface for a player.
         * @param player The player editing the advertisement
         * @param advert The advertisement to edit
         */
        fun open(player: Player, advert: Advert) {
            player.openInventory(AdvertEditInterface(player, advert).inventory)
        }
    }
}

/**
 * Handles inventory interaction events for the advertisement edit interface.
 * Processes edit option selection and opens a dialog for text changes.
 */
class AdvertEditListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? AdvertEditInterface ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val advert = holder.advert

        when (event.rawSlot) {
            4 -> {
                // Icon change
                player.closeInventory()
				Bukkit.getScheduler().runTaskLater(SneakyJobBoard.getInstance(), Runnable {
					AdvertIconSelector.open(player, advert.category) { material, modelData ->
						advert.iconMaterial = material
						advert.iconCustomModelData = modelData
						SneakyJobBoard.getDatabaseManager().updateAdvert(advert)
						Bukkit.getScheduler().runTaskLater(SneakyJobBoard.getInstance(), Runnable {
							AdvertEditInterface.open(player, advert)
						}, 1L)
					}
				}, 1L)
            }

            6 -> {
                TextInputDialogs.fromInventory(player, event.view) {
                    TextInputDialogs.editAdvert(player, advert)
                }
            }

            8 -> {
                // Close interface
                player.closeInventory()
            }
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is AdvertEditInterface) {
            event.isCancelled = true
        }
    }
}
