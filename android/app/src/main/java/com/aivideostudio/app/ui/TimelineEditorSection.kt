package com.aivideostudio.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aivideostudio.app.model.TimelineClipItem
import com.aivideostudio.app.model.TimelineProjectBundle
import org.json.JSONArray
import org.json.JSONObject

private const val TIMELINE_PREFS = "ai_video_studio"
private val TRANSITION_CHOICES = listOf("Cut Tajam", "Cross Dissolve", "Fade In/Out")

private fun buildInitialClips(context: Context, projectId: Long, fallbackTitle: String): List<TimelineClipItem> {
    val savedV03Raw = context.getSharedPreferences(TIMELINE_PREFS, Context.MODE_PRIVATE)
        .getString("v03_bundle_$projectId", null)
    val clips = mutableListOf<TimelineClipItem>()
    if (savedV03Raw != null) {
        try {
            val scenesArr = JSONObject(savedV03Raw).optJSONArray("scenes")
            if (scenesArr != null && scenesArr.length() > 0) {
                for (i in 0 until scenesArr.length()) {
                    val s = scenesArr.optJSONObject(i) ?: continue
                    clips.add(
                        TimelineClipItem(
                            sceneNumber = s.optInt("sceneNumber", i + 1),
                            title = s.optString("title", "Adegan ${i + 1}"),
                            durationSec = s.optInt("durationSec", 15).coerceAtLeast(3),
                            transition = s.optString("transition", "Cut Tajam"),
                            visualSourceLabel = "Prompt / Aset Adegan ${i + 1}",
                            audioTrackLabel = s.optString("audioDirection", "Narasi + Musik Latar")
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
    }
    if (clips.isEmpty()) {
        val baseTitle = fallbackTitle.ifBlank { "Adegan" }
        for (i in 1..4) {
            clips.add(
                TimelineClipItem(
                    sceneNumber = i,
                    title = "$baseTitle — Bagian $i",
                    durationSec = 15,
                    transition = TRANSITION_CHOICES[(i - 1) % TRANSITION_CHOICES.size],
                    visualSourceLabel = "Visual Adegan $i",
                    audioTrackLabel = "Narasi + Ambience"
                )
            )
        }
    }
    return clips
}

private fun loadTimelineBundle(
    context: Context,
    projectId: Long,
    fallbackTitle: String
): TimelineProjectBundle {
    val fallback = TimelineProjectBundle(
        projectId = projectId,
        clips = buildInitialClips(context, projectId, fallbackTitle)
    )
    return try {
        val raw = context.getSharedPreferences(TIMELINE_PREFS, Context.MODE_PRIVATE)
            .getString("v07_timeline_$projectId", null) ?: return fallback
        val root = JSONObject(raw)
        val arr = root.optJSONArray("clips") ?: JSONArray()
        val parsed = (0 until arr.length()).map { idx ->
            val o = arr.optJSONObject(idx) ?: JSONObject()
            TimelineClipItem(
                sceneNumber = o.optInt("sceneNumber", idx + 1),
                title = o.optString("title", "Adegan ${idx + 1}"),
                durationSec = o.optInt("durationSec", 15).coerceAtLeast(3),
                transition = o.optString("transition", "Cut Tajam"),
                visualSourceLabel = o.optString("visualSourceLabel", "Visual Adegan ${idx + 1}"),
                audioTrackLabel = o.optString("audioTrackLabel", "Narasi + Musik Latar")
            )
        }
        TimelineProjectBundle(
            projectId = projectId,
            clips = if (parsed.isNotEmpty()) parsed else fallback.clips,
            updatedAt = root.optLong("updatedAt", System.currentTimeMillis())
        )
    } catch (_: Exception) {
        fallback
    }
}

private fun saveTimelineBundle(context: Context, bundle: TimelineProjectBundle) {
    val arr = JSONArray().apply {
        bundle.clips.forEach { clip ->
            put(JSONObject().apply {
                put("sceneNumber", clip.sceneNumber)
                put("title", clip.title)
                put("durationSec", clip.durationSec)
                put("transition", clip.transition)
                put("visualSourceLabel", clip.visualSourceLabel)
                put("audioTrackLabel", clip.audioTrackLabel)
            })
        }
    }
    val root = JSONObject().apply {
        put("projectId", bundle.projectId)
        put("clips", arr)
        put("updatedAt", System.currentTimeMillis())
    }
    context.getSharedPreferences(TIMELINE_PREFS, Context.MODE_PRIVATE)
        .edit().putString("v07_timeline_${bundle.projectId}", root.toString()).apply()
}

@Composable
fun TimelineEditorSection(
    context: Context,
    projectId: Long,
    projectTitle: String
) {
    val initial = remember(projectId) {
        loadTimelineBundle(context, projectId, projectTitle)
    }
    var bundle by remember(projectId) { mutableStateOf(initial) }
    var previewActive by remember(projectId) { mutableStateOf(false) }
    var statusText by remember(projectId) {
        mutableStateOf("Timeline editor v0.7 siap untuk proyek #$projectId.")
    }

    val totalDurationSec = bundle.clips.sumOf { it.durationSec }

    fun moveClip(fromIndex: Int, toIndex: Int) {
        if (toIndex !in bundle.clips.indices) return
        val mutable = bundle.clips.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        bundle = bundle.copy(clips = mutable, updatedAt = System.currentTimeMillis())
        statusText = "Urutan timeline diperbarui. Tekan Simpan Timeline untuk menyimpan."
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "TIMELINE VIDEO EDITOR (V0.7)",
                color = Color(0xFF7356D8),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Total durasi timeline: ${totalDurationSec} detik • ${bundle.clips.size} klip adegan",
                color = Color(0xFF543BB2),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        previewActive = !previewActive
                        statusText = if (previewActive) {
                            "Pratinjau urutan timeline aktif (${bundle.clips.size} klip, ${totalDurationSec}d)."
                        } else {
                            "Pratinjau urutan timeline ditutup."
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (previewActive) "Tutup Pratinjau" else "Pratinjau Timeline")
                }
                OutlinedButton(
                    onClick = {
                        saveTimelineBundle(context, bundle)
                        statusText = "Susunan timeline v0.7 disimpan ke proyek #$projectId."
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Simpan Timeline")
                }
            }

            Text(statusText, color = Color(0xFF543BB2), style = MaterialTheme.typography.bodySmall)

            if (previewActive) {
                var elapsed = 0
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF171329), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "PRATINJAU URUTAN WAKTU (STACK/LIST)",
                        color = Color(0xFFC9BDFD),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    for ((pos, clip) in bundle.clips.withIndex()) {
                        val start = elapsed
                        val end = start + clip.durationSec
                        elapsed = end
                        Text(
                            "${pos + 1}. [${start}s–${end}s] ${clip.title} • ${clip.transition} • ${clip.audioTrackLabel}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            for ((idx, clip) in bundle.clips.withIndex()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1EDFF).copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Posisi #${idx + 1} (Adegan #${clip.sceneNumber}) • ${clip.durationSec} detik",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        OutlinedTextField(
                            value = clip.title,
                            onValueChange = { newTitle ->
                                val next = bundle.clips.mapIndexed { i, it ->
                                    if (i == idx) it.copy(title = newTitle) else it
                                }
                                bundle = bundle.copy(clips = next)
                            },
                            label = { Text("Judul klip / adegan") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = clip.durationSec.toString(),
                                onValueChange = { raw ->
                                    val parsed = raw.filter { it.isDigit() }.toIntOrNull()?.coerceIn(2, 300) ?: clip.durationSec
                                    val next = bundle.clips.mapIndexed { i, it ->
                                        if (i == idx) it.copy(durationSec = parsed) else it
                                    }
                                    bundle = bundle.copy(clips = next)
                                },
                                label = { Text("Durasi (d)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = clip.transition,
                                onValueChange = { newTr ->
                                    val next = bundle.clips.mapIndexed { i, it ->
                                        if (i == idx) it.copy(transition = newTr) else it
                                    }
                                    bundle = bundle.copy(clips = next)
                                },
                                label = { Text("Transisi") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = clip.audioTrackLabel,
                            onValueChange = { newAudio ->
                                val next = bundle.clips.mapIndexed { i, it ->
                                    if (i == idx) it.copy(audioTrackLabel = newAudio) else it
                                }
                                bundle = bundle.copy(clips = next)
                            },
                            label = { Text("Jalur audio (voice-over / musik)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { moveClip(idx, idx - 1) },
                                enabled = idx > 0,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Naikkan")
                            }
                            OutlinedButton(
                                onClick = { moveClip(idx, idx + 1) },
                                enabled = idx < bundle.clips.lastIndex,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Turunkan")
                            }
                        }
                    }
                }
            }
        }
    }
}
