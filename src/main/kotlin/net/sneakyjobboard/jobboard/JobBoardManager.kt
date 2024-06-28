package net.sneakyjobboard.jobboard

import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.commands.CommandJobBoard
import net.sneakyjobboard.job.Job
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Rotation
import org.bukkit.block.BlockFace
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Display
import org.bukkit.entity.Display.Brightness
import org.bukkit.entity.Entity
import org.bukkit.entity.GlowItemFrame
import org.bukkit.entity.ItemDisplay
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
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapView
import org.bukkit.scheduler.BukkitRunnable

/** Manages jobboards and their configurations. */
class JobBoardManager {

    public val jobBoards = mutableListOf<JobBoard>()

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
        private var scale: Int,
        val isometricAngle: Double
) {
    val attachedFace: BlockFace? by lazy {
        val itemFrame =
                mapLocation.world?.getNearbyEntities(
                                mapLocation.clone().add(0.5, 0.5, 0.5),
                                0.5,
                                0.5,
                                0.5
                        )
                        ?.firstOrNull {
                            (it is ItemFrame || it is GlowItemFrame) &&
                                    it.location.blockX == mapLocation.blockX &&
                                    it.location.blockY == mapLocation.blockY &&
                                    it.location.blockZ == mapLocation.blockZ
                        }

        if (itemFrame == null) {
            SneakyJobBoard.log(
                    "One of the jobboards listed in map-central-vectors does not have an item frame on it: ${mapLocation.toString()}"
            )
            return@lazy null
        }

        (itemFrame as ItemFrame).attachedFace
    }

    val frameRotation: Rotation? by lazy {
        val itemFrame =
                mapLocation.world?.getNearbyEntities(
                                mapLocation.clone().add(0.5, 0.5, 0.5),
                                0.5,
                                0.5,
                                0.5
                        )
                        ?.firstOrNull {
                            (it is ItemFrame || it is GlowItemFrame) &&
                                    it.location.blockX == mapLocation.blockX &&
                                    it.location.blockY == mapLocation.blockY &&
                                    it.location.blockZ == mapLocation.blockZ
                        }

        if (itemFrame == null) {
            SneakyJobBoard.log(
                    "One of the jobboards listed in map-central-vectors does not have an item frame on it: ${mapLocation.toString()}"
            )
            return@lazy null
        }

        (itemFrame as ItemFrame).rotation
    }

    fun getScale(): Int {
        if (scale <= 0) {
            val itemFrame =
                    mapLocation.world?.getNearbyEntities(
                                    mapLocation.clone().add(0.5, 0.5, 0.5),
                                    0.5,
                                    0.5,
                                    0.5
                            )
                            ?.firstOrNull {
                                (it is ItemFrame || it is GlowItemFrame) &&
                                        it.location.blockX == mapLocation.blockX &&
                                        it.location.blockY == mapLocation.blockY &&
                                        it.location.blockZ == mapLocation.blockZ
                            } as?
                            ItemFrame

            if (itemFrame == null) {
                SneakyJobBoard.log(
                        "One of the job boards listed in map-central-vectors does not have an item frame on it: ${mapLocation.toString()}"
                )
                scale = 128
            } else {
                val frameItem = itemFrame.item
                if (frameItem.type != Material.FILLED_MAP) {
                    SneakyJobBoard.log(
                            "One of the job boards listed in map-central-vectors does not have a filled map item in the item frame: ${mapLocation.toString()}"
                    )
                    scale = 128
                } else {
                    val mapView = (frameItem.itemMeta as? MapMeta)?.mapView

                    if (mapView == null) {
                        SneakyJobBoard.log(
                                "One of the job boards listed in map-central-vectors does not have a valid map item in the item frame: ${mapLocation.toString()}"
                        )
                        scale = 128
                    } else {
                        scale =
                                when (mapView.scale) {
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

    /** Gets the axis that the board is aligned across. */
    fun getAxis(): Char {
        return when (attachedFace) {
            BlockFace.NORTH, BlockFace.SOUTH -> 'z'
            BlockFace.WEST, BlockFace.EAST -> 'x'
            else -> 'y'
        }
    }

    /** Gets the coordinate value that all item frames have on the aligned axis. */
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

    /** Checks if an item frame is part of this job board. */
    fun isPartOfBoard(itemFrame: ItemFrame): Boolean {
        if (!mapLocation.chunk.isLoaded) return false

        return checkAlignmentAndPath(itemFrame.location.block.location, mapLocation.block.location)
    }

    /** Checkss which axes to iterate over, and run those checks. */
    private fun checkAlignmentAndPath(start: Location, end: Location): Boolean {
        if (start.world != end.world) return false

        if (getAxis().equals('x') && start.x == end.x) {
            return checkPath(start, end, 'y', 'z')
        } else if (getAxis().equals('y') && start.y == end.y) {
            return checkPath(start, end, 'x', 'z')
        } else if (getAxis().equals('z') && start.z == end.z) {
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

    /** Spawn a jobs icons on this JobBoards. */
    fun spawnIcons(job: Job) {
        if (job.isExpired()) return
        if (job.location.world != worldLocation.world) return

        val displayLocation = getDisplayLocation(job)

        // Spawn the ItemDisplay
        val itemDisplayEntity: ItemDisplay =
                displayLocation.world.spawn(displayLocation, ItemDisplay::class.java)

        itemDisplayEntity.setItemStack(job.getIconItem())
        itemDisplayEntity.setTransformation(job.getTransformation())
        itemDisplayEntity.setBrightness(job.category.brightness)

        itemDisplayEntity.addScoreboardTag("JobBoardIcon")

        job.itemDisplays.put(this, itemDisplayEntity)

        // Spawn the TextDisplay
        val textDisplayEntity: TextDisplay =
                displayLocation.world!!.spawn(displayLocation, TextDisplay::class.java)

        textDisplayEntity.setBrightness(Brightness(15, 15))
        textDisplayEntity.setAlignment(TextDisplay.TextAlignment.LEFT)

        textDisplayEntity.addScoreboardTag("JobBoardIcon")

        job.textDisplays.put(this, textDisplayEntity)

        job.updateTextDisplays()

        for (player in Bukkit.getOnlinePlayers()) {
            player.hideEntity(SneakyJobBoard.getInstance(), textDisplayEntity)
        }
    }

    fun getDisplayLocation(job: Job): Location {
        val jobLocation = job.location
        val displayLocation = mapLocation.clone().add(0.5, 0.5, 0.5)

        val worldLocation =
                worldLocation
                        .clone()
                        .add((getScale() / 2).toDouble(), 0.0, (getScale() / 2).toDouble())

        // Calculate correct horizontal and vertical offsets
        var horizOffset = (jobLocation.x - worldLocation.x) / getScale()
        var vertOffset = -(jobLocation.z - worldLocation.z) / getScale()

        // Apply isometry
        if (isometricAngle > 0) {
            val radianAngle = Math.toRadians(isometricAngle)

            val xTemp = horizOffset
            val yTemp = vertOffset

            horizOffset =
                    xTemp * Math.cos((Math.PI / 2) - radianAngle) + yTemp * Math.sin(radianAngle) -
                            0.5
            vertOffset =
                    -xTemp * Math.sin((Math.PI / 2) - radianAngle) + yTemp * Math.cos(radianAngle)

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

        var xOffset: Double
        var yOffset: Double
        var zOffset: Double

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

        return displayLocation
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
                entry.value.forEach { job -> jobBoard.spawnIcons(job) }
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

    /** Dispatch a player via right-clicking a map icon. */
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
    val shownIcons = mutableMapOf<Player, TextDisplay>()

    override fun run() {
        val players = mutableMapOf<JobBoard, MutableList<Player>>()

        // Build a map of players who are nearby a JobBoard
        for (jobBoard in SneakyJobBoard.getJobBoardManager().jobBoards) {
            val nearbyPlayers =
                    jobBoard.mapLocation.world?.entities?.filterIsInstance<Player>()?.filter {
                        it.location.distanceSquared(jobBoard.mapLocation) <= 100.0
                    }
                            ?: emptyList()

            players[jobBoard] = nearbyPlayers.toMutableList()
        }

        // Hide icons for players who are no longer nearby
        for (player in shownIcons.keys) {
            val isInProximity = players.any { it.value.contains(player) }
            if (!isInProximity) {
                hide(player)
            }
        }

        val lookedAtIcons = mutableMapOf<Player, TextDisplay?>()

        // Find the icon that each of these players is looking at, or null
        for ((jobBoard, playerList) in players) {
            for (player in playerList) {
                if (lookedAtIcons[player] != null) continue

                var entity = getLookedAtIcon(jobBoard, player)

                if (entity != null || !lookedAtIcons.keys.contains(player)) {
                    lookedAtIcons[player] = entity
                }
            }
        }

        // Show the looked at icon to the player if it exists
        for ((player, icon) in lookedAtIcons) {
            if (icon == null) {
                hide(player)
            } else {
                show(player, icon)
            }
        }
    }

    /** Hide the textdisplay. */
    private fun hide(player: Player) {
        val entity: Entity? = shownIcons.remove(player)
        if (entity != null) player.hideEntity(SneakyJobBoard.getInstance(), entity)
    }

    /** Show the textdisplay. */
    private fun show(player: Player, entity: TextDisplay) {
        if (shownIcons.get(player) == entity) return

        hide(player)
        shownIcons[player] = entity
        player.showEntity(SneakyJobBoard.getInstance(), entity)
    }

    /** Get the first JobBoardIcon that the player is looking at. */
    private fun getLookedAtIcon(jobBoard: JobBoard, player: Player): TextDisplay? {
        val playerEyeLocation = player.eyeLocation
        val direction = playerEyeLocation.direction.normalize()

        val axis = jobBoard.getAxis()

        // Determine the coordinate value of the axis intersection point
        val axisIntersection = jobBoard.getAxisIntersection()

        // Calculate the distance along the player's line of sight direction to the axis
        // intersection point
        val distanceToIntersection =
                when (axis) {
                    'x' -> (axisIntersection - playerEyeLocation.x) / direction.x
                    'y' -> (axisIntersection - playerEyeLocation.y) / direction.y
                    'z' -> (axisIntersection - playerEyeLocation.z) / direction.z
                    else -> return null
                }

        if (distanceToIntersection < 0) return null

        val intersectionPoint =
                playerEyeLocation.clone().add(direction.multiply(distanceToIntersection))

        val nearbyEntities =
                intersectionPoint
                        .world
                        ?.getNearbyEntities(intersectionPoint, 0.3, 0.3, 0.3)
                        ?.filterIsInstance<TextDisplay>()
                        ?.filter { it.scoreboardTags.contains("JobBoardIcon") }
                        ?.sortedBy { it.location.distanceSquared(intersectionPoint) }

        return nearbyEntities?.firstOrNull()
    }
}

class JobBoardMaintenance : BukkitRunnable() {
    override fun run() {
        // Build a list of all Display Entities that have the JobBoardIcon tag
        val worlds =
                SneakyJobBoard.getJobBoardManager().jobBoards.map { it.mapLocation.world }.toSet()

        val displays = mutableSetOf<Entity>()
        for (world in worlds) {
            displays.addAll(
                    world.entities.filterIsInstance<Display>().filter {
                        it.scoreboardTags.contains("JobBoardIcon")
                    }
            )
        }

        // If any of these entities do not belong to a listed job, remove them
        SneakyJobBoard.getJobManager().jobs.values.forEach { job ->
            displays.removeAll(job.itemDisplays.values)
            displays.removeAll(job.textDisplays.values)
        }

        displays.forEach(Entity::remove)

        // Build a list of players who aren't near a JobBoard
        val players = Bukkit.getServer().onlinePlayers.toMutableSet()

        for (jobBoard in SneakyJobBoard.getJobBoardManager().jobBoards) {
            val nearbyPlayers =
                    jobBoard.mapLocation.world?.players?.filter {
                        it.location.distanceSquared(jobBoard.mapLocation) <= 100.0
                    }
                            ?: emptyList()

            players.removeAll(nearbyPlayers)
        }

        // Ensure that TextDisplay icons are hidden to them
        val textDisplays =
                SneakyJobBoard.getJobManager().getJobs().flatMap { it.textDisplays.values }
        players.forEach { player ->
            textDisplays.forEach { textDisplay ->
                player.hideEntity(SneakyJobBoard.getInstance(), textDisplay)
            }
        }

        // Update duration-based scaling
        if (SneakyJobBoard.getInstance().getConfig().getLong("duration-scale-base-duration") > 0) {
            for (job in SneakyJobBoard.getJobManager().getJobs()) {
                val newTransformation = job.getTransformation()

                job.itemDisplays.values.forEach { it.setTransformation(newTransformation) }
            }
        }
    }
}

class TrackingJobsUpdater : BukkitRunnable() {
    override fun run() {
        for (job in SneakyJobBoard.getJobManager().getJobs()) {
            if (job.tracking &&
                            job.player.isOnline() &&
                            job.player.location.world == job.location.world
            ) {
                job.location = job.player.location

                for ((jobBoard, itemDisplay) in job.itemDisplays) {
                    itemDisplay.teleport(jobBoard.getDisplayLocation(job))
                }

                for ((jobBoard, textDisplay) in job.textDisplays) {
                    textDisplay.teleport(jobBoard.getDisplayLocation(job))
                }

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
                                                    SneakyJobBoard.log(
                                                            "Failed to create a new marker set."
                                                    )
                                                    null
                                                }
                                    }

                    markerSet
                            ?.findMarker(job.uuid)
                            ?.setLocation(
                                    job.location.world.name,
                                    job.location.x,
                                    job.location.y,
                                    job.location.z
                            )
                }
            }
        }
    }
}
