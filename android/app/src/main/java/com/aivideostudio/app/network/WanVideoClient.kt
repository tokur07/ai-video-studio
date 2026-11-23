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
        onProgress: (String) -> Unit = {},
        onResult: (SceneVideoResult) -> Unit
    ) {
        Thread {
            var currentStage = "Memulai proses video."
            fun progress(message: String) {
                currentStage = message
                mainHandler.post { onProgress(message) }
            }
            val result = runCatching {
                progress("Memeriksa gambar adegan dan konfigurasi layanan...")
                require(imageUri.isNotBlank()) { "Gambar adegan belum tersedia." }
                require(prompt.isNotBlank()) { "Prompt gerakan masih kosong." }
                val clean = workerBaseUrl.trim().trimEnd('/')
                require(clean.isNotBlank()) { "URL Cloudflare Worker belum diatur." }
                val imageContentUri = Uri.parse(imageUri)
                val imageMime = context.contentResolver.getType(imageContentUri) ?: "image/png"
                val imageExtension = when (imageMime.lowercase()) { "image/jpeg", "image/jpg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
                val imageBytes = context.contentResolver.openInputStream(imageContentUri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("File gambar adegan tidak dapat dibaca.")
                require(imageBytes.size <= 8 * 1024 * 1024) { "Gambar maksimal 8 MB untuk layanan GPU gratis." }
                progress("Gambar berhasil dibaca (${imageBytes.size / 1024} KB). Menghubungi Cloudflare Worker...")
                val boundary = "----AIVideoStudio${System.currentTimeMillis()}"
                val conn = (URL("$clean/v1/generate-video").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30000
                    readTimeout = 900000
                    doOutput = true
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    setRequestProperty("Accept", "text/event-stream")
                }
                progress("Mengirim gambar dan prompt ke Cloudflare Worker...")
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
                    out.write("Content-Disposition: form-data; name=\"image\"; filename=\"scene_$sceneNumber.$imageExtension\"\r\n".toByteArray())
                    out.write("Content-Type: $imageMime\r\n\r\n".toByteArray())
                    out.write(imageBytes)
                    out.write("\r\n--$boundary--\r\n".toByteArray())
                    out.flush()
                }
                val code = conn.responseCode
                progress("Cloudflare Worker merespons HTTP $code. Memeriksa antrean video...")
                if (code !in 200..299) {
                    val error = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    conn.disconnect()
                    val message = runCatching {
                        val obj = JSONObject(error)
                        listOf("error", "message", "detail").firstNotNullOfOrNull { key ->
                            val value = obj.opt(key)
                            if (value == null || value === JSONObject.NULL) null else value.toString().takeIf { it.isNotBlank() && it != "null" }
                        }
                    }.getOrNull().orEmpty()
                    throw IllegalStateException(message.ifBlank { "Worker video gagal (HTTP $code). Respons: ${error.take(500)}" })
                }
                progress("Permintaan diterima Worker. Menunggu hasil GPU Wan 2.2...")
                val videoUrl = try { readSseVideoUrl(conn) } finally { conn.disconnect() }
                progress("Video selesai diproses. Mengunduh file MP4...")
                val videoConn = (URL(videoUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30000
                    readTimeout = 120000
                }
                val videoCode = videoConn.responseCode
                if (videoCode !in 200..299) throw IllegalStateException("Unduhan MP4 gagal (HTTP $videoCode).")
                val contentType = videoConn.contentType ?: "tidak diketahui"
                val bytes = videoConn.inputStream.use { it.readBytes() }
                videoConn.disconnect()
                require(bytes.size > 1000) { "File video kosong atau tidak valid (ukuran ${bytes.size} byte)." }
                val mp4Signature = if (bytes.size >= 8) String(bytes, 4, 4, Charsets.US_ASCII) else ""
                require(mp4Signature == "ftyp") { "Server mengirim respons yang bukan MP4 (Content-Type: $contentType)." }
                val dir = File(context.filesDir, "scene_videos").apply { mkdirs() }
                val file = File(dir, "project_${projectId}_scene_${sceneNumber}_${System.currentTimeMillis()}.mp4")
                file.writeBytes(bytes)
                progress("Video adegan #$sceneNumber berhasil diunduh dan disimpan.")
                SceneVideoResult(true, Uri.fromFile(file).toString(), "Video adegan #$sceneNumber selesai dibuat dan disimpan.")
            }.getOrElse {
                val detail = it.message?.takeIf(String::isNotBlank) ?: "Tidak ada pesan dari server."
                SceneVideoResult(false, message = "$currentStage Gagal: ${it.javaClass.simpleName}: $detail")
            }
            mainHandler.post { onResult(result) }
        }.start()
    }

    private fun readSseVideoUrl(conn: HttpURLConnection): String {
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            var event = "message"
            val data = StringBuilder()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith("event:")) {
                    event = line.substringAfter(":").trim()
                } else if (line.startsWith("data:")) {
                    if (data.isNotEmpty()) data.append("\n")
                    data.append(line.substringAfter(":").trim())
                } else if (line.isBlank()) {
                    if (event == "error") {
                        val rawError = data.toString().ifBlank { "Model video melaporkan error." }
                        val message = runCatching {
                            val obj = JSONObject(rawError)
                            listOf("message", "error", "detail").firstNotNullOfOrNull { key ->
                                val value = obj.opt(key)
                                if (value == null || value === JSONObject.NULL) null else value.toString().takeIf { it.isNotBlank() && it != "null" }
                            } ?: rawError
                        }.getOrDefault(rawError)
                        throw IllegalStateException(message.ifBlank { "Model video mengirim error tanpa keterangan: ${rawError.take(500)}" })
                    }
                    if (event == "complete" || event == "done" || event == "success") {
                        val raw = data.toString()
                        val payload = runCatching { JSONArray(raw) }.getOrNull()
                        if (payload != null) {
                            for (index in 0 until payload.length()) {
                                val candidate = findGradioFile(payload.opt(index))
                                if (candidate != null) return candidate
                            }
                        } else {
                            val candidate = findGradioFile(runCatching { JSONObject(raw) }.getOrNull() ?: raw)
                            if (candidate != null) return candidate
                        }
                        throw IllegalStateException("Wan 2.2 selesai tetapi format hasil Gradio tidak berisi file MP4 yang dikenali: ${raw.take(300)}")
                    }
                    event = "message"
                    data.setLength(0)
                }
            }
        }
        throw IllegalStateException("Antrean ditutup sebelum video selesai. Coba ulangi saat GPU tersedia.")
    }

    private fun findGradioFile(value: Any?): String? {
        when (value) {
            is String -> {
                if (value.startsWith("https://") || value.startsWith("http://")) return value
                if (value.startsWith("/gradio_api/file=")) return "$SPACE_BASE$value"
                if (value.endsWith(".mp4") || value.contains("/tmp/") || value.contains("gradio")) {
                    return "$SPACE_BASE/gradio_api/file=$value"
                }
            }
            is JSONObject -> {
                val directUrl = value.optString("url").orEmpty()
                if (directUrl.startsWith("https://") || directUrl.startsWith("http://")) return directUrl
                if (directUrl.startsWith("/gradio_api/file=")) return "$SPACE_BASE$directUrl"
                val path = value.optString("path").orEmpty()
                if (path.isNotBlank()) return "$SPACE_BASE/gradio_api/file=$path"
                val video = value.opt("video")
                findGradioFile(video)?.let { return it }
                val nested = value.opt("data")
                findGradioFile(nested)?.let { return it }
            }
            is JSONArray -> {
                for (i in 0 until value.length()) findGradioFile(value.opt(i))?.let { return it }
            }
        }
        return null
    }
}
