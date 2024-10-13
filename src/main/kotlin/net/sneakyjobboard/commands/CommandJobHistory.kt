package net.sneakyjobboard.commands

import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CommandJobHistory : CommandBase("jobhistory") {

    init {
        this.usageMessage = "/${this@CommandJobHistory.name} [durationMillis]"
        this.description = "Opens your recent job history."
    }

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
