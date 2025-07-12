package net.sneakyjobboard.commands

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.TextComponent
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
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.Location

/**
 * Command for listing jobs to the job board.
 * Handles both direct command listing and interactive job creation through chat.
 */
class CommandListJob : CommandBase("listjob") {

    companion object {
        private val playerListeners = mutableMapOf<Player, Listener>()

        /** Unregisters any active chat listener for a player. */
        fun unregisterListener(player: Player) {
            playerListeners[player]?.let {
                HandlerList.unregisterAll(it)
                playerListeners.remove(player)
            }
        }

        /** Registers a new chat listener for a player, removing any existing one. */
        fun registerListener(player: Player, listener: Listener) {
            unregisterListener(player)
            playerListeners[player] = listener
            Bukkit.getPluginManager().registerEvents(listener, SneakyJobBoard.getInstance())
        }
    }

    init {
        this.usageMessage =
            "/${this@CommandListJob.name} [jobCategory] [durationMillis] (tracking) (\"name\") (\"description\")" +
			"\r\nIf the command is run from the console, use one of the following:" +
			"\r\n${this@CommandListJob.name} [playerName] [jobCategory] [durationMillis] (tracking) (\"name\") (\"description\")" +
			"\r\n${this@CommandListJob.name} [jobCategory] [durationMillis] [\"name\"] [\"description\"] [world] [x] [y] [z]"
        this.description = "List a job to the job board."
    }

    /**
     * Executes the command to list a job.
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
        var nextArg: Int = if (sender is Player || player == null) 0 else 1

        if ((player != null && args.size < nextArg + 2) || (player == null && args.size < nextArg + 8)) {
            sender.sendMessage(TextUtility.convertToComponent("&4Invalid Usage: $usageMessage"))
            return false
        }

        val jobcategory: JobCategory? = SneakyJobBoard.getJobCategoryManager().getJobCategories()[args[nextArg]]

        if (jobcategory == null) {
            sender.sendMessage(
                TextUtility.convertToComponent(
                    "&4${args[nextArg]} is not a valid job category!"
                )
            )
            return false
        }

        val durationMillis: Long = args[++nextArg].toLongOrNull() ?: run {
            sender.sendMessage(
                TextUtility.convertToComponent(
                    "&4${args[nextArg]} is not a valid duration value. Please provide a valid number."
                )
            )
            return false
        }

        val tracking = if (player != null && args.size > nextArg + 1) {
            args[++nextArg].toBooleanOrNull() ?: run {
                sender.sendMessage(
                    TextUtility.convertToComponent(
                        "&4Invalid boolean value '${args[nextArg]}'. Please provide 'true' or 'false'."
                    )
                )
                return false
            }
        } else {
            false
        }

		val location = if (player != null) player.location else {
            val world = Bukkit.getWorld(args[args.size - 4]) ?: run {
                sender.sendMessage(
                    TextUtility.convertToComponent(
                        "&4Invalid world name '${args[args.size - 4]}'. Please provide a valid world name."
                    )
                )
                return false
            }

            // Parse and validate coordinates
            val x = args[args.size - 3].toDoubleOrNull() ?: run {
                sender.sendMessage(
                    TextUtility.convertToComponent(
                        "&4Invalid x coordinate '${args[args.size - 3]}'. Please provide a valid number."
                    )
                )
                return false
            }

            val y = args[args.size - 2].toDoubleOrNull() ?: run {
                sender.sendMessage(
                    TextUtility.convertToComponent(
                        "&4Invalid y coordinate '${args[args.size - 2]}'. Please provide a valid number."
                    )
                )
                return false
            }

            val z = args[args.size - 1].toDoubleOrNull() ?: run {
                sender.sendMessage(
                    TextUtility.convertToComponent(
                        "&4Invalid z coordinate '${args[args.size - 1]}'. Please provide a valid number."
                    )
                )
                return false
            }

            // Create location with validated values
            Location(world, x, y, z)
        }

        val job = Job(
            category = jobcategory, player = player, location = location, durationMillis = durationMillis, tracking = tracking
        )

        // Now check if name and description are provided as arguments in the command
        if (args.size > nextArg + 1) {
			val nameAndDescArray = if (player == null) args.drop(++nextArg).dropLast(4) else args.drop(++nextArg)
            val nameAndDesc: List<String> = nameAndDescArray.joinToString(" ").split("\" \"")

			nameAndDesc.forEach {
				sender.sendMessage(TextUtility.convertToComponent(it))
			}

            if (nameAndDesc.size == 2 && nameAndDesc[0].startsWith("\"") && nameAndDesc[1].endsWith("\"")) {
                // Remove the quotes and set name and description
                job.name = nameAndDesc[0].substring(1)
                job.description = nameAndDesc[1].substring(0, nameAndDesc[1].length - 1)

                // List the job without additional input
                SneakyJobBoard.getJobManager().list(job)

                sender.sendMessage(TextUtility.convertToComponent("&eJob listed. Name: &3'${job.name}'&e, Description: &3'${job.description}'"))
                return true
            } else {
                sender.sendMessage(TextUtility.convertToComponent("&4Invalid Usage: $usageMessage"))
                return false
            }
        } else if (player != null) {
            // If the name and description are not passed, prompt for them
            player.sendMessage(TextUtility.convertToComponent("&ePlease type the name of the job."))

            registerListener(player, JobNameInputListener(player, job))
        }

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
        val startIndex: Int = if (sender is Player) 0 else 1

        return when {
            args.size == 1 && sender !is Player -> {
                Bukkit.getOnlinePlayers().filter { !it.name.equals("CMI-Fake-Operator", ignoreCase = true) }
                    .filter { it.name.startsWith(args.last(), ignoreCase = true) }.map { it.name }
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

/**
 * Listener for receiving job name input from a player.
 * Handles chat events during the job creation process.
 *
 * @property sender The player creating the job
 * @property job The job being created
 */
class JobNameInputListener(private val sender: Player, private val job: Job) : Listener {

    /**
     * Handles chat events for the job name input.
     *
     * @param event The chat event triggered when a player sends a message.
     */
    @EventHandler
    fun onPlayerChat(event: AsyncChatEvent) {
        if (event.player == sender) {
            val name = MiniMessage.miniMessage().escapeTags(
                TextUtility.replaceFormatCodes((event.message() as TextComponent).content().replace("|", ""))
            )

            job.name = name
            event.isCancelled = true
            sender.sendMessage(
                TextUtility.convertToComponent(
                    "&eJob name set to: &3'$name'\n&eNow please type the description of the job."
                )
            )

            CommandListJob.registerListener(sender, JobDescriptionInputListener(sender, job))
        }
    }

    /**
     * Handles player quit events to unregister the listener.
     *
     * @param event The event triggered when a player quits.
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (event.player == sender) {
            unregisterListener()
        }
    }

    /** Unregisters this listener */
    private fun unregisterListener() {
        CommandListJob.unregisterListener(sender)
    }
}

/**
 * Listener for receiving job description input from a player.
 * Handles chat events during the job creation process.
 *
 * @property sender The player creating the job
 * @property job The job being created
 */
class JobDescriptionInputListener(private val sender: Player, private val job: Job) : Listener {

    /**
     * Handles chat events for the job description input.
     *
     * @param event The chat event triggered when a player sends a message.
     */
    @EventHandler
    fun onPlayerChat(event: AsyncChatEvent) {
        if (event.player == sender) {
            val description = MiniMessage.miniMessage()
                .escapeTags(TextUtility.replaceFormatCodes((event.message() as TextComponent).content()))

            job.description = description
            event.isCancelled = true

            Bukkit.getScheduler().runTask(SneakyJobBoard.getInstance(), Runnable {
                SneakyJobBoard.getJobManager().list(job)

                sender.sendMessage(
                    TextUtility.convertToComponent(
                        "&eJob listed. description set to: &3'$description'&e."
                    )
                )
            })

            unregisterListener()
        }
    }

    /**
     * Handles player quit events to unregister the listener.
     *
     * @param event The event triggered when a player quits.
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (event.player == sender) {
            unregisterListener()
        }
    }

    /** Unregisters this listener */
    private fun unregisterListener() {
        CommandListJob.unregisterListener(sender)
    }
}

/**
 * Extension function to convert a String to a Boolean or null.
 */
fun String?.toBooleanOrNull(): Boolean? {
    return when (this?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}
