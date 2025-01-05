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

/**
 * Holds and manages the job board inventory.
 *
 * This class implements an InventoryHolder to represent the job board, allowing players to interact with job listings
 * and special buttons defined in the configuration.
 */
class JobInventoryHolder(isJobBoardInteract: Boolean) : InventoryHolder {
    private var inventory: Inventory

    /**
     * Initializes the job inventory and populates it with job icons and extra buttons.
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
     * Returns the inventory associated with this holder.
     *
     * @return The Inventory object representing the job board.
     */
    override fun getInventory(): Inventory {
        return inventory
    }

    /**
     * Processes an item click within the job board inventory.
     *
     * @param clickedItem The ItemStack that was clicked by the player.
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
         * Parses the job board extra buttons configuration from the configuration file.
         *
         * This method loads additional buttons from the configuration, which are shown on the job board at specific slots.
         * If the configuration file is not found, an IllegalStateException is thrown.
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
                    val iconMaterialString = jobCategoriesSection.getString("$key.material") ?: ""
                    val iconCustomModelData = jobCategoriesSection.getInt("$key.custom-model-data")

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

        /**
         * Represents a job board button with its associated item and display properties.
         *
         * @property itemStack The ItemStack representing the button.
         * @property showOnBoardInteract Whether the button is shown when interacting with the job board.
         */
        data class JobBoardButton(
            val itemStack: ItemStack?, val showOnBoardInteract: Boolean
        )

        /**
         * Opens the job board inventory UI for a specific player.
         *
         * @param player The player to show the job board to.
         * @param isJobBoardInteract Indicates if the board was opened by interacting with a job board.
         */
        fun openJobBoard(player: Player, isJobBoardInteract: Boolean) {
            player.openInventory(JobInventoryHolder(isJobBoardInteract).inventory)
        }
    }
}

/**
 * Listener class for handling interactions with the job board inventory.
 */
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
