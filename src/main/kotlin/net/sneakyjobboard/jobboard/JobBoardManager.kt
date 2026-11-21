package net.sneakyjobboard.jobboard

import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.job.Job
import org.bukkit.*
import org.bukkit.block.BlockFace
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.*
import org.bukkit.entity.Display.Brightness
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.EventPriority
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldUnloadEvent
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapView
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import com.sneakyutils.SneakyUtils
import com.sneakyutils.triggers.Area
import com.sneakyutils.triggers.AreaHandler
import com.sneakyutils.triggers.AreaOptions
import com.sneakyutils.triggers.PlayerTick
import com.sneakyutils.triggers.PlayerTimer
import com.sneakyutils.triggers.PlayerTimerOptions
import com.sneakyutils.triggers.RiderPolicy
import com.sneakyutils.triggers.Subscription
import java.util.IdentityHashMap
import java.util.UUID
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Manages physical job boards in the world and their configurations.
 * Handles loading, spawning, and updating of job board displays.
 */
class JobBoardManager {

    val jobBoards = mutableListOf<JobBoard>()

    /**
     * Initializes the JobBoardManager and loads job boards from the configuration file.
     */
    init {
        parseConfig()
    }

    /**
     * Loads job boards from the configuration file.
     *
     * This method reads the configuration file, parses the specified
     * central vectors for maps and worlds, and constructs job board
     * instances from the parsed data. Errors encountered during the
     * loading process are logged.
     */
    private fun parseConfig() {
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
            val worldCentralVectorStrings: List<String> = config.getStringList("world-central-vectors")

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
                        val interactable = mapInteractables[i]
                        val scaleOverride = mapScaleOverrides[i]
                        val isometricAngle = isometricAngles[i]

                        // Find the last non-null worldVector
                        for (j in i downTo 0) {
                            if (j < worldCentralLocations.size && worldCentralLocations[j] != null) {
                                worldLocation = worldCentralLocations[j]
                                break
                            }
                        }

                        if (worldLocation == null || interactable == null || scaleOverride == null || isometricAngle == null) continue

                        jobBoards.add(
                            JobBoard(
                                mapLocation, worldLocation, interactable, scaleOverride, isometricAngle
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

/**
 * Represents a job board with its associated map and world locations,
 * interactability, scale, and isometric angle.
 *
 * @property mapLocation The location of the job board on the map.
 * @property worldLocation The location of the job board in the world.
 * @property interactable Indicates if the job board is interactable.
 * @property scale The scale of the map represented by this job board.
 * @property isometricAngle The angle of the job board in isometric projection.
 */
data class JobBoard(
    val mapLocation: Location,
    val worldLocation: Location,
    val interactable: Boolean,
    private var scale: Int,
    val isometricAngle: Double
) {
    private val attachedFace: BlockFace? by lazy {
        val itemFrame = mapLocation.world?.getNearbyEntities(
            mapLocation.clone().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5
        )?.firstOrNull {
            (it is ItemFrame) && it.location.blockX == mapLocation.blockX && it.location.blockY == mapLocation.blockY && it.location.blockZ == mapLocation.blockZ
        }

        if (itemFrame == null) {
            SneakyJobBoard.log(
                "One of the jobboards listed in map-central-vectors does not have an item frame on it: $mapLocation"
            )
            return@lazy null
        }

        (itemFrame as ItemFrame).attachedFace
    }

    private val frameRotation: Rotation? by lazy {
        val itemFrame = mapLocation.world?.getNearbyEntities(
            mapLocation.clone().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5
        )?.firstOrNull {
            (it is ItemFrame) && it.location.blockX == mapLocation.blockX && it.location.blockY == mapLocation.blockY && it.location.blockZ == mapLocation.blockZ
        }

        if (itemFrame == null) {
            SneakyJobBoard.log(
                "One of the jobboards listed in map-central-vectors does not have an item frame on it: $mapLocation"
            )
            return@lazy null
        }

        (itemFrame as ItemFrame).rotation
    }

    /**
     * Retrieves the Minecraft scale value of the map at the center job board position.
     *
     * If the scale is not already set, it checks for the associated item frame
     * and retrieves the scale based on the map view settings. If no valid scale
     * can be determined, it defaults to 128.
     *
     * @return The current scale of the job board.
     */
    private fun getScale(): Int {
        if (scale <= 0) {
            val itemFrame = mapLocation.world?.getNearbyEntities(
                mapLocation.clone().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5
            )?.firstOrNull {
                (it is ItemFrame) && it.location.blockX == mapLocation.blockX && it.location.blockY == mapLocation.blockY && it.location.blockZ == mapLocation.blockZ
            } as? ItemFrame

            if (itemFrame == null) {
                SneakyJobBoard.log(
                    "One of the job boards listed in map-central-vectors does not have an item frame on it: $mapLocation"
                )
                scale = 128
            } else {
                val frameItem = itemFrame.item
                if (frameItem.type != Material.FILLED_MAP) {
                    SneakyJobBoard.log(
                        "One of the job boards listed in map-central-vectors does not have a filled map item in the item frame: $mapLocation"
                    )
                    scale = 128
                } else {
                    val mapView = (frameItem.itemMeta as? MapMeta)?.mapView

                    if (mapView == null) {
                        SneakyJobBoard.log(
                            "One of the job boards listed in map-central-vectors does not have a valid map item in the item frame: $mapLocation"
                        )
                        scale = 128
                    } else {
                        scale = when (mapView.scale) {
                            MapView.Scale.CLOSE -> 256
                            MapView.Scale.NORMAL -> 512
                            MapView.Scale.FAR -> 1024
                            MapView.Scale.FARTHEST -> 2048
                            else -> 128
                        }
                    }
                }
            }
        }

        return scale
    }

    /**
     * Gets the axis that the job board is aligned along.
     *
     * This method determines whether the board is aligned along the x, y,
     * or z axis based on the attached face of the item frame.
     *
     * @return A character representing the aligned axis ('x', 'y', or 'z').
     */
    fun getAxis(): Char {
        return when (attachedFace) {
            BlockFace.NORTH, BlockFace.SOUTH -> 'z'
            BlockFace.WEST, BlockFace.EAST -> 'x'
            else -> 'y'
        }
    }

    /**
     * Gets the coordinate value where all item frames align on the associated axis.
     *
     * This method retrieves the coordinate value for the aligned axis,
     * which represents the position of all item frames connected to this job board.
     *
     * @return The coordinate value on the aligned axis.
     */
    fun getAxisIntersection(): Double {
        return when (attachedFace) {
            BlockFace.UP -> mapLocation.y() + 1
            BlockFace.NORTH -> mapLocation.z()
            BlockFace.EAST -> mapLocation.x() + 1
            BlockFace.SOUTH -> mapLocation.z() + 1
            BlockFace.WEST -> mapLocation.x()
            else -> mapLocation.y()
        }
    }

    /**
     * Checks if a specified item frame is part of this job board.
     *
     * This method determines if the given item frame is aligned with
     * this job board based on its location.
     *
     * @param itemFrame The item frame to check for alignment.
     * @return True if the item frame is part of this job board; otherwise, false.
     */
    fun isPartOfBoard(itemFrame: ItemFrame): Boolean {
        if (!mapLocation.chunk.isLoaded) return false

        return checkAlignmentAndPath(itemFrame.location.block.location, mapLocation.block.location)
    }

    /**
     * Checks alignment and path for the specified start and end locations.
     *
     * This method verifies if the start and end locations share the same world
     * and checks if they align along the job board's axis, performing further
     * checks along the path if they do.
     *
     * @param start The starting location.
     * @param end The ending location.
     * @return True if the alignment and path are valid; otherwise, false.
     */
    private fun checkAlignmentAndPath(start: Location, end: Location): Boolean {
        if (start.world != end.world) return false

        if (getAxis() == 'x' && start.x == end.x) {
            return checkPath(start, end, 'y', 'z')
        } else if (getAxis() == 'y' && start.y == end.y) {
            return checkPath(start, end, 'x', 'z')
        } else if (getAxis() == 'z' && start.z == end.z) {
            return checkPath(start, end, 'x', 'y')
        }

        return false
    }

    /**
     * Iterates over both axes and ensures that item frames are present at every location in between.
     *
     * This method checks each location along the path between the start and end
     * locations to ensure that item frames exist at all intermediary points.
     *
     * @param start The starting location.
     * @param end The ending location.
     * @param axis1 The first axis to iterate over.
     * @param axis2 The second axis to iterate over.
     * @return True if item frames are found at all locations along the path; otherwise, false.
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

    /**
     * Gets the block coordinate value for the specified axis of a location.
     *
     * This method retrieves the block coordinate (x, y, or z) from the given location
     * based on the specified axis.
     *
     * @param axis The axis for which to get the block coordinate ('x', 'y', or 'z').
     * @return The block coordinate value for the specified axis.
     * @throws IllegalArgumentException if the provided axis is invalid.
     */
    private fun Location.getBlock(axis: Char): Int {
        return when (axis) {
            'x' -> this.blockX
            'y' -> this.blockY
            'z' -> this.blockZ
            else -> throw IllegalArgumentException("Invalid axis")
        }
    }

    /**
     * Sets the block coordinate value for the specified axis of a location.
     *
     * This method updates the block coordinate (x, y, or z) of the given location
     * based on the specified axis.
     *
     * @param axis The axis for which to set the block coordinate ('x', 'y', or 'z').
     * @param value The new block coordinate value for the specified axis.
     * @throws IllegalArgumentException if the provided axis is invalid.
     */
    private fun Location.setBlock(axis: Char, value: Int) {
        when (axis) {
            'x' -> this.x = value.toDouble()
            'y' -> this.y = value.toDouble()
            'z' -> this.z = value.toDouble()
            else -> throw IllegalArgumentException("Invalid axis")
        }
    }

    /**
     * Checks if a specified location contains item frames.
     *
     * This method determines if there are any item frames located at the specified
     * location by checking nearby entities.
     *
     * @param location The location to check for item frames.
     * @return True if item frames are found at the location; otherwise, false.
     */
    private fun locationHasItemFrame(location: Location): Boolean {
        val entitiesAtLocation = location.world.getNearbyEntities(location.clone().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5)
        return entitiesAtLocation.any { entity -> entity is ItemFrame }
    }

    /**
     * Spawns icons on the job board for the specified job if it is valid and not expired.
     *
     * @param job The job for which to spawn icons.
     */
    fun spawnIcons(job: Job) {
        if (job.isExpired()) return
        if (job.location.world != worldLocation.world) return

        val displayLocation = getDisplayLocation(job)

        // Spawn the ItemDisplay
        val itemDisplayEntity: ItemDisplay = displayLocation.world.spawn(displayLocation, ItemDisplay::class.java)

		itemDisplayEntity.setItemStack(job.getIconItem())
        itemDisplayEntity.transformation = job.getTransformation()
        itemDisplayEntity.brightness = job.category.brightness

        itemDisplayEntity.addScoreboardTag("JobBoardIcon")

        job.itemDisplays[this] = itemDisplayEntity

        // Spawn the TextDisplay
        val textDisplayEntity: TextDisplay = displayLocation.world!!.spawn(displayLocation, TextDisplay::class.java)

        textDisplayEntity.brightness = Brightness(15, 15)
        textDisplayEntity.alignment = TextDisplay.TextAlignment.LEFT
        textDisplayEntity.backgroundColor = Color.fromARGB(200, 0, 0, 0)

        textDisplayEntity.addScoreboardTag("JobBoardIcon")

        job.textDisplays[this] = textDisplayEntity

        job.updateTextDisplays()

        for (player in Bukkit.getOnlinePlayers()) {
            player.hideEntity(SneakyJobBoard.getInstance(), textDisplayEntity)
        }
    }

    /**
     * Calculates the display location for the specified job on the job board.
     *
     * @param job The job for which to calculate the display location.
     * @return The calculated display location.
     */
    fun getDisplayLocation(job: Job): Location {
        val jobLocation = job.location
        val displayLocation = mapLocation.clone().add(0.5, 0.5, 0.5)

        val worldLocation = worldLocation.clone().add((getScale() / 2).toDouble(), 0.0, (getScale() / 2).toDouble())

        // Calculate correct horizontal and vertical offsets
        var horizOffset = (jobLocation.x - worldLocation.x) / getScale()
        var vertOffset = -(jobLocation.z - worldLocation.z) / getScale()

        // Apply isometry
        if (isometricAngle > 0) {
            val radianAngle = Math.toRadians(isometricAngle)

            val xTemp = horizOffset
            val yTemp = vertOffset

            horizOffset = xTemp * cos((Math.PI / 2) - radianAngle) + yTemp * sin(radianAngle) - 0.5
            vertOffset = -xTemp * sin((Math.PI / 2) - radianAngle) + yTemp * cos(radianAngle)

            vertOffset += (jobLocation.y - worldLocation.y) / getScale()
        }

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

        val xOffset: Double
        val yOffset: Double
        val zOffset: Double

        when (attachedFace) {
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

        // Nudge each job's displays a tiny, stable distance away from the board so overlapping icons don't z-fight
        val outward = attachedFace?.oppositeFace ?: BlockFace.UP
        val depth = getDepthOffset(job.uuid)
        displayLocation.add(outward.modX * depth, outward.modY * depth, outward.modZ * depth)

        return displayLocation
    }

    companion object {
        /** Maximum distance in blocks a job's displays are pushed away from the board surface. */
        const val MAX_DEPTH_OFFSET = 0.01

        /**
         * Returns a small pseudo-random offset in `[0, MAX_DEPTH_OFFSET)` derived from the job UUID.
         * Stable per job, so tracking teleports and respawns don't make icons jitter.
         */
        fun getDepthOffset(jobUuid: String): Double {
            val hash = jobUuid.hashCode().toLong() and 0xFFFFL
            return hash / 65536.0 * MAX_DEPTH_OFFSET
        }
    }
}

class JobBoardListener : Listener {

    /**
     * Handles right-click interactions with item frames.
     *
     * @param event The interaction event.
     */
    @EventHandler
    fun onRightClickItemFrame(event: PlayerInteractAtEntityEvent) {
        val entity = event.rightClicked
        if (entity is ItemFrame) {
            val player = event.player
            if (dispatchViaIcon(player)) {
                event.isCancelled = true
            } else {
                val jobBoards = SneakyJobBoard.getJobBoardManager().jobBoards

                jobBoards.forEach { jobBoard ->
                    if (jobBoard.interactable && jobBoard.isPartOfBoard(entity)) {
                        JobBoardMenu.open(player)
                        event.isCancelled = true
                        return
                    }
                }
            }
        }
    }

    /**
     * Handles the lazy spawning of job board icons when a chunk is loaded.
     *
     * @param event The chunk load event.
     */
    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        val jobManager = SneakyJobBoard.getJobManager()
        jobManager.pendingSpawns.entries.removeIf { entry ->
            val jobBoard = entry.key
            if (jobBoard.mapLocation.chunk == event.chunk) {
                entry.value.forEach { job -> jobBoard.spawnIcons(job) }
                true
            } else {
                false
            }
        }
    }

    /**
     * Hides text displays from players when they join the game.
     *
     * @param event The player join event.
     */
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        for (job in SneakyJobBoard.getJobManager().jobs.values) {
            for (entity in job.textDisplays.values) {
                event.player.hideEntity(SneakyJobBoard.getInstance(), entity)
            }
        }
    }

    /**
     * Handles interactions with the job board entities.
     *
     * @param event The player interaction event.
     */
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR) return

        val player = event.player
        event.setCancelled(dispatchViaIcon(player))
    }

    /**
     * Dispatches a player when they right-click a job board icon.
     *
     * @param player The player interacting with the icon.
     * @return True if dispatching occurred, false otherwise.
     */
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

/**
 * Shows each player near a job board the text of the icon they are looking at.
 *
 * Each board has a SneakyUtils area covering every block that reaches within [NEAR_RADIUS] of its map location.
 * Entering adds the player to a per-player timer that runs the look-at check; leaving the last board removes them
 * and hides their icons, so players away from boards cost nothing per tick.
 */
class JobBoardUpdater : Listener {
    val shownIcons = mutableMapOf<Player, TextDisplay>()

    /**
     * Boards each player is inside the area of, by player UUID, kept in board-config order (the old loop's order, so
     * the same board wins where areas overlap). Boards compare by identity: JobBoard is a data class.
     */
    private val members = HashMap<UUID, MutableList<JobBoard>>()

    /** Each board's position in the config, by identity. */
    private val boardOrder = IdentityHashMap<JobBoard, Int>()

    /** Each board's map world and world-location world, by name, so they can be rebound when the world loads again. */
    private val boardWorlds = IdentityHashMap<JobBoard, Pair<String, String>>()

    /** Area subscriptions by the name of the world the board's map is in. SneakyUtils closes them on world unload. */
    private val subscriptions = HashMap<String, MutableList<Subscription>>()
    private var timer: PlayerTimer? = null
    private var plugin: Plugin? = null

    /**
     * Subscribes the board areas and the look-at timer. Call on the server thread from onEnable, and register this
     * as a listener so board areas follow their world unloading and loading again.
     *
     * Interval 1 keeps the old every-tick responsiveness: right clicks dispatch the icon in [shownIcons], so a slower
     * timer could dispatch the icon looked at a tick earlier. Only players inside a board's area are in the timer.
     */
    fun start(plugin: Plugin, jobBoards: List<JobBoard>) {
        stop()
        this.plugin = plugin
        timer = SneakyUtils.getTriggers().everyForPlayers(
            plugin, 1, PlayerTick { tick(it) },
            PlayerTimerOptions.builder().label("sneakyjobboard:board-look").build()
        )
        jobBoards.forEachIndexed { index, jobBoard ->
            boardOrder[jobBoard] = index
            // parseConfig only builds boards in loaded worlds, so both worlds are readable here.
            boardWorlds[jobBoard] = jobBoard.mapLocation.world.name to jobBoard.worldLocation.world.name
            subscribe(jobBoard)
        }
    }

    /** Closes the subscriptions and forgets per-player state. */
    fun stop() {
        subscriptions.values.forEach { list -> list.forEach { it.close() } }
        subscriptions.clear()
        timer?.close()
        timer = null
        plugin = null
        members.clear()
        shownIcons.clear()
        boardOrder.clear()
        boardWorlds.clear()
    }

    private fun subscribe(jobBoard: JobBoard) {
        val owner = plugin ?: return
        val world = jobBoard.mapLocation.world
        val handler = AreaHandler.of(
            { player, _, _ -> enter(player, jobBoard) },
            { player, _, _ -> exit(player, jobBoard) }
        )
        val area = nearArea(world, jobBoard.mapLocation)
        subscriptions.getOrPut(world.name) { mutableListOf() } +=
            SneakyUtils.getTriggers().area(owner, area, handler, areaOptions())
    }

    /** SneakyUtils closes a world's areas when it unloads; drop ours and their memberships with them. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldUnload(event: WorldUnloadEvent) {
        val closed = subscriptions.remove(event.world.name) ?: return
        closed.forEach { it.close() }
        val gone = boardWorlds.entries.filter { it.value.first == event.world.name }.map { it.key }
        members.values.forEach { boards -> boards.removeIf { board -> gone.any { it === board } } }
    }

    /** A board world loaded again: rebind the boards to the new World and subscribe their areas again. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldLoad(event: WorldLoadEvent) {
        if (plugin == null) return
        val world = event.world
        for ((jobBoard, names) in boardWorlds) {
            if (names.second == world.name) jobBoard.worldLocation.world = world
            if (names.first == world.name) jobBoard.mapLocation.world = world
        }
        if (subscriptions.containsKey(world.name)) return
        boardOrder.entries.sortedBy { it.value }.forEach { (jobBoard, _) ->
            if (boardWorlds[jobBoard]?.first == world.name) subscribe(jobBoard)
        }
    }

    private fun enter(player: Player, jobBoard: JobBoard) {
        val boards = members.getOrPut(player.uniqueId) { ArrayList(2) }
        insertInOrder(boards, jobBoard) { boardOrder[it] ?: Int.MAX_VALUE }
        timer?.add(player)
    }

    private fun exit(player: Player, jobBoard: JobBoard) {
        val boards = members[player.uniqueId] ?: return
        boards.removeIf { it === jobBoard }
        if (boards.isNotEmpty()) return
        members.remove(player.uniqueId)
        timer?.remove(player)
        // Only the tracked icon can be visible: job texts are hidden from everyone at spawn and from each player at join.
        hide(player)
    }

    /** The old per-tick update, for one player inside at least one board area. */
    private fun tick(player: Player) {
        val boards = members[player.uniqueId]
        if (boards == null) {
            hide(player)
            return
        }
        val location = player.location
        var icon: TextDisplay? = null
        // The player's boards in config order, as the old loop visited them; the first with a looked-at icon wins.
        for (jobBoard in boards) {
            if (location.world != jobBoard.mapLocation.world) continue
            if (location.distanceSquared(jobBoard.mapLocation) > NEAR_RADIUS * NEAR_RADIUS) continue
            icon = getLookedAtIcon(jobBoard, player)
            if (icon != null) break
        }
        if (icon == null) hide(player) else show(player, icon)
    }

    companion object {
        /** Players within this many blocks of a board's map location see the text of the icon they look at. */
        const val NEAR_RADIUS = 10.0

        private fun areaOptions(): AreaOptions = AreaOptions.builder()
            .riders(RiderPolicy.ALLOW)
            .includeSpectators(true)
            .fireOnRegister(true)
            .label("sneakyjobboard:board-near")
            .build()

        /** Adds [item] (by identity, once) to [list], which is kept sorted by [order]; equal orders keep arrival order. */
        fun <T> insertInOrder(list: MutableList<T>, item: T, order: (T) -> Int) {
            if (list.any { it === item }) return
            val rank = order(item)
            var at = list.size
            while (at > 0 && order(list[at - 1]) > rank) at--
            list.add(at, item)
        }

        /** The board's area: every block that reaches within [NEAR_RADIUS] of [center]. */
        fun nearArea(world: World, center: Location): Area {
            val cx = center.x
            val cy = center.y
            val cz = center.z
            return Area.custom(
                world,
                floor(cx - NEAR_RADIUS).toInt(), floor(cy - NEAR_RADIUS).toInt(), floor(cz - NEAR_RADIUS).toInt(),
                floor(cx + NEAR_RADIUS).toInt(), floor(cy + NEAR_RADIUS).toInt(), floor(cz + NEAR_RADIUS).toInt(),
            ) { x, y, z -> blockReaches(x, y, z, cx, cy, cz) }
        }

        /**
         * Whether the block holding position ([x], [y], [z]) has a point within [NEAR_RADIUS] of the centre.
         *
         * The area tests players only when their block changes, so its shape is decided per block: a superset of the
         * exact sphere. The timer then applies the exact distance, as the old code did every tick.
         */
        fun blockReaches(x: Double, y: Double, z: Double, cx: Double, cy: Double, cz: Double): Boolean {
            val dx = axisGap(floor(x), cx)
            val dy = axisGap(floor(y), cy)
            val dz = axisGap(floor(z), cz)
            return dx * dx + dy * dy + dz * dz <= NEAR_RADIUS * NEAR_RADIUS
        }

        /** Distance from [c] to the block span [[b], [b] + 1] on one axis (0 inside it). */
        private fun axisGap(b: Double, c: Double): Double = when {
            c < b -> b - c
            c > b + 1 -> c - (b + 1)
            else -> 0.0
        }
    }

    /**
     * Hides the text display for the given player.
     *
     * @param player The player whose display to hide.
     */
    private fun hide(player: Player) {
        val entity: Entity? = shownIcons.remove(player)
        if (entity != null) player.hideEntity(SneakyJobBoard.getInstance(), entity)
    }

    /**
     * Shows a text display entity to a player.
     *
     * @param player The player to show the display to.
     * @param entity The text display entity to show.
     */
    private fun show(player: Player, entity: TextDisplay) {
        if (shownIcons[player] == entity) return

        hide(player)
        shownIcons[player] = entity
        player.showEntity(SneakyJobBoard.getInstance(), entity)
    }

    /**
     * Gets the first job board icon the player is looking at.
     *
     * @param jobBoard The job board to check against.
     * @param player The player whose line of sight to check.
     * @return The first text display entity the player is looking at, or null if none found.
     */
    private fun getLookedAtIcon(jobBoard: JobBoard, player: Player): TextDisplay? {
        val playerEyeLocation = player.eyeLocation
        val direction = playerEyeLocation.direction.normalize()

        val axis = jobBoard.getAxis()

        // Determine the coordinate value of the axis intersection point
        val axisIntersection = jobBoard.getAxisIntersection()

        // Calculate the distance along the player's line of sight direction to the axis
        // intersection point
        val distanceToIntersection = when (axis) {
            'x' -> (axisIntersection - playerEyeLocation.x) / direction.x
            'y' -> (axisIntersection - playerEyeLocation.y) / direction.y
            'z' -> (axisIntersection - playerEyeLocation.z) / direction.z
            else -> return null
        }

        if (distanceToIntersection < 0) return null

        val intersectionPoint = playerEyeLocation.clone().add(direction.multiply(distanceToIntersection))

        val nearbyEntities = intersectionPoint.world?.getNearbyEntities(intersectionPoint, 0.3, 0.3, 0.3)
            ?.filterIsInstance<TextDisplay>()?.filter { it.scoreboardTags.contains("JobBoardIcon") }
            ?.sortedBy { it.location.distanceSquared(intersectionPoint) }

        return nearbyEntities?.firstOrNull()
    }
}

/**
 * Performs periodic maintenance on job board displays.
 * Handles cleanup of orphaned entities and updates duration-based scaling.
 */
class JobBoardMaintenance : BukkitRunnable() {

    /**
     * Runs the maintenance tasks for job board displays.
     */
    override fun run() {
        // Collect JobBoardIcon Displays near each board (icons only spawn on the map plane)
        val displays = mutableSetOf<Entity>()
        val orphanScanRadius = 48.0

        for (jobBoard in SneakyJobBoard.getJobBoardManager().jobBoards) {
            val center = jobBoard.mapLocation.clone().add(0.5, 0.5, 0.5)
            val world = center.world ?: continue

            displays.addAll(
                world.getNearbyEntities(center, orphanScanRadius, orphanScanRadius, orphanScanRadius)
                    .filterIsInstance<Display>()
                    .filter { it.scoreboardTags.contains("JobBoardIcon") }
            )
        }

        // If any of these entities do not belong to a listed job, remove them
        SneakyJobBoard.getJobManager().jobs.values.forEach { job ->
            job.itemDisplays.entries.removeIf { !it.value.isValid }
            job.textDisplays.entries.removeIf { !it.value.isValid }

            displays.removeAll(job.itemDisplays.values.toSet())
            displays.removeAll(job.textDisplays.values.toSet())
        }

        displays.forEach(Entity::remove)

        // Update duration-based scaling
        if (SneakyJobBoard.getInstance().getConfig().getLong("duration-scale-base-duration") > 0) {
            for (job in SneakyJobBoard.getJobManager().getJobs()) {
                val newTransformation = job.getTransformation()

                job.itemDisplays.values.forEach { it.transformation = newTransformation }
            }
        }
    }
}

/**
 * Updates the locations of tracking jobs periodically.
 * Ensures job displays and markers follow their owners' movements.
 */
class TrackingJobsUpdater : BukkitRunnable() {

    /**
     * Runs the update cycle for tracking jobs.
     */
    override fun run() {
        for (job in SneakyJobBoard.getJobManager().getJobs()) {
            val poster = job.player ?: continue
            if (job.tracking && poster.isOnline && poster.location.world == job.location.world) {
                job.location = poster.location

                for ((jobBoard, itemDisplay) in job.itemDisplays) {
                    itemDisplay.teleport(jobBoard.getDisplayLocation(job))
                }

                for ((jobBoard, textDisplay) in job.textDisplays) {
                    textDisplay.teleport(jobBoard.getDisplayLocation(job))
                }

                if (SneakyJobBoard.isDynmapActive()) {
                    val markerAPI = SneakyJobBoard.getInstance().markerAPI

                    val markerSet = markerAPI?.getMarkerSet("SneakyJobBoard") ?: run {
                        markerAPI?.createMarkerSet(
                            "SneakyJobBoard", "SneakyJobBoard", null, false
                        ) ?: run {
                            SneakyJobBoard.log(
                                "Failed to create a new marker set."
                            )
                            null
                        }
                    }

                    markerSet?.findMarker(job.uuid)?.setLocation(
                        job.location.world.name, job.location.x, job.location.y, job.location.z
                    )
                }
            }
        }
    }
}
