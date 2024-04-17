package net.sneakyjobboard.commands

import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.util.TextUtility
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CommandUnlistJob : CommandBase("unlistjob") {

    init {
        this.usageMessage = buildString {
            append("/")
            append(this@CommandUnlistJob.name)
        }
        this.description = "Unlist the last job that you listed."
    }

    override fun execute(
            sender: CommandSender,
            commandLabel: String,
            args: Array<out String>
    ): Boolean {
        // Check if sender is player
        if (sender !is Player) {
            sender.sendMessage(
                    TextUtility.convertToComponent(
                            "&4This command can only be executed by players."
                    )
            )
            return false
        }

        // Find the last job that was listed by this player
        val lastJob = SneakyJobBoard.getJobManager().getLastListedJob(sender)

        if (lastJob == null) {
            sender.sendMessage(TextUtility.convertToComponent("&4You haven't listed any jobs yet."))
            return false
        }

        // Unlist that job
        CommandListJob.unregisterListener(sender)
		lastJob.unlist()
        sender.sendMessage(
                TextUtility.convertToComponent("&aYour job ${lastJob.name} has been unlisted.")
        )

        return true
    }

    override fun tabComplete(
            sender: CommandSender,
            alias: String,
            args: Array<String>
    ): List<String> {
        return emptyList()
    }
}
