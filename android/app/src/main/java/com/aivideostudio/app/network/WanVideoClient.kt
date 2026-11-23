package com.aivideostudio.app.network

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class SceneVideoResult(
    val success: Boolean,
    val localUri: String = "",
    val message: String = ""
)

object WanVideoClient {
    private const val SPACE_BASE = "https://cbensimon-wan2-2-fp8da-aoti-preview2.hf.space"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun generateAsync(
        context: Context,
        workerBaseUrl: String,
        projectId: Long,
        sceneNumber: Int,
        imageUri: String,
        prompt: String,
        negativePrompt: String,
        onResult: (SceneVideoResult) -> Unit
    ) {
        Thread {
            val result = runCatching {
                require(imageUri.isNotBlank()) { "Gambar adegan belum tersedia." }
                require(prompt.isNotBlank()) { "Prompt gerakan masih kosong." }
                val clean = workerBaseUrl.trim().trimEnd('/')
                require(clean.isNotBlank()) { "URL Cloudflare Worker belum diatur." }
                val imageBytes = context.contentResolver.openInputStream(Uri.parse(imageUri))?.use { it.readBytes() }
                    ?: throw IllegalStateException("File gambar adegan tidak dapat dibaca.")
                require(imageBytes.size <= 8 * 1024 * 1024) { "Gambar maksimal 8 MB untuk layanan GPU gratis." }
                val boundary = "----AIVideoStudio${System.currentTimeMillis()}"
                val conn = (URL("$clean/v1/generate-video").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30000
                    readTimeout = 300000
                    doOutput = true
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    setRequestProperty("Accept", "text/event-stream")
                }
                conn.outputStream.use { out ->
                    fun field(name: String, value: String) {
                        out.write("--$boundary\r\n".toByteArray())
                        out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                        out.write(value.toByteArray(Charsets.UTF_8))
                        out.write("\r\n".toByteArray())
                    }
                    field("params", JSONObject().apply {
                        put("prompt", prompt)
                        put("negativePrompt", negativePrompt)
                        put("durationSeconds", 3.5)
                        put("steps", 6)
                    }.toString())
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"image\"; filename=\"scene_$sceneNumber.png\"\r\n".toByteArray())
                    out.write("Content-Type: image/png\r\n\r\n".toByteArray())
                    out.write(imageBytes)
                    out.write("\r\n--$boundary--\r\n".toByteArray())
                    out.flush()
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val error = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    conn.disconnect()
                    val message = runCatching { JSONObject(error).optString("error") }.getOrNull().orEmpty()
                    throw IllegalStateException(message.ifBlank { "Worker video gagal (HTTP $code)." })
                }
                val videoUrl = readSseVideoUrl(conn)
                conn.disconnect()
                val videoConn = (URL(videoUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30000
                    readTimeout = 120000
                }
                val videoCode = videoConn.responseCode
                if (videoCode !in 200..299) throw IllegalStateException("Unduhan MP4 gagal (HTTP $videoCode).")
                val bytes = videoConn.inputStream.use { it.readBytes() }
                videoConn.disconnect()
                require(bytes.size > 1000) { "File video kosong atau tidak valid." }
                val dir = File(context.filesDir, "scene_videos").apply { mkdirs() }
                val file = File(dir, "project_${projectId}_scene_${sceneNumber}_${System.currentTimeMillis()}.mp4")
                file.writeBytes(bytes)
                SceneVideoResult(true, Uri.fromFile(file).toString(), "Video adegan #$sceneNumber selesai dibuat dan disimpan.")
            }.getOrElse { SceneVideoResult(false, message = it.message ?: "Gagal membuat video.") }
            mainHandler.post { onResult(result) }
        }.start()
    }

    private fun readSseVideoUrl(conn: HttpURLConnection): String {
        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
        var event = "message"
        val data = StringBuilder()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.startsWith("event:")) event = line.substringAfter(":").trim()
            else if (line.startsWith("data:")) {
                if (data.isNotEmpty()) data.append("\n")
                data.append(line.substringAfter(":").trim())
            } else if (line.isBlank()) {
                if (event == "error") throw IllegalStateException(data.toString().ifBlank { "Model video melaporkan error." })
                if (event == "complete") {
                    val payload = JSONArray(data.toString())
                    val first = payload.optJSONObject(0)
                    val url = first?.optString("url").orEmpty()
                    val path = first?.optString("path").orEmpty()
                    if (url.startsWith("https://")) return url
                    if (path.isNotBlank()) return "$SPACE_BASE/gradio_api/file=$path"
                    throw IllegalStateException("Wan 2.2 selesai tetapi tidak mengembalikan URL MP4.")
                }
                event = "message"
                data.setLength(0)
            }
        }
        throw IllegalStateException("Antrean ditutup sebelum video selesai. Coba ulangi saat GPU tersedia.")
    }
}
