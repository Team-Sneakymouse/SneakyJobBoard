package net.sneakyjobboard.commands

import net.kyori.adventure.text.minimessage.MiniMessage
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.job.Job
import net.sneakyjobboard.job.JobCategory
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
        private val playerListeners = mutableMapOf<Player, Listener>()

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
        this.usageMessage = "/${this@CommandListJob.name} [jobCategory] [durationMillis] (tracking)"
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

        val durationMillis: Long =
                remainingArgs[1].toLongOrNull()
                        ?: run {
                            sender.sendMessage(
                                    TextUtility.convertToComponent(
                                            "&4Invalid duration value. Please provide a valid number."
                                    )
                            )
                            return false
                        }

        val tracking =
                if (remainingArgs.size > 2) {
                    remainingArgs[2].toBooleanOrNull()
                            ?: run {
                                sender.sendMessage(
                                        TextUtility.convertToComponent(
                                                "&4Invalid boolean value '${remainingArgs[2]}'. Please provide 'true' or 'false'."
                                        )
                                )
                                return false
                            }
                } else {
                    false
                }

        val job =
                Job(
                        category = jobcategory,
                        player = player,
                        durationMillis = durationMillis,
                        tracking = tracking
                )

        player.sendMessage(TextUtility.convertToComponent("&aPlease type the name of the job."))

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
                        .filter { it.name.startsWith(args.last(), ignoreCase = true) }
                        .map { it.name }
            }
            args.size - startIndex == 1 -> {
                SneakyJobBoard.getJobCategoryManager().getJobCategories().keys.toList().filter {
                    it.startsWith(args.last(), ignoreCase = true)
                }
            }
            args.size - startIndex == 3 -> {
                listOf("TRUE", "FALSE").filter { it.startsWith(args.last(), ignoreCase = true) }
            }
            else -> emptyList()
        }
    }
}

class JobNameInputListener(private val sender: Player, private val job: Job) : Listener {

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        if (event.player == sender) {
            val name =
                    MiniMessage.miniMessage()
                            .escapeTags(
                                    TextUtility.replaceFormatCodes(event.message.replace("|", ""))
                            )

            job.name = name
            event.isCancelled = true
            sender.sendMessage(
                    TextUtility.convertToComponent(
                            "&aJob name set to: &b'$name'\n&aNow please type the description of the job."
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
            val description =
                    MiniMessage.miniMessage()
                            .escapeTags(TextUtility.replaceFormatCodes(event.message))

            job.description = description
            event.isCancelled = true

            Bukkit.getScheduler()
                    .runTask(
                            SneakyJobBoard.getInstance(),
                            Runnable {
                                SneakyJobBoard.getJobManager().list(job)

                                sender.sendMessage(
                                        TextUtility.convertToComponent(
                                                "&aJob listed. description set to: &b'$description'"
                                        )
                                )
                            }
                    )

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

fun String?.toBooleanOrNull(): Boolean? {
    return when (this?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}
