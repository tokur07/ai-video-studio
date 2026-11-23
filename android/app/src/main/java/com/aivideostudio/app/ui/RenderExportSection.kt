package com.aivideostudio.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aivideostudio.app.model.RenderExportBundle
import com.aivideostudio.app.render.LocalVideoRenderer
import org.json.JSONObject

private val RESOLUTIONS = listOf("720p", "1080p")
private val EXPORT_RATIOS = listOf("9:16", "16:9", "1:1")
private const val PREFS_NAME = "ai_video_studio"

private fun loadRenderBundle(context: Context, projectId: Long): RenderExportBundle {
    val fallback = RenderExportBundle(projectId = projectId)
    return try {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("v08_render_$projectId", null) ?: return fallback
        val o = JSONObject(raw)
        RenderExportBundle(
            projectId = projectId,
            resolution = o.optString("resolution", "720p"),
            aspectRatio = o.optString("aspectRatio", "9:16"),
            audioMixingEnabled = o.optBoolean("audioMixingEnabled", true),
            subtitleBurnInEnabled = o.optBoolean("subtitleBurnInEnabled", true),
            renderStatus = o.optString("renderStatus", "Siap diekspor"),
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

    fun submitRenderJob() {
        if (isSubmitting) return
        isSubmitting = true
        val started = bundle.copy(
            renderStatus = "Memeriksa video adegan dan menyiapkan ekspor lokal...",
            progressPercent = 5,
            lastOutputLabel = "AI_Video_Studio_${projectId}.mp4"
        )
        bundle = started
        saveRenderBundle(context, started)

        LocalVideoRenderer.renderProject(
            context = context,
            projectId = projectId,
            audioEnabled = bundle.audioMixingEnabled,
            subtitlesEnabled = bundle.subtitleBurnInEnabled,
            resolution = bundle.resolution,
            aspectRatio = bundle.aspectRatio,
            onStatus = { message, percent ->
                val next = bundle.copy(
                    renderStatus = message,
                    progressPercent = percent.coerceIn(0, 99),
                    updatedAt = System.currentTimeMillis()
                )
                bundle = next
                saveRenderBundle(context, next)
            },
            onComplete = { success, outputUri, message ->
                isSubmitting = false
                val next = bundle.copy(
                    renderStatus = message,
                    progressPercent = if (success) 100 else 0,
                    lastOutputLabel = if (success) "AI_Video_Studio_${projectId}.mp4" else bundle.lastOutputLabel,
                    lastDownloadUrl = if (success) outputUri else bundle.lastDownloadUrl,
                    updatedAt = System.currentTimeMillis()
                )
                bundle = next
                saveRenderBundle(context, next)
            }
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "RENDER & EKSPOR VIDEO MP4",
                color = Color(0xFF7356D8),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Gabungkan seluruh klip animasi menjadi satu MP4 langsung di Android. Narasi dibuat dengan mesin Text-to-Speech perangkat, subtitle dibakar ke video, dan hasil disimpan ke folder Movies/AI Video Studio. Tidak membutuhkan runner berbayar.",
                color = Color(0xFF77738A),
                style = MaterialTheme.typography.bodySmall
            )

            Text("Resolusi target", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (res in RESOLUTIONS) {
                    FilterChip(
                        selected = bundle.resolution == res,
                        onClick = { bundle = bundle.copy(resolution = res) },
                        label = { Text(res) }
                    )
                }
            }

            Text("Rasio aspek", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (ratio in EXPORT_RATIOS) {
                    FilterChip(
                        selected = bundle.aspectRatio == ratio,
                        onClick = { bundle = bundle.copy(aspectRatio = ratio) },
                        label = { Text(ratio) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = bundle.audioMixingEnabled,
                    onClick = { bundle = bundle.copy(audioMixingEnabled = !bundle.audioMixingEnabled) },
                    label = { Text("Narasi: ${if (bundle.audioMixingEnabled) "Aktif" else "Mati"}") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = bundle.subtitleBurnInEnabled,
                    onClick = { bundle = bundle.copy(subtitleBurnInEnabled = !bundle.subtitleBurnInEnabled) },
                    label = { Text("Subtitle: ${if (bundle.subtitleBurnInEnabled) "Aktif" else "Mati"}") },
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
                Text("File: ${bundle.lastOutputLabel}", color = Color(0xFF171329), style = MaterialTheme.typography.bodySmall)
            }

            if (bundle.lastDownloadUrl.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(bundle.lastDownloadUrl), "video/mp4")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Putar Video MP4 Final")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { submitRenderJob() },
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isSubmitting) "Merender..." else "Render MP4 Final")
                }
                OutlinedButton(
                    onClick = {
                        saveRenderBundle(context, bundle)
                        bundle = bundle.copy(renderStatus = "Setelan ekspor disimpan.")
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
