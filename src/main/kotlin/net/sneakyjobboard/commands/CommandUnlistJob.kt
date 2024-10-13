package net.sneakyjobboard.commands

import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.job.Job
import net.sneakyjobboard.util.TextUtility
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command for unlisting jobs from the job board.
 *
 * This command allows players to unlist the last job they listed, or an admin to unlist a specified job by name.
 */
class CommandUnlistJob : CommandBase("unlistjob") {

    init {
        this.usageMessage = "/${this@CommandUnlistJob.name} (Job Name (admin only))"
        this.description = "Unlist the last job that you listed."
    }

    /**
     * Executes the command to unlist a job.
     *
     * @param sender The entity that sent the command (should be a player).
     * @param commandLabel The label used to invoke the command.
     * @param args The arguments provided with the command.
     * @return True if the command was executed successfully, false otherwise.
     */
    override fun execute(
        sender: CommandSender, commandLabel: String, args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage(
                TextUtility.convertToComponent(
                    "&4This command can only be executed by players."
                )
            )
            return false
        }

        val job: Job?

        if (sender.hasPermission("${SneakyJobBoard.IDENTIFIER}.admin") && args.isNotEmpty()) {
            val name = args.joinToString(" ")
            job = SneakyJobBoard.getJobManager().getJobByName(name)
        } else {
            job = SneakyJobBoard.getJobManager().getLastListedJob(sender)
            CommandListJob.unregisterListener(sender)
        }

        if (job == null) {
            sender.sendMessage(TextUtility.convertToComponent("&4No listed job found."))
            return false
        }

        job.unlist()
        sender.sendMessage(
            TextUtility.convertToComponent("&aThe job &b'${job.name}' &ahas been unlisted.")
        )

        return true
    }

    /**
     * Provides tab completion for job names when the command is executed by an admin.
     *
     * @param sender The entity that sent the command.
     * @param alias The alias used to invoke the command.
     * @param args The arguments provided with the command.
     * @return A list of job names matching the provided prefix for tab completion.
     */
    override fun tabComplete(
        sender: CommandSender, alias: String, args: Array<String>
    ): List<String> {
        if (sender !is Player || !sender.hasPermission("${SneakyJobBoard.IDENTIFIER}.admin")) {
            return emptyList()
        }

        val prefix = args.joinToString(" ").lowercase()
        return SneakyJobBoard.getJobManager().jobs.values.filter {
            it.name.lowercase().startsWith(prefix, ignoreCase = true)
        }.map { it.name }
    }
}
