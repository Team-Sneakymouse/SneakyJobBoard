package net.sneakyjobboard.commands

import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.job.Job
import net.sneakyjobboard.util.TextUtility
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CommandUnlistJob : CommandBase("unlistjob") {

    init {
        this.usageMessage = "/${this@CommandUnlistJob.name} (Job Name (admin only))"
        this.description = "Unlist the last job that you listed."
    }

    override fun execute(
            sender: CommandSender,
            commandLabel: String,
            args: Array<out String>
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

    override fun tabComplete(
            sender: CommandSender,
            alias: String,
            args: Array<String>
    ): List<String> {
        if (sender !is Player || !sender.hasPermission("${SneakyJobBoard.IDENTIFIER}.admin")) {
            return emptyList()
        }

        val prefix = args.joinToString(" ").lowercase()
        return SneakyJobBoard.getJobManager()
                .jobs
                .values
                .filter { it.name.lowercase().startsWith(prefix, ignoreCase = true) }
                .map { it.name }
    }
}
