package com.aivideostudio.app.network

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class SceneImageGenResult(
    val success: Boolean,
    val localUri: String = "",
    val providerLabel: String = "",
    val message: String = ""
)

object WorkerVisualClient {

    private const val TAG = "AIVideoDiag"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun generateSceneImageAsync(
        context: Context,
        workerBaseUrl: String,
        projectId: Long,
        sceneNumber: Int,
        visualPrompt: String,
        negativePrompt: String,
        aspectRatio: String,
        onResult: (SceneImageGenResult) -> Unit
    ) {
        Log.d(TAG, "generateSceneImageAsync called: proj=$projectId scene=$sceneNumber url=$workerBaseUrl")
        Thread {
            val res = generateSceneImageBlocking(
                context = context,
                workerBaseUrl = workerBaseUrl,
                projectId = projectId,
                sceneNumber = sceneNumber,
                visualPrompt = visualPrompt,
                negativePrompt = negativePrompt,
                aspectRatio = aspectRatio
            )
            Log.d(TAG, "generateSceneImageBlocking finished: success=${res.success} msg=${res.message}")
            mainHandler.post { onResult(res) }
        }.start()
    }

    private fun generateSceneImageBlocking(
        context: Context,
        workerBaseUrl: String,
        projectId: Long,
        sceneNumber: Int,
        visualPrompt: String,
        negativePrompt: String,
        aspectRatio: String
    ): SceneImageGenResult {
        val cleanUrl = workerBaseUrl.trim().trimEnd('/')
        if (cleanUrl.isEmpty()) {
            Log.w(TAG, "workerBaseUrl is empty")
            return SceneImageGenResult(
                success = false,
                message = "URL Cloudflare Worker belum diisi di menu Setelan. Gunakan Impor Gambar atau isi URL Worker terlebih dahulu."
            )
        }

        return try {
            val endpoint = URL("$cleanUrl/v1/generate-image")
            Log.d(TAG, "Connecting to $endpoint")
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 45000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val payload = JSONObject().apply {
                put("visualPrompt", visualPrompt)
                put("negativePrompt", negativePrompt)
                put("aspectRatio", aspectRatio)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = conn.responseCode
            Log.d(TAG, "HTTP responseCode=$code")
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val rawResponse = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            conn.disconnect()

            val root = JSONObject(rawResponse)
            if (code !in 200..299 || !root.optBoolean("ok", false)) {
                val err = root.optString("error", "Server mengembalikan status $code")
                Log.w(TAG, "Server returned not ok: code=$code err=$err")
                return SceneImageGenResult(success = false, message = "Gagal generate gambar AI: $err")
            }

            val hasLive = root.optBoolean("hasLiveImage", false)
            val providerUsed = root.optString("providerUsed", "Cloudflare Worker")
            val b64 = root.optString("imageBase64", "")
            Log.d(TAG, "Parsed JSON: hasLive=$hasLive provider=$providerUsed b64Len=${b64.length}")
            if (!hasLive || b64.isBlank()) {
                return SceneImageGenResult(
                    success = false,
                    providerLabel = providerUsed,
                    message = "Worker terhubung ($providerUsed), tetapi provider gambar AI di server belum aktif."
                )
            }

            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val dir = File(context.filesDir, "scene_images").apply { if (!exists()) mkdirs() }
            Log.d(TAG, "scene_images dir=${dir.absolutePath} exists=${dir.exists()} canWrite=${dir.canWrite()}")
            val outFile = File(dir, "proj_${projectId}_scene_${sceneNumber}_${System.currentTimeMillis()}.png")
            outFile.writeBytes(bytes)
            Log.d(TAG, "Wrote file=${outFile.absolutePath} size=${outFile.length()}")

            SceneImageGenResult(
                success = true,
                localUri = Uri.fromFile(outFile).toString(),
                providerLabel = providerUsed,
                message = "Gambar AI adegan #$sceneNumber berhasil dibuat ($providerUsed)."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception in generateSceneImageBlocking: ${e.javaClass.simpleName}: ${e.message}", e)
            SceneImageGenResult(
                success = false,
                message = "Gagal menghubungi Worker untuk gambar AI: ${e.message ?: "kesalahan jaringan"}"
            )
        }
    }
}
