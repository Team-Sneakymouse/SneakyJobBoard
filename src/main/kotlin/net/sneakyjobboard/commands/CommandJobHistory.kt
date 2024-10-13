package net.sneakyjobboard.commands

import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command for accessing a player's job history.
 *
 * This command allows players to view their recent job history, specified by a duration.
 */
class CommandJobHistory : CommandBase("jobhistory") {

    init {
        this.usageMessage = "/${this@CommandJobHistory.name} [durationMillis]"
        this.description = "Opens your recent job history."
    }

    /**
     * Executes the command to retrieve a player's job history.
     *
     * @param sender The entity that sent the command.
     * @param commandLabel The label used to invoke the command.
     * @param args The arguments provided with the command.
     * @return True if the command was executed successfully, false otherwise.
     */
    override fun execute(
        sender: CommandSender, commandLabel: String, args: Array<out String>
    ): Boolean {
        val pocketbaseUrl = SneakyJobBoard.getInstance().getConfig().getString("pocketbase-url")

        if (pocketbaseUrl.isNullOrEmpty()) {
            sender.sendMessage(
                TextUtility.convertToComponent(
                    "&4Job history cannot be viewed if Pocketbase is not set up."
                )
            )
            return false
        }

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

        if (remainingArgs.isEmpty()) {
            sender.sendMessage(TextUtility.convertToComponent("&4Invalid Usage: $usageMessage"))
            return false
        }

        val durationMillis: Long = remainingArgs[0].toLongOrNull() ?: run {
            sender.sendMessage(
                TextUtility.convertToComponent(
                    "&4Invalid duration value. Please provide a valid number."
                )
            )
            return false
        }

        SneakyJobBoard.getPocketbaseManager().getJobHistory(player, durationMillis)

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
        return when {
            args.size == 1 && sender !is Player -> {
                Bukkit.getOnlinePlayers().filter { !it.name.equals("CMI-Fake-Operator", ignoreCase = true) }
                    .filter { it.name.startsWith(args[0], ignoreCase = true) }.map { it.name }
            }

            else -> emptyList()
        }
    }
}
