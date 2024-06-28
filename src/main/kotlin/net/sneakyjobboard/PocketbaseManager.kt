package net.sneakyjobboard

import com.destroystokyo.paper.ClientOption
import com.google.gson.Gson
import java.awt.Graphics2D
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.Base64
import javax.imageio.ImageIO
import me.clip.placeholderapi.PlaceholderAPI
import net.sneakyjobboard.job.Job
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bukkit.Bukkit
import org.json.JSONObject

class PocketbaseManager {

    var authToken: String = ""

    init {
        Bukkit.getScheduler()
                .runTaskAsynchronously(SneakyJobBoard.getInstance(), Runnable { auth() })
    }

    /** Get a PocketBase auth token. Only run this asynchronously! */
    @Synchronized
    private fun auth() {
        val url = SneakyJobBoard.getInstance().getConfig().getString("pocketbase-auth-url")
        val email = SneakyJobBoard.getInstance().getConfig().getString("pocketbase-email")
        val password = SneakyJobBoard.getInstance().getConfig().getString("pocketbase-password")

        if (url == null || email == null || password == null) return

        try {
            val client = OkHttpClient()
            val authRequestBody =
                    FormBody.Builder().add("identity", email).add("password", password).build()
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
            println("Error occurred: ${e.message}")
        }
    }

    /** Add the job to the pocketbase collection. */
    @Synchronized
    fun listJob(job: Job) {
        val url = SneakyJobBoard.getInstance().getConfig().getString("pocketbase-url")

        if (url == null || url.isEmpty()) return

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        SneakyJobBoard.getInstance(),
                        Runnable {
                            try {
                                if (authToken.isEmpty()) auth()

                                if (authToken.isNotEmpty()) {
                                    val client = OkHttpClient()

                                    val jobData = createJobDataMap(job)

                                    val jsonRequestBody =
                                            Gson().toJson(jobData)
                                                    .toRequestBody("application/json".toMediaType())

                                    val request =
                                            Request.Builder()
                                                    .url("$url")
                                                    .header("Authorization", authToken)
                                                    .post(jsonRequestBody)
                                                    .build()

                                    val response = client.newCall(request).execute()

                                    if (response.isSuccessful) {
                                        val responseBody = response.body?.string()
                                        val jsonResponse = JSONObject(responseBody)
                                        job.recordID = jsonResponse.getString("id")
                                    } else {
                                        println(
                                                "Pocketbase request unsuccessful: ${response.code}, ${response.body?.string()}"
                                        )
                                    }
                                    response.close()
                                }
                            } catch (e: Exception) {
                                println("Error occurred: ${e.message}")
                            }
                        }
                )
    }

    /** Add an endtime to the pocketbase record. */
    @Synchronized
    fun unlistJob(job: Job) {
        val url = SneakyJobBoard.getInstance().getConfig().getString("pocketbase-url")

        if (url == null || url.isEmpty() || job.recordID.isEmpty()) return

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        SneakyJobBoard.getInstance(),
                        Runnable {
                            try {
                                if (authToken.isEmpty()) auth()

                                if (authToken.isNotEmpty()) {
                                    val client = OkHttpClient()

                                    val jobData = mapOf("endTime" to System.currentTimeMillis())
                                    val jsonRequestBody =
                                            Gson().toJson(jobData)
                                                    .toRequestBody("application/json".toMediaType())

                                    val request =
                                            Request.Builder()
                                                    .url("$url/${job.recordID}")
                                                    .header("Authorization", authToken)
                                                    .patch(jsonRequestBody)
                                                    .build()

                                    val response = client.newCall(request).execute()

                                    if (!response.isSuccessful) {
                                        println(
                                                "Pocketbase request unsuccessful: ${response.code}, ${response.body?.string()}"
                                        )
                                    }
                                    response.close()
                                }
                            } catch (e: Exception) {
                                println("Error occurred: ${e.message}")
                            }
                        }
                )
    }

    /** Converts a Job into a json-ready map. */
    private fun createJobDataMap(job: Job): Map<String, Any> {
        // Poster display string
        var displayStringPoster =
                (SneakyJobBoard.getInstance().getConfig().getString("pocketbase-poster")
                                ?: "[playerName]").replace("[playerName]", job.player.name)

        if (SneakyJobBoard.isPapiActive()) {
            displayStringPoster = PlaceholderAPI.setPlaceholders(job.player, displayStringPoster)
        }

        // Location display string
        var displayStringLocation =
                (SneakyJobBoard.getInstance().getConfig().getString("pocketbase-location")
                                ?: "[x],[y],[z]")
                        .replace("[x]", job.location.blockX.toString())
                        .replace("[y]", job.location.blockY.toString())
                        .replace("[z]", job.location.blockZ.toString())

        if (SneakyJobBoard.isPapiActive()) {
            displayStringLocation =
                    PlaceholderAPI.setPlaceholders(job.player, displayStringLocation)
                            .replace("none", "Dinky Dank")
        }

        // Base64 face icon
        val skinURL = job.player.playerProfile.textures.skin

        val faceIconBase64 =
                skinURL?.let {
                    val skinImage = downloadImage(it)
                    skinImage?.let {
                        val faceIcon =
                                createPlayerIcon(
                                        it,
                                        job.player
                                                .getClientOption(ClientOption.SKIN_PARTS)
                                                .hasHatsEnabled()
                                )
                        encodeImageToBase64(faceIcon)
                    }
                }
                        ?: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEgAAABICAYAAABV7bNHAAADo0lEQVR4Xu3aIU4EQRQE0L0L3INroLkPHo1CgkZgOAAag0IhEBwA/KtkOpXthE5mKnmup/r3Vyv2dDpy5MiRhfJ5e/W75efhetPX3dUmz8v7ZJ/sk31yHxE/kBfKgeV5eZ/sk32yT+4j4gfyQjmwPC/vk32yT/bJfUT8QF4oB5bn5X2yT/bJPrmPiB/IC+XA8ry8T/bJPtkn9xHxA3mhHFiel/fJPtkn++Q+In4gL5QDy/PyPtkn+2Sf3EfED+SFcmB5Xt4n+2Sf7JP7iHihvFBeKPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFslAOLPvk+ZZ9cl65j4iFciB5Xp7Xx+v9WeyT88h9RPxAXijPy/PywS375DxyHxE/kBfK8/K8fHDLPjmP3EfED+SF8rw8Lx/csk/OI/cR8QN5oTwvz8sHt+yT88h9RPxAXijPy/PywS375DxyHxE/kBfK8/K8fHDLPjmP3EfED+SF8rw8Lx/csk/OI/cR8YOWA+nr7XHT9/vLJs+3nEfuI+KDW14oB5YLkedbziP3EfHBLS+UA8uFyPMt55H7iPjglhfKgeVC5PmW88h9RHxwywvlwHIh8nzLeeQ+Ij645YVyYLkQeb7lPHIfER/c8kI5sFyIPN9yHrmPiA9ueaEcWC5Enm85j9xHxEKNcnFzuckHt+zTKL5H7iPiBxrFgeWDW/ZpFN8j9xHxA43iwPLBLfs0iu+R+4j4gUZxYPngln0axffIfUT8QKM4sHxwyz6N4nvkPiJ+oFEcWD64ZZ9G8T1yHxE/0CgOLB/csk+j+B65j4gfaBQHlg9u2adRfI/cR8SB9sZ9RPxgb9xHxA/2xn1E/GBv3EfED/bGfUT8YG/cR8QP9sZ9RPxgb9xHxA+me346j30lfxjKfUQsnM4Ht+wruRC5j4iF0/ngln0lFyL3EbFwOh/csq/kQuQ+IhZO54Nb9pVciNxHxMLpfHDLvpILkfuIWDidD27ZV3Ihch8RC6fzwS37Si5E7iPiB/LC1Tiv/MOV3EfEQjnQapxXLkTuI2KhHGg1zisXIvcRsVAOtBrnlQuR+4hYKAdajfPKhch9RCyUA63GeeVC5D4iFsqBVuO8ciFyHxEL5UCrcV65ELmPI0eOHPnP/AFaOEKvqAik7AAAAABJRU5ErkJggg=="

        return mapOf(
                "uuid" to job.uuid,
                "category" to job.category.name,
                "posterDisplayString" to displayStringPoster,
                "posterIconBase64" to faceIconBase64,
                "location" to job.location.toString(),
                "locationDisplayString" to displayStringLocation,
                "startTime" to job.startTime,
                "durationMillis" to job.durationMillis,
                "name" to job.name,
                "description" to job.description,
                "discordEmbedIcon" to job.category.discordEmbedIcon,
        )
    }

    /** Downloads an image from a specified URL. */
    private fun downloadImage(url: URL): BufferedImage? {
        return try {
            ImageIO.read(url)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Creates a player icon by extracting the face and overlaying the helmet layer from a Minecraft
     * skin.
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
        val scaledHelmet =
                skin.getSubimage(40, 8, 8, 8).getScaledInstance(72, 72, Image.SCALE_DEFAULT)
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

    /** Checks if an image is fully transparent. */
    private fun isFullyTransparent(image: BufferedImage): Boolean =
            (0 until image.width).none { x ->
                (0 until image.height).any { y -> (image.getRGB(x, y) ushr 24) != 0 }
            }

    /** Encode an image to a base64 String with proper prefix. */
    private fun encodeImageToBase64(image: BufferedImage): String {
        val outputStream = ByteArrayOutputStream()
        return try {
            ImageIO.write(image, "png", outputStream)
            val imageBytes = outputStream.toByteArray()
            val base64String = Base64.getEncoder().encodeToString(imageBytes)
            "data:image/png;base64,$base64String"
        } finally {
            outputStream.close()
        }
    }
}
