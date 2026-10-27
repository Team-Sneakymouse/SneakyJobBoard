package net.sneakyjobboard

import com.sneakydb.SneakyDBProvider
import com.destroystokyo.paper.ClientOption
import com.google.gson.Gson
import com.google.gson.JsonObject
import me.clip.placeholderapi.PlaceholderAPI
import com.sneakyutils.util.TextUtility
import net.sneakyjobboard.job.Job
import net.sneakyjobboard.job.JobHistoryInventoryHolder
import net.sneakyjobboard.advert.Advert
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import java.awt.Graphics2D
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import java.net.URL
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO

/** Captures Bukkit state on the server thread; only immutable SQL values and images leave it. */
class DatabaseManager(private val store: JobBoardRepository = JobBoardRepository(
    SneakyDBProvider.getApi(), SneakyJobBoard.getInstance().config.getString("database-schema", "lom")!!, Bukkit.getPort()
)) : AutoCloseable {
    private val plugin = SneakyJobBoard.getInstance()
    private val gson = Gson()
    private val boot = ManagementFactory.getRuntimeMXBean().startTime
    @Volatile private var closed = false
    private val historyRequests = mutableMapOf<UUID, Long>()
    private val advertRequests = mutableMapOf<UUID, Long>()
    private var sequence = 0L
    private val locallyEndedJobs = mutableSetOf<String>()
    private val locallyDeletedAdverts = mutableSetOf<String>()

    init {
        store.whenReady().whenComplete { _, error ->
            if (error != null) report("initialization/preflight")
            else if (!closed) reconcileOpenJobs()
        }
    }
    private fun report(operation: String) {
        // Do not include JDBC exceptions, SQL parameters, credentials or player data in logs.
        if (!closed) plugin.logger.warning("Job board database $operation failed; check SneakyDB/operator schema readiness. No automatic write retry.")
    }
    private fun main(action: () -> Unit) {
        if (closed) return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!closed && SneakyJobBoard.getInstance() === plugin) action()
        })
    }
    private fun observe(future: CompletableFuture<*>, operation: String) {
        future.whenComplete { _, error -> if (error != null) report(operation) }
    }
    private fun owns(row: JsonObject): Boolean = row.get("serverPort")?.takeUnless { it.isJsonNull }?.asString
        ?.toBigDecimalOrNull()?.let { it.signum() != 0 && it.compareTo(store.port.toBigDecimal()) == 0 } == true
    private fun json(rows: List<Map<String, Any?>>) = rows.map { gson.toJsonTree(it).asJsonObject }
    private fun value(row: JsonObject, key: String): String? = row.get(key)?.takeUnless { it.isJsonNull }?.asString
    private fun bool(row: JsonObject, key: String) = value(row, key)?.toBooleanStrictOrNull() ?: false
    private fun millis(row: JsonObject, key: String): Long? =
        value(row, key)?.toBigDecimalOrNull()?.let { runCatching { it.longValueExact() }.getOrNull() }

    fun listJob(job: Job) {
        if (closed) return
        if (job.recordID.isEmpty()) job.recordID = JobBoardRepository.newId()
        val snapshot = createJobDataMap(job).filterKeys { it != "serverPort" }
        val poster = requireNotNull(job.player)
        val url = poster.playerProfile.textures.skin
        @Suppress("UNNECESSARY_SAFE_CALL")
        val hats = poster.getClientOption(ClientOption.SKIN_PARTS)?.hasHatsEnabled() ?: false
        val icon = if (url == null) CompletableFuture.completedFuture(DEFAULT_ICON) else CompletableFuture.supplyAsync {
            runCatching { downloadImage(url)?.let { encodeImageToBase64(createPlayerIcon(it, hats)) } }.getOrNull() ?: DEFAULT_ICON
        }
        observe(store.insert(JobBoardRepository.JOBS, job.recordID, snapshot, icon), "job insert")
    }
    fun unlistJob(job: Job, endReason: String) {
        if (!closed && job.recordID.isNotEmpty()) endJobRecord(job.recordID, endReason)
    }
    private fun endJobRecord(id: String, reason: String) {
        locallyEndedJobs.add(id)
        observe(store.update(JobBoardRepository.JOBS, id, mapOf("endTime" to System.currentTimeMillis(), "endReason" to reason)), "job end")
    }
    fun reconcileOpenJobs() {
        if (closed) return
        store.reconcile(boot).whenComplete { rows, error ->
            if (error != null) report("reconciliation") else main {
                for (item in json(rows)) {
                    if (!owns(item)) continue
                    try {
                        val id = value(item, "id") ?: continue
                        if (id in locallyEndedJobs) continue
                        val persistent = bool(item, "persist")
                        val start = millis(item, "startTime") ?: continue
                        if (start >= boot && !persistent) continue
                        if (!persistent) { endJobRecord(id, "restart"); continue }
                        val duration = millis(item, "durationMillis") ?: continue
                        val expired = duration <= 0 || start <= System.currentTimeMillis() - duration
                        if (expired) { endJobRecord(id, "expired"); continue }
                        val category = SneakyJobBoard.getJobCategoryManager().getJobCategories().values.find { it.name == value(item, "category") }
                        val location = value(item, "location")?.let { parseStoredLocation(it) }
                        if (category == null || location == null) {
                            if (start < boot) endJobRecord(id, "restart")
                            continue
                        }
                        val uuid = value(item, "uuid") ?: continue
                        val manager = SneakyJobBoard.getJobManager()
                        if (manager.jobs.containsKey(uuid) || manager.jobs.values.any { it.recordID == id }) continue
                        val poster = value(item, "poster")
                        val job = Job(category, poster?.let { Bukkit.getPlayerExact(it) }, location, duration, bool(item, "tracking"), true, poster).apply {
                            this.uuid = uuid; recordID = id; startTime = start
                            value(item, "name")?.let { name = it }; value(item, "description")?.let { description = it }
                        }
                        manager.list(job, sendToDatabase = false)
                    } catch (_: Exception) { report("reconciliation row") }
                }
                // Re-enable must also restore adverts for players already connected, not just future joins.
                Bukkit.getOnlinePlayers().forEach { getAdvertHistory(it.uniqueId.toString()) }
            }
        }
    }
    fun getJobHistory(player: Player, durationMillis: Long) {
        if (closed) return
        val request = ++sequence
        historyRequests[player.uniqueId] = request
        val name = player.name
        store.history(name).whenComplete { rows, error ->
            if (error != null) report("job history") else main {
                if (!player.isOnline || Bukkit.getPlayer(player.uniqueId) !== player || historyRequests[player.uniqueId] != request) return@main
                val jobs = mutableListOf<Job>()
                val seen = mutableSetOf<List<String>>()
                for (raw in rows) {
                    if (jobs.size >= 9) break
                    val row = gson.toJsonTree(raw).asJsonObject
                    if (!owns(row)) continue
                    val categoryName = value(row, "category") ?: continue
                    val nameValue = value(row, "name") ?: continue
                    val description = value(row, "description") ?: continue
                    val tracking = bool(row, "tracking")
                    if (!seen.add(listOf(categoryName, nameValue, description, tracking.toString()))) continue
                    val categories = SneakyJobBoard.getJobCategoryManager().getJobCategories().values
                    val category = categories.find { it.name == categoryName } ?: categories.firstOrNull() ?: continue
                    jobs.add(Job(category, player, player.location, durationMillis, tracking).apply { this.name = nameValue; this.description = description })
                }
                if (jobs.isEmpty()) player.sendMessage(TextUtility.convertToComponent("&4You do not have a job posting history."))
                else player.openInventory(JobHistoryInventoryHolder(jobs).inventory)
            }
        }
    }
    private fun advertData(advert: Advert): Map<String, Any> = linkedMapOf(
        "uuid" to advert.uuid, "category" to (advert.category?.name ?: ""), "posteruuid" to advert.player.uniqueId.toString(),
        "posterDisplayString" to advert.posterString, "name" to advert.name, "description" to advert.description,
        "iconMaterial" to (advert.iconMaterial?.name ?: ""), "iconCustomModelData" to (advert.storedIconCustomModelData ?: advert.iconCustomModelData ?: 0),
        "enabled" to advert.enabled, "deleted" to advert.deleted
    )
    fun listAdvert(advert: Advert) {
        if (closed) return
        if (advert.recordID.isEmpty()) advert.recordID = JobBoardRepository.newId()
        // Existing local UUIDs win when an older history read completes.
        observe(store.insert(JobBoardRepository.ADVERTS, advert.recordID, advertData(advert)), "advert insert")
    }
    fun updateAdvert(advert: Advert) {
        if (closed || advert.recordID.isEmpty()) return
        if (advert.deleted) locallyDeletedAdverts.add(advert.recordID)
        else locallyDeletedAdverts.remove(advert.recordID)
        observe(store.update(JobBoardRepository.ADVERTS, advert.recordID, advertData(advert)), "advert update")
    }
    fun getAdvertHistory(playerUUID: String) {
        if (closed) return
        val uuid = UUID.fromString(playerUUID)
        val player = Bukkit.getPlayer(uuid) ?: return
        val request = ++sequence
        advertRequests[uuid] = request
        store.adverts(playerUUID).whenComplete { rows, error ->
            if (error != null) report("advert history") else main {
                if (!player.isOnline || Bukkit.getPlayer(uuid) !== player || advertRequests[uuid] != request) return@main
                val seen = mutableSetOf<List<String>>()
                for (row in json(rows)) {
                    if (!owns(row) || bool(row, "deleted") || value(row, "posteruuid") != playerUUID) continue
                    try {
                        val category = value(row, "category") ?: ""
                        val name = value(row, "name") ?: ""
                        val description = value(row, "description") ?: ""
                        if (!seen.add(listOf(category, name, description))) continue
                        val id = value(row, "id") ?: continue
                        if (id in locallyDeletedAdverts) continue
                        val advertUuid = value(row, "uuid") ?: continue
                        val adverts = SneakyJobBoard.getAdvertManager()
                        if (adverts.getAdvert(advertUuid) != null) continue
                        val advert = Advert(SneakyJobBoard.getAdvertCategoryManager().getCategory(category), player).apply {
                            this.uuid = advertUuid; recordID = id; this.name = name; this.description = description
                            posterString = value(row, "posterDisplayString") ?: ""
                            iconMaterial = Material.matchMaterial(value(row, "iconMaterial") ?: "")
                            iconCustomModelData = value(row, "iconCustomModelData")?.toBigDecimalOrNull()?.toInt() ?: 0
                            storedIconCustomModelData = value(row, "iconCustomModelData")?.toBigDecimalOrNull()
                            enabled = bool(row, "enabled")
                        }
                        adverts.list(advert, false)
                    } catch (_: Exception) { report("advert row") }
                }
            }
        }
    }
    override fun close() { closed = true; store.close(); historyRequests.clear(); advertRequests.clear(); locallyEndedJobs.clear(); locallyDeletedAdverts.clear() }
    private fun parseStoredLocation(text: String): org.bukkit.Location? {
        val world = Regex("name=([^}]+)").find(text)?.groupValues?.get(1)?.let { Bukkit.getWorld(it) } ?: return null
        fun coordinate(name: String) = Regex("$name=([-\\d.]+)").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        return org.bukkit.Location(world, coordinate("x") ?: return null, coordinate("y") ?: return null, coordinate("z") ?: return null)
    }

    private fun createJobDataMap(job: Job): Map<String, Any> {
        val poster = requireNotNull(job.player)

        // Poster display string
        var displayStringPoster = (SneakyJobBoard.getInstance().getConfig().getString("database-poster")
            ?: "[playerName]").replace("[playerName]", poster.name)

        if (SneakyJobBoard.isPapiActive()) {
            displayStringPoster = PlaceholderAPI.setPlaceholders(poster, displayStringPoster)
        }

        // Location display string
        var displayStringLocation =
            (SneakyJobBoard.getInstance().getConfig().getString("database-location") ?: "[x],[y],[z]").replace(
                "[x]", job.location.blockX.toString()
            ).replace("[y]", job.location.blockY.toString()).replace("[z]", job.location.blockZ.toString())

        if (SneakyJobBoard.isPapiActive()) {
            displayStringLocation =
                PlaceholderAPI.setPlaceholders(poster, displayStringLocation).replace("none", "Moonwell Pass")
        }

        val faceIconBase64 = DEFAULT_ICON

        return mapOf(
            "uuid" to job.uuid,
            "serverPort" to Bukkit.getPort(),
            "category" to job.category.name,
            "poster" to poster.name,
            "posterDisplayString" to displayStringPoster,
            "posterIconBase64" to faceIconBase64,
            "location" to job.location.toString(),
            "locationDisplayString" to displayStringLocation,
            "startTime" to job.startTime,
            "durationMillis" to job.durationMillis,
            "tracking" to job.tracking,
            "persist" to job.persist,
            "name" to job.name,
            "description" to job.description,
            "discordEmbedIcon" to job.category.discordEmbedIcon,
        )
    }

    /**
     * Downloads an image from the specified URL.
     *
     * @param url The URL from which to download the image.
     * @return The downloaded image as a BufferedImage, or null if an error occurs.
     */
    private fun downloadImage(url: URL): BufferedImage? {
        return try {
            url.openConnection().apply { connectTimeout = 3000; readTimeout = 3000 }.getInputStream().use { ImageIO.read(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Creates a player icon by extracting the face and overlaying the helmet layer (if present)
     * from a Minecraft skin.
     *
     * @param skin The player's skin image.
     * @param hatVisible Whether the helmet/hat layer of the skin should be displayed on top of the face.
     * @return A BufferedImage of the player icon with the face (and optionally the helmet).
     */
    private fun createPlayerIcon(skin: BufferedImage, hatVisible: Boolean): BufferedImage {
        val icon = BufferedImage(72, 72, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = icon.createGraphics()

        // Scale the face image to fit the inner layer of the icon
        val scaledFace = skin.getSubimage(8, 8, 8, 8).getScaledInstance(64, 64, Image.SCALE_DEFAULT)
        val faceBufferedImage = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        faceBufferedImage.createGraphics().apply {
            drawImage(scaledFace, 0, 0, null)
            dispose()
        }
        g.drawImage(faceBufferedImage, 4, 4, null)

        // Scale the helmet layer to match the dimensions of the icon and overlay it on top of the
        // scaled face
        val scaledHelmet = skin.getSubimage(40, 8, 8, 8).getScaledInstance(72, 72, Image.SCALE_DEFAULT)
        val helmetBufferedImage = BufferedImage(72, 72, BufferedImage.TYPE_INT_ARGB)
        helmetBufferedImage.createGraphics().apply {
            drawImage(scaledHelmet, 0, 0, null)
            dispose()
        }

        if (!isFullyTransparent(helmetBufferedImage) && hatVisible) {
            g.drawImage(helmetBufferedImage, 0, 0, null)
        }

        g.dispose()
        return icon
    }

    /**
     * Checks if an image is fully transparent by inspecting all its pixels.
     *
     * @param image The BufferedImage to check.
     * @return True if the image is fully transparent, false otherwise.
     */
    private fun isFullyTransparent(image: BufferedImage): Boolean = (0 until image.width).none { x ->
        (0 until image.height).any { y -> (image.getRGB(x, y) ushr 24) != 0 }
    }

    /**
     * Encodes an image to a Base64 string with a data URI prefix.
     *
     * This function writes the image to a ByteArrayOutputStream and encodes the image bytes as Base64.
     * The result is returned as a valid data URI for PNG images.
     *
     * @param image The BufferedImage to be encoded.
     * @return A String representing the image as a Base64 encoded data URI.
     */
    private fun encodeImageToBase64(image: BufferedImage): String {
        val outputStream = ByteArrayOutputStream()
        return outputStream.use {
            ImageIO.write(image, "png", it)
            val imageBytes = it.toByteArray()
            val base64String = Base64.getEncoder().encodeToString(imageBytes)
            "data:image/png;base64,$base64String"
        }
    }
    companion object { private const val DEFAULT_ICON = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEgAAABICAYAAABV7bNHAAADo0lEQVR4Xu3aIU4EQRQE0L0L3INroLkPHo1CgkZgOAAag0IhEBwA/KtkOpXthE5mKnmup/r3Vyv2dDpy5MiRhfJ5e/W75efhetPX3dUmz8v7ZJ/sk31yHxE/kBfKgeV5eZ/sk32yT+4j4gfyQjmwPC/vk32yT/bJfUT8QF4oB5bn5X2yT/bJPrmPiB/IC+XA8ry8T/bJPtkn9xHxA3mhHFiel/fJPtkn+2Sf3EfED+SFcmB5Xt4n+2Sf7JP7iHihvFBeKPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFciB5Xp7Xx+v9WeyT88h9RPxAXijPy/PywS375DxyHxE/kBfK8/K8fHDLPjmP3EfED+SF8rw8Lx/csk/OI/cR8QN5oTwvz8sHt+yT88h9RPxAXijPy/PywS375DxyHxE/kBfK8/K8fHDLPjmP3EfED+SF8rw8Lx/csk/OI/cR8YOWA+nr7XHT9/vLJs+3nEfuI+KDW14oB5YLkedbziP3EfHBLS+UA8uFyPMt55H7iPjglhfKgeVC5PmW88h9RHxwywvlwHIh8nzLeeQ+Ij645YVyYLkQeb7lPHIfER/c8kI5sFyIPN9yHrmPiA9ueaEcWC5Enm85j9xHxEKNcnFzuckHt+zTKL5H7iPiBxrFgeWDW/ZpFN8j9xHxA43iwPLBLfs0iu+R+4j4gUZxYPngln0axffIfUT8QKM4sHxwyz6N4nvkPiJ+oFEcWD64ZZ9G8T1yHxE/0CgOLB/csk+j+B65j4gfaBQHlg9u2adRfI/cR8SB9sZ9RPxgb9xHxA/2xn1E/GBv3EfED/bGfUT8YG/cR8QP9sZ9RPxgb9xHxA+me346j30lfxjKfUQsnM4Ht+wruRC5j4iF0/ngln0lFyL3EbFwOh/csq/kQuQ+IhZO54Nb9pVciNxHxMLpfHDLvpILkfuIWDidD27ZV3Ihch8RC6fzwS37Si5E7iPiB/LC1Tiv/MOV3EfEQjnQapxXLkTuI2KhHGg1zisXIvcRsVAOtBrnlQuR+4hYKAdajfPKhch9RCyUA63GeeVC5D4iFsqBVuO8ciFyHxEL5UCrcV65ELmPI0eOHPnP/AFaOEKvqAik7AAAAABJRU5ErkJggg==" }
}

fun String.toBooleanOrNull(): Boolean? = lowercase().toBooleanStrictOrNull()
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
