package net.sneakyjobboard.jobboard

import java.util.*
import net.sneakyjobboard.jobcategory.Job
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/** Manages listed jobs and dispatching. */
class JobManager {

    private val jobs: MutableMap<String, Job> = mutableMapOf()

    /** Adds a new job to the map. */
    fun report(job: Job) {
        cleanup()
        jobs[job.uuid] = job
    }

    /** Get job value collection. */
    fun getJobs(): MutableCollection<Job> {
        return jobs.values
    }

    /** Clean up expired jobs. */
    fun cleanup() {
        jobs.entries.removeIf { it.value.isExpired() }
    }

    /** Dispatch a player to an ongoing job. */
    fun dispatch(uuid: String, pl: Player) {
        val job = jobs.get(uuid)

        if (job == null) return

        job.incrementDispatched()

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
