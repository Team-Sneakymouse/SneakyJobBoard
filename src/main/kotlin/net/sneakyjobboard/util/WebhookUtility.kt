package net.sneakyjobboard.util

import me.clip.placeholderapi.PlaceholderAPI
import net.sneakyjobboard.SneakyJobBoard
import net.sneakyjobboard.job.Job
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import org.json.JSONObject

object WebhookUtility {

    fun listJob(job: Job) {
        val jobData = createJobDataData(job)

        val jsonRequestBody =
                JSONObject(jobData).toString().toRequestBody("application/json".toMediaType())

        val client = OkHttpClient()

        val request =
                Request.Builder()
                        .url("http://localhost:80/lom2jobboard")
                        .post(jsonRequestBody)
                        .build()

        client.newCall(request)
                .enqueue(
                        object : Callback {
                            override fun onFailure(call: Call, e: IOException) {}

                            override fun onResponse(call: Call, response: Response) {}
                        }
                )
    }

    fun createJobDataData(job: Job): Map<String, Any> {
        var posterStringWebhook =
                (SneakyJobBoard.getInstance().getConfig().getString("poster-string-webhook")
                                ?: "[playerName]").replace("[playerName]", job.player.name)

        if (SneakyJobBoard.isPapiActive()) {
            posterStringWebhook = PlaceholderAPI.setPlaceholders(job.player, posterStringWebhook)
        }

        return mapOf(
                "uuid" to job.uuid,
                "category" to job.category.name,
                "poster" to posterStringWebhook,
                "location" to job.location.toString(),
                "startTime" to job.startTime,
                "durationMilis" to job.durationMilis,
                "name" to job.name,
                "description" to job.description,
        )
    }
}
