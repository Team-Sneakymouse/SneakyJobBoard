package net.sneakyjobboard

import net.sneakyjobboard.commands.CommandJobBoard
import net.sneakyjobboard.commands.CommandJobHistory
import net.sneakyjobboard.commands.CommandListJob
import net.sneakyjobboard.commands.CommandUnlistJob
import net.sneakyjobboard.job.JobCategoryManager
import net.sneakyjobboard.job.JobHistoryInventoryListener
import net.sneakyjobboard.job.JobManager
import net.sneakyjobboard.jobboard.*
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.permissions.Permission
import org.bukkit.plugin.java.JavaPlugin
import org.dynmap.DynmapAPI
import org.dynmap.markers.MarkerAPI
import java.io.File

class SneakyJobBoard : JavaPlugin(), Listener {

    lateinit var jobCategoryManager: JobCategoryManager
    lateinit var jobBoardManager: JobBoardManager
    lateinit var jobManager: JobManager
    lateinit var pocketBaseManager: PocketbaseManager
    var papiActive = false
    var markerAPI: MarkerAPI? = null
    val jobBoardUpdater = JobBoardUpdater()
    private val jobBoardMaintenance = JobBoardMaintenance()
    private val trackingJobsUpdater = TrackingJobsUpdater()

    override fun onEnable() {
        saveDefaultConfig()

        jobCategoryManager = JobCategoryManager()
        jobBoardManager = JobBoardManager()
        jobManager = JobManager()
        pocketBaseManager = PocketbaseManager()

        server.commandMap.register(IDENTIFIER, CommandListJob())
        server.commandMap.register(IDENTIFIER, CommandJobBoard())
        server.commandMap.register(IDENTIFIER, CommandUnlistJob())
        server.commandMap.register(IDENTIFIER, CommandJobHistory())

        server.pluginManager.registerEvents(PluginListener(this), this)
        server.pluginManager.registerEvents(JobInventoryListener(), this)
        server.pluginManager.registerEvents(JobHistoryInventoryListener(), this)
        server.pluginManager.registerEvents(JobBoardListener(), this)

        server.pluginManager.addPermission(Permission("$IDENTIFIER.*"))
        server.pluginManager.addPermission(Permission("$IDENTIFIER.admin"))
        server.pluginManager.addPermission(Permission("$IDENTIFIER.command.*"))

        jobBoardUpdater.runTaskTimer(this, 0L, 1L)
        jobBoardMaintenance.runTaskTimer(this, 0L, 100L)
        trackingJobsUpdater.runTaskTimer(
            this, 0L, getConfig().getLong("tracking-jobs-update-interval")
        )

        val papiPlugin = Bukkit.getServer().pluginManager.getPlugin("PlaceholderAPI")
        if (papiPlugin != null && papiPlugin.isEnabled) {
            papiActive = true
            Placeholders().register()
        }

        val dynmapPlugin = Bukkit.getServer().pluginManager.getPlugin("dynmap")
        if (dynmapPlugin != null && dynmapPlugin.isEnabled) {
            val dynmapAPI = (dynmapPlugin as DynmapAPI)
            markerAPI = dynmapAPI.markerAPI
        }
    }

    companion object {
        const val IDENTIFIER = "sneakyjobboard"
        const val AUTHORS = "Team Sneakymouse"
        const val VERSION = "1.0.0"
        private lateinit var instance: SneakyJobBoard

        /**
         * Logs a message to the console.
         * @param msg the message to log.
         */
        fun log(msg: String) {
            instance.logger.info(msg)
        }

        /**
         * Retrieves the plugin's data folder.
         * @return the File object representing the data folder.
         * @throws IllegalStateException if the data folder is not initialized.
         */
        private fun getDataFolder(): File {
            return instance.dataFolder
        }

        /**
         * Retrieves the configuration file for the plugin.
         * @return the File object representing the config.yml file.
         */
        fun getConfigFile(): File {
            return File(getDataFolder(), "config.yml")
        }

        /**
         * Checks if PlaceholderAPI (PAPI) is active.
         * @return true if PAPI is active, false otherwise.
         */
        fun isPapiActive(): Boolean {
            return instance.papiActive
        }

        /**
         * Checks if Dynmap is active.
         * @return true if Dynmap is active, false otherwise.
         */
        fun isDynmapActive(): Boolean {
            return (instance.markerAPI != null)
        }

        /**
         * Retrieves the running instance of the SneakyJobBoard plugin.
         * @return the SneakyJobBoard instance.
         */
        fun getInstance(): SneakyJobBoard {
            return instance
        }

        /**
         * Retrieves the job category manager instance.
         * @return the JobCategoryManager instance responsible for managing job categories.
         */
        fun getJobCategoryManager(): JobCategoryManager {
            return instance.jobCategoryManager
        }

        /**
         * Retrieves the job board manager instance.
         * @return the JobBoardManager instance responsible for managing job boards.
         */
        fun getJobBoardManager(): JobBoardManager {
            return instance.jobBoardManager
        }

        /**
         * Retrieves the job manager instance.
         * @return the JobManager instance responsible for managing jobs.
         */
        fun getJobManager(): JobManager {
            return instance.jobManager
        }

        /**
         * Retrieves the PocketBase manager instance.
         * @return the PocketbaseManager instance responsible for interacting with the PocketBase API.
         */
        fun getPocketbaseManager(): PocketbaseManager {
            return instance.pocketBaseManager
        }
    }

    override fun onLoad() {
        instance = this
    }
}

class PluginListener(private val instance: SneakyJobBoard) : Listener {

    /**
     * Handles the PluginDisableEvent, which is triggered when the plugin is disabled.
     * Cleans up the job manager when the plugin is disabled.
     * @param event the PluginDisableEvent indicating that a plugin has been disabled.
     */
    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        if (event.plugin == instance) {
            SneakyJobBoard.getJobManager().cleanup()
        }
    }

}