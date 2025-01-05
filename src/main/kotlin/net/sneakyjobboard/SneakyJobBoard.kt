package net.sneakyjobboard

import net.sneakyjobboard.commands.*
import net.sneakyjobboard.job.JobCategoryManager
import net.sneakyjobboard.job.JobHistoryInventoryListener
import net.sneakyjobboard.job.JobManager
import net.sneakyjobboard.advert.AdvertCategoryManager
import net.sneakyjobboard.advert.AdvertManager
import net.sneakyjobboard.advert.AdvertBoardListener
import net.sneakyjobboard.advert.AdvertIconSelectorListener
import net.sneakyjobboard.advert.AdvertInvitationListener
import net.sneakyjobboard.advert.AdvertManagementListener
import net.sneakyjobboard.advert.AdvertEditListener
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

/**
 * Main plugin class for SneakyJobBoard.
 * Handles plugin initialization, manager setup, and provides global access to core components.
 */
class SneakyJobBoard : JavaPlugin(), Listener {

    private lateinit var jobCategoryManager: JobCategoryManager
    private lateinit var jobBoardManager: JobBoardManager
    private lateinit var jobManager: JobManager
    private lateinit var pocketBaseManager: PocketbaseManager
    private lateinit var advertCategoryManager: AdvertCategoryManager
    private lateinit var advertManager: AdvertManager
    var papiActive = false
    var markerAPI: MarkerAPI? = null
    val jobBoardUpdater = JobBoardUpdater()
    private val jobBoardMaintenance = JobBoardMaintenance()
    private val trackingJobsUpdater = TrackingJobsUpdater()

    /**
     * Initializes the plugin instance during server load.
     */
    override fun onLoad() {
        instance = this
    }

    /**
     * Performs plugin setup on enable:
     * - Initializes managers
     * - Registers commands and listeners
     * - Sets up permissions
     * - Starts scheduled tasks
     * - Integrates with PlaceholderAPI and Dynmap if available
     */
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
        server.commandMap.register(IDENTIFIER, CommandListAdvert())
        server.commandMap.register(IDENTIFIER, CommandInvitations())
        server.commandMap.register(IDENTIFIER, CommandAdvertBoard())
        server.commandMap.register(IDENTIFIER, CommandManageAdverts())

        server.pluginManager.registerEvents(PluginListener(this), this)
        server.pluginManager.registerEvents(JobInventoryListener(), this)
        server.pluginManager.registerEvents(JobHistoryInventoryListener(), this)
        server.pluginManager.registerEvents(JobBoardListener(), this)
        server.pluginManager.registerEvents(AdvertBoardListener(), this)
        server.pluginManager.registerEvents(AdvertIconSelectorListener(), this)
        server.pluginManager.registerEvents(AdvertInvitationListener(), this)
        server.pluginManager.registerEvents(advertManager, this)
        server.pluginManager.registerEvents(AdvertManagementListener(), this)
        server.pluginManager.registerEvents(AdvertEditListener(), this)

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
         * Logs a message to the plugin's logger.
         * @param msg The message to log
         */
        fun log(msg: String) {
            instance.logger.info(msg)
        }

        /**
         * Gets the plugin's configuration file.
         * @return The config.yml file
         */
        fun getConfigFile(): File {
            return File(instance.dataFolder, "config.yml")
        }

        /**
         * Gets the plugin instance.
         * @return The SneakyJobBoard plugin instance
         */
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

/**
 * Listener for handling plugin lifecycle events.
 * @property instance The SneakyJobBoard plugin instance
 */
class PluginListener(private val instance: SneakyJobBoard) : Listener {

    /**
     * Handles cleanup when the plugin is disabled.
     * @param event The plugin disable event
     */
    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        if (event.plugin == instance) {
            SneakyJobBoard.getJobManager().cleanup()
        }
    }

}