package net.sneakyjobboard.job

import java.util.*
import kotlin.collections.mutableListOf
import me.clip.placeholderapi.PlaceholderAPI
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.job.Job
import net.sneakyjobboard.jobboard.JobBoard
import net.sneakyjobboard.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Rotation
import org.bukkit.block.BlockFace
import org.bukkit.entity.Display.Brightness
import org.bukkit.entity.GlowItemFrame
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapView
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.bukkit.NamespacedKey
import org.joml.Quaternionf
import org.joml.Vector3f

/** Manages listed jobs and dispatching. */
class JobManager {

    public val IDKEY: NamespacedKey = NamespacedKey(SneakyJobBoard.getInstance(), "id")

    public val jobs: MutableMap<String, Job> = mutableMapOf()
    val pendingSpawns: MutableMap<JobBoard, MutableList<Job>> = mutableMapOf()

    /** Adds a new job to the map. */
    fun list(job: Job) {
        jobs[job.uuid] = job

        // Spawn item displays
        for (jobBoard in SneakyJobBoard.getJobBoardManager().jobBoards) {
            if (jobBoard.mapLocation.chunk.isLoaded) {
                spawnIcons(jobBoard, job)
            } else {
                val list: MutableList<Job> = pendingSpawns.getOrDefault(jobBoard, mutableListOf())
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
                                            return
                                        }
                            }

            val icon = markerAPI?.getMarkerIcon(job.category.dynmapMapIcon)

            val marker =
                    markerSet.createMarker(
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
                        20 * job.durationMilis / 1000
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
                                            return
                                        }
                            }

            markerSet.deleteMarkerSet()
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

    companion object {
        fun spawnIcons(jobBoard: JobBoard, job: Job) {
            if (job.isExpired()) return

            val jobLocation = job.location
            val displayLocation = jobBoard.mapLocation.clone().add(0.5, 0.5, 0.5)

            // Find the ItemFrame at the centre of the job board
            val itemFrame =
                    displayLocation.world?.getNearbyEntities(displayLocation, 0.5, 0.5, 0.5)
                            ?.firstOrNull {
                                (it is ItemFrame || it is GlowItemFrame) &&
                                        it.location.blockX == displayLocation.blockX &&
                                        it.location.blockY == displayLocation.blockY &&
                                        it.location.blockZ == displayLocation.blockZ
                            } as?
                            ItemFrame

            if (itemFrame == null) {
                SneakyJobBoard.log(
                        "One of the jobboards listed in map-central-vectors does not have an item frame on it: ${displayLocation.toString()}"
                )
                return
            }

            val scale: Int =
                    when {
                        jobBoard.mapScaleOverride > 0 -> jobBoard.mapScaleOverride
                        else -> {
                            // Retrieve the scale from the map item
                            val frameItem = itemFrame.item

                            if (frameItem.type != Material.FILLED_MAP) {
                                SneakyJobBoard.log(
                                        "One of the job boards listed in map-central-vectors does not have a filled map item in the item frame: ${displayLocation.toString()}"
                                )
                                return
                            }

                            val mapView = (frameItem.itemMeta as? MapMeta)?.mapView

                            if (mapView == null) {
                                SneakyJobBoard.log(
                                        "One of the job boards listed in map-central-vectors does not have a valid map item in the item frame: ${displayLocation.toString()}"
                                )
                                return
                            }
                            when (mapView.scale) {
                                MapView.Scale.CLOSE -> 256
                                MapView.Scale.NORMAL -> 512
                                MapView.Scale.FAR -> 1024
                                MapView.Scale.FARTHEST -> 2048
                                else -> 128
                            }
                        }
                    }

            val worldLocation =
                    jobBoard.worldLocation
                            .clone()
                            .add((scale / 2).toDouble(), 0.0, (scale / 2).toDouble())

            // Calculate correct horizontal and vertical offsets
            var horizOffset = (jobLocation.x - worldLocation.x) / scale
            var vertOffset = -(jobLocation.z - worldLocation.z) / scale

            // Apply isometry
            if (jobBoard.isometricAngle > 0) {
                val radianAngle = Math.toRadians(jobBoard.isometricAngle)

                val xTemp = horizOffset
                val yTemp = vertOffset

                horizOffset =
                        xTemp * Math.cos((Math.PI / 2) - radianAngle) +
                                yTemp * Math.sin(radianAngle) - 0.5
                vertOffset =
                        -xTemp * Math.sin((Math.PI / 2) - radianAngle) +
                                yTemp * Math.cos(radianAngle)

                vertOffset += (jobLocation.y - worldLocation.y) / scale
            }

            val frameRotation = itemFrame.rotation

            // Handle frame rotations
            when (frameRotation) {
                Rotation.CLOCKWISE_45, Rotation.FLIPPED_45 -> {
                    val temp = horizOffset
                    horizOffset = vertOffset
                    vertOffset = -temp
                }
                Rotation.CLOCKWISE, Rotation.COUNTER_CLOCKWISE -> {
                    horizOffset = -horizOffset
                    vertOffset = -vertOffset
                }
                Rotation.CLOCKWISE_135, Rotation.COUNTER_CLOCKWISE_45 -> {
                    val temp = horizOffset
                    horizOffset = -vertOffset
                    vertOffset = temp
                }
                else -> {}
            }

            val facing = itemFrame.attachedFace

            var xOffset: Double
            var yOffset: Double
            var zOffset: Double

            when (facing) {
                BlockFace.UP -> {
                    displayLocation.pitch = 180F
                    xOffset = horizOffset
                    yOffset = 0.5
                    zOffset = vertOffset
                }
                BlockFace.NORTH -> {
                    displayLocation.pitch = 90F
                    xOffset = horizOffset
                    yOffset = vertOffset
                    zOffset = -0.5
                }
                BlockFace.EAST -> {
                    displayLocation.pitch = 90F
                    displayLocation.yaw = 90F
                    xOffset = 0.5
                    yOffset = vertOffset
                    zOffset = horizOffset
                }
                BlockFace.SOUTH -> {
                    displayLocation.pitch = 90F
                    displayLocation.yaw = 180F
                    xOffset = -horizOffset
                    yOffset = vertOffset
                    zOffset = 0.5
                }
                BlockFace.WEST -> {
                    displayLocation.pitch = 90F
                    displayLocation.yaw = 270F
                    xOffset = -0.5
                    yOffset = vertOffset
                    zOffset = -horizOffset
                }
                else -> {
                    xOffset = horizOffset
                    yOffset = -0.5
                    zOffset = -vertOffset
                }
            }

            displayLocation.add(xOffset, yOffset, zOffset)

            // Spawn the ItemDisplay
            val itemDisplayEntity: ItemDisplay =
                    displayLocation.world!!.spawn(displayLocation, ItemDisplay::class.java)

            itemDisplayEntity.setItemStack(job.getIconItem())
            itemDisplayEntity.setTransformation(job.category.transformation)
            itemDisplayEntity.setBrightness(job.category.brightness)

            itemDisplayEntity.addScoreboardTag("JobBoardIcon")

            job.itemDisplays.put(jobBoard, itemDisplayEntity)

            // Spawn the TextDisplay
            val textDisplayEntity: TextDisplay =
                    displayLocation.world!!.spawn(displayLocation, TextDisplay::class.java)

            textDisplayEntity.setBrightness(Brightness(15, 15))
            textDisplayEntity.setAlignment(TextDisplay.TextAlignment.LEFT)

            textDisplayEntity.addScoreboardTag("JobBoardIcon")

            job.textDisplays.put(jobBoard, textDisplayEntity)

            job.updateTextDisplays()

            for (player in Bukkit.getOnlinePlayers()) {
                player.hideEntity(SneakyJobBoard.getInstance(), textDisplayEntity)
            }
        }
    }
}

data class Job(val category: JobCategory, val player: Player, val durationMilis: Long) {
    val uuid: String = UUID.randomUUID().toString()
    val location: Location = player.location
    val startTime: Long = System.currentTimeMillis()
    val itemDisplays: MutableMap<JobBoard, ItemDisplay> = mutableMapOf()
    val textDisplays: MutableMap<JobBoard, TextDisplay> = mutableMapOf()
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
                                                return
                                            }
                                }

                markerSet.findMarker(uuid)?.label = value
            }
        }
    var description: String = category.description
        set(value) {
            field = value
            updateTextDisplays()
        }

    fun isExpired(): Boolean {
        return (System.currentTimeMillis() >= startTime + durationMilis)
    }

    fun unlist() {
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
                                            return
                                        }
                            }

            markerSet.findMarker(uuid)?.deleteMarker()
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
        persistentData.set(
                SneakyJobBoard.getJobManager().IDKEY,
                PersistentDataType.STRING,
                uuid
        )

        itemStack.itemMeta = meta
        return itemStack
    }
}
