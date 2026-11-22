package com.aivideostudio.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aivideostudio.app.model.FactCheckItem
import com.aivideostudio.app.model.ReferenceItem
import com.aivideostudio.app.model.ResearchScriptBundle
import com.aivideostudio.app.network.WorkerResearchClient
import org.json.JSONArray
import org.json.JSONObject

private val WRITING_STYLES = listOf(
    "Informatif & Jelas",
    "Sinematik & Naratif",
    "Tegas & Ringkas",
    "Santai & Edukatif"
)

private const val PREFS_NAME = "ai_video_studio"
private const val KEY_WORKER_URL = "worker_api_url"

fun loadWorkerUrl(context: Context): String {
    val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_WORKER_URL, "")?.trim().orEmpty()
    return saved.ifEmpty { "https://ai-video-studio-proxy.tonnykurniawan26.workers.dev" }
}

fun saveWorkerUrl(context: Context, url: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_WORKER_URL, url.trim()).apply()
}

private fun loadResearchBundle(
    context: Context,
    projectId: Long,
    fallbackTopic: String,
    fallbackType: String
): ResearchScriptBundle {
    val fallback = ResearchScriptBundle(
        projectId = projectId,
        topic = fallbackTopic,
        videoType = fallbackType
    )
    return try {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("v04_research_$projectId", null) ?: return fallback
        val root = JSONObject(raw)

        val kpArr = root.optJSONArray("keyPoints") ?: JSONArray()
        val keyPoints = (0 until kpArr.length()).map { kpArr.optString(it, "") }.filter { it.isNotBlank() }

        val fcArr = root.optJSONArray("factChecks") ?: JSONArray()
        val factChecks = (0 until fcArr.length()).map { idx ->
            val o = fcArr.optJSONObject(idx) ?: JSONObject()
            FactCheckItem(
                claim = o.optString("claim", ""),
                status = o.optString("status", "Perlu Verifikasi"),
                note = o.optString("note", "")
            )
        }

        val refArr = root.optJSONArray("references") ?: JSONArray()
        val references = (0 until refArr.length()).map { idx ->
            val o = refArr.optJSONObject(idx) ?: JSONObject()
            ReferenceItem(
                title = o.optString("title", ""),
                sourceType = o.optString("sourceType", ""),
                note = o.optString("note", "")
            )
        }

        ResearchScriptBundle(
            projectId = projectId,
            topic = root.optString("topic", fallbackTopic),
            videoType = root.optString("videoType", fallbackType),
            writingStyle = root.optString("writingStyle", "Informatif & Jelas"),
            targetAudience = root.optString("targetAudience", "Pemula / Umum"),
            targetDurationSec = root.optInt("targetDurationSec", 60),
            summary = root.optString("summary", ""),
            keyPoints = keyPoints,
            factChecks = factChecks,
            references = references,
            opening = root.optString("opening", ""),
            body = root.optString("body", ""),
            closing = root.optString("closing", ""),
            revisionInstruction = root.optString("revisionInstruction", ""),
            providerLabel = root.optString("providerLabel", "Kerangka Terstruktur Lokal (Belum Terhubung Cloud)"),
            isLiveCloudResult = root.optBoolean("isLiveCloudResult", false),
            updatedAt = root.optLong("updatedAt", System.currentTimeMillis())
        )
    } catch (_: Exception) {
        fallback
    }
}

private fun saveResearchBundle(context: Context, bundle: ResearchScriptBundle) {
    val kpArr = JSONArray().apply { bundle.keyPoints.forEach { put(it) } }
    val fcArr = JSONArray().apply {
        bundle.factChecks.forEach {
            put(JSONObject().apply {
                put("claim", it.claim)
                put("status", it.status)
                put("note", it.note)
            })
        }
    }
    val refArr = JSONArray().apply {
        bundle.references.forEach {
            put(JSONObject().apply {
                put("title", it.title)
                put("sourceType", it.sourceType)
                put("note", it.note)
            })
        }
    }
    val root = JSONObject().apply {
        put("projectId", bundle.projectId)
        put("topic", bundle.topic)
        put("videoType", bundle.videoType)
        put("writingStyle", bundle.writingStyle)
        put("targetAudience", bundle.targetAudience)
        put("targetDurationSec", bundle.targetDurationSec)
        put("summary", bundle.summary)
        put("keyPoints", kpArr)
        put("factChecks", fcArr)
        put("references", refArr)
        put("opening", bundle.opening)
        put("body", bundle.body)
        put("closing", bundle.closing)
        put("revisionInstruction", bundle.revisionInstruction)
        put("providerLabel", bundle.providerLabel)
        put("isLiveCloudResult", bundle.isLiveCloudResult)
        put("updatedAt", System.currentTimeMillis())
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString("v04_research_${bundle.projectId}", root.toString()).apply()
}

@Composable
fun ResearchScriptSection(
    context: Context,
    projectId: Long,
    projectTitle: String,
    projectType: String
) {
    val loaded = remember(projectId) {
        loadResearchBundle(context, projectId, projectTitle, projectType)
    }

    var state by remember(projectId) { mutableStateOf(loaded) }
    var isLoading by remember(projectId) { mutableStateOf(false) }
    var statusText by remember(projectId) {
        mutableStateOf("Siap menyusun riset & naskah v0.4 untuk proyek #$projectId.")
    }

    fun runResearchAndScript() {
        val cleanTopic = state.topic.trim().ifEmpty { projectTitle.trim() }
        if (cleanTopic.isBlank()) {
            statusText = "Masukkan topik riset terlebih dahulu."
            return
        }
        isLoading = true
        statusText = "Memproses riset dan draf naskah..."
        val workerUrl = loadWorkerUrl(context)
        WorkerResearchClient.generateResearchAndScriptAsync(
            workerBaseUrl = workerUrl,
            current = state.copy(topic = cleanTopic)
        ) { updated ->
            state = updated
            saveResearchBundle(context, updated)
            isLoading = false
            statusText = if (updated.isLiveCloudResult) {
                "Selesai via ${updated.providerLabel} dan tersimpan."
            } else {
                "Selesai (${updated.providerLabel}) dan tersimpan."
            }
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "RISET TOPIK & NASKAH (V0.4)",
                color = Color(0xFF7356D8),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Sumber: ${state.providerLabel}",
                color = Color(0xFF543BB2),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = state.topic,
                onValueChange = { state = state.copy(topic = it) },
                label = { Text("Topik riset") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Gaya penulisan naskah", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (style in WRITING_STYLES) {
                    FilterChip(
                        selected = state.writingStyle == style,
                        onClick = { state = state.copy(writingStyle = style) },
                        label = { Text(style, maxLines = 1) }
                    )
                }
            }

            OutlinedTextField(
                value = state.revisionInstruction,
                onValueChange = { state = state.copy(revisionInstruction = it) },
                label = { Text("Instruksi revisi / penekanan naskah") },
                placeholder = { Text("Contoh: Fokuskan pada kronologi dan perjelas fakta kunci") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { runResearchAndScript() },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isLoading) "Memproses..." else "Riset & Susun Naskah")
                }
                OutlinedButton(
                    onClick = {
                        saveResearchBundle(context, state)
                        statusText = "Perubahan riset & naskah v0.4 disimpan."
                    },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Simpan")
                }
            }

            Text(statusText, color = Color(0xFF543BB2), style = MaterialTheme.typography.bodySmall)

            if (state.summary.isNotBlank()) {
                Text("Ringkasan Riset", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(state.summary, style = MaterialTheme.typography.bodySmall)
            }

            if (state.keyPoints.isNotEmpty()) {
                Text("Poin Utama", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                for (pt in state.keyPoints) {
                    Text("• $pt", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (state.factChecks.isNotEmpty()) {
                Text("Catatan Verifikasi Fakta", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                for (fc in state.factChecks) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1EDFF), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("[${fc.status}] ${fc.claim}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        if (fc.note.isNotBlank()) {
                            Text(fc.note, color = Color(0xFF77738A), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (state.references.isNotEmpty()) {
                Text("Sumber Referensi", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                for (ref in state.references) {
                    Text("• ${ref.title} (${ref.sourceType}) — ${ref.note}", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (state.opening.isNotBlank() || state.body.isNotBlank() || state.closing.isNotBlank()) {
                Text("Draf Naskah (Dapat Direvisi Langsung)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = state.opening,
                    onValueChange = { state = state.copy(opening = it) },
                    label = { Text("Pembukaan / Hook") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.body,
                    onValueChange = { state = state.copy(body = it) },
                    label = { Text("Isi Naskah") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.closing,
                    onValueChange = { state = state.copy(closing = it) },
                    label = { Text("Penutup") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
