package net.sneakyjobboard

import com.destroystokyo.paper.ClientOption
import com.google.gson.Gson
import com.google.gson.JsonParser
import me.clip.placeholderapi.PlaceholderAPI
import net.sneakyjobboard.job.Job
import net.sneakyjobboard.job.JobHistoryInventoryHolder
import net.sneakyjobboard.advert.Advert
import net.sneakyjobboard.util.TextUtility
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.Material
import org.json.JSONObject
import java.awt.Graphics2D
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.*
import javax.imageio.ImageIO

/**
 * Manages interactions with PocketBase database for job and advertisement persistence.
 * Handles authentication, data synchronization, and history retrieval.
 */
class PocketbaseManager {

    private var authToken: String = ""

    /**
     * Initializes the PocketbaseManager. Upon creation, it authenticates with PocketBase
     * and unlists all active jobs that do not have an end time, running the operations asynchronously.
     */
    init {
        Bukkit.getScheduler().runTaskAsynchronously(SneakyJobBoard.getInstance(), Runnable {
            try {
                auth()

                if (authToken.isNotEmpty()) {
                    unlistAllJobs()
                }
            } catch (e: Exception) {
                SneakyJobBoard.log("Error occurred during startup: ${e.message}")
            }
        })
    }

    /**
     * Authenticates with PocketBase using configured credentials.
     * Must be called asynchronously.
     */
    @Synchronized
    private fun auth() {
        val url = SneakyJobBoard.getInstance().getConfig().getString("pocketbase-auth-url")
        val email = SneakyJobBoard.getInstance().getConfig().getString("pocketbase-email")
        val password = SneakyJobBoard.getInstance().getConfig().getString("pocketbase-password")

        if (url == null || email == null || password == null) return

        try {
            val client = OkHttpClient()
            val authRequestBody = FormBody.Builder().add("identity", email).add("password", password).build()
            val authRequest = Request.Builder().url("$url").post(authRequestBody).build()
            val authResponse = client.newCall(authRequest).execute()

            if (authResponse.isSuccessful) {
                val responseBody = authResponse.body?.string()
                val jsonResponse = JSONObject(responseBody)

                authToken = jsonResponse.optString("token", "")

                if (authToken.isEmpty()) {
                    SneakyJobBoard.log(
                        "Pocketbase authentication was succesfull but there was no token in the response."
                    )
                }
                authResponse.close()
            } else {
                SneakyJobBoard.log("Pocketbase authentication failed: ${authResponse.code}")
            }
        } catch (e: Exception) {
            SneakyJobBoard.log("Error occurred: ${e.message}")
        }
    }

    /**
     * Lists a job in PocketBase.
     * Creates a new record with job details and schedules cleanup.
     *
     * @param job The job to be listed
     */
    @Synchronized
    fun listJob(job: Job) {
        val url = SneakyJobBoard.getInstance().getConfig().getString("pocketbase-jobs-url")

        if (url.isNullOrEmpty()) return

        Bukkit.getScheduler().runTaskAsynchronously(SneakyJobBoard.getInstance(), Runnable {
            try {
                if (authToken.isEmpty()) auth()

                if (authToken.isNotEmpty()) {
                    val client = OkHttpClient()

                    val jobData = createJobDataMap(job)

                    val jsonRequestBody = Gson().toJson(jobData).toRequestBody("application/json".toMediaType())

                    val request =
                        Request.Builder().url("$url").header("Authorization", authToken).post(jsonRequestBody).build()

                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val jsonResponse = JSONObject(responseBody)
                        job.recordID = jsonResponse.getString("id")
                    } else {
                        SneakyJobBoard.log(
                            "Pocketbase request unsuccessful: ${response.code}, ${response.body?.string()}"
                        )
                    }
                    response.close()
                }
            } catch (e: Exception) {
                SneakyJobBoard.log("Error occurred: ${e.message}")
            }
        })
    }

    /**
     * Updates a job's end time in PocketBase.
     * Used when a job is unlisted or expires.
     *
     * @param job The job being unlisted
     * @param endReason The reason for unlisting ("expired", "unlisted", "deleted", or "restart")
     */
    @Synchronized
    fun unlistJob(job: Job, endReason: String) {
        val url = SneakyJobBoard.getInstance().getConfig().getString("pocketbase-jobs-url")

        if (url.isNullOrEmpty() || job.recordID.isEmpty()) return

        Bukkit.getScheduler().runTaskAsynchronously(SneakyJobBoard.getInstance(), Runnable {
            try {
                if (authToken.isEmpty()) auth()

                if (authToken.isNotEmpty()) {
                    val client = OkHttpClient()

                    val jobData = mapOf(
                        "endTime" to System.currentTimeMillis(),
                        "endReason" to endReason
                    )
                    val jsonRequestBody = Gson().toJson(jobData).toRequestBody("application/json".toMediaType())

                    val request = Request.Builder().url("$url/${job.recordID}").header("Authorization", authToken)
                        .patch(jsonRequestBody).build()

                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        SneakyJobBoard.log(
                            "Pocketbase request unsuccessful: ${response.code}, ${response.body?.string()}"
                        )
                    }
                    response.close()
                }
            } catch (e: Exception) {
                SneakyJobBoard.log("Error occurred: ${e.message}")
            }
        })
    }

    /**
     * Unlists all jobs in PocketBase that do not have an end time. This method retrieves all jobs with an empty endTime field
     * and patches them to add the current system time as their endTime.
     */
    @Synchronized
    fun unlistAllJobs() {
        val url = SneakyJobBoard.getInstance().getConfig().getString("pocketbase-jobs-url")

        if (url.isNullOrEmpty()) return

        Bukkit.getScheduler().runTaskAsynchronously(SneakyJobBoard.getInstance(), Runnable {
            try {
                if (authToken.isEmpty()) auth()

                if (authToken.isNotEmpty()) {
                    val client = OkHttpClient()

                    // Retrieve all jobs with an empty endtime
                    val requestGet =
                        Request.Builder().url("$url?filter=(endTime='0')").header("Authorization", authToken).get()
                            .build()

                    val responseGet = client.newCall(requestGet).execute()
                    val responseBody = responseGet.body?.string()

                    if (!responseGet.isSuccessful || responseBody == null) {
                        SneakyJobBoard.log(
                            "Pocketbase request unsuccessful: ${responseGet.code}, ${responseBody ?: "No response body"}"
                        )
                        responseGet.close()
                        return@Runnable
                    }

                    val recordIDs = JsonParser.parseString(responseBody).asJsonObject.getAsJsonArray("items")
                        .map { it.asJsonObject.get("id").asString }

                    responseGet.close()
                    // Iterate over the jobs and update them
                    recordIDs.forEach { recordID ->
                        val jobData = mapOf(
                            "endTime" to System.currentTimeMillis(),
                            "endReason" to "restart"
                        )
                        val jsonRequestBody = Gson().toJson(jobData).toRequestBody(
                            "application/json".toMediaType()
                        )

                        val requestPatch = Request.Builder().url("$url/${recordID}").header("Authorization", authToken)
                            .patch(jsonRequestBody).build()

                        val responsePatch = client.newCall(requestPatch).execute()
                        if (!responsePatch.isSuccessful) {
                            SneakyJobBoard.log(
                                "Pocketbase request unsuccessful: ${responsePatch.code}, ${responsePatch.body?.string()}"
                            )
                        }
                        responsePatch.close()
                    }
                }
            } catch (e: Exception) {
                SneakyJobBoard.log("Error occurred: ${e.message}")
            }
        })
    }

    /**
     * Retrieves a player's job history from PocketBase, showing the 9 most recent unique job listings.
     * The retrieved job history is displayed to the player in a custom inventory UI.
     *
     * @param player The player for whom the job history is being retrieved.
     * @param durationMillis The duration to display the job listings.
     */
    @Synchronized
    fun getJobHistory(player: Player, durationMillis: Long) {
        val url = SneakyJobBoard.getInstance().getConfig().getString("pocketbase-jobs-url")

        if (url.isNullOrEmpty()) return

        Bukkit.getScheduler().runTaskAsynchronously(SneakyJobBoard.getInstance(), Runnable {
            try {
                if (authToken.isEmpty()) auth()

                if (authToken.isNotEmpty()) {
                    val client = OkHttpClient()

                    val requestGet = Request.Builder().url("$url?filter=(poster='${player.name}')")
                        .header("Authorization", authToken).get().build()

                    val responseGet = client.newCall(requestGet).execute()
                    val responseBody = responseGet.body?.string()

                    if (!responseGet.isSuccessful || responseBody.isNullOrEmpty()) {
                        SneakyJobBoard.log(
                            "Pocketbase request unsuccessful: ${responseGet.code}, ${responseBody ?: "No response body"}"
                        )
                        responseGet.close()
                        return@Runnable
                    }

                    val items = JsonParser.parseString(responseBody).asJsonObject.getAsJsonArray("items")
                        .map { it.asJsonObject }

                    val jobHistory = mutableListOf<Job>()
                    val seenJobs = mutableSetOf<Quadruple<String, String, String, String>>()

                    for (item in items) {
                        if (jobHistory.size >= 9) break

                        val category = item.get("category").asString
                        val name = item.get("name").asString
                        val tracking = item.get("tracking").asString
                        val description = item.get("description").asString

                        val jobKey = Quadruple(category, name, tracking, description)
                        if (seenJobs.contains(jobKey)) continue

                        seenJobs.add(jobKey)

                        val jobCategory = SneakyJobBoard.getJobCategoryManager()
                            .getJobCategories().values.find { it.name == category }
                            ?: SneakyJobBoard.getJobCategoryManager().getJobCategories().values.first()

                        val job = Job(
                            category = jobCategory,
                            player = player,
                            durationMillis = durationMillis,
                            tracking = tracking.toBooleanOrNull() ?: false
                        ).apply {
                            this.name = name
                            this.description = description
                        }

                        jobHistory.add(job)
                    }

                    Bukkit.getScheduler().runTask(SneakyJobBoard.getInstance(), Runnable {
                        if (jobHistory.isNotEmpty()) {
                            player.openInventory(
                                JobHistoryInventoryHolder(
                                    jobHistory.reversed()
                                ).inventory
                            )
                        } else {
                            player.sendMessage(
                                TextUtility.convertToComponent(
                                    "&4You do not have a job posting history."
                                )
                            )
                        }
                    })
                }
            } catch (e: Exception) {
                SneakyJobBoard.log("Error occurred: ${e.message}")
            }
        })
    }

    /**
     * Helper method to create a job data map used for job listings in PocketBase.
     *
     * @param job The job object from which the data is derived.
     * @return A map containing job data ready to be converted to JSON and sent to PocketBase.
     */
    private fun createJobDataMap(job: Job): Map<String, Any> {
        // Poster display string
        var displayStringPoster = (SneakyJobBoard.getInstance().getConfig().getString("pocketbase-poster")
            ?: "[playerName]").replace("[playerName]", job.player.name)

        if (SneakyJobBoard.isPapiActive()) {
            displayStringPoster = PlaceholderAPI.setPlaceholders(job.player, displayStringPoster)
        }

        // Location display string
        var displayStringLocation =
            (SneakyJobBoard.getInstance().getConfig().getString("pocketbase-location") ?: "[x],[y],[z]").replace(
                "[x]", job.location.blockX.toString()
            ).replace("[y]", job.location.blockY.toString()).replace("[z]", job.location.blockZ.toString())

        if (SneakyJobBoard.isPapiActive()) {
            displayStringLocation =
                PlaceholderAPI.setPlaceholders(job.player, displayStringLocation).replace("none", "Dinky Dank")
        }

        // Base64 face icon
        val skinURL = job.player.playerProfile.textures.skin

        val faceIconBase64 = skinURL?.let {
            val skinImage = downloadImage(it)
            skinImage?.let { image ->
                val faceIcon = createPlayerIcon(
                    image, job.player.getClientOption(ClientOption.SKIN_PARTS).hasHatsEnabled()
                )
                encodeImageToBase64(faceIcon)
            }
        }
            ?: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEgAAABICAYAAABV7bNHAAADo0lEQVR4Xu3aIU4EQRQE0L0L3INroLkPHo1CgkZgOAAag0IhEBwA/KtkOpXthE5mKnmup/r3Vyv2dDpy5MiRhfJ5e/W75efhetPX3dUmz8v7ZJ/sk31yHxE/kBfKgeV5eZ/sk32yT+4j4gfyQjmwPC/vk32yT/bJfUT8QF4oB5bn5X2yT/bJPrmPiB/IC+XA8ry8T/bJPtkn9xHxA3mhHFiel/fJPtkn++Q+In4gL5QDy/PyPtkn+2Sf3EfED+SFcmB5Xt4n+2Sf7JP7iHihvFBeKPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFciB5Xp7Xx+v9WeyT88h9RPxAXijPy/PywS375DxyHxE/kBfK8/K8fHDLPjmP3EfED+SF8rw8Lx/csk/OI/cR8QN5oTwvz8sHt+yT88h9RPxAXijPy/PywS375DxyHxE/kBfK8/K8fHDLPjmP3EfED+SF8rw8Lx/csk/OI/cR8YOWA+nr7XHT9/vLJs+3nEfuI+KDW14oB5YLkedbziP3EfHBLS+UA8uFyPMt55H7iPjglhfKgeVC5PmW88h9RHxwywvlwHIh8nzLeeQ+Ij645YVyYLkQeb7lPHIfER/c8kI5sFyIPN9yHrmPiA9ueaEcWC5Enm85j9xHxEKNcnFzuckHt+zTKL5H7iPiBxrFgeWDW/ZpFN8j9xHxA43iwPLBLfs0iu+R+4j4gUZxYPngln0axffIfUT8QKM4sHxwyz6N4nvkPiJ+oFEcWD64ZZ9G8T1yHxE/0CgOLB/csk+j+B65j4gfaBQHlg9u2adRfI/cR8SB9sZ9RPxgb9xHxA/2xn1E/GBv3EfED/bGfUT8YG/cR8QP9sZ9RPxgb9xHxA+me346j30lfxjKfUQsnM4Ht+wruRC5j4iF0/ngln0lFyL3EbFwOh/csq/kQuQ+IhZO54Nb9pVciNxHxMLpfHDLvpILkfuIWDidD27ZV3Ihch8RC6fzwS37Si5E7iPiB/LC1Tiv/MOV3EfEQjnQapxXLkTuI2KhHGg1zisXIvcRsVAOtBrnlQuR+4hYKAdajfPKhch9RCyUA63GeeVC5D4iFsqBVuO8ciFyHxEL5UCrcV65ELmPI0eOHPnP/AFaOEKvqAik7AAAAABJRU5ErkJggg=="

        return mapOf(
            "uuid" to job.uuid,
            "category" to job.category.name,
            "poster" to job.player.name,
            "posterDisplayString" to displayStringPoster,
            "posterIconBase64" to faceIconBase64,
            "location" to job.location.toString(),
            "locationDisplayString" to displayStringLocation,
            "startTime" to job.startTime,
            "durationMillis" to job.durationMillis,
            "tracking" to job.tracking,
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
            ImageIO.read(url)
        } catch (e: Exception) {
            e.printStackTrace()
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

    /**
     * Lists an advert in the PocketBase database. This method sends a POST request containing advert details to PocketBase.
     * If authentication is required, it will authenticate before sending the advert listing request.
     *
     * @param advert The advert object containing all relevant information to be listed in PocketBase.
     */
    @Synchronized
    fun listAdvert(advert: Advert) {
        val url = SneakyJobBoard.getInstance().getConfig().getString("pocketbase-adverts-url")

        if (url.isNullOrEmpty()) return

        Bukkit.getScheduler().runTaskAsynchronously(SneakyJobBoard.getInstance(), Runnable {
            try {
                if (authToken.isEmpty()) auth()

                if (authToken.isNotEmpty()) {
                    val client = OkHttpClient()

                    val advertData = mapOf(
                        "uuid" to advert.uuid,
                        "category" to (advert.category?.name ?: ""),
                        "posteruuid" to advert.player.uniqueId.toString(),
                        "posterDisplayString" to advert.posterString,
                        "name" to advert.name,
                        "description" to advert.description,
                        "iconMaterial" to (advert.iconMaterial?.name ?: ""),
                        "iconCustomModelData" to (advert.iconCustomModelData ?: 0),
                        "enabled" to advert.enabled,
                        "deleted" to advert.deleted
                    )

                    val jsonRequestBody = Gson().toJson(advertData).toRequestBody("application/json".toMediaType())

                    val request = Request.Builder()
                        .url("$url")
                        .header("Authorization", authToken)
                        .post(jsonRequestBody)
                        .build()

                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val jsonResponse = JSONObject(responseBody)
                        advert.recordID = jsonResponse.getString("id")
                    } else {
                        SneakyJobBoard.log(
                            "Pocketbase request unsuccessful: ${response.code}, ${response.body?.string()}"
                        )
                    }
                    response.close()
                }
            } catch (e: Exception) {
                SneakyJobBoard.log("Error occurred: ${e.message}")
            }
        })
    }

    /**
     * Updates an existing advert in PocketBase with all current values from the advert object.
     * The advert's recordID is used to identify the advert in PocketBase.
     *
     * @param advert The advert containing the updated information.
     */
    @Synchronized
    fun updateAdvert(advert: Advert) {
        val url = SneakyJobBoard.getInstance().getConfig().getString("pocketbase-adverts-url")

        if (url.isNullOrEmpty() || advert.recordID.isEmpty()) return

        Bukkit.getScheduler().runTaskAsynchronously(SneakyJobBoard.getInstance(), Runnable {
            try {
                if (authToken.isEmpty()) auth()

                if (authToken.isNotEmpty()) {
                    val client = OkHttpClient()

                    val advertData = mapOf(
                        "uuid" to advert.uuid,
                        "category" to (advert.category?.name ?: ""),
                        "posteruuid" to advert.player.uniqueId.toString(),
                        "posterDisplayString" to advert.posterString,
                        "name" to advert.name,
                        "description" to advert.description,
                        "iconMaterial" to (advert.iconMaterial?.name ?: ""),
                        "iconCustomModelData" to (advert.iconCustomModelData ?: 0),
                        "enabled" to advert.enabled,
                        "deleted" to advert.deleted
                    )

                    val jsonRequestBody = Gson().toJson(advertData).toRequestBody("application/json".toMediaType())

                    val request = Request.Builder()
                        .url("$url/${advert.recordID}")
                        .header("Authorization", authToken)
                        .patch(jsonRequestBody)
                        .build()

                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        SneakyJobBoard.log(
                            "Pocketbase request unsuccessful: ${response.code}, ${response.body?.string()}"
                        )
                    }
                    response.close()
                }
            } catch (e: Exception) {
                SneakyJobBoard.log("Error occurred: ${e.message}")
            }
        })
    }

    /**
     * Retrieves a player's advert history from PocketBase, showing all non-deleted adverts.
     * The retrieved adverts are displayed to the player in a custom inventory UI.
     *
     * @param playerUUID The UUID of the player whose advert history to retrieve.
     */
    @Synchronized
    fun getAdvertHistory(playerUUID: String) {
        val url = SneakyJobBoard.getInstance().getConfig().getString("pocketbase-adverts-url")

        if (url.isNullOrEmpty()) return

        Bukkit.getScheduler().runTaskAsynchronously(SneakyJobBoard.getInstance(), Runnable {
            try {
                if (authToken.isEmpty()) auth()

                if (authToken.isNotEmpty()) {
                    val client = OkHttpClient()

                    val requestGet = Request.Builder()
                        .url("$url?filter=(posteruuid='${playerUUID}')&&(deleted=false)")
                        .header("Authorization", authToken)
                        .get()
                        .build()

                    val responseGet = client.newCall(requestGet).execute()
                    val responseBody = responseGet.body?.string()

                    if (!responseGet.isSuccessful || responseBody.isNullOrEmpty()) {
                        SneakyJobBoard.log(
                            "Pocketbase request unsuccessful: ${responseGet.code}, ${responseBody ?: "No response body"}"
                        )
                        responseGet.close()
                        return@Runnable
                    }

                    val items = JsonParser.parseString(responseBody).asJsonObject.getAsJsonArray("items")
                        .map { it.asJsonObject }

                    val adverts = mutableListOf<Advert>()
                    val seenAdverts = mutableSetOf<Triple<String, String, String>>()

                    for (item in items) {
                        val category = item.get("category").asString
                        val name = item.get("name").asString
                        val description = item.get("description").asString
                        val iconMaterialStr = item.get("iconMaterial").asString
                        val iconCustomModelData = item.get("iconCustomModelData").asInt
						val enabled = item.get("enabled").asBoolean

                        val advertKey = Triple(category, name, description)
                        if (seenAdverts.contains(advertKey)) continue

                        seenAdverts.add(advertKey)

                        val advertCategory = SneakyJobBoard.getAdvertCategoryManager()
                            .getAdvertCategories().values.find { it.name == category }
                            ?: SneakyJobBoard.getAdvertCategoryManager().getAdvertCategories().values.first()

                        val player = Bukkit.getPlayer(UUID.fromString(playerUUID)) ?: continue

                        val advert = Advert(
                            category = advertCategory,
                            player = player
                        ).apply {
                            this.name = name
                            this.description = description
                            this.recordID = item.get("id").asString
                            this.iconMaterial = Material.matchMaterial(iconMaterialStr)
                            this.iconCustomModelData = iconCustomModelData
							this.enabled = enabled
                        }

                        adverts.add(advert)
                    }

                    responseGet.close()

                    adverts.forEach { advert ->
						SneakyJobBoard.getAdvertManager().list(advert, false)
                    }
                }
            } catch (e: Exception) {
                SneakyJobBoard.log("Error occurred: ${e.message}")
            }
        })
    }
}

/**
 * Extension function to safely convert a string to a boolean.
 * @return Boolean value if string is "true" or "false", null otherwise
 */
fun String.toBooleanOrNull(): Boolean? {
    return when (this.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}

/**
 * Data class for holding four related values.
 * Used for grouping related data in PocketBase operations.
 */
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
