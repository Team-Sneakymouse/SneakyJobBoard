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
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*
import kotlin.math.floor

/** Manages listed jobs and dispatching. */
class JobManager {

    val IDKEY: NamespacedKey = NamespacedKey(SneakyJobBoard.getInstance(), "id")

    val jobs = mutableMapOf<String, Job>()
    val pendingSpawns = mutableMapOf<JobBoard, MutableList<Job>>()

    /**
     * Lists a new job, spawning its display and scheduling unlisting.
     * @param job The job to be listed.
     */
    fun list(job: Job) {
        job.startTime = System.currentTimeMillis()
        SneakyJobBoard.getPocketbaseManager().listJob(job)
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
        var displayStringLocation =
            (SneakyJobBoard.getInstance().getConfig().getString("pocketbase-location") ?: "[x],[y],[z]").replace(
                "[x]", job.location.blockX.toString()
            ).replace("[y]", job.location.blockY.toString()).replace("[z]", job.location.blockZ.toString())

        if (SneakyJobBoard.isPapiActive()) {
            displayStringLocation =
                PlaceholderAPI.setPlaceholders(job.player, displayStringLocation).replace("none", "Dinky Dank")
        }

        for (player in job.location.world.players) {
            Bukkit.getServer().dispatchCommand(
                Bukkit.getServer().consoleSender,
                "cast forcecast " + player.name + " jobboard-listed " + job.category.name.replace(
                    " ", "\u00A0"
                ) + " " + displayStringLocation.replace(
                    " ", "\u00A0"
                ) + " " + job.getIconItem().type + " " + (job.getIconItem().itemMeta?.customModelData?.toString()
                    ?: "0")
            )
        }

        // Schedule unlisting
        Bukkit.getScheduler().runTaskLater(
            SneakyJobBoard.getInstance(), Runnable { job.unlist("expired") }, 20 * job.durationMillis / 1000
        )
    }

    /**
     * Gets the collection of currently listed jobs.
     * @return A mutable collection of jobs.
     */
    fun getJobs(): MutableCollection<Job> {
        return jobs.values
    }

    /**
     * Retrieves the last job that was listed by the specified player.
     * @param player The player whose last listed job is to be retrieved.
     * @return The last job listed by the player, or null if none exists.
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
     * Gets a listed job by its name, ignoring case.
     * @param name The name of the job.
     * @return The job matching the specified name, or null if not found.
     */
    fun getJobByName(name: String): Job? {
        return jobs.values.find { it.name.equals(name, ignoreCase = true) }
    }

    /** Cleans up all listed jobs, including removing associated markers. */
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
     * Dispatches a player to the specified job.
     * @param uuid The UUID of the job to dispatch to.
     * @param pl The player to be dispatched.
     */
    fun dispatch(uuid: String, pl: Player) {
        val job = jobs[uuid] ?: return

        if (job.player.isOnline) {
            Bukkit.getServer().dispatchCommand(
                Bukkit.getServer().consoleSender,
                "cast forcecast ${pl.name} jobboard-dispatch-self ${floor(job.location.x)} ${floor(job.location.y)} ${
                    floor(job.location.z)
                }"
            )
            Bukkit.getServer().dispatchCommand(
                Bukkit.getServer().consoleSender, "cast forcecast ${job.player.name} jobboard-dispatch-other ${pl.name}"
            )
        } else {
            Bukkit.getServer().dispatchCommand(
                Bukkit.getServer().consoleSender,
                "cast forcecast ${pl.name} jobboard-dispatch-self-offline ${floor(job.location.x)} ${floor(job.location.y)} ${
                    floor(job.location.z)
                }"
            )
        }
    }
}

data class Job(
    val category: JobCategory, val player: Player, val durationMillis: Long, val tracking: Boolean
) {
    val uuid = UUID.randomUUID().toString()
    var recordID = ""
    var location = player.location
    var startTime = 0L
    val itemDisplays = mutableMapOf<JobBoard, ItemDisplay>()
    val textDisplays = mutableMapOf<JobBoard, TextDisplay>()
    var name: String = category.name
        set(value) {
            field = value
            updateTextDisplays()
            if (SneakyJobBoard.isDynmapActive()) {
                val markerAPI = SneakyJobBoard.getInstance().markerAPI

                val markerSet = markerAPI?.getMarkerSet("SneakyJobBoard") ?: run {
                    markerAPI?.createMarkerSet(
                        "SneakyJobBoard", "SneakyJobBoard", null, false
                    ) ?: run {
                        SneakyJobBoard.log(
                            "Failed to create a new marker set."
                        )
                        null
                    }
                }

                markerSet?.findMarker(uuid)?.label = value
            }
        }
    var description: String = category.description
        set(value) {
            field = value
            updateTextDisplays()
        }

    /**
     * Returns the remaining duration of this job in milliseconds.
     * @return Remaining duration in milliseconds.
     */
    private fun remainingDurationMillis(): Long {
        return ((startTime + durationMillis) - System.currentTimeMillis())
    }

    /**
     * Checks if this job is expired based on its remaining duration.
     * @return True if the job is expired, false otherwise.
     */
    fun isExpired(): Boolean {
        return remainingDurationMillis() < 0L
    }

    /**
     * Unlists this job from all platforms and cleans up associated displays.
     * @param endReason The unlisting reason. Can be "expired", "unlisted" or "restart".
     * */
    fun unlist(endReason: String) {
        if (!SneakyJobBoard.getJobManager().jobs.values.contains(this)) return

        SneakyJobBoard.getPocketbaseManager().unlistJob(this, endReason)
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
     * Updates all Text Display entities associated with this job.
     */
    fun updateTextDisplays() {
        for (textDisplayEntity in textDisplays.values) {
            val text: MutableList<String> = mutableListOf("&a${name}")

            for (line in TextUtility.splitIntoLines(description, 30)) {
                text.add("&e$line")
            }

            var posterString = (SneakyJobBoard.getInstance().getConfig().getString("poster-string")
                ?: "&ePosted by: &b[playerName]").replace(
                "[playerName]", player.name
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
     * Returns the ItemStack that represents this job, including metadata.
     * @return The item representing this job.
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

        // Add poster line
        var posterString = (SneakyJobBoard.getInstance().getConfig().getString("poster-string")
            ?: "&ePosted by: &b[playerName]").replace(
            "[playerName]", player.name
        )

        if (SneakyJobBoard.isPapiActive()) {
            posterString = PlaceholderAPI.setPlaceholders(player, posterString)
        }

        lore.add(posterString)

        meta.lore(lore.map { TextUtility.convertToComponent(it) })

        // Set persistent data.
        val persistentData = meta.persistentDataContainer
        persistentData.set(SneakyJobBoard.getJobManager().IDKEY, PersistentDataType.STRING, uuid)

        meta.setEnchantmentGlintOverride(true)

        itemStack.itemMeta = meta
        return itemStack
    }

    /**
     * Returns the transformation values for this job's item displays.
     * @return The transformation to be applied to the job's item displays.
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
