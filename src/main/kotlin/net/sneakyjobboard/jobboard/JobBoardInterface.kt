package net.sneakyjobboard.jobboard

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

class JobInventoryHolder : InventoryHolder {
    private var inventory: Inventory

    init {
        val jobs = SneakyJobBoard.getJobManager().getJobs()
        val size = (((jobs.size + 8) / 9) * 9).coerceAtLeast(9).coerceAtMost(54)
        inventory = Bukkit.createInventory(this, size, TextUtility.convertToComponent("&eJob Board"))

        for (job in jobs) {
            if (inventory.firstEmpty() != -1) {
                inventory.addItem(job.getIconItem())
            } else {
                break
            }
        }
    }

    override fun getInventory(): Inventory {
        return inventory
    }

    fun clickedItem(clickedItem: ItemStack, player: Player) {
        val meta = clickedItem.itemMeta
        val uuid = meta.persistentDataContainer.get(SneakyJobBoard.getJobManager().IDKEY, PersistentDataType.STRING)

        if (uuid.isNullOrEmpty()) return

        player.closeInventory()

        SneakyJobBoard.getJobManager().dispatch(uuid, player)
    }
}

class JobInventoryListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val clickedInventory = event.clickedInventory ?: return

        val topInventoryHolder = event.view.topInventory.holder
        if (topInventoryHolder is JobInventoryHolder) {
            event.isCancelled = true
        }

        if (clickedInventory.holder !is JobInventoryHolder) return

        val clickedItem = event.currentItem ?: return

        when (event.click) {
            ClickType.LEFT -> {
                val holder = clickedInventory.holder as? JobInventoryHolder ?: return
                val player = event.whoClicked as? Player ?: return
                holder.clickedItem(clickedItem, player)
            }

            else -> {}
        }
    }

    @EventHandler
    fun onInventoryInteract(event: InventoryInteractEvent) {
        val topInventoryHolder = event.view.topInventory.holder
        if (topInventoryHolder is JobInventoryHolder) {
            event.isCancelled = true
        }
    }
}
