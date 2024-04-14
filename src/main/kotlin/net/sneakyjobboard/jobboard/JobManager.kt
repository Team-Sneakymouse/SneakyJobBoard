package net.sneakyjobboard.jobboard

import java.util.*
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.jobcategory.Job
import org.bukkit.Bukkit
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player

/** Manages listed jobs and dispatching. */
class JobManager {

    private val jobs: MutableMap<String, Job> = mutableMapOf()
    public val jobBoardIcons: MutableList<ItemDisplay> = mutableListOf()

    /** Adds a new job to the map. */
    fun list(job: Job) {
        jobs[job.uuid] = job

        // Spawn item displays
        for (jobBoard in SneakyJobBoard.getJobCategoryManager().jobBoards) {
            val displayLocation = jobBoard.mapLocation.clone()
            val jobLocation = job.location
            val worldLocation = jobBoard.worldLocation

            displayLocation.add(
                    (jobLocation.x - worldLocation.x) / jobBoard.scale,
                    (jobLocation.y - worldLocation.y) / jobBoard.scale,
                    (jobLocation.z - worldLocation.z) / jobBoard.scale
            )
			
            val itemDisplayEntity: ItemDisplay =
                    displayLocation.world!!.spawn(displayLocation, ItemDisplay::class.java)

            itemDisplayEntity.setItemStack(job.getIconItem())
            itemDisplayEntity.setTransformation(job.category.transformation)
            itemDisplayEntity.setBrightness(job.category.brightness)

            itemDisplayEntity.addScoreboardTag("JobBoardIcon")

            jobBoardIcons.add(itemDisplayEntity)
            Bukkit.getScheduler()
                    .runTaskLater(
                            SneakyJobBoard.getInstance(),
                            Runnable {
                                jobBoardIcons.remove(itemDisplayEntity)
                                itemDisplayEntity.remove()
                            },
                            20 * job.durationMilis / 1000
                    )
        }
    }

    /** Get job value collection. */
    fun getJobs(): MutableCollection<Job> {
        return jobs.values
    }

    /** Clean up expired jobs. */
    fun cleanup() {
        jobs.entries.removeIf { it.value.isExpired() }
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
