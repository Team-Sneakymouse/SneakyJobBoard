package net.sneakyjobboard.commands

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.advert.Advert
import net.sneakyjobboard.advert.AdvertCategory
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
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
    }

    init {
        this.usageMessage =
            "/${this@CommandListAdvert.name} [advertCategory] (\"name\") (\"description\")"
        this.description = "List an advert to the job board."
    }

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<String>): Boolean {
        val player: Player? = if (sender is Player) sender
        else if (args.isNotEmpty()) Bukkit.getPlayer(args[0]) else null
        val remainingArgs: Array<out String> = if (sender is Player) args else args.drop(1).toTypedArray()

        if (player == null) {
            sender.sendMessage(
                TextUtility.convertToComponent(
                    "&4${args[0]} is not a player name. When running this command from the console, the first arg must be the reporting player."
                )
            )
            return false
        }

        val advertCategories = SneakyJobBoard.getAdvertCategoryManager().getAdvertCategories()
        val category = advertCategories[args[0]]

        val advert = Advert(category, player)

        // Now check if name and description are provided as arguments in the command
        if (remainingArgs.size > 1) {
            val nameAndDesc: List<String> = remainingArgs.drop(1).joinToString(" ").split("\" \"")

            if (nameAndDesc.size == 2 && nameAndDesc[0].startsWith("\"") && nameAndDesc[1].endsWith("\"")) {
                // Remove the quotes and set name and description
                advert.name = nameAndDesc[0].substring(1)
                advert.description = nameAndDesc[1].substring(0, nameAndDesc[1].length - 1)

                // List the job without additional input
                SneakyJobBoard.getAdvertManager().list(advert)

                sender.sendMessage(TextUtility.convertToComponent("&eAdvert listed. Name: &3'${advert.name}'&e, Description: &3'${advert.description}'"))
                return true
            } else {
                sender.sendMessage(TextUtility.convertToComponent("&4Invalid Usage: $usageMessage"))
                return false
            }
        }

        // Otherwise, register chat listener for title and description
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
                    sender.sendMessage(TextUtility.convertToComponent("&aEnter the description for your advert:"))
                } else {
                    advert.description = message
                    unregisterListener(player)
                    SneakyJobBoard.getInstance().advertManager.list(advert)
                    sender.sendMessage(TextUtility.convertToComponent("&aAdvert listed successfully!"))
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
        sender.sendMessage(TextUtility.convertToComponent("&aEnter the title for your advert:"))
        return true
    }
} 