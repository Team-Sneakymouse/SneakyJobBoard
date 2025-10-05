package net.sneakyjobboard.job

import me.clip.placeholderapi.PlaceholderAPI
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.jobboard.JobBoard
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.bukkit.Location
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*
import kotlin.math.floor

/**
 * Manages the lifecycle and operations of jobs in the system.
 * Handles job listing, unlisting, dispatching, and cleanup operations.
 */
class JobManager {

    val IDKEY: NamespacedKey = NamespacedKey(SneakyJobBoard.getInstance(), "id")

    val jobs = mutableMapOf<String, Job>()
    val pendingSpawns = mutableMapOf<JobBoard, MutableList<Job>>()

    /**
     * Lists a new job in the system, spawning its display entities and scheduling cleanup.
     * Also integrates with Dynmap if available.
     *
     * @param job The job to be listed
     */
    fun list(job: Job) {
        job.startTime = System.currentTimeMillis()
        if (job.player != null) SneakyJobBoard.getPocketbaseManager().listJob(job)
        jobs[job.uuid] = job

        // Spawn item displays
        for (jobBoard in SneakyJobBoard.getJobBoardManager().jobBoards) {
            if (jobBoard.mapLocation.chunk.isLoaded) {
                jobBoard.spawnIcons(job)
            } else {
                val list = pendingSpawns.getOrDefault(jobBoard, mutableListOf())
                list.add(job)
                pendingSpawns[jobBoard] = list
            }
        }

        // Add dynmap icon
        if (SneakyJobBoard.isDynmapActive()) {
            val jobLocation = job.location
            val markerAPI = SneakyJobBoard.getInstance().markerAPI

            val markerSet = markerAPI?.getMarkerSet("SneakyJobBoard") ?: run {
                markerAPI?.createMarkerSet(
                    "SneakyJobBoard", "SneakyJobBoard", null, false
                ) ?: run {
                    SneakyJobBoard.log("Failed to create a new marker set.")
                    null
                }
            }

            val icon = markerAPI?.getMarkerIcon(job.category.dynmapMapIcon)

            val marker = markerSet?.createMarker(
                job.uuid, job.name, jobLocation.world.name, jobLocation.x, jobLocation.y, jobLocation.z, icon, false
            )

            if (marker == null) {
                SneakyJobBoard.log("Failed to create marker")
            }
        }

        // Play toast on all players
		if (job.player != null) {
			var displayStringLocation =
				(SneakyJobBoard.getInstance().getConfig().getString("pocketbase-location") ?: "[x],[y],[z]").replace(
					"[x]", job.location.blockX.toString()
				).replace("[y]", job.location.blockY.toString()).replace("[z]", job.location.blockZ.toString())

			if (SneakyJobBoard.isPapiActive()) {
				displayStringLocation =
					PlaceholderAPI.setPlaceholders(job.player, displayStringLocation).replace("none", "Moonwell Pass")
			}

			for (player in job.location.world.players) {
				Bukkit.getServer().dispatchCommand(
					Bukkit.getServer().consoleSender,
					"cast forcecast ${player.name} jobboard-listed ${
						job.category.name.replace(
							" ",
							"\u00A0"
						)
					} ${
						displayStringLocation.replace(
							" ",
							"\u00A0"
						)
					} ${job.category.iconMaterial} ${job.category.iconCustomModelData}"
					)
            }
        }

        // Schedule unlisting
        Bukkit.getScheduler().runTaskLater(
            SneakyJobBoard.getInstance(), Runnable { job.unlist("expired") }, 20 * job.durationMillis / 1000
        )
    }

    /**
     * Gets the collection of currently listed jobs.
     * @return A mutable collection of active jobs
     */
    fun getJobs(): MutableCollection<Job> {
        return jobs.values
    }

    /**
     * Retrieves the last job listed by a specific player.
     * @param player The player whose last job to find
     * @return The player's most recently listed job, or null if none exists
     */
    fun getLastListedJob(player: Player): Job? {
        for (job in jobs.values.reversed()) {
            if (job.player == player) {
                return job
            }
        }
        return null
    }

    /**
     * Finds a listed job by its name.
     * @param name The name of the job to find (case-insensitive)
     * @return The matching job, or null if not found
     */
    fun getJobByName(name: String): Job? {
        return jobs.values.find { it.name.equals(name, ignoreCase = true) }
    }

    /**
     * Cleans up all listed jobs and their associated entities.
     * Called during plugin shutdown or reload.
     */
    fun cleanup() {
        val jobIdsToRemove = jobs.values.toList()
        jobIdsToRemove.forEach { it.unlist("restart") }

        // Clean up dynmap markers
        if (SneakyJobBoard.isDynmapActive()) {
            val markerAPI = SneakyJobBoard.getInstance().markerAPI

            val markerSet = markerAPI?.getMarkerSet("SneakyJobBoard") ?: run {
                markerAPI?.createMarkerSet(
                    "SneakyJobBoard", "SneakyJobBoard", null, false
                ) ?: run {
                    SneakyJobBoard.log("Failed to create a new marker set.")
                    null
                }
            }

            markerSet?.deleteMarkerSet()
        }
    }

    /**
     * Dispatches a player to a job's location.
     * Handles both online and offline job posters.
     *
     * @param uuid The UUID of the job to dispatch to
     * @param player The player to dispatch
     */
    fun dispatch(uuid: String, player: Player) {
        val job = jobs[uuid] ?: return

		if (job.player != null) {
			if (job.player.isOnline == true) {
				Bukkit.getServer().dispatchCommand(
					Bukkit.getServer().consoleSender,
					"cast forcecast ${player.name} jobboard-dispatch-self ${floor(job.location.x)} ${floor(job.location.y)} ${
						floor(job.location.z)
					}"
				)
			} else {
				Bukkit.getServer().dispatchCommand(
					Bukkit.getServer().consoleSender,
					"cast forcecast ${player.name} jobboard-dispatch-self-offline ${floor(job.location.x)} ${floor(job.location.y)} ${
						floor(job.location.z)
					}"
				)
			}

			Bukkit.getServer().dispatchCommand(
                Bukkit.getServer().consoleSender,
                "cast forcecast ${job.player.name} jobboard-dispatch-other ${player.name} ${job.category.iconMaterial} ${job.category.iconCustomModelData}"
            )
		} else {
			Bukkit.getServer().dispatchCommand(
				Bukkit.getServer().consoleSender,
				"cast forcecast ${player.name} jobboard-dispatch-self ${floor(job.location.x)} ${floor(job.location.y)} ${
					floor(job.location.z)
				}"
			)
		}
    }
}

/**
 * Represents a job listing in the system.
 *
 * @property category The category this job belongs to
 * @property player The player who created the job
 * @property durationMillis How long the job should remain listed (in milliseconds)
 * @property tracking Whether the job's location should track the player's movement
 */
data class Job(
    val category: JobCategory, 
    val player: Player?,
	var location: Location,
    val durationMillis: Long, 
    val tracking: Boolean
) {
    val uuid = UUID.randomUUID().toString()
    var recordID = ""
    var startTime = 0L
    val itemDisplays = mutableMapOf<JobBoard, ItemDisplay>()
    val textDisplays = mutableMapOf<JobBoard, TextDisplay>()

    /**
     * The name of the job. Setting this value updates all display entities
     * and Dynmap markers if enabled.
     */
    var name: String = category.name
        set(value) {
            field = value
            updateTextDisplays()
        }

    /**
     * The description of the job. Setting this value updates all display entities.
     */
    var description: String = category.description
        set(value) {
            field = value
            updateTextDisplays()
        }
    private val posterString =
		if (player == null) "&eFrom: &6The Grand Paladin Order"
		else if (SneakyJobBoard.isPapiActive()) PlaceholderAPI.setPlaceholders(player, SneakyJobBoard.getInstance().getConfig().getString("poster-string") ?: "&eFrom: &b[playerName]").replace("[playerName]", player?.name ?: "Moonwell Pass")
		else (SneakyJobBoard.getInstance().getConfig().getString("poster-string") ?: "&eFrom: &b[playerName]").replace("[playerName]", player?.name ?: "Moonwell Pass")

    /**
     * Gets the remaining duration of this job in milliseconds.
     * @return The time remaining before the job expires
     */
    private fun remainingDurationMillis(): Long {
        return ((startTime + durationMillis) - System.currentTimeMillis())
    }

    /**
     * Checks if this job has expired.
     * @return true if the job's duration has elapsed, false otherwise
     */
    fun isExpired(): Boolean {
        return remainingDurationMillis() < 0L
    }

    /**
     * Unlists this job from all platforms and cleans up associated entities.
     * @param endReason The reason for unlisting ("expired", "unlisted", "deleted", or "restart")
     */
    fun unlist(endReason: String) {
        if (!SneakyJobBoard.getJobManager().jobs.values.contains(this)) return

        if (player != null) SneakyJobBoard.getPocketbaseManager().unlistJob(this, endReason)
        itemDisplays.values.forEach { entity -> entity.remove() }
        textDisplays.values.forEach { entity -> entity.remove() }
        SneakyJobBoard.getJobManager().jobs.remove(uuid)

        if (SneakyJobBoard.isDynmapActive()) {
            val markerAPI = SneakyJobBoard.getInstance().markerAPI

            val markerSet = markerAPI?.getMarkerSet("SneakyJobBoard") ?: run {
                markerAPI?.createMarkerSet(
                    "SneakyJobBoard", "SneakyJobBoard", null, false
                ) ?: run {
                    SneakyJobBoard.log("Failed to create a new marker set.")
                    null
                }
            }

            markerSet?.findMarker(uuid)?.deleteMarker()
        }
    }

    /**
     * Updates all text display entities associated with this job.
     * Handles formatting and positioning of the display text.
     */
    fun updateTextDisplays() {
        for (textDisplayEntity in textDisplays.values) {
            val text: MutableList<String> = mutableListOf("&a${name}")

            for (line in TextUtility.splitIntoLines(description, 30)) {
                text.add("&e$line")
            }

            var posterString = (SneakyJobBoard.getInstance().getConfig().getString("poster-string")
                ?: "&eFrom: &b[playerName]").replace(
                "[playerName]", player?.name ?: "Moonwell Pass"
            )

            if (SneakyJobBoard.isPapiActive()) {
                posterString = PlaceholderAPI.setPlaceholders(player, posterString)
            }

            text.add(posterString)

            textDisplayEntity.text(TextUtility.convertToComponent(text.joinToString("\n")))
            textDisplayEntity.transformation = Transformation(
                Vector3f(0F, 0.3F, 0.025F + (0.025F * text.size)),
                Quaternionf(-1F, 0F, 0F, 1F),
                Vector3f(0.1F, 0.1F, 0.1F),
                Quaternionf(0F, 0F, 0F, 1F)
            )
        }
    }

    /**
     * Creates an ItemStack representing this job for inventory displays.
     * @return The item representing this job
     */
    fun getIconItem(): ItemStack {
        val itemStack = ItemStack(category.iconMaterial)
        val customModelData: Int = category.iconCustomModelData

        val meta = itemStack.itemMeta

        // Set custom model data, display name, and lore.
        meta.setCustomModelData(customModelData)
        meta.displayName(TextUtility.convertToComponent("&a${name}"))

        val lore = mutableListOf<String>()

        // Split the description into lines of a maximum length
        val descriptionLines = TextUtility.splitIntoLines(description, 30)

        // Add each line of the description to the lore
        for (line in descriptionLines) {
            lore.add("&e$line")
        }

        lore.add(posterString)

        meta.lore(lore.map { TextUtility.convertToComponent(it) })

        // Set persistent data.
        val persistentData = meta.persistentDataContainer
        persistentData.set(NamespacedKey(SneakyJobBoard.getInstance(), "job_id"), PersistentDataType.STRING, uuid)

        meta.setEnchantmentGlintOverride(true)

        itemStack.itemMeta = meta
        return itemStack
    }

    /**
     * Gets the transformation to apply to this job's display entities.
     * Handles duration-based scaling if enabled in config.
     * @return The transformation to apply
     */
    fun getTransformation(): Transformation {
        val config = SneakyJobBoard.getInstance().getConfig()

        val baseDuration = config.getLong("duration-scale-base-duration")

        if (baseDuration > 0) {
            val minScale = config.getDouble("duration-scale-scale-min").toFloat()
            val maxScale = config.getDouble("duration-scale-scale-max").toFloat()

            for (job in SneakyJobBoard.getJobManager().getJobs()) {
                val remainingDuration = job.remainingDurationMillis()
                val scaleFactor = (remainingDuration.toFloat() / baseDuration).coerceIn(minScale, maxScale)

                val oldTransformation = job.category.transformation
                val newScale = Vector3f(
                    scaleFactor * oldTransformation.scale.x(),
                    scaleFactor * oldTransformation.scale.y(),
                    scaleFactor * oldTransformation.scale.z()
                )

                return Transformation(
                    oldTransformation.translation,
                    oldTransformation.leftRotation,
                    newScale,
                    oldTransformation.rightRotation
                )
            }
        }

        return category.transformation
    }
}
