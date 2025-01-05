package net.sneakyjobboard.advert

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.TextComponent
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.commands.CommandListAdvert
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerQuitEvent
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
    }

    companion object {
        private val editListeners = mutableMapOf<Player, Listener>()

        /**
         * Opens the edit interface for a player.
         * @param player The player editing the advertisement
         * @param advert The advertisement to edit
         */
        fun open(player: Player, advert: Advert) {
            player.openInventory(AdvertEditInterface(player, advert).inventory)
        }

        /**
         * Unregisters any active chat listeners for a player.
         * @param player The player whose listeners to unregister
         */
        fun unregisterListener(player: Player) {
            editListeners[player]?.let {
                HandlerList.unregisterAll(it)
                editListeners.remove(player)
            }
        }

        /**
         * Registers a new chat listener for a player.
         * @param player The player to register the listener for
         * @param listener The listener to register
         */
        fun registerListener(player: Player, listener: Listener) {
            unregisterListener(player)
            editListeners[player] = listener
            Bukkit.getPluginManager().registerEvents(listener, SneakyJobBoard.getInstance())
        }
    }
}

/**
 * Handles inventory interaction events for the advertisement edit interface.
 * Processes edit option selection and manages chat input for text changes.
 */
class AdvertEditListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? AdvertEditInterface ?: return
        event.isCancelled = true

        val clickedItem = event.currentItem ?: return
        val player = event.whoClicked as? Player ?: return
        val advert = holder.advert

        when (event.slot) {
            2 -> {
                // Category change
                player.closeInventory()
                val chatListener = object : Listener {
                    @EventHandler
                    fun onChat(event: AsyncChatEvent) {
                        if (event.player != player) return
                        event.isCancelled = true

                        val categoryId = (event.message() as TextComponent).content()
                        val category = if (categoryId.equals("none", ignoreCase = true)) {
                            null
                        } else {
                            SneakyJobBoard.getAdvertCategoryManager().getCategory(categoryId)
                        }

                        if (category == null && !categoryId.equals("none", ignoreCase = true)) {
                            player.sendMessage(TextUtility.convertToComponent("&cInvalid category ID!"))
                            val categories = listOf("none") + SneakyJobBoard.getAdvertCategoryManager()
                                .getAdvertCategories().values.map { it.id }
                            player.sendMessage(
                                TextUtility.convertToComponent(
                                    "&eAvailable categories: &b${
                                        categories.joinToString(
                                            ", "
                                        )
                                    }"
                                )
                            )
                            return
                        }

                        advert.category = category
                        SneakyJobBoard.getPocketbaseManager().updateAdvert(advert)
                        player.sendMessage(TextUtility.convertToComponent("&aCategory updated!"))
                        AdvertEditInterface.unregisterListener(player)
                        Bukkit.getScheduler().runTask(SneakyJobBoard.getInstance(), Runnable {
                            AdvertEditInterface.open(player, advert)
                        })
                    }

                    @EventHandler
                    fun onQuit(event: PlayerQuitEvent) {
                        if (event.player == player) {
                            AdvertEditInterface.unregisterListener(player)
                        }
                    }
                }

                AdvertEditInterface.registerListener(player, chatListener)
                player.sendMessage(TextUtility.convertToComponent("&aEnter the new category ID:"))
                val categories = listOf("none") + SneakyJobBoard.getAdvertCategoryManager()
                    .getAdvertCategories().values.map { it.id }
                player.sendMessage(TextUtility.convertToComponent("&eAvailable categories: &b${categories.joinToString(", ")}"))
            }

            4 -> {
                // Icon change
                player.closeInventory()
                AdvertIconSelector.open(player, advert.category) { material, modelData ->
                    advert.iconMaterial = material
                    advert.iconCustomModelData = modelData
                    SneakyJobBoard.getPocketbaseManager().updateAdvert(advert)
                    AdvertEditInterface.open(player, advert)
                }
            }

            6 -> {
                // Text change
                player.closeInventory()
                val chatListener = object : Listener {
                    var waitingForTitle = true

                    @EventHandler
                    fun onChat(event: AsyncChatEvent) {
                        if (event.player != player) return
                        event.isCancelled = true

                        val message = (event.message() as TextComponent).content()

                        if (waitingForTitle) {
                            advert.name = message
                            waitingForTitle = false
                            player.sendMessage(TextUtility.convertToComponent("&aEnter the new description:"))
                            player.sendMessage(
                                TextUtility.convertToComponent("&7Current: &e[Click to use old description]")
                                    .clickEvent(
                                        net.kyori.adventure.text.event.ClickEvent.suggestCommand(advert.description)
                                    )
                            )
                        } else {
                            advert.description = message
                            SneakyJobBoard.getPocketbaseManager().updateAdvert(advert)
                            AdvertEditInterface.unregisterListener(player)
                            Bukkit.getScheduler().runTask(SneakyJobBoard.getInstance(), Runnable {
                                AdvertEditInterface.open(player, advert)
                            })
                        }
                    }

                    @EventHandler
                    fun onQuit(event: PlayerQuitEvent) {
                        if (event.player == player) {
                            AdvertEditInterface.unregisterListener(player)
                        }
                    }
                }

                AdvertEditInterface.registerListener(player, chatListener)
                player.sendMessage(TextUtility.convertToComponent("&aEnter the new title:"))
                player.sendMessage(
                    TextUtility.convertToComponent("&7Current: &e[Click to use old title]").clickEvent(
                        net.kyori.adventure.text.event.ClickEvent.suggestCommand(advert.name)
                    )
                )
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