package net.sneakyjobboard.jobboard

import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
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

/** Holds the job inventory for the job board. */
class JobInventoryHolder(isJobBoardInteract: Boolean) : InventoryHolder {
    private var inventory: Inventory

    /**
     * Initializes the job inventory and populates it with job icons.
     */
    init {
        var i = (extraButtons.keys.maxOrNull()?.plus(1)) ?: 0
        val jobs = SneakyJobBoard.getJobManager().getJobs()
        val size = (((jobs.size + i + 8) / 9) * 9).coerceAtLeast(9).coerceAtMost(54)
        inventory = Bukkit.createInventory(this, size, TextUtility.convertToComponent("&eJob Board"))

        extraButtons.forEach { extraButton ->
            if (extraButton.value.showOnBoardInteract || !isJobBoardInteract) inventory.setItem(
                extraButton.key, extraButton.value.itemStack
            )
        }

        for (job in jobs) {
            inventory.setItem(i++, job.getIconItem())
        }
    }

    /**
     * Gets the inventory associated with this holder.
     * @return The inventory for this job board.
     */
    override fun getInventory(): Inventory {
        return inventory
    }

    /**
     * Handles item clicks in the inventory, dispatching the player to the associated job.
     * @param clickedItem The item that was clicked.
     * @param player The player who clicked the item.
     */
    fun clickedItem(clickedItem: ItemStack, player: Player) {
        val meta = clickedItem.itemMeta
        val uuid = meta.persistentDataContainer.get(
            NamespacedKey(SneakyJobBoard.getInstance(), "job_id"), PersistentDataType.STRING
        )
        val commandConsole = meta.persistentDataContainer.get(
            NamespacedKey(SneakyJobBoard.getInstance(), "command_console"), PersistentDataType.STRING
        )

        if (!uuid.isNullOrEmpty()) {
            player.closeInventory()

            SneakyJobBoard.getJobManager().dispatch(uuid, player)
        } else if (!commandConsole.isNullOrEmpty()) {
            Bukkit.getServer()
                .dispatchCommand(Bukkit.getServer().consoleSender, commandConsole.replace("[playerName]", player.name))
        }
    }

    companion object {
        private val extraButtons = mutableMapOf<Int, JobBoardButton>()

        init {
            parseConfig()
        }

        /**
         * Loads job categories from the configuration file.
         * Throws an IllegalStateException if the configuration file is not found.
         */
        private fun parseConfig() {
            try {
                val configFile = SneakyJobBoard.getConfigFile()
                if (!configFile.exists()) {
                    throw IllegalStateException("config.yml not found")
                }

                val config = YamlConfiguration.loadConfiguration(configFile)
                val jobCategoriesSection = config.getConfigurationSection("job-board-extra-buttons") ?: return

                extraButtons.clear()

                for (key in jobCategoriesSection.getKeys(false)) {
                    val slot = jobCategoriesSection.getInt("$key.slot")

                    val name = jobCategoriesSection.getString("$key.name") ?: key
                    val description = jobCategoriesSection.getString("$key.description") ?: key
                    val iconMaterialString = jobCategoriesSection.getString("$key.icon-material") ?: ""
                    val iconCustomModelData = jobCategoriesSection.getInt("$key.icon-custom-model-data")

                    val showOnBoardInteract = jobCategoriesSection.getBoolean("$key.show-on-board-interact", true)

                    val commandConsole = jobCategoriesSection.getString("$key.command-console")

                    val iconMaterial = Material.matchMaterial(iconMaterialString)

                    val itemStack = iconMaterial?.let {
                        ItemStack(it).apply {
                            itemMeta = itemMeta?.also { meta ->
                                meta.itemName(TextUtility.convertToComponent(name))
                                meta.lore(mutableListOf(TextUtility.convertToComponent(description)))
                                meta.setCustomModelData(iconCustomModelData)

                                commandConsole?.let { command ->
                                    meta.persistentDataContainer.set(
                                        NamespacedKey(SneakyJobBoard.getInstance(), "command_console"),
                                        PersistentDataType.STRING,
                                        command
                                    )
                                }
                            }
                        }
                    }

                    extraButtons[slot] = JobBoardButton(
                        itemStack, showOnBoardInteract
                    )
                }
            } catch (e: IllegalStateException) {
                SneakyJobBoard.log("Error: ${e.message}")
            } catch (e: Exception) {
                SneakyJobBoard.log(
                    "An unexpected error occurred while loading job categories: ${e.message}"
                )
            }
        }


        data class JobBoardButton(
            val itemStack: ItemStack?, val showOnBoardInteract: Boolean
        )

        /**
         * Opens the job board inventory UI for the provided player.
         *
         * @param player The player for whom to open the job board inventory.
         * @param isJobBoardInteract Whether the board was opened by a job board interact.
         */
        fun openJobBoard(player: Player, isJobBoardInteract: Boolean) {
            player.openInventory(JobInventoryHolder(isJobBoardInteract).inventory)
        }
    }
}

/** Listener for job inventory interactions. */
class JobInventoryListener : Listener {

    /**
     * Handles inventory click events for the job inventory.
     * @param event The inventory click event.
     */
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

    /**
     * Handles interactions with the job inventory.
     * @param event The inventory interact event.
     */
    @EventHandler
    fun onInventoryInteract(event: InventoryInteractEvent) {
        val topInventoryHolder = event.view.topInventory.holder
        if (topInventoryHolder is JobInventoryHolder) {
            event.isCancelled = true
        }
    }
}
