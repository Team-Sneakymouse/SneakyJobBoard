package net.sneakyjobboard.job

import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/** Holds the job history inventory for re-listing jobs. */
class JobHistoryInventoryHolder(private val jobHistory: List<Job>) : InventoryHolder {

    private var inventory: Inventory = Bukkit.createInventory(
        this, 9, TextUtility.convertToComponent("&eJob History. Click to re-list.")
    )

    init {
        for (job in jobHistory) {
            if (inventory.firstEmpty() != -1) {
                inventory.addItem(job.getIconItem())
            } else {
                break
            }
        }
    }

    /**
     * Returns the inventory associated with this holder.
     * @return The inventory displaying job history.
     */
    override fun getInventory(): Inventory {
        return inventory
    }

    /**
     * Handles a click on a job item, re-listing the associated job if found.
     * @param clickedItem The item that was clicked.
     * @param player The player who clicked the item.
     */
    fun clickedItem(clickedItem: ItemStack, player: Player) {
        val meta = clickedItem.itemMeta
        val uuid = meta.persistentDataContainer.get(SneakyJobBoard.getJobManager().IDKEY, PersistentDataType.STRING)

        if (uuid.isNullOrEmpty()) return

        player.closeInventory()

        val job = jobHistory.find { it.uuid == uuid }

        if (job != null) {
            SneakyJobBoard.getJobManager().list(job)
            Bukkit.getServer().dispatchCommand(
                Bukkit.getServer().consoleSender,
                "cast forcecast ${player.name} jobboard-history-listing ${job.durationMillis}"
            )
        }
    }
}

/** Listens for inventory interactions related to job history. */
class JobHistoryInventoryListener : Listener {

    /**
     * Handles clicks in the job history inventory.
     * @param event The inventory click event.
     */
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val clickedInventory = event.clickedInventory ?: return

        val topInventoryHolder = event.view.topInventory.holder
        if (topInventoryHolder is JobHistoryInventoryHolder) {
            event.isCancelled = true
        }

        if (clickedInventory.holder !is JobHistoryInventoryHolder) return

        val clickedItem = event.currentItem ?: return

        when (event.click) {
            ClickType.LEFT -> {
                val holder = clickedInventory.holder as? JobHistoryInventoryHolder ?: return
                val player = event.whoClicked as? Player ?: return
                holder.clickedItem(clickedItem, player)
            }

            else -> {}
        }
    }

    /**
     * Prevents interaction with the job history inventory.
     * @param event The inventory interact event.
     */
    @EventHandler
    fun onInventoryInteract(event: InventoryInteractEvent) {
        val topInventoryHolder = event.view.topInventory.holder
        if (topInventoryHolder is JobHistoryInventoryHolder) {
            event.isCancelled = true
        }
    }
}
