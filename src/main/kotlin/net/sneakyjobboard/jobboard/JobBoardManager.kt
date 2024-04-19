package net.sneakyjobboard.jobboard

import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.commands.CommandJobBoard
import net.sneakyjobboard.job.JobManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Entity
import org.bukkit.entity.GlowItemFrame
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.scheduler.BukkitRunnable

/** Manages jobboards and their configurations. */
class JobBoardManager {

    public val jobBoards: MutableList<JobBoard> = mutableListOf()

    /** Loads job boards from the configuration file on initialization. */
    init {
        parseConfig()
    }

    /** Loads job boards from the configuration file. */
    public fun parseConfig() {
        try {
            val configFile = SneakyJobBoard.getConfigFile()
            if (!configFile.exists()) {
                throw IllegalStateException("config.yml not found")
            }

            val config = YamlConfiguration.loadConfiguration(configFile)

            val mapCentralLocations = mutableListOf<Location?>()
            val mapInteractables = mutableListOf<Boolean?>()
            val mapScaleOverrides = mutableListOf<Int?>()
            val isometricAngles = mutableListOf<Double?>()
            val worldCentralLocations = mutableListOf<Location?>()

            val mapCentralVectorStrings: List<String> = config.getStringList("map-central-vectors")
            val worldCentralVectorStrings: List<String> =
                    config.getStringList("world-central-vectors")

            // Parse map central vectors
            mapCentralVectorStrings.forEach { vectorString ->
                val components = vectorString.split(",")
                if (components.size >= 4) {
                    val world = Bukkit.getWorld(components[0])
                    val x = components[1].toDouble()
                    val y = components[2].toDouble()
                    val z = components[3].toDouble()
                    val interactable = components.getOrNull(4)?.toBoolean() ?: true
                    val scaleOverride = components.getOrNull(5)?.toInt() ?: 0
                    val isometricAngle = components.getOrNull(6)?.toDouble() ?: 0.0

                    if (world != null) {
                        val location = Location(world, x, y, z)
                        mapCentralLocations.add(location)
                        mapInteractables.add(interactable)
                        mapScaleOverrides.add(scaleOverride)
                        isometricAngles.add(isometricAngle)
                    } else {
                        SneakyJobBoard.log(
                                "Error parsing map central vector: World '${components[1]}' not found."
                        )
                        mapCentralLocations.add(null)
                        mapInteractables.add(null)
                        mapScaleOverrides.add(null)
                        isometricAngles.add(null)
                    }
                } else {
                    mapCentralLocations.add(null)
                    mapInteractables.add(null)
                    mapScaleOverrides.add(null)
                    isometricAngles.add(null)
                }
            }

            // Parse world central vectors
            worldCentralVectorStrings.forEach { vectorString ->
                val components = vectorString.split(",")
                if (components.size == 4) {
                    val world = Bukkit.getWorld(components[0])
                    val x = components[1].toDouble()
                    val y = components[2].toDouble()
                    val z = components[3].toDouble()

                    if (world != null) {
                        val location = Location(world, x, y, z)
                        worldCentralLocations.add(location)
                    } else {
                        SneakyJobBoard.log(
                                "Error parsing world central vector: World '${components[0]}' not found."
                        )
                        worldCentralLocations.add(null)
                    }
                } else {
                    worldCentralLocations.add(null)
                }
            }

            if (mapCentralLocations.size > 0 && worldCentralLocations.size > 0) {
                for (i in 0 until mapCentralLocations.size) {
                    val mapLocation = mapCentralLocations[i]

                    if (mapLocation != null) {
                        var worldLocation: Location? = null
                        val interactable = mapInteractables.get(i)
                        val scaleOverride = mapScaleOverrides.get(i)
                        val isometricAngle = isometricAngles.get(i)

                        // Find the last non-null worldVector
                        for (j in i downTo 0) {
                            if (j < worldCentralLocations.size && worldCentralLocations[j] != null
                            ) {
                                worldLocation = worldCentralLocations[j]
                                break
                            }
                        }

                        if (worldLocation == null ||
                                        interactable == null ||
                                        scaleOverride == null ||
                                        isometricAngle == null
                        )
                                continue

                        jobBoards.add(
                                JobBoard(
                                        mapLocation,
                                        worldLocation,
                                        interactable,
                                        scaleOverride,
                                        isometricAngle
                                )
                        )
                    }
                }
            }
        } catch (e: IllegalStateException) {
            SneakyJobBoard.log("Error: ${e.message}")
        } catch (e: Exception) {
            SneakyJobBoard.log("An unexpected error occurred while loading jobboards: ${e.message}")
        }
    }
}

data class JobBoard(
        val mapLocation: Location,
        val worldLocation: Location,
        val interactable: Boolean,
        val mapScaleOverride: Int,
        val isometricAngle: Double
) {
    /** Checks if an item frame is part of this job board. */
    fun isPartOfBoard(itemFrame: ItemFrame): Boolean {
        val frameLocation = itemFrame.location.block.location

        return checkAlignmentAndPath(frameLocation, mapLocation.block.location)
    }

    /** Checkss which axes to iterate over, and run those checks. */
    private fun checkAlignmentAndPath(start: Location, end: Location): Boolean {
        if (start.world != end.world) return false

        if (start.x == end.x) {
            return checkPath(start, end, 'y', 'z')
        } else if (start.y == end.y) {
            return checkPath(start, end, 'x', 'z')
        } else if (start.z == end.z) {
            return checkPath(start, end, 'x', 'y')
        }
        return false
    }

    /**
     * Iterate over both axes and ensure that there are item frames on every location in between.
     */
    private fun checkPath(start: Location, end: Location, axis1: Char, axis2: Char): Boolean {
        val increment1 = if (start.getBlock(axis1) < end.getBlock(axis1)) 1 else -1
        val increment2 = if (start.getBlock(axis2) < end.getBlock(axis2)) 1 else -1

        var currentPos1 = start.getBlock(axis1)
        var currentPos2 = start.getBlock(axis2)

        while (currentPos1 != end.getBlock(axis1)) {
            currentPos1 += increment1

            val currentLocation = start.clone()
            currentLocation.setBlock(axis1, currentPos1)

            if (!locationHasItemFrame(currentLocation)) {
                return false
            }
        }

        while (currentPos2 != end.getBlock(axis2)) {
            currentPos2 += increment2

            val currentLocation = start.clone()
            currentLocation.setBlock(axis2, currentPos2)

            if (!locationHasItemFrame(currentLocation)) {
                return false
            }
        }
        return true
    }

    /** Get the axis value of a block location. */
    private fun Location.getBlock(axis: Char): Int {
        return when (axis) {
            'x' -> this.blockX
            'y' -> this.blockY
            'z' -> this.blockZ
            else -> throw IllegalArgumentException("Invalid axis")
        }
    }

    /** Set the axis value of a block location. */
    private fun Location.setBlock(axis: Char, value: Int) {
        when (axis) {
            'x' -> this.x = value.toDouble()
            'y' -> this.y = value.toDouble()
            'z' -> this.z = value.toDouble()
            else -> throw IllegalArgumentException("Invalid axis")
        }
    }

    /** Check a specified location for item frames. */
    private fun locationHasItemFrame(location: Location): Boolean {
        val entitiesAtLocation =
                location.world.getNearbyEntities(location.clone().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5)
        return entitiesAtLocation.any { entity -> entity is ItemFrame || entity is GlowItemFrame }
    }
}

class JobBoardListener : Listener {

    /** Handles right-clicking the job board. */
    @EventHandler
    fun onRightClickItemFrame(event: PlayerInteractAtEntityEvent) {
        val entity = event.rightClicked
        if (entity is ItemFrame) {
            val player = event.player
            if (dispatchViaIcon(player)) {
                event.setCancelled(true)
            } else {
                val jobBoards = SneakyJobBoard.getJobBoardManager().jobBoards

                jobBoards.forEach { jobBoard ->
                    if (jobBoard.interactable && jobBoard.isPartOfBoard(entity)) {
                        CommandJobBoard.openJobBoard(event.player)
                        event.setCancelled(true)
                        return
                    }
                }
            }
        }
    }

    /** Handles lazy spawning of job board icons. */
    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        val jobManager = SneakyJobBoard.getJobManager()
        jobManager.pendingSpawns.entries.removeIf { entry ->
            val jobBoard = entry.key
            if (jobBoard.mapLocation.chunk == event.chunk) {
                entry.value.forEach { job -> JobManager.spawnIcons(jobBoard, job) }
                true
            } else {
                false
            }
        }
    }

    /** Hide text displays to joining players. */
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        for (job in SneakyJobBoard.getJobManager().jobs.values) {
            for (entity in job.textDisplays.values) {
                event.player.hideEntity(SneakyJobBoard.getInstance(), entity)
            }
        }
    }

    /** Handle right-clicking the job board entities. */
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR)
                return

        val player = event.player
        event.setCancelled(dispatchViaIcon(player))
    }

    private fun dispatchViaIcon(player: Player): Boolean {
        val entity = SneakyJobBoard.getInstance().jobBoardUpdater.shownIcons[player]

        if (entity != null) {
            for (job in SneakyJobBoard.getJobManager().jobs.values) {
                if (job.textDisplays.containsValue(entity)) {
                    SneakyJobBoard.getJobManager().dispatch(job.uuid, player)
                    return true
                }
            }
        }
        return false
    }
}

class JobBoardUpdater : BukkitRunnable() {
    val shownIcons: MutableMap<Player, Entity> = mutableMapOf()

    override fun run() {
        var players: MutableList<Player> = mutableListOf()
        for (jobBoard in SneakyJobBoard.getJobBoardManager().jobBoards) {
            for (player in
                    jobBoard.mapLocation.world?.entities?.filterIsInstance<Player>()?.filter {
                        it.location.distanceSquared(jobBoard.mapLocation) <= 10.0 * 10.0
                    }
                            ?: emptyList()) {
                players.add(player)
            }
        }

        for (player in shownIcons.keys) {
            if (!players.contains(player)) {
                hide(player)
            }
        }

        for (player in players) {
            var entity = getLookedAtIcon(player)
            if (entity == null) {
                hide(player)
            } else {}
            if (entity !is TextDisplay) {
                for (job in SneakyJobBoard.getJobManager().jobs.values) {
                    if (job.itemDisplays.containsValue(entity)) {
                        entity =
                                job.textDisplays[
                                        job.itemDisplays.entries.first { it.value == entity }.key]
                        break
                    }
                }
            }
            if (entity != null) show(player, entity)
        }
    }

    /** Hide the textdisplay. */
    private fun hide(player: Player) {
        val entity: Entity? = shownIcons.remove(player)
        if (entity != null) player.hideEntity(SneakyJobBoard.getInstance(), entity)
    }

    /** Show the textdisplay. */
    private fun show(player: Player, entity: Entity) {
        if (shownIcons.get(player) == entity) return

        hide(player)
        shownIcons[player] = entity
        player.showEntity(SneakyJobBoard.getInstance(), entity)
    }

    /** Get the first JobBoardIcon that the player is looking at. */
    private fun getLookedAtIcon(player: Player): Entity? {
        val playerEyeLocation = player.eyeLocation
        val direction = playerEyeLocation.direction.normalize()
        val maxDistanceInBlocks = 5
        val stepSize = 0.3
        val maxSteps = (maxDistanceInBlocks / stepSize).toInt()

        var entity: Entity? = null

        for (step in 0 until maxSteps) {
            val targetLocation =
                    playerEyeLocation.clone().add(direction.clone().multiply(step * stepSize))
            entity = getLocationIcons(targetLocation, stepSize)

            if (entity != null) {
                break
            }
        }

        return entity
    }

    /** Get the first JobBoardIcon at the specified location. */
    private fun getLocationIcons(location: Location, stepSize: Double): Entity? {
        val nearbyEntities =
                location.world.getNearbyEntities(
                                location,
                                stepSize / 1.5,
                                stepSize / 1.5,
                                stepSize / 1.5
                        )
                        .filter { it.scoreboardTags.contains("JobBoardIcon") }
        return nearbyEntities.firstOrNull()
    }
}
