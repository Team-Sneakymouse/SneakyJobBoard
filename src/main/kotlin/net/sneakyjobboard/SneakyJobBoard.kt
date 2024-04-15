package net.sneakyjobboard

import java.io.File
import net.sneakyjobboard.commands.CommandJobBoard
import net.sneakyjobboard.commands.CommandListJob
import net.sneakyjobboard.commands.CommandUnlistJob
import net.sneakyjobboard.jobboard.JobInventoryListener
import net.sneakyjobboard.jobboard.JobManager
import net.sneakyjobboard.jobcategory.JobCategoryManager
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.permissions.Permission
import org.bukkit.plugin.java.JavaPlugin

class SneakyJobBoard : JavaPlugin(), Listener {

    lateinit var jobCategoryManager: JobCategoryManager
    lateinit var jobManager: JobManager
    var papiActive: Boolean = false

    override fun onEnable() {
        saveDefaultConfig()

        jobCategoryManager = JobCategoryManager()
        jobManager = JobManager()

        getServer().getCommandMap().register(IDENTIFIER, CommandListJob())
        getServer().getCommandMap().register(IDENTIFIER, CommandJobBoard())
        getServer().getCommandMap().register(IDENTIFIER, CommandUnlistJob())

        getServer().getPluginManager().registerEvents(this, this)
        getServer().getPluginManager().registerEvents(JobInventoryListener(), this)

        server.pluginManager.addPermission(Permission("$IDENTIFIER.*"))
        server.pluginManager.addPermission(Permission("$IDENTIFIER.command.*"))

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papiActive = true
            Placeholders().register()
        }
    }

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        if (event.plugin == this) {
            jobManager.cleanup()
        }
    }

    companion object {
        const val IDENTIFIER = "sneakyjobboard"
        const val AUTHORS = "Team Sneakymouse"
        const val VERSION = "1.0.0"
        private lateinit var instance: SneakyJobBoard
            private set

        /** Logs a message using the plugin logger. */
        fun log(msg: String) {
            instance?.logger?.info(msg) ?: System.err.println("SneakyJobBoard instance is null")
        }

        /**
         * Retrieves the plugin data folder.
         * @throws IllegalStateException if the data folder is null.
         */
        fun getDataFolder(): File {
            return instance?.dataFolder ?: throw IllegalStateException("Data folder is null")
        }

        /** Retrieves the configuration file. */
        fun getConfigFile(): File {
            return File(getDataFolder(), "config.yml")
        }

        /** Whether placeholderAPI is running. */
        fun isPapiActive(): Boolean {
            return instance?.papiActive ?: false
        }

        /** The running instance. */
        fun getInstance(): SneakyJobBoard {
            return instance
        }

        /** Retrieves the job category manager instance, creating a new one if necessary. */
        fun getJobCategoryManager(): JobCategoryManager {
            return instance?.jobCategoryManager
                    ?: JobCategoryManager().also { instance?.jobCategoryManager = it }
        }

        /** Retrieves the dispatch manager instance, creating a new one if necessary. */
        fun getJobManager(): JobManager {
            return instance?.jobManager ?: JobManager().also { instance?.jobManager = it }
        }
    }

    override fun onLoad() {
        instance = this
    }
}
