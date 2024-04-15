package net.sneakyjobboard.commands

import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.jobcategory.Job
import net.sneakyjobboard.jobcategory.JobCategory
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerQuitEvent

class CommandListJob : CommandBase("listjob") {

    companion object {
        private val playerListeners: MutableMap<Player, Listener> = mutableMapOf()

        fun unregisterListener(player: Player) {
            playerListeners[player]?.let {
                HandlerList.unregisterAll(it)
                playerListeners.remove(player)
            }
        }

        fun registerListener(player: Player, listener: Listener) {
            unregisterListener(player)
            playerListeners[player] = listener
            Bukkit.getPluginManager().registerEvents(listener, SneakyJobBoard.getInstance())
        }
    }

    init {
        this.usageMessage = buildString {
            append("/")
            append(this@CommandListJob.name)
            append(" [jobCategory] [durationMilis]")
        }
        this.description = "List a job to the job board."
    }

    override fun execute(
            sender: CommandSender,
            commandLabel: String,
            args: Array<out String>
    ): Boolean {
        val player: Player? =
                if (sender is Player) sender
                else if (args.isNotEmpty()) Bukkit.getPlayer(args[0]) else null
        val remainingArgs: Array<out String> =
                if (sender is Player) args else args.drop(1).toTypedArray()

        if (player == null) {
            sender.sendMessage(
                    TextUtility.convertToComponent(
                            "&4${args[0]} is not a player name. When running this command from the console, the first arg must be the reporting player."
                    )
            )
            return false
        }

        if (remainingArgs.size < 2) {
            sender.sendMessage(TextUtility.convertToComponent("&4Invalid Usage: $usageMessage"))
            return false
        }

        val jobcategory: JobCategory? =
                SneakyJobBoard.getJobCategoryManager().getJobCategories().get(remainingArgs[0])

        if (jobcategory == null) {
            sender.sendMessage(
                    TextUtility.convertToComponent(
                            "&4${remainingArgs[0]} is not a valid job category!"
                    )
            )
            return false
        }

        val durationMilis: Long =
                remainingArgs[1].toLongOrNull()
                        ?: run {
                            sender.sendMessage(
                                    TextUtility.convertToComponent(
                                            "&4Invalid duration value. Please provide a valid number."
                                    )
                            )
                            return false
                        }

        val job = Job(category = jobcategory, player = player, durationMilis = durationMilis)

        SneakyJobBoard.getJobManager().list(job)
        sender.sendMessage(
                TextUtility.convertToComponent(
                        "&aYour job has been listed. Please type the name of the job."
                )
        )

        CommandListJob.registerListener(player, JobNameInputListener(player, job))

        return true
    }

    override fun tabComplete(
            sender: CommandSender,
            alias: String,
            args: Array<String>
    ): List<String> {
        var startIndex: Int = if (sender is Player) 0 else 1

        return when {
            args.size == 1 && sender !is Player -> {
                Bukkit.getOnlinePlayers()
                        .filter { !it.name.equals("CMI-Fake-Operator", ignoreCase = true) }
                        .filter { it.name.startsWith(args[0], ignoreCase = true) }
                        .map { it.name }
            }
            args.size - startIndex == 1 -> {
                SneakyJobBoard.getJobCategoryManager().getJobCategories().keys.toList()
            }
            else -> emptyList()
        }
    }
}

class JobNameInputListener(private val sender: Player, private val job: Job) : Listener {

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        if (event.player == sender) {
            job.name = event.message
            event.isCancelled = true
            sender.sendMessage(
                    TextUtility.convertToComponent(
                            "&aJob name set to: ${event.message}\nNow please type the description of the job."
                    )
            )

            CommandListJob.registerListener(sender, JobDescriptionInputListener(sender, job))
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (event.player == sender) {
            unregisterListener()
        }
    }

    private fun unregisterListener() {
        CommandListJob.unregisterListener(sender)
    }
}

class JobDescriptionInputListener(private val sender: Player, private val job: Job) : Listener {

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        if (event.player == sender) {
            job.description = event.message
            event.isCancelled = true
            sender.sendMessage(
                    TextUtility.convertToComponent("&aJob description set to: ${event.message}")
            )
            // Unregister this listener after receiving the input
            unregisterListener()
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (event.player == sender) {
            unregisterListener()
        }
    }

    private fun unregisterListener() {
        CommandListJob.unregisterListener(sender)
    }
}
