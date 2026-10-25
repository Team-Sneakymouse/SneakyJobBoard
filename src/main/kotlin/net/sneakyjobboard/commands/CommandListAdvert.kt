package net.sneakyjobboard.commands

import net.sneakyjobboard.dialog.TextInputDialogs
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.advert.Advert
import net.sneakyjobboard.advert.AdvertCategory
import net.sneakyjobboard.advert.AdvertIconSelector
import com.sneakyutils.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command for creating and listing advertisements.
 * Handles both direct command listing and interactive advertisement creation through a dialog.
 */
class CommandListAdvert : CommandBase("listadvert") {

    companion object {
        /**
         * Starts the advertisement creation process after icon selection.
         * Guides the player through entering title and description.
         *
         * @param player The player creating the advertisement
         * @param iconMaterial The selected icon material
         * @param iconCustomModelData The selected icon model data
         * @param category Optional category for the advertisement
         */
        fun startAdvertCreation(
            player: Player,
            iconMaterial: Material,
            iconCustomModelData: Int,
            category: AdvertCategory?
        ) {
            val advert = Advert(category, player)
            advert.iconMaterial = iconMaterial
            advert.iconCustomModelData = iconCustomModelData

            TextInputDialogs.createAdvert(player, advert)
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

        // Check if player has reached the maximum number of adverts
        val currentAdverts = SneakyJobBoard.getAdvertManager().getAdvertsForPlayer(player).size
        if (currentAdverts >= 8) {
            sender.sendMessage(TextUtility.convertToComponent("&4You can only have up to 8 active advertisements at a time."))
            return false
        }
/*
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
*/
        TextInputDialogs.cancel(player)
        // Open the icon selector with the selected category (or null)
        AdvertIconSelector.open(player, null)
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
/*
            args.size == startIndex + 1 -> {
                // Get all advert category keys and filter based on input
                val categories = SneakyJobBoard.getAdvertCategoryManager().getAdvertCategories().keys
                categories.filter { it.startsWith(args[startIndex], ignoreCase = true) }.toList()
            }
*/

            else -> emptyList()
        }
    }
} 