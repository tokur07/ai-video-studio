package com.aivideostudio.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aivideostudio.app.model.RenderExportBundle
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private val RESOLUTIONS = listOf("720p", "1080p")
private val EXPORT_RATIOS = listOf("9:16", "16:9", "1:1")
private const val PREFS_NAME = "ai_video_studio"
private const val KEY_RENDER_RUNNER_URL = "render_runner_url"

fun loadRenderRunnerUrl(context: Context): String {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_RENDER_RUNNER_URL, "") ?: ""
}

fun saveRenderRunnerUrl(context: Context, url: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_RENDER_RUNNER_URL, url.trim()).apply()
}

private fun loadRenderBundle(context: Context, projectId: Long): RenderExportBundle {
    val fallback = RenderExportBundle(projectId = projectId)
    return try {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("v08_render_$projectId", null) ?: return fallback
        val o = JSONObject(raw)
        RenderExportBundle(
            projectId = projectId,
            resolution = o.optString("resolution", "1080p"),
            aspectRatio = o.optString("aspectRatio", "9:16"),
            audioMixingEnabled = o.optBoolean("audioMixingEnabled", true),
            subtitleBurnInEnabled = o.optBoolean("subtitleBurnInEnabled", true),
            renderStatus = o.optString("renderStatus", "Siap Antre (Belum Dijalankan)"),
            progressPercent = o.optInt("progressPercent", 0).coerceIn(0, 100),
            lastOutputLabel = o.optString("lastOutputLabel", ""),
            lastDownloadUrl = o.optString("lastDownloadUrl", ""),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    } catch (_: Exception) {
        fallback
    }
}

private fun saveRenderBundle(context: Context, bundle: RenderExportBundle) {
    val o = JSONObject().apply {
        put("projectId", bundle.projectId)
        put("resolution", bundle.resolution)
        put("aspectRatio", bundle.aspectRatio)
        put("audioMixingEnabled", bundle.audioMixingEnabled)
        put("subtitleBurnInEnabled", bundle.subtitleBurnInEnabled)
        put("renderStatus", bundle.renderStatus)
        put("progressPercent", bundle.progressPercent)
        put("lastOutputLabel", bundle.lastOutputLabel)
        put("lastDownloadUrl", bundle.lastDownloadUrl)
        put("updatedAt", System.currentTimeMillis())
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString("v08_render_${bundle.projectId}", o.toString()).apply()
}

@Composable
fun RenderExportSection(
    context: Context,
    projectId: Long,
    projectTitle: String
) {
    val initial = remember(projectId) { loadRenderBundle(context, projectId) }
    var bundle by remember(projectId) { mutableStateOf(initial) }
    var isSubmitting by remember(projectId) { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun submitRenderJob() {
        val runnerUrl = loadRenderRunnerUrl(context).trim().trimEnd('/')
        val basePayload = JSONObject().apply {
            put("projectId", projectId)
            put("title", projectTitle)
            put("resolution", bundle.resolution)
            put("aspectRatio", bundle.aspectRatio)
            put("audioMixing", bundle.audioMixingEnabled)
            put("subtitleBurnIn", bundle.subtitleBurnInEnabled)
        }

        if (runnerUrl.isEmpty()) {
            val queued = bundle.copy(
                renderStatus = "Manifest render MP4 disimpan (Runner FFmpeg eksternal belum dikonfigurasi)",
                progressPercent = 15,
                lastOutputLabel = "project_${projectId}_${bundle.resolution}_${bundle.aspectRatio.replace(":", "x")}.mp4",
                updatedAt = System.currentTimeMillis()
            )
            bundle = queued
            saveRenderBundle(context, queued)
            return
        }

        isSubmitting = true
        val submittingState = bundle.copy(
            renderStatus = "Mengirim job render ke FFmpeg runner...",
            progressPercent = 25
        )
        bundle = submittingState

        Thread {
            try {
                val conn = (URL("$runnerUrl/v1/render").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 25000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                    it.write(basePayload.toString())
                    it.flush()
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val raw = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                conn.disconnect()

                val root = JSONObject(raw)
                val dlUrl = root.optString("downloadUrl", root.optString("outputUrl", ""))
                val statusMsg = if (code in 200..299 && root.optBoolean("ok", true)) {
                    root.optString("status", "Job render MP4 diterima runner FFmpeg.")
                } else {
                    "Runner merespons status $code: ${root.optString("error", "periksa endpoint render")}"
                }
                val pct = if (code in 200..299) 100 else 25

                mainHandler.post {
                    isSubmitting = false
                    val next = bundle.copy(
                        renderStatus = statusMsg,
                        progressPercent = pct,
                        lastOutputLabel = "project_${projectId}_${bundle.resolution}.mp4",
                        lastDownloadUrl = dlUrl,
                        updatedAt = System.currentTimeMillis()
                    )
                    bundle = next
                    saveRenderBundle(context, next)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    isSubmitting = false
                    val next = bundle.copy(
                        renderStatus = "Gagal menghubungi runner render: ${e.message ?: "kesalahan jaringan"}",
                        progressPercent = 0,
                        updatedAt = System.currentTimeMillis()
                    )
                    bundle = next
                    saveRenderBundle(context, next)
                }
            }
        }.start()
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "RENDER & EKSPOR VIDEO MP4 (V0.8)",
                color = Color(0xFF7356D8),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Konfigurasi ekspor MP4 melalui layanan/runner FFmpeg terpisah agar HP Android & Cloudflare Worker tidak terbebani rendering video berat.",
                color = Color(0xFF77738A),
                style = MaterialTheme.typography.bodySmall
            )

            Text("Resolusi video", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (res in RESOLUTIONS) {
                    FilterChip(
                        selected = bundle.resolution == res,
                        onClick = { bundle = bundle.copy(resolution = res) },
                        label = { Text(res) }
                    )
                }
            }

            Text("Rasio aspek ekspor", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (ratio in EXPORT_RATIOS) {
                    FilterChip(
                        selected = bundle.aspectRatio == ratio,
                        onClick = { bundle = bundle.copy(aspectRatio = ratio) },
                        label = { Text(ratio) }
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = bundle.audioMixingEnabled,
                    onClick = { bundle = bundle.copy(audioMixingEnabled = !bundle.audioMixingEnabled) },
                    label = { Text("Audio Mixing: ${if (bundle.audioMixingEnabled) "Aktif" else "Mati"}") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = bundle.subtitleBurnInEnabled,
                    onClick = { bundle = bundle.copy(subtitleBurnInEnabled = !bundle.subtitleBurnInEnabled) },
                    label = { Text("Burn-In Subtitle: ${if (bundle.subtitleBurnInEnabled) "Ya" else "Tidak"}") },
                    modifier = Modifier.weight(1f)
                )
            }

            LinearProgressIndicator(
                progress = { bundle.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Progres: ${bundle.progressPercent}% • ${bundle.renderStatus}",
                color = Color(0xFF543BB2),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            if (bundle.lastOutputLabel.isNotBlank()) {
                Text(
                    "Target file: ${bundle.lastOutputLabel}",
                    color = Color(0xFF171329),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (bundle.lastDownloadUrl.isNotBlank()) {
                Text(
                    "Link unduhan hasil: ${bundle.lastDownloadUrl}",
                    color = Color(0xFF247B5B),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { submitRenderJob() },
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isSubmitting) "Mengirim..." else "Render MP4")
                }
                OutlinedButton(
                    onClick = {
                        saveRenderBundle(context, bundle)
                        bundle = bundle.copy(renderStatus = "Setelan render v0.8 disimpan.")
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Simpan Setelan")
                }
            }
        }
    }
}
