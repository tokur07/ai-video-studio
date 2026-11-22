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
import com.aivideostudio.app.model.SubtitleCue
import com.aivideostudio.app.model.VoiceSubtitleBundle
import org.json.JSONArray
import org.json.JSONObject

private val VOICE_CHARACTERS = listOf(
    "Narator Jelas & Hangat",
    "Dokumenter Serius",
    "Enerjik & Ringkas",
    "Tenang & Bercerita"
)

private val LANGUAGE_ACCENTS = listOf(
    "Indonesia (Netral)",
    "Indonesia (Santai Percakapan)",
    "Inggris (Netral)"
)

private val TEMPO_OPTIONS = listOf(
    "Lambat (0.85x)",
    "Normal (1.0x)",
    "Cepat (1.15x)"
)

private const val VOICE_PREFS = "ai_video_studio"

private fun loadVoiceSubtitleBundle(
    context: Context,
    projectId: Long,
    fallbackTitle: String
): VoiceSubtitleBundle {
    val fallback = VoiceSubtitleBundle(projectId = projectId)
    return try {
        val raw = context.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE)
            .getString("v06_voice_sub_$projectId", null) ?: return fallback
        val root = JSONObject(raw)
        val arr = root.optJSONArray("cues") ?: JSONArray()
        val cues = (0 until arr.length()).map { idx ->
            val o = arr.optJSONObject(idx) ?: JSONObject()
            SubtitleCue(
                sceneNumber = o.optInt("sceneNumber", idx + 1),
                startSec = o.optInt("startSec", idx * 15),
                endSec = o.optInt("endSec", (idx + 1) * 15),
                text = o.optString("text", fallbackTitle.ifBlank { "Narasi adegan ${idx + 1}" })
            )
        }
        VoiceSubtitleBundle(
            projectId = projectId,
            voiceCharacter = root.optString("voiceCharacter", "Narator Jelas & Hangat"),
            languageAccent = root.optString("languageAccent", "Indonesia (Netral)"),
            tempoPace = root.optString("tempoPace", "Normal (1.0x)"),
            pauseNote = root.optString("pauseNote", "Jeda 0.4 detik antar kalimat utama"),
            autoSyncEnabled = root.optBoolean("autoSyncEnabled", true),
            cues = cues,
            updatedAt = root.optLong("updatedAt", System.currentTimeMillis())
        )
    } catch (_: Exception) {
        fallback
    }
}

private fun saveVoiceSubtitleBundle(context: Context, bundle: VoiceSubtitleBundle) {
    val arr = JSONArray().apply {
        bundle.cues.forEach { cue ->
            put(JSONObject().apply {
                put("sceneNumber", cue.sceneNumber)
                put("startSec", cue.startSec)
                put("endSec", cue.endSec)
                put("text", cue.text)
            })
        }
    }
    val root = JSONObject().apply {
        put("projectId", bundle.projectId)
        put("voiceCharacter", bundle.voiceCharacter)
        put("languageAccent", bundle.languageAccent)
        put("tempoPace", bundle.tempoPace)
        put("pauseNote", bundle.pauseNote)
        put("autoSyncEnabled", bundle.autoSyncEnabled)
        put("cues", arr)
        put("updatedAt", System.currentTimeMillis())
    }
    context.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE)
        .edit().putString("v06_voice_sub_${bundle.projectId}", root.toString()).apply()
}

private fun buildSyncedCues(
    context: Context,
    projectId: Long,
    fallbackTitle: String,
    tempoPace: String
): List<SubtitleCue> {
    val baseDur = when {
        tempoPace.contains("0.85x") -> 18
        tempoPace.contains("1.15x") -> 12
        else -> 15
    }
    val savedV03Raw = context.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE)
        .getString("v03_bundle_$projectId", null)
    val sceneTexts = mutableListOf<Pair<String, Int>>()
    if (savedV03Raw != null) {
        try {
            val scenesArr = JSONObject(savedV03Raw).optJSONArray("scenes")
            if (scenesArr != null && scenesArr.length() > 0) {
                for (i in 0 until scenesArr.length()) {
                    val s = scenesArr.optJSONObject(i) ?: continue
                    val narr = s.optString("narration", "").trim()
                    val title = s.optString("title", "Adegan ${i + 1}").trim()
                    val dur = s.optInt("durationSec", baseDur).coerceAtLeast(3)
                    val text = if (narr.isNotBlank()) narr else "$title — ${fallbackTitle.ifBlank { "Topik video" }}"
                    sceneTexts.add(text to dur)
                }
            }
        } catch (_: Exception) {
        }
    }
    if (sceneTexts.isEmpty()) {
        val topic = fallbackTitle.ifBlank { "Topik video" }
        for (i in 1..4) {
            val text = when (i) {
                1 -> "Pembukaan: Mari bahas $topic."
                4 -> "Penutup: Ringkasan utama tentang $topic."
                else -> "Bagian $i: Poin penting terkait $topic."
            }
            sceneTexts.add(text to baseDur)
        }
    }

    var cursor = 0
    return sceneTexts.mapIndexed { idx, (text, dur) ->
        val start = cursor
        val end = start + dur
        cursor = end
        SubtitleCue(
            sceneNumber = idx + 1,
            startSec = start,
            endSec = end,
            text = text
        )
    }
}

@Composable
fun VoiceSubtitleSection(
    context: Context,
    projectId: Long,
    projectTitle: String
) {
    val initial = remember(projectId) {
        loadVoiceSubtitleBundle(context, projectId, projectTitle)
    }
    var bundle by remember(projectId) { mutableStateOf(initial) }
    var statusText by remember(projectId) {
        mutableStateOf("Pengaturan suara & subtitle v0.6 siap untuk proyek #$projectId.")
    }

    fun generateAndSyncSubtitles() {
        val synced = buildSyncedCues(context, projectId, projectTitle, bundle.tempoPace)
        val updated = bundle.copy(cues = synced, updatedAt = System.currentTimeMillis())
        bundle = updated
        saveVoiceSubtitleBundle(context, updated)
        statusText = "Subtitle otomatis tersinkronisasi (${synced.size} adegan) dan disimpan."
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "SUARA (VOICE DIRECTION) & SUBTITLE (V0.6)",
                color = Color(0xFF7356D8),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Atur karakter suara, bahasa/aksen, tempo, jeda, serta draf subtitle tersinkronisasi waktu adegan.",
                color = Color(0xFF77738A),
                style = MaterialTheme.typography.bodySmall
            )

            Text("Karakter suara", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (voice in VOICE_CHARACTERS) {
                    FilterChip(
                        selected = bundle.voiceCharacter == voice,
                        onClick = { bundle = bundle.copy(voiceCharacter = voice) },
                        label = { Text(voice, maxLines = 1) }
                    )
                }
            }

            Text("Bahasa & aksen", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (lang in LANGUAGE_ACCENTS) {
                    FilterChip(
                        selected = bundle.languageAccent == lang,
                        onClick = { bundle = bundle.copy(languageAccent = lang) },
                        label = { Text(lang, maxLines = 1) }
                    )
                }
            }

            Text("Tempo bicara", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (tempo in TEMPO_OPTIONS) {
                    FilterChip(
                        selected = bundle.tempoPace == tempo,
                        onClick = { bundle = bundle.copy(tempoPace = tempo) },
                        label = { Text(tempo) }
                    )
                }
            }

            OutlinedTextField(
                value = bundle.pauseNote,
                onValueChange = { bundle = bundle.copy(pauseNote = it) },
                label = { Text("Arahan jeda & pelafalan") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { generateAndSyncSubtitles() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sinkronkan Subtitle")
                }
                OutlinedButton(
                    onClick = {
                        saveVoiceSubtitleBundle(context, bundle)
                        statusText = "Pengaturan suara & subtitle v0.6 disimpan."
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Simpan")
                }
            }

            Text(statusText, color = Color(0xFF543BB2), style = MaterialTheme.typography.bodySmall)

            for ((idx, cue) in bundle.cues.withIndex()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1EDFF).copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Adegan ${cue.sceneNumber} • ${cue.startSec}s – ${cue.endSec}s (durasi ${cue.endSec - cue.startSec}s)",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF543BB2),
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = cue.text,
                        onValueChange = { newText ->
                            val next = bundle.cues.mapIndexed { i, item ->
                                if (i == idx) item.copy(text = newText) else item
                            }
                            bundle = bundle.copy(cues = next)
                        },
                        label = { Text("Teks subtitle adegan ${cue.sceneNumber}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
