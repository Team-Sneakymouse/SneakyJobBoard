package net.sneakyjobboard.job

import java.util.*
import kotlin.collections.mutableListOf
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

/** Manages listed jobs and dispatching. */
class JobManager {

    public val IDKEY: NamespacedKey = NamespacedKey(SneakyJobBoard.getInstance(), "id")

    public val jobs = mutableMapOf<String, Job>()
    val pendingSpawns = mutableMapOf<JobBoard, MutableList<Job>>()

    /** Adds a new job to the map. */
    fun list(job: Job) {
        job.startTime = System.currentTimeMillis()
        SneakyJobBoard.getPocketbaseManager().listJob(job)
        jobs[job.uuid] = job

        // Spawn item displays
        for (jobBoard in SneakyJobBoard.getJobBoardManager().jobBoards) {
            if (jobBoard.mapLocation.chunk.isLoaded) {
                jobBoard.spawnIcons(job)
            } else {
                val list = pendingSpawns.getOrDefault(jobBoard, mutableListOf<Job>())
                list.add(job)
                pendingSpawns[jobBoard] = list
            }
        }

        // Add dynmap icon
        if (SneakyJobBoard.isDynmapActive()) {
            val jobLocation = job.location
            val markerAPI = SneakyJobBoard.getInstance().markerAPI

            val markerSet =
                    markerAPI?.getMarkerSet("SneakyJobBoard")
                            ?: run {
                                markerAPI?.createMarkerSet(
                                        "SneakyJobBoard",
                                        "SneakyJobBoard",
                                        null,
                                        false
                                )
                                        ?: run {
                                            SneakyJobBoard.log("Failed to create a new marker set.")
                                            null
                                        }
                            }

            val icon = markerAPI?.getMarkerIcon(job.category.dynmapMapIcon)

            val marker =
                    markerSet?.createMarker(
                            job.uuid,
                            job.name,
                            jobLocation.world.name,
                            jobLocation.x,
                            jobLocation.y,
                            jobLocation.z,
                            icon,
                            false
                    )

            if (marker == null) {
                SneakyJobBoard.log("Failed to create marker")
            }
        }

        // Schedule unlisting
        Bukkit.getScheduler()
                .runTaskLater(
                        SneakyJobBoard.getInstance(),
                        Runnable { job.unlist() },
                        20 * job.durationMillis / 1000
                )
    }

    /** Get job value collection. */
    fun getJobs(): MutableCollection<Job> {
        return jobs.values
    }

    /** Get the last job that was listed by player. */
    fun getLastListedJob(player: Player): Job? {
        for (job in jobs.values.reversed()) {
            if (job.player == player) {
                return job
            }
        }
        return null
    }

    /** Get a listed job by its name. */
    fun getJobByName(name: String): Job? {
        return jobs.values.find { it.name.equals(name, ignoreCase = true) }
    }

    /** Clean up all listed jobs. */
    fun cleanup() {
        val jobIdsToRemove = jobs.values.toList()
        jobIdsToRemove.forEach { it.unlist() }

        // Clean up dynmap markers
        if (SneakyJobBoard.isDynmapActive()) {
            val markerAPI = SneakyJobBoard.getInstance().markerAPI

            val markerSet =
                    markerAPI?.getMarkerSet("SneakyJobBoard")
                            ?: run {
                                markerAPI?.createMarkerSet(
                                        "SneakyJobBoard",
                                        "SneakyJobBoard",
                                        null,
                                        false
                                )
                                        ?: run {
                                            SneakyJobBoard.log("Failed to create a new marker set.")
                                            null
                                        }
                            }

            markerSet?.deleteMarkerSet()
        }
    }

    /** Dispatch a player to a listed job. */
    fun dispatch(uuid: String, pl: Player) {
        val job = jobs.get(uuid)

        if (job == null) return

        Bukkit.getServer()
                .dispatchCommand(
                        Bukkit.getServer().getConsoleSender(),
                        "cast forcecast " +
                                pl.getName() +
                                " jobboard-dispatch-self " +
                                Math.floor(job.location.getX()) +
                                " " +
                                Math.floor(job.location.getY()) +
                                " " +
                                Math.floor(job.location.getZ())
                )
    }
}

data class Job(
        val category: JobCategory,
        val player: Player,
        val durationMillis: Long,
        val tracking: Boolean
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

                val markerSet =
                        markerAPI?.getMarkerSet("SneakyJobBoard")
                                ?: run {
                                    markerAPI?.createMarkerSet(
                                            "SneakyJobBoard",
                                            "SneakyJobBoard",
                                            null,
                                            false
                                    )
                                            ?: run {
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

    fun remainingDurationMillis(): Long {
        return ((startTime + durationMillis) - System.currentTimeMillis())
    }

    fun isExpired(): Boolean {
        return remainingDurationMillis() < 0L
    }

    fun unlist() {
        SneakyJobBoard.getPocketbaseManager().unlistJob(this)
        itemDisplays.values.forEach { entity -> entity.remove() }
        textDisplays.values.forEach { entity -> entity.remove() }
        SneakyJobBoard.getJobManager().jobs.remove(uuid)

        if (SneakyJobBoard.isDynmapActive()) {
            val markerAPI = SneakyJobBoard.getInstance().markerAPI

            val markerSet =
                    markerAPI?.getMarkerSet("SneakyJobBoard")
                            ?: run {
                                markerAPI?.createMarkerSet(
                                        "SneakyJobBoard",
                                        "SneakyJobBoard",
                                        null,
                                        false
                                )
                                        ?: run {
                                            SneakyJobBoard.log("Failed to create a new marker set.")
                                            null
                                        }
                            }

            markerSet?.findMarker(uuid)?.deleteMarker()
        }
    }

    fun updateTextDisplays() {
        for (textDisplayEntity in textDisplays.values) {
            val text: MutableList<String> = mutableListOf("&a${name}")

            for (line in TextUtility.splitIntoLines(description, 30)) {
                text.add("&e$line")
            }

            var posterString =
                    (SneakyJobBoard.getInstance().getConfig().getString("poster-string")
                                    ?: "&ePosted by: &b[playerName]").replace(
                            "[playerName]",
                            player.name
                    )

            if (SneakyJobBoard.isPapiActive()) {
                posterString = PlaceholderAPI.setPlaceholders(player, posterString)
            }

            text.add(posterString)

            textDisplayEntity.text(TextUtility.convertToComponent(text.joinToString("\n")))
            textDisplayEntity.setTransformation(
                    Transformation(
                            Vector3f(0F, 0.3F, 0.025F + (0.025F * text.size)),
                            Quaternionf(-1F, 0F, 0F, 1F),
                            Vector3f(0.1F, 0.1F, 0.1F),
                            Quaternionf(0F, 0F, 0F, 1F)
                    )
            )
        }
    }

    fun getIconItem(): ItemStack {
        var itemStack: ItemStack = ItemStack(category.iconMaterial)
        var customModelData: Int = category.iconCustomModelData

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
        var posterString =
                (SneakyJobBoard.getInstance().getConfig().getString("poster-string")
                                ?: "&ePosted by: &b[playerName]").replace(
                        "[playerName]",
                        player.name
                )

        if (SneakyJobBoard.isPapiActive()) {
            posterString = PlaceholderAPI.setPlaceholders(player, posterString)
        }

        lore.add(posterString)

        meta.lore(lore.map { TextUtility.convertToComponent(it) })

        // Set persistent data.
        val persistentData = meta.persistentDataContainer
        persistentData.set(SneakyJobBoard.getJobManager().IDKEY, PersistentDataType.STRING, uuid)

        itemStack.itemMeta = meta
        return itemStack
    }

    fun getTransformation(): Transformation {
        val config = SneakyJobBoard.getInstance().getConfig()

        val baseDuration = config.getLong("duration-scale-base-duration")

        if (baseDuration > 0) {
            val minScale = config.getDouble("duration-scale-scale-min").toFloat()
            val maxScale = config.getDouble("duration-scale-scale-max").toFloat()

            for (job in SneakyJobBoard.getJobManager().getJobs()) {
                val remainingDuration = job.remainingDurationMillis()
                val scaleFactor =
                        (remainingDuration.toFloat() / baseDuration).coerceIn(minScale, maxScale)

                val oldTransformation = job.category.transformation
                val newScale =
                        Vector3f(
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
