package net.sneakyjobboard.jobboard

import com.sneakyutils.util.DisplayOnlyInventoryHolder
import com.sneakyutils.util.TextUtility
import net.sneakyjobboard.JobBoardEffects
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.advert.AdvertBoardInterface
import net.sneakyjobboard.util.ItemModelUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * The job board start screen (`/jobboard` without arguments, or right-clicking a board): one picture
 * (`lom:jobboard` / `jobboard`) whose left half opens the places board and whose right half opens the people
 * (advert) board. It was the MagicSpells `jobboard-menu-default` menu; the click areas below are its option slots.
 */
class JobBoardMenu private constructor() : DisplayOnlyInventoryHolder {

    private val inventory: Inventory = Bukkit.createInventory(
        this, 54, TextUtility.convertToComponent(SneakyJobBoard.getInstance().config.getString("menu-title") ?: "")
    ).apply {
        setItem(0, ItemStack(Material.JIGSAW).apply {
            itemMeta = itemMeta?.also { meta ->
                ItemModelUtility.applyModel(meta, "lom:jobboard", "jobboard")
                meta.setHideTooltip(true)
            }
        })
    }

    override fun getInventory(): Inventory = inventory

    companion object {
        private val PLACES_SLOTS = setOf(0, 5, 6, 7, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35, 41, 42, 43, 44)
        private val PEOPLE_SLOTS =
            setOf(1, 2, 3, 8, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30, 36, 37, 38, 39, 45, 46, 47, 48)
        private const val CLOSE_SLOT = 53

        /** Opens the start screen for [player] and plays `effects.menu-open`. */
        fun open(player: Player) {
            player.openInventory(JobBoardMenu().inventory)
            JobBoardEffects.play(JobBoardEffects.MENU_OPEN, player)
        }

        /** What clicking [slot] does: `places`, `people`, `close`, or null for nothing. */
        internal fun actionFor(slot: Int): String? = when (slot) {
            in PLACES_SLOTS -> "places"
            in PEOPLE_SLOTS -> "people"
            CLOSE_SLOT -> "close"
            else -> null
        }
    }
}

/** Cancels every click and drag in [JobBoardMenu] and runs the clicked area's action. */
class JobBoardMenuListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.topInventory.holder !is JobBoardMenu) return
        event.isCancelled = true
        if (event.clickedInventory != event.view.topInventory) return
        val player = event.whoClicked as? Player ?: return

        when (JobBoardMenu.actionFor(event.slot)) {
            // Open the next screen a tick later, outside the click event.
            "places" -> Bukkit.getScheduler().runTask(SneakyJobBoard.getInstance(), Runnable {
                if (player.isOnline) JobInventoryHolder.openJobBoard(player, false)
            })
            "people" -> Bukkit.getScheduler().runTask(SneakyJobBoard.getInstance(), Runnable {
                if (player.isOnline) AdvertBoardInterface.open(player)
            })
            "close" -> Bukkit.getScheduler().runTask(SneakyJobBoard.getInstance(), Runnable { player.closeInventory() })
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is JobBoardMenu) event.isCancelled = true
    }
}
