package net.sneakyjobboard.util

import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.URL
import java.util.Base64
import javax.imageio.ImageIO
import me.clip.placeholderapi.PlaceholderAPI
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.job.Job
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bukkit.Bukkit
import org.json.JSONObject

object WebhookUtility {

    fun listJob(job: Job) {
        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        SneakyJobBoard.getInstance(),
                        Runnable {
                            val jobData = createJobDataData(job)

                            val jsonRequestBody =
                                    JSONObject(jobData)
                                            .toString()
                                            .toRequestBody("application/json".toMediaType())

                            val client = OkHttpClient()

                            val request =
                                    Request.Builder()
                                            .url("http://localhost:80/lom2jobboard")
                                            .post(jsonRequestBody)
                                            .build()

                            try {
                                client.newCall(request).execute()
                            } catch (e: ConnectException) {} catch (e: Exception) {}
                        }
                )
    }

    /** Converts a Job into a json-ready map. */
    private fun createJobDataData(job: Job): Map<String, Any> {
        var posterStringWebhook =
                (SneakyJobBoard.getInstance().getConfig().getString("poster-string-webhook")
                                ?: "[playerName]").replace("[playerName]", job.player.name)

        if (SneakyJobBoard.isPapiActive()) {
            posterStringWebhook = PlaceholderAPI.setPlaceholders(job.player, posterStringWebhook)
        }

        val skinURL = job.player.playerProfile.textures.skin

        val faceIconBase64 =
                skinURL?.let {
                    val skinImage = downloadImage(it)
                    skinImage?.let {
                        val faceIcon = createPlayerIcon(it)
                        encodeImageToBase64(faceIcon)
                    }
                }
                        ?: "default-icon-base64"

        return mapOf(
                "uuid" to job.uuid,
                "category" to job.category.name,
                "poster" to posterStringWebhook,
                "posterIcon" to faceIconBase64,
                "location" to job.location.toString(),
                "startTime" to job.startTime,
                "durationMilis" to job.durationMilis,
                "name" to job.name,
                "description" to job.description,
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
    private fun createPlayerIcon(skin: BufferedImage): BufferedImage {
        val icon = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = icon.createGraphics()

        val face = skin.getSubimage(8, 8, 8, 8)
        val helmet = skin.getSubimage(40, 8, 8, 8)

        g.drawImage(face, 0, 0, null)
        if (!isFullyTransparent(helmet)) {
            g.drawImage(helmet, 0, 0, null)
        }

        g.dispose()
        return icon
    }

    /** Checks if an image is fully transparent. */
    private fun isFullyTransparent(image: BufferedImage): Boolean =
            (0 until image.width).none { x ->
                (0 until image.height).any { y -> (image.getRGB(x, y) ushr 24) != 0 }
            }

    /** Encode an image to a base64 String. */
    private fun encodeImageToBase64(image: BufferedImage): String {
        val outputStream = ByteArrayOutputStream()
        return try {
            ImageIO.write(image, "png", outputStream)
            val imageBytes = outputStream.toByteArray()
            Base64.getEncoder().encodeToString(imageBytes)
        } finally {
            outputStream.close()
        }
    }
}
