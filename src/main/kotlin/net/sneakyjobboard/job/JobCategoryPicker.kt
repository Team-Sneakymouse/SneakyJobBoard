package net.sneakyjobboard.job

import com.sneakyutils.util.DisplayOnlyInventoryHolder
import com.sneakyutils.util.TextUtility
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.dialog.TextInputDialogs
import net.sneakyjobboard.util.ItemModelUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * "Choose a Category": the screen players post a job from (`/listjob` without a category, the job board's
 * "Post New Job" button). Clicking a category starts the name/description dialog for a job of that category at the
 * player's location; the history button opens their job history. It was the MagicSpells `item-jobListing-menu-*`
 * menus. Layout and icons come from `job-category-picker` in config.yml; categories without a slot there fill the
 * free slots in config order.
 */
class JobCategoryPicker private constructor(private val player: Player) : DisplayOnlyInventoryHolder {

    private val settings: ConfigurationSection? =
        SneakyJobBoard.getInstance().config.getConfigurationSection("job-category-picker")
    private val slots: Map<Int, String>
    private val historySlot: Int?
    private val inventory: Inventory

    init {
        val categories = SneakyJobBoard.getJobCategoryManager().getJobCategories()
        val configured = settings?.getConfigurationSection("categories")
        historySlot = settings?.getConfigurationSection("history")?.getInt("slot", -1)?.takeIf { it >= 0 }

        val layout = layout(
            categories.keys.toList(),
            configured?.getKeys(false)?.associateWith { configured.getInt("$it.slot", -1) } ?: emptyMap(),
            setOfNotNull(historySlot)
        )
        slots = layout
        val highest = (layout.keys + listOfNotNull(historySlot)).maxOrNull() ?: 0
        inventory = Bukkit.createInventory(
            this, ((highest / 9) + 1).coerceIn(1, 6) * 9,
            TextUtility.convertToComponent(settings?.getString("title") ?: "&aChoose a Category")
        )

        for ((slot, id) in layout) {
            val category = categories[id] ?: continue
            inventory.setItem(slot, ItemStack(category.iconMaterial).apply {
                itemMeta = itemMeta?.also { meta ->
                    val override = configured?.getString("$id.icon-item-model")
                        ?.let { ItemModelUtility.parseItemModel(it, "job-category-picker.categories.$id") }
                    if (override != null) meta.setItemModel(override)
                    else ItemModelUtility.applyModel(meta, category.iconModel, player)
                    applyTooltip(
                        meta, configured?.getConfigurationSection(id), "&6${category.name}", listOf("&7${category.description}"),
                        listOfNotNull(category.durationOverrideMillis?.let { durationLore(it) })
                    )
                }
            })
        }
        historySlot?.let { slot ->
            val history = settings?.getConfigurationSection("history")
            val material = Material.matchMaterial(history?.getString("icon-material") ?: "jigsaw") ?: Material.JIGSAW
            inventory.setItem(slot, ItemStack(material).apply {
                itemMeta = itemMeta?.also { meta ->
                    history?.getString("icon-item-model")
                        ?.let { ItemModelUtility.parseItemModel(it, "job-category-picker.history") }
                        ?.let { meta.setItemModel(it) }
                    applyTooltip(meta, history, "&aJob Listing History", listOf("&7Copy your recent job posting."))
                }
            })
        }
    }

    override fun getInventory(): Inventory = inventory

    /**
     * Name and lore from [entry] (`name`, `lore`), else the defaults. Like MagicSpells' `hide-tooltip`, the parts the
     * material adds itself (attributes and other item flags) are hidden; the name and lore still show.
     */
    private fun applyTooltip(
        meta: ItemMeta,
        entry: ConfigurationSection?,
        defaultName: String,
        defaultLore: List<String>,
        extraLore: List<String> = emptyList()
    ) {
        meta.displayName(TextUtility.convertToComponent(entry?.getString("name") ?: defaultName))
        val lore = (if (entry?.isList("lore") == true) entry.getStringList("lore") else defaultLore) + extraLore
        meta.lore(lore.map { TextUtility.convertToComponent(it) })
        meta.addItemFlags(*ItemFlag.values())
    }

    /** The lore line for a category whose jobs always last [millis] (its `duration-override`). */
    private fun durationLore(millis: Long): String =
        if (millis > DAY_MILLIS) settings?.getString("restart-lore") ?: "&7Until server restart"
        else (settings?.getString("duration-lore") ?: "&7Lasts %duration%").replace("%duration%", humanDuration(millis))

    internal fun click(slot: Int) {
        if (slot == historySlot) {
            val duration = settings?.getLong("history.duration-millis", 1800000L) ?: 1800000L
            Bukkit.getScheduler().runTask(SneakyJobBoard.getInstance(), Runnable {
                if (player.isOnline) SneakyJobBoard.getDatabaseManager().getJobHistory(player, duration)
            })
            return
        }
        val category = slots[slot]?.let { SneakyJobBoard.getJobCategoryManager().getJobCategories()[it] } ?: return
        val job = Job(
            category = category,
            player = player,
            location = player.location,
            durationMillis = settings?.getLong("duration-millis", 5400000L) ?: 5400000L,
            tracking = false,
            persist = false
        )
        // Closes this screen next tick and opens the name/description dialog.
        TextInputDialogs.jobFromCommand(player, job)
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /** [millis] as words, largest units first, seconds dropped once there are hours: "1 hour 30 minutes". */
        internal fun humanDuration(millis: Long): String {
            val totalSeconds = millis / 1000
            val hours = totalSeconds / 3600
            val minutes = totalSeconds % 3600 / 60
            val seconds = totalSeconds % 60
            fun part(n: Long, unit: String) = if (n == 0L) null else "$n $unit${if (n == 1L) "" else "s"}"
            val parts = listOfNotNull(part(hours, "hour"), part(minutes, "minute"), if (hours == 0L) part(seconds, "second") else null)
            return parts.joinToString(" ").ifEmpty { "0 minutes" }
        }

        /**
         * Opens the picker for [player], or tells them to unlist first when they already have a job listed (the
         * MagicSpells menu refused the same way).
         */
        fun open(player: Player) {
            if (SneakyJobBoard.getJobManager().jobs.values.any { it.player == player }) {
                val message = SneakyJobBoard.getInstance().config.getString("job-category-picker.already-listed-message")
                if (!message.isNullOrBlank()) player.sendMessage(TextUtility.convertToComponent(message))
                return
            }
            player.openInventory(JobCategoryPicker(player).inventory)
        }

        /**
         * Slot -> category id. Categories in [configured] with a slot from 0 to 53 take it (first one wins); every
         * other category in [categories] fills the lowest free slot, skipping [reserved].
         */
        internal fun layout(categories: List<String>, configured: Map<String, Int>, reserved: Set<Int>): Map<Int, String> {
            val result = sortedMapOf<Int, String>()
            for (id in categories) {
                val slot = configured[id] ?: continue
                if (slot in 0..53 && slot !in reserved && slot !in result) result[slot] = id
            }
            var free = 0
            for (id in categories) {
                if (id in result.values) continue
                while (free in result || free in reserved) free++
                if (free > 53) break
                result[free] = id
            }
            return result
        }
    }
}

/** Cancels every click and drag in [JobCategoryPicker] and runs the clicked slot. */
class JobCategoryPickerListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val picker = event.view.topInventory.holder as? JobCategoryPicker ?: return
        event.isCancelled = true
        if (event.clickedInventory != event.view.topInventory) return
        picker.click(event.slot)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is JobCategoryPicker) event.isCancelled = true
    }
}
