package net.sneakyjobboard

import net.sneakyjobboard.commands.CommandJobBoard
import net.sneakyjobboard.commands.CommandJobHistory
import net.sneakyjobboard.commands.CommandListJob
import net.sneakyjobboard.commands.CommandUnlistJob
import net.sneakyjobboard.commands.CommandInvitations
import net.sneakyjobboard.job.JobCategoryManager
import net.sneakyjobboard.job.JobHistoryInventoryListener
import net.sneakyjobboard.job.JobManager
import net.sneakyjobboard.advert.AdvertCategoryManager
import net.sneakyjobboard.advert.AdvertManager
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
    lateinit var advertCategoryManager: AdvertCategoryManager
    lateinit var advertManager: AdvertManager
    var papiActive = false
    var markerAPI: MarkerAPI? = null
    val jobBoardUpdater = JobBoardUpdater()
    private val jobBoardMaintenance = JobBoardMaintenance()
    private val trackingJobsUpdater = TrackingJobsUpdater()

    override fun onLoad() {
        instance = this
    }

    override fun onEnable() {
        saveDefaultConfig()

        jobCategoryManager = JobCategoryManager()
        jobBoardManager = JobBoardManager()
        jobManager = JobManager()
        pocketBaseManager = PocketbaseManager()
        advertCategoryManager = AdvertCategoryManager()
        advertManager = AdvertManager()

        server.commandMap.register(IDENTIFIER, CommandListJob())
        server.commandMap.register(IDENTIFIER, CommandJobBoard())
        server.commandMap.register(IDENTIFIER, CommandUnlistJob())
        server.commandMap.register(IDENTIFIER, CommandJobHistory())
        server.commandMap.register(IDENTIFIER, CommandInvitations())

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

        fun log(msg: String) {
            instance.logger.info(msg)
        }

        fun getConfigFile(): File {
            return File(instance.dataFolder, "config.yml")
        }

        fun getInstance(): SneakyJobBoard {
            return instance
        }

        fun getJobCategoryManager(): JobCategoryManager {
            return instance.jobCategoryManager
        }

        fun getJobBoardManager(): JobBoardManager {
            return instance.jobBoardManager
        }

        fun getJobManager(): JobManager {
            return instance.jobManager
        }

        fun getPocketbaseManager(): PocketbaseManager {
            return instance.pocketBaseManager
        }

        fun getAdvertCategoryManager(): AdvertCategoryManager {
            return instance.advertCategoryManager
        }

        fun getAdvertManager(): AdvertManager {
            return instance.advertManager
        }

        fun isPapiActive(): Boolean {
            return instance.papiActive
        }

        fun isDynmapActive(): Boolean {
            return instance.markerAPI != null
        }
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