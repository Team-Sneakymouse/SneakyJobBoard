package net.sneakyjobboard.commands

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.advert.Advert
import net.sneakyjobboard.advert.AdvertCategory
import net.sneakyjobboard.advert.AdvertIconSelector
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Command for listing adverts to the job board.
 *
 * This command allows players to list an advert with specified details, including category, duration, and tracking options.
 */
class CommandListAdvert : CommandBase("listadvert") {

    companion object {
        private val playerListeners = mutableMapOf<Player, Listener>()

        /** Unregisters the listener associated with a player. */
        fun unregisterListener(player: Player) {
            playerListeners[player]?.let {
                HandlerList.unregisterAll(it)
                playerListeners.remove(player)
            }
        }

        /** Registers a new listener for a player. */
        fun registerListener(player: Player, listener: Listener) {
            unregisterListener(player)
            playerListeners[player] = listener
            Bukkit.getPluginManager().registerEvents(listener, SneakyJobBoard.getInstance())
        }

        /** Starts the advert creation process after an icon is selected. */
        fun startAdvertCreation(player: Player, iconMaterial: Material, iconCustomModelData: Int, category: AdvertCategory?) {
            val advert = Advert(category, player)
            advert.iconMaterial = iconMaterial
            advert.iconCustomModelData = iconCustomModelData

            // Register chat listener for title and description
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
                        player.sendMessage(TextUtility.convertToComponent("&aEnter the description for your advert:"))
                    } else {
                        advert.description = message
                        unregisterListener(player)
                        SneakyJobBoard.getInstance().advertManager.list(advert)
                        player.sendMessage(TextUtility.convertToComponent("&aAdvert listed successfully!"))
                    }
                }

                @EventHandler
                fun onQuit(event: PlayerQuitEvent) {
                    if (event.player == player) {
                        unregisterListener(player)
                    }
                }
            }

            registerListener(player, chatListener)
            player.sendMessage(TextUtility.convertToComponent("&aEnter the title for your advert:"))
        }
    }

    init {
        this.usageMessage = "/${this@CommandListAdvert.name}"
        this.description = "List an advert to the job board."
    }

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<String>): Boolean {
        val startIndex = if (sender is Player) 0 else 1
        val player: Player? = if (sender is Player) sender
        else if (args.isNotEmpty()) Bukkit.getPlayer(args[0]) else null

        if (player == null) {
            sender.sendMessage(
                TextUtility.convertToComponent(
                    if (args.isEmpty()) {
                        "&4When running this command from the console, the first arg must be the reporting player."
                    } else {
                        "&4${args[0]} is not a player name. When running this command from the console, the first arg must be the reporting player."
                    }
                )
            )
            return false
        }

        // Get category from args if provided
        val category = if (args.size > startIndex) {
            val categoryId = args[startIndex]
            val foundCategory = SneakyJobBoard.getAdvertCategoryManager().getCategory(categoryId)
            if (foundCategory == null) {
                sender.sendMessage(TextUtility.convertToComponent("&4Invalid advert category: $categoryId"))
                return false
            }
            foundCategory
        } else null

        // Open the icon selector with the selected category (or null)
        AdvertIconSelector.open(player, category)
        return true
    }

    /**
     * Provides tab completion for the command arguments.
     *
     * @param sender The entity that sent the command.
     * @param alias The alias used to invoke the command.
     * @param args The arguments provided with the command.
     * @return A list of possible completions based on the current input.
     */
    override fun tabComplete(
        sender: CommandSender, alias: String, args: Array<String>
    ): List<String> {
        val startIndex = if (sender is Player) 0 else 1
        return when {
            args.size == 1 && sender !is Player -> {
                Bukkit.getOnlinePlayers().filter { !it.name.equals("CMI-Fake-Operator", ignoreCase = true) }
                    .filter { it.name.startsWith(args.last(), ignoreCase = true) }.map { it.name }
            }
            args.size == startIndex + 1 -> {
                // Get all advert category keys and filter based on input
                val categories = SneakyJobBoard.getAdvertCategoryManager().getAdvertCategories().keys
                categories.filter { it.startsWith(args[startIndex], ignoreCase = true) }.toList()
            }
            else -> emptyList()
        }
    }
} 