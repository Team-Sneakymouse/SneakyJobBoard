package net.sneakyjobboard.jobboard

import java.util.*
import kotlin.collections.mutableListOf
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.jobcategory.Job
import net.sneakyjobboard.jobcategory.JobBoard
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Rotation
import org.bukkit.block.BlockFace
import org.bukkit.entity.GlowItemFrame
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapView

/** Manages listed jobs and dispatching. */
class JobManager {

    private val jobs: MutableMap<String, Job> = mutableMapOf()
    val pendingSpawns: MutableMap<JobBoard, MutableList<Job>> = mutableMapOf()

    /** Adds a new job to the map. */
    fun list(job: Job) {
        jobs[job.uuid] = job

        // Spawn item displays
        for (jobBoard in SneakyJobBoard.getJobCategoryManager().jobBoards) {
            if (jobBoard.mapLocation.chunk.isLoaded) {
                spawnItemDisplays(jobBoard, job)
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
                        Runnable { unlist(job.uuid) },
                        20 * job.durationMilis / 1000
                )
    }

    /** Get job value collection. */
    fun getJobs(): MutableCollection<Job> {
        return jobs.values
    }

    /** Get the last job that was listed by player. */
    fun getLastListedJob(player: Player): Job? {
        // Iterate through the jobs in reverse order to find the last job listed by the player
        for (job in jobs.values.reversed()) {
            if (job.player == player) {
                return job
            }
        }
        return null
    }

    /** Clean up all listed jobs. */
    fun cleanup() {
        val jobIdsToRemove = jobs.keys.toList()
        jobIdsToRemove.forEach { unlist(it) }

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

    /** Unlist a job and kill off its item displays. */
    fun unlist(uuid: String) {
        val job = jobs[uuid] ?: return

        job.itemDisplays.forEach { entity -> entity.remove() }

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

        jobs.remove(uuid)
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
        fun spawnItemDisplays(jobBoard: JobBoard, job: Job) {
            if (job.isExpired()) return

            val jobLocation = job.location
            val displayLocation = jobBoard.mapLocation.clone().add(0.5, 0.5, 0.5)

            // Find the ItemFrame at the centre of the job board
            val itemFrame =
                    displayLocation.world?.getNearbyEntities(displayLocation, 1.0, 1.0, 1.0)
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

            val scale: Int =
                    when (mapView.scale) {
                        MapView.Scale.CLOSE -> 256
                        MapView.Scale.NORMAL -> 512
                        MapView.Scale.FAR -> 1024
                        MapView.Scale.FARTHEST -> 2048
                        else -> 128
                    }

            val worldLocation =
                    jobBoard.worldLocation
                            .clone()
                            .add((scale / 2).toDouble(), 0.0, (scale / 2).toDouble())

            // Calculate correct horizontal and vertical offsets
            var horizOffset = (jobLocation.x - worldLocation.x) / scale
            var vertOffset = (jobLocation.z - worldLocation.z) / scale

            val frameRotation = itemFrame.rotation

            // Handle frame rotations
            when (frameRotation) {
                Rotation.CLOCKWISE_45, Rotation.FLIPPED_45 -> {
                    val temp = horizOffset
                    horizOffset = -vertOffset
                    vertOffset = temp
                }
                Rotation.CLOCKWISE, Rotation.COUNTER_CLOCKWISE -> {
                    horizOffset = -horizOffset
                    vertOffset = -vertOffset
                }
                Rotation.CLOCKWISE_135, Rotation.COUNTER_CLOCKWISE_45 -> {
                    val temp = horizOffset
                    horizOffset = vertOffset
                    vertOffset = -temp
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
                    zOffset = -vertOffset
                }
                BlockFace.NORTH -> {
                    displayLocation.pitch = 90F
                    xOffset = horizOffset
                    yOffset = -vertOffset
                    zOffset = -0.5
                }
                BlockFace.EAST -> {
                    displayLocation.pitch = 90F
                    displayLocation.yaw = 90F
                    xOffset = 0.5
                    yOffset = -vertOffset
                    zOffset = horizOffset
                }
                BlockFace.SOUTH -> {
                    displayLocation.pitch = 90F
                    displayLocation.yaw = 180F
                    xOffset = -horizOffset
                    yOffset = -vertOffset
                    zOffset = 0.5
                }
                BlockFace.WEST -> {
                    displayLocation.pitch = 90F
                    displayLocation.yaw = 270F
                    xOffset = -0.5
                    yOffset = -vertOffset
                    zOffset = -horizOffset
                }
                else -> {
                    xOffset = horizOffset
                    yOffset = -0.5
                    zOffset = vertOffset
                }
            }

            displayLocation.add(xOffset, yOffset, zOffset)

            val itemDisplayEntity: ItemDisplay =
                    displayLocation.world!!.spawn(displayLocation, ItemDisplay::class.java)

            itemDisplayEntity.setItemStack(job.getIconItem())
            itemDisplayEntity.setTransformation(job.category.transformation)
            itemDisplayEntity.setBrightness(job.category.brightness)

            itemDisplayEntity.addScoreboardTag("JobBoardIcon")

            job.itemDisplays.add(itemDisplayEntity)
        }
    }
}

class JobManagerListener : Listener {

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        val jobManager = SneakyJobBoard.getJobManager()
        jobManager.pendingSpawns.entries.removeIf { entry ->
            val jobBoard = entry.key
            if (jobBoard.mapLocation.chunk == event.chunk) {
                entry.value.forEach { job -> JobManager.spawnItemDisplays(jobBoard, job) }
                true
            } else {
                false
            }
        }
    }
}
