package net.sneakyjobboard.jobboard

import java.util.*
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.jobcategory.Job
import org.bukkit.Bukkit
import org.bukkit.Rotation
import org.bukkit.block.BlockFace
import org.bukkit.entity.GlowItemFrame
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player

/** Manages listed jobs and dispatching. */
class JobManager {

    private val jobs: MutableMap<String, Job> = mutableMapOf()

    /** Adds a new job to the map. */
    fun list(job: Job) {
        jobs[job.uuid] = job

        // Spawn item displays
        for (jobBoard in SneakyJobBoard.getJobCategoryManager().jobBoards) {
            val displayLocation = jobBoard.mapLocation.clone().add(0.5, 0.0, 0.5)
            val jobLocation = job.location
            val worldLocation =
                    jobBoard.worldLocation
                            .clone()
                            .add(
                                    (jobBoard.scale / 2).toDouble(),
                                    0.0,
                                    (jobBoard.scale / 2).toDouble()
                            )

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
                continue
            }

            // Calculate correct horizontal and vertical offsets
            var horizOffset = (jobLocation.z - worldLocation.z) / jobBoard.scale
            var vertOffset = (jobLocation.x - worldLocation.x) / jobBoard.scale

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

            if (facing.equals(BlockFace.DOWN)) {} else if (facing.equals(BlockFace.UP)) {
                displayLocation.pitch = 180F
            } else if (facing.equals(BlockFace.NORTH)) {
                displayLocation.pitch = 90F
            } else if (facing.equals(BlockFace.EAST)) {
                displayLocation.pitch = 90F
            } else if (facing.equals(BlockFace.SOUTH)) {
                displayLocation.pitch = 90F
            } else if (facing.equals(BlockFace.WEST)) {
                displayLocation.pitch = 90F
            }

            displayLocation.add(horizOffset, 0.0, vertOffset)

            val itemDisplayEntity: ItemDisplay =
                    displayLocation.world!!.spawn(displayLocation, ItemDisplay::class.java)

            itemDisplayEntity.setItemStack(job.getIconItem())
            itemDisplayEntity.setTransformation(job.category.transformation)
            itemDisplayEntity.setBrightness(job.category.brightness)

            itemDisplayEntity.addScoreboardTag("JobBoardIcon")

            job.itemDisplays.add(itemDisplayEntity)
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

    /** Clean up all listed jobs. */
    fun cleanup() {
        val jobIdsToRemove = jobs.keys.toList()
        jobIdsToRemove.forEach { unlist(it) }
    }

    /** Unlist a job and kill off its item displays. */
    fun unlist(uuid: String) {
        val job = jobs[uuid] ?: return

        job.itemDisplays.forEach { entity -> entity.remove() }
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
}
