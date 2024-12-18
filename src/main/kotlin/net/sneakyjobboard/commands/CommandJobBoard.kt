package net.sneakyjobboard.commands

import net.sneakyjobboard.jobboard.JobInventoryHolder
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command for opening the job board for players.
 *
 * This command allows players to access the job board interface where they can view available jobs.
 */
class CommandJobBoard : CommandBase("jobboard") {

    init {
        this.usageMessage = "/${this@CommandJobBoard.name}"
        this.description = "Opens the job board."
    }

    /**
     * Executes the command to open the job board for a specified player.
     *
     * @param sender The entity that sent the command.
     * @param commandLabel The label used to invoke the command.
     * @param args The arguments provided with the command.
     * @return True if the command was executed successfully, false otherwise.
     */
    override fun execute(
        sender: CommandSender, commandLabel: String, args: Array<out String>
    ): Boolean {
        val player: Player? = if (sender is Player) sender
        else if (args.isNotEmpty()) Bukkit.getPlayer(args[0]) else null

        if (player == null) {
            sender.sendMessage(
                TextUtility.convertToComponent(
                    "&4${args[0]} is not a player name. When running this command from the console, the first arg must be the reporting player."
                )
            )
            return false
        }
        JobInventoryHolder.openJobBoard(player, false)

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
