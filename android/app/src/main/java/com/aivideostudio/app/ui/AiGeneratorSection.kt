package com.aivideostudio.app.ui
import org.json.JSONObject
import org.json.JSONArray
import com.aivideostudio.app.model.VoiceSubtitleBundle
import com.aivideostudio.app.model.SubtitleCue
import android.content.Context

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aivideostudio.app.generator.LocalTemplateGenerator
import com.aivideostudio.app.model.GeneratedScript
import com.aivideostudio.app.model.GeneratorConfig
import com.aivideostudio.app.model.ProjectContentBundle
import com.aivideostudio.app.model.StoryboardScene
import com.aivideostudio.app.storage.GeneratorStorage
import com.aivideostudio.app.model.ResearchScriptBundle
import com.aivideostudio.app.network.WorkerResearchClient

private val CardBg = Color.White
private val SoftLavender = Color(0xFFF1EDFF)
private val PrimaryViolet = Color(0xFF7356D8)
private val DeepViolet = Color(0xFF543BB2)
private val DarkInk = Color(0xFF171329)
private val SubInk = Color(0xFF77738A)
private val BadgeMint = Color(0xFFDCF7ED)
private val BadgeMintText = Color(0xFF247B5B)

private val VIDEO_TYPES = listOf(
    "Dokumenter",
    "Video Shorts",
    "Promosi & Iklan",
    "Edukasi & Tutorial",
    "Storytelling",
    "Video Informatif",
    "Video Presentasi"
)

private val NARRATION_STYLES = listOf(
    "Informatif & Ramah",
    "Sinematik & Emosional",
    "Tegas & persuasif",
    "Santai & Komunikatif"
)

private val ASPECT_RATIOS = listOf("9:16", "16:9", "1:1")
private val DETAIL_LEVELS = listOf("Ringkas", "Standar", "Mendalam")
private val SCENE_PRESETS = listOf(3, 4, 6, 8)
private val DURATION_PRESETS = listOf(30, 60, 90, 120)

@Composable
fun AiGeneratorSection(
    context: android.content.Context,
    projectId: Long,
    projectTitle: String,
    projectType: String,
    projectMode: String
) {
    val initialBundle = remember(projectId) {
        val loaded = GeneratorStorage.loadBundle(context, projectId, projectTitle, projectType)
        val mappedType = if (projectType in VIDEO_TYPES) projectType else loaded.config.videoType
        val mappedTopic = if (loaded.config.topic.isBlank()) projectTitle else loaded.config.topic
        loaded.copy(config = loaded.config.copy(topic = mappedTopic, videoType = mappedType))
    }

    var config by remember(projectId) { mutableStateOf(initialBundle.config) }
    var script by remember(projectId) { mutableStateOf(initialBundle.script) }
    var scenes by remember(projectId) { mutableStateOf(initialBundle.scenes) }

    var isEditingScript by remember(projectId) { mutableStateOf(false) }
    var isEditingScenes by remember(projectId) { mutableStateOf(false) }
    var isGenerating by remember(projectId) { mutableStateOf(false) }
    var statusMessage by remember(projectId) {
        mutableStateOf(
            if (initialBundle.scenes.isNotEmpty() || initialBundle.script.opening.isNotBlank()) {
                "Data naskah & storyboard dimuat dari penyimpanan proyek."
            } else {
                "Siap membuat draf awal menggunakan ${LocalTemplateGenerator.GENERATOR_LABEL}."
            }
        )
    }

    fun persistCurrent(customStatus: String? = null) {
        val updatedScript = script.copy(
            sceneNarrations = scenes.map { it.narration },
            estimatedDurationSec = scenes.sumOf { it.durationSec }.takeIf { it > 0 } ?: config.targetDurationSec
        )
        script = updatedScript
        val bundle = ProjectContentBundle(
            projectId = projectId,
            config = config,
            script = updatedScript,
            scenes = scenes,
            updatedAt = System.currentTimeMillis()
        )
        GeneratorStorage.saveBundle(context, bundle)
        statusMessage = customStatus ?: "Tersimpan ke proyek #$projectId."
    }

    fun runGenerate(isRegenerate: Boolean) {
        val cleanTopic = config.topic.trim().ifEmpty { projectTitle.trim() }
        if (cleanTopic.isBlank()) {
            statusMessage = "Isi topik video terlebih dahulu sebelum generate."
            return
        }
        if (isGenerating) return
        val activeConfig = config.copy(topic = cleanTopic)
        isGenerating = true
        statusMessage = "AI sedang mencari konteks topik dan menyusun naskah mendalam. Proses dapat memerlukan waktu..."
        val request = ResearchScriptBundle(
            projectId = projectId,
            topic = cleanTopic,
            videoType = activeConfig.videoType,
            writingStyle = activeConfig.narrationStyle,
            targetAudience = activeConfig.targetAudience,
            targetDurationSec = activeConfig.targetDurationSec,
            revisionInstruction = if (activeConfig.detailLevel == "Mendalam") {
                "Buat naskah dokumenter mendalam: fakta spesifik, kronologi, tokoh/tempat/tanggal penting, sebab-akibat, dampak, dan konteks sejarah. Jangan hanya membuat pembukaan."
            } else ""
        )
        WorkerResearchClient.generateResearchAndScriptAsync(
            workerBaseUrl = loadWorkerUrl(context),
            current = request
        ) { research ->
            isGenerating = false
            if (!research.isLiveCloudResult || research.body.isBlank()) {
                statusMessage = "AI cloud belum menghasilkan naskah. Periksa koneksi Worker dan API key. Status: ${research.providerLabel}"
                return@generateResearchAndScriptAsync
            }

            val sentenceList = Regex("(?<=[.!?])\\s+").split(research.body)
                .map { it.trim() }.filter { it.isNotBlank() }
            val count = activeConfig.sceneCount.coerceIn(1, 8)
            val perScene = ((sentenceList.size + count - 1) / count).coerceAtLeast(1)
            val narrationParts = (0 until count).map { index ->
                sentenceList.drop(index * perScene).take(perScene).joinToString(" ").ifBlank { research.body }
            }
            val perDuration = (research.targetDurationSec / count).coerceAtLeast(3)
            val generatedScenes = narrationParts.mapIndexed { index, narration ->
                val number = index + 1
                val point = research.keyPoints.getOrNull(index)
                val title = point?.take(72) ?: "Pembahasan ${number}: ${cleanTopic}"
                StoryboardScene(
                    sceneNumber = number,
                    title = title,
                    durationSec = if (number == count) (research.targetDurationSec - perDuration * (count - 1)).coerceAtLeast(3) else perDuration,
                    narration = narration,
                    visualDescription = "Visual dokumenter yang menggambarkan secara langsung: ${narration.take(180)}",
                    cameraDirection = when (index % 4) {
                        0 -> "Wide establishing shot dengan gerakan kamera perlahan"
                        1 -> "Medium shot mengikuti aksi utama"
                        2 -> "Close-up pada detail peristiwa atau objek penting"
                        else -> "Tracking shot memperlihatkan konteks lokasi"
                    },
                    visualPrompt = "${cleanTopic}, ${title}, depict the exact factual moment described in the narration, historically grounded documentary illustration, cinematic composition, consistent visual style, no text",
                    transition = if (number == 1) "Fade In" else if (number == count) "Fade Out" else "Cut",
                    audioDirection = "Narasi utama jelas dengan ambience dokumenter lembut"
                )
            }
            config = activeConfig
            script = GeneratedScript(
                opening = research.opening,
                body = research.body,
                closing = research.closing,
                sceneNarrations = narrationParts,
                estimatedDurationSec = research.targetDurationSec,
                storyStructure = research.keyPoints.joinToString(" → "),
                isManuallyEdited = false,
                sourceLabel = research.providerLabel
            )
            scenes = generatedScenes
            isEditingScript = false
            isEditingScenes = false
            val bundle = ProjectContentBundle(projectId, activeConfig, script, scenes)
            GeneratorStorage.saveBundle(context, bundle)

            // Share the same AI research result with the dedicated storyboard section.
            val researchJson = JSONObject().apply {
                put("projectId", projectId)
                put("topic", research.topic)
                put("videoType", research.videoType)
                put("writingStyle", research.writingStyle)
                put("targetAudience", research.targetAudience)
                put("targetDurationSec", research.targetDurationSec)
                put("summary", research.summary)
                put("keyPoints", JSONArray().apply { research.keyPoints.forEach { put(it) } })
                put("factChecks", JSONArray().apply { research.factChecks.forEach { item ->
                    put(JSONObject().apply { put("claim", item.claim); put("status", item.status); put("note", item.note) })
                } })
                put("references", JSONArray().apply { research.references.forEach { item ->
                    put(JSONObject().apply { put("title", item.title); put("sourceType", item.sourceType); put("note", item.note) })
                } })
                put("opening", research.opening)
                put("body", research.body)
                put("closing", research.closing)
                put("revisionInstruction", research.revisionInstruction)
                put("providerLabel", research.providerLabel)
                put("isLiveCloudResult", true)
                put("updatedAt", System.currentTimeMillis())
            }
            context.getSharedPreferences("ai_video_studio", Context.MODE_PRIVATE)
                .edit().putString("v04_research_${projectId}", researchJson.toString()).apply()

            statusMessage = if (isRegenerate) {
                "Naskah AI berhasil diperbarui dari riset topik (${research.providerLabel})."
            } else {
                "Naskah mendalam berhasil dibuat dari riset AI (${research.providerLabel})."
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Banner Status & Label Generator Dasar
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SoftLavender),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .background(BadgeMint, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "AI Riset Topik",
                            color = BadgeMintText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Mode: $projectMode",
                        color = DeepViolet,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "Menggunakan AI cloud dan konteks riset topik. Jika layanan AI gagal, aplikasi tidak akan mengganti hasilnya dengan naskah template lokal.",
                    color = DarkInk,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Status: $statusMessage",
                    color = DeepViolet,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // A. GENERATOR CONFIGURATION
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "KONFIGURASI GENERATOR (V0.3)",
                    color = PrimaryViolet,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = config.topic,
                    onValueChange = { config = config.copy(topic = it) },
                    label = { Text("Topik video") },
                    placeholder = { Text("Contoh: Sejarah jalur rempah Nusantara") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Jenis video", style = MaterialTheme.typography.labelLarge, color = DarkInk, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VIDEO_TYPES.forEach { vType ->
                        FilterChip(
                            selected = config.videoType == vType,
                            onClick = { config = config.copy(videoType = vType) },
                            label = { Text(vType, maxLines = 1) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = config.language,
                        onValueChange = { config = config.copy(language = it) },
                        label = { Text("Bahasa") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = config.targetAudience,
                        onValueChange = { config = config.copy(targetAudience = it) },
                        label = { Text("Target penonton") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Durasi target (detik)", style = MaterialTheme.typography.labelLarge, color = DarkInk, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DURATION_PRESETS.forEach { sec ->
                        FilterChip(
                            selected = config.targetDurationSec == sec,
                            onClick = { config = config.copy(targetDurationSec = sec) },
                            label = { Text("${sec}d") }
                        )
                    }
                }

                Text("Jumlah adegan", style = MaterialTheme.typography.labelLarge, color = DarkInk, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SCENE_PRESETS.forEach { count ->
                        FilterChip(
                            selected = config.sceneCount == count,
                            onClick = { config = config.copy(sceneCount = count) },
                            label = { Text("$count adegan") }
                        )
                    }
                }

                Text("Gaya narasi", style = MaterialTheme.typography.labelLarge, color = DarkInk, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NARRATION_STYLES.forEach { style ->
                        FilterChip(
                            selected = config.narrationStyle == style,
                            onClick = { config = config.copy(narrationStyle = style) },
                            label = { Text(style, maxLines = 1) }
                        )
                    }
                }

                Text("Rasio video", style = MaterialTheme.typography.labelLarge, color = DarkInk, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ASPECT_RATIOS.forEach { ratio ->
                        FilterChip(
                            selected = config.aspectRatio == ratio,
                            onClick = { config = config.copy(aspectRatio = ratio) },
                            label = { Text(ratio) }
                        )
                    }
                }

                Text("Tingkat detail konten", style = MaterialTheme.typography.labelLarge, color = DarkInk, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DETAIL_LEVELS.forEach { level ->
                        FilterChip(
                            selected = config.detailLevel == level,
                            onClick = { config = config.copy(detailLevel = level) },
                            label = { Text(level) }
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { runGenerate(isRegenerate = false) },
                        enabled = !isGenerating,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isGenerating) "Menyusun..." else "Generate AI")
                    }
                    OutlinedButton(
                        onClick = { runGenerate(isRegenerate = true) },
                        enabled = !isGenerating,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isGenerating) "Memproses..." else "Regenerate AI")
                    }
                }
            }
        }

        // B. SCRIPT GENERATOR & EDITOR
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "NASKAH VIDEO",
                            color = PrimaryViolet,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Estimasi durasi: ${script.estimatedDurationSec} detik • ${script.sourceLabel}",
                            color = SubInk,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = { isEditingScript = !isEditingScript }) {
                        Text(if (isEditingScript) "Selesai Edit" else "Edit")
                    }
                }

                if (script.storyStructure.isNotBlank()) {
                    Text(
                        "Struktur: ${script.storyStructure}",
                        color = DeepViolet,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (script.opening.isBlank() && script.body.isBlank() && script.closing.isBlank() && !isEditingScript) {
                    Text(
                        "Belum ada naskah untuk proyek ini. Tekan Generate untuk membuat draf naskah.",
                        color = SubInk,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (isEditingScript) {
                    OutlinedTextField(
                        value = script.opening,
                        onValueChange = { script = script.copy(opening = it, isManuallyEdited = true) },
                        label = { Text("Pembukaan") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = script.body,
                        onValueChange = { script = script.copy(body = it, isManuallyEdited = true) },
                        label = { Text("Isi / Narasi per Adegan") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = script.closing,
                        onValueChange = { script = script.copy(closing = it, isManuallyEdited = true) },
                        label = { Text("Penutup") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    ScriptBlock("Pembukaan", script.opening)
                    ScriptBlock("Isi", script.body)
                    ScriptBlock("Penutup", script.closing)
                }

                Button(
                    onClick = { persistCurrent("Naskah & storyboard disimpan ke penyimpanan lokal proyek.") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Naskah & Storyboard")
                }
            }
        }

        // C. STORYBOARD GENERATOR & EDITOR
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "STORYBOARD (${scenes.size} ADEGAN)",
                            color = PrimaryViolet,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Detail judul, durasi, narasi, visual, kamera, prompt, transisi & audio",
                            color = SubInk,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (scenes.isNotEmpty()) {
                        TextButton(onClick = { isEditingScenes = !isEditingScenes }) {
                            Text(if (isEditingScenes) "Tutup Edit" else "Edit Adegan")
                        }
                    }
                }

                if (scenes.isEmpty()) {
                    Text(
                        "Storyboard belum tersedia. Tekan Generate untuk menyusun adegan otomatis.",
                        color = SubInk,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    scenes.forEachIndexed { index, scene ->
                        SceneCard(
                            scene = scene,
                            editing = isEditingScenes,
                            onUpdate = { updatedScene ->
                                scenes = scenes.mapIndexed { idx, existing ->
                                    if (idx == index) updatedScene else existing
                                }
                            },
                            onSaveScene = {
                                persistCurrent("Adegan #${scene.sceneNumber} tersimpan.")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptBlock(label: String, text: String) {
    if (text.isBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SoftLavender, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold, color = DeepViolet, style = MaterialTheme.typography.labelLarge)
        Text(text, color = DarkInk, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SceneCard(
    scene: StoryboardScene,
    editing: Boolean,
    onUpdate: (StoryboardScene) -> Unit,
    onSaveScene: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SoftLavender.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Adegan ${scene.sceneNumber} • ${scene.title} (${scene.durationSec}d)",
                fontWeight = FontWeight.Bold,
                color = DarkInk,
                style = MaterialTheme.typography.titleSmall
            )
            if (editing) {
                OutlinedTextField(
                    value = scene.title,
                    onValueChange = { onUpdate(scene.copy(title = it)) },
                    label = { Text("Judul adegan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = scene.durationSec.toString(),
                    onValueChange = { raw ->
                        val parsed = raw.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 300) ?: scene.durationSec
                        onUpdate(scene.copy(durationSec = parsed))
                    },
                    label = { Text("Durasi (detik)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = scene.narration,
                    onValueChange = { onUpdate(scene.copy(narration = it)) },
                    label = { Text("Narasi") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = scene.visualDescription,
                    onValueChange = { onUpdate(scene.copy(visualDescription = it)) },
                    label = { Text("Deskripsi visual") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = scene.cameraDirection,
                    onValueChange = { onUpdate(scene.copy(cameraDirection = it)) },
                    label = { Text("Camera direction") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = scene.visualPrompt,
                    onValueChange = { onUpdate(scene.copy(visualPrompt = it)) },
                    label = { Text("Visual prompt") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = scene.transition,
                    onValueChange = { onUpdate(scene.copy(transition = it)) },
                    label = { Text("Transisi") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = scene.audioDirection,
                    onValueChange = { onUpdate(scene.copy(audioDirection = it)) },
                    label = { Text("Audio direction") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = onSaveScene, modifier = Modifier.fillMaxWidth()) {
                    Text("Simpan Perubahan Adegan ${scene.sceneNumber}")
                }
            } else {
                Text("Narasi: ${scene.narration}", color = DarkInk, style = MaterialTheme.typography.bodySmall)
                Text("Visual: ${scene.visualDescription}", color = SubInk, style = MaterialTheme.typography.bodySmall)
                Text("Kamera: ${scene.cameraDirection}", color = SubInk, style = MaterialTheme.typography.bodySmall)
                Text("Visual prompt: ${scene.visualPrompt}", color = DeepViolet, style = MaterialTheme.typography.bodySmall)
                Text("Transisi: ${scene.transition}  •  Audio: ${scene.audioDirection}", color = SubInk, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
