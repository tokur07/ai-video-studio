package com.aivideostudio.app.ui

import android.util.Log
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.VideoView

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aivideostudio.app.model.SceneVisualAsset
import com.aivideostudio.app.model.StoryboardVisualBundle
import com.aivideostudio.app.network.WorkerVisualClient
import com.aivideostudio.app.network.WanVideoClient
import org.json.JSONArray
import org.json.JSONObject

private val VISUAL_STYLES = listOf(
    "Animasi 3D Kartun Sinematik",
    "Fotorealistik & Sinematik",
    "Ilustrasi Editorial",
    "Dokumenter Arsip & Nyata",
    "Animasi 2D Bersih"
)

private val VISUAL_RATIOS = listOf("9:16", "16:9", "1:1")
private val SHOT_TYPES = listOf("Wide Shot", "Medium Shot", "Close-Up", "Tracking Shot")

private const val PREFS_NAME = "ai_video_studio"

private fun loadVisualBundle(
    context: Context,
    projectId: Long,
    fallbackTitle: String
): StoryboardVisualBundle {
    val defaultAssets = (1..4).map { idx ->
        SceneVisualAsset(
            sceneNumber = idx,
            sceneTitle = "Adegan $idx: ${fallbackTitle.ifBlank { "Visual Utama" }}",
            visualPrompt = "${fallbackTitle.ifBlank { "Visual video" }}, adegan $idx, komposisi jelas, pencahayaan alami",
            aspectRatio = "9:16"
        )
    }
    val fallback = StoryboardVisualBundle(
        projectId = projectId,
        assets = defaultAssets
    )
    return try {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("v05_visual_$projectId", null) ?: return fallback
        val root = JSONObject(raw)
        val arr = root.optJSONArray("assets") ?: JSONArray()
        val parsedAssets = (0 until arr.length()).map { idx ->
            val o = arr.optJSONObject(idx) ?: JSONObject()
            SceneVisualAsset(
                sceneNumber = o.optInt("sceneNumber", idx + 1),
                sceneTitle = o.optString("sceneTitle", "Adegan ${idx + 1}"),
                visualPrompt = o.optString("visualPrompt", ""),
                sceneSummary = o.optString("sceneSummary", ""),
                negativePrompt = o.optString("negativePrompt", "teks acak, watermark, buram"),
                cameraShot = o.optString("cameraShot", "Medium Shot"),
                aspectRatio = o.optString("aspectRatio", root.optString("aspectRatio", "9:16")),
                localImageUri = o.optString("localImageUri", ""),
                localVideoUri = o.optString("localVideoUri", ""),
                assetSourceLabel = o.optString("assetSourceLabel", "Prompt Siap Pakai (Belum Ada Gambar)")
            )
        }
        StoryboardVisualBundle(
            projectId = projectId,
            aspectRatio = root.optString("aspectRatio", "9:16"),
            visualStyle = root.optString("visualStyle", "Animasi 3D Kartun Sinematik"),
            assets = if (parsedAssets.isNotEmpty()) parsedAssets else defaultAssets,
            updatedAt = root.optLong("updatedAt", System.currentTimeMillis())
        )
    } catch (_: Exception) {
        fallback
    }
}

private fun saveVisualBundle(context: Context, bundle: StoryboardVisualBundle) {
    val arr = JSONArray().apply {
        bundle.assets.forEach { a ->
            put(JSONObject().apply {
                put("sceneNumber", a.sceneNumber)
                put("sceneTitle", a.sceneTitle)
                put("visualPrompt", a.visualPrompt)
                put("sceneSummary", a.sceneSummary)
                put("negativePrompt", a.negativePrompt)
                put("cameraShot", a.cameraShot)
                put("aspectRatio", a.aspectRatio)
                put("localImageUri", a.localImageUri)
                put("localVideoUri", a.localVideoUri)
                put("assetSourceLabel", a.assetSourceLabel)
            })
        }
    }
    val root = JSONObject().apply {
        put("projectId", bundle.projectId)
        put("aspectRatio", bundle.aspectRatio)
        put("visualStyle", bundle.visualStyle)
        put("assets", arr)
        put("updatedAt", System.currentTimeMillis())
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString("v05_visual_${bundle.projectId}", root.toString()).apply()
}

@Composable
fun StoryboardVisualSection(
    context: Context,
    projectId: Long,
    projectTitle: String,
    projectType: String,
    pickedImageUri: String?,
    onClearPickedImage: () -> Unit,
    onRequestPickImage: (Int) -> Unit
) {
    val initial = remember(projectId) {
        loadVisualBundle(context, projectId, projectTitle)
    }

    var bundle by remember(projectId) { mutableStateOf(initial) }
    var activePickScene by remember(projectId) { mutableStateOf<Int?>(null) }
    var generatingSceneNum by remember(projectId) { mutableStateOf<Int?>(null) }
    var generatingVideoSceneNum by remember(projectId) { mutableStateOf<Int?>(null) }
    var isGeneratingStoryboard by remember(projectId) { mutableStateOf(false) }
    var statusText by remember(projectId) {
        mutableStateOf(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("v05_status_$projectId", null)
            ?: "Manajemen aset, visual prompt & generate gambar AI siap untuk proyek #$projectId.")
    }

    fun setStatus(message: String) {
        statusText = message
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("v05_status_$projectId", message).apply()
    }

    LaunchedEffect(pickedImageUri, activePickScene) {
        val targetScene = activePickScene
        val uri = pickedImageUri
        if (targetScene != null && !uri.isNullOrBlank()) {
            val updatedAssets = bundle.assets.map { asset ->
                if (asset.sceneNumber == targetScene) {
                    asset.copy(
                        localImageUri = uri,
                        assetSourceLabel = "Gambar Diimpor dari Perangkat"
                    )
                } else {
                    asset
                }
            }
            val updated = bundle.copy(assets = updatedAssets, updatedAt = System.currentTimeMillis())
            bundle = updated
            saveVisualBundle(context, updated)
            statusText = "Gambar adegan #$targetScene berhasil diimpor dan disimpan."
            activePickScene = null
            onClearPickedImage()
        }
    }

    fun rebuildPromptsFromTopic() {
        val topicClean = projectTitle.trim().ifEmpty { "Topik Video" }
        val rebuilt = bundle.assets.mapIndexed { index, item ->
            val shot = SHOT_TYPES[index % SHOT_TYPES.size]
            val prompt = "$topicClean, ${item.sceneTitle}, $shot, gaya ${bundle.visualStyle.lowercase()}, format $projectType, rasio ${bundle.aspectRatio}, pencahayaan konsisten, tanpa teks tertanam"
            item.copy(
                cameraShot = shot,
                aspectRatio = bundle.aspectRatio,
                visualPrompt = prompt
            )
        }
        val updated = bundle.copy(assets = rebuilt, updatedAt = System.currentTimeMillis())
        bundle = updated
        saveVisualBundle(context, updated)
        statusText = "Prompt visual seluruh adegan diperbarui (${bundle.visualStyle}, ${bundle.aspectRatio})."
    }

    fun generateStoryboardFromScript() {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("v04_research_$projectId", null)
        if (raw.isNullOrBlank()) {
            statusText = "Naskah belum ditemukan. Buka bagian Riset & Naskah, susun naskah terlebih dahulu, lalu coba lagi."
            return
        }
        val script = runCatching { JSONObject(raw) }.getOrNull()
        if (script == null) {
            statusText = "Data naskah tidak dapat dibaca. Simpan ulang naskah lalu coba lagi."
            return
        }
        val opening = script.optString("opening", "").trim()
        val body = script.optString("body", "").trim()
        val closing = script.optString("closing", "").trim()
        if (opening.isBlank() && body.isBlank() && closing.isBlank()) {
            statusText = "Naskah masih kosong. Isi pembukaan, isi naskah, dan penutup terlebih dahulu."
            return
        }

        isGeneratingStoryboard = true
        statusText = "AI sedang membaca naskah dan menyusun empat adegan yang berbeda..."
        WorkerVisualClient.generateStoryboardAsync(
            workerBaseUrl = loadWorkerUrl(context),
            topic = script.optString("topic", projectTitle).ifBlank { projectTitle },
            videoType = script.optString("videoType", projectType),
            opening = opening,
            body = body,
            closing = closing,
            visualStyle = bundle.visualStyle,
            aspectRatio = bundle.aspectRatio
        ) { result ->
            isGeneratingStoryboard = false
            statusText = result.message + if (result.providerLabel.isNotBlank()) " (${result.providerLabel})" else ""
            if (result.success) {
                val freshAssets = result.scenes.map { scene ->
                    scene.copy(
                        localImageUri = "",
                        assetSourceLabel = "Prompt AI dari Naskah"
                    )
                }
                val updated = bundle.copy(
                    assets = freshAssets,
                    updatedAt = System.currentTimeMillis()
                )
                bundle = updated
                saveVisualBundle(context, updated)
                statusText = "Empat adegan berhasil disusun dari naskah. Generate gambar tiap adegan untuk membuat visualnya."
            }
        }
    }

    fun triggerAiImageGeneration(asset: SceneVisualAsset) {
        Log.d("AIVideoDiag", "triggerAiImageGeneration scene=${asset.sceneNumber} promptBlank=${asset.visualPrompt.isBlank()} len=${asset.visualPrompt.length}")
        if (asset.visualPrompt.isBlank()) {
            statusText = "Isi visual prompt adegan #${asset.sceneNumber} terlebih dahulu."
            return
        }
        generatingSceneNum = asset.sceneNumber
        statusText = "Menghasilkan gambar AI untuk adegan #${asset.sceneNumber}..."
        val workerUrl = loadWorkerUrl(context)
        WorkerVisualClient.generateSceneImageAsync(
            context = context,
            workerBaseUrl = workerUrl,
            projectId = projectId,
            sceneNumber = asset.sceneNumber,
            visualPrompt = asset.visualPrompt,
            negativePrompt = asset.negativePrompt,
            sceneTitle = asset.sceneTitle,
            sceneSummary = asset.sceneSummary,
            visualStyle = bundle.visualStyle,
            aspectRatio = bundle.aspectRatio
        ) { res ->
            generatingSceneNum = null
            statusText = res.message
            if (res.success && res.localUri.isNotBlank()) {
                val updatedAssets = bundle.assets.map { item ->
                    if (item.sceneNumber == asset.sceneNumber) {
                        item.copy(
                            localImageUri = res.localUri,
                            assetSourceLabel = "Gambar AI (${res.providerLabel})"
                        )
                    } else {
                        item
                    }
                }
                val updated = bundle.copy(assets = updatedAssets, updatedAt = System.currentTimeMillis())
                bundle = updated
                saveVisualBundle(context, updated)
            }
        }
    }

    fun triggerSceneVideoGeneration(asset: SceneVisualAsset) {
        if (asset.localImageUri.isBlank()) {
            statusText = "Generate atau impor gambar adegan #${asset.sceneNumber} terlebih dahulu."
            return
        }
        if (generatingVideoSceneNum != null || generatingSceneNum != null) return
        generatingVideoSceneNum = asset.sceneNumber
        setStatus("Mengirim adegan #${asset.sceneNumber} ke Wan 2.2. GPU gratis dapat mengantre dan proses pertama bisa lama...")
        Log.i("AIVideoDiag", "single video start project=$projectId scene=${asset.sceneNumber}")
        WanVideoClient.generateAsync(
            context = context,
            workerBaseUrl = loadWorkerUrl(context),
            projectId = projectId,
            sceneNumber = asset.sceneNumber,
            imageUri = asset.localImageUri,
            prompt = asset.visualPrompt + ". Cinematic 3D animated film, preserve the exact character identity, face, clothing, proportions and scene composition. Natural body movement, clear physical action, subtle cinematic camera movement, smooth temporal consistency.",
            negativePrompt = asset.negativePrompt,
            onProgress = { message -> setStatus(message) }
        ) { result ->
            generatingVideoSceneNum = null
            setStatus(result.message.ifBlank { "Adegan #${asset.sceneNumber}: server mengembalikan hasil kosong." })
            Log.i("AIVideoDiag", "single video result project=$projectId scene=${asset.sceneNumber} success=${result.success} message=${result.message.take(300)}")
            if (result.success && result.localUri.isNotBlank()) {
                val next = bundle.assets.map { item ->
                    if (item.sceneNumber == asset.sceneNumber) item.copy(localVideoUri = result.localUri) else item
                }
                val updated = bundle.copy(assets = next, updatedAt = System.currentTimeMillis())
                bundle = updated
                saveVisualBundle(context, updated)
            }
        }
    }

    fun triggerAllSceneVideoGeneration() {
        if (generatingVideoSceneNum != null || generatingSceneNum != null) return
        val pending = bundle.assets
            .filter { it.localImageUri.isNotBlank() && it.localVideoUri.isBlank() }
            .sortedBy { it.sceneNumber }
        if (pending.isEmpty()) {
            statusText = "Semua adegan yang memiliki gambar sudah mempunyai video."
            return
        }

        Log.i("AIVideoDiag", "queue tapped project=$projectId assets=${bundle.assets.size}")
        val failedScenes = mutableListOf<Int>()
        val failureDetails = mutableListOf<String>()
        var successCount = 0
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        lateinit var runNext: (Int) -> Unit
        lateinit var attemptScene: (Int, Int) -> Unit

        runNext = { index ->
            if (index >= pending.size) {
                generatingVideoSceneNum = null
                val finalMessage = if (failedScenes.isEmpty()) {
                    "Selesai. " + successCount + " dari " + pending.size + " video adegan berhasil dibuat dan disimpan."
                } else {
                    "Antrean selesai: " + successCount + " berhasil, " + failedScenes.size + " gagal (adegan " + failedScenes.joinToString(", ") + "). Video yang berhasil tetap tersimpan. Penyebab: " + failureDetails.joinToString(" | ") + ". Periksa pesan ini untuk diagnosis."
                }
                setStatus(finalMessage)
                Log.i("AIVideoDiag", "queue finished project=$projectId success=$successCount failed=${failedScenes.joinToString(",")} details=${failureDetails.joinToString(" | ")}")
            } else {
                attemptScene(index, 1)
            }
        }

        attemptScene = { index, attempt ->
            val asset = pending[index]
            generatingVideoSceneNum = asset.sceneNumber
            val stageMessage = "Antrean Wan 2.2: adegan " + (index + 1) + "/" + pending.size + " (#" + asset.sceneNumber + "), percobaan " + attempt + "/3. Mengirim permintaan..."
            setStatus(stageMessage)
            Log.i("AIVideoDiag", "queue attempt project=$projectId index=$index scene=${asset.sceneNumber} attempt=$attempt")
            WanVideoClient.generateAsync(
                context = context,
                workerBaseUrl = loadWorkerUrl(context),
                projectId = projectId,
                sceneNumber = asset.sceneNumber,
                imageUri = asset.localImageUri,
                prompt = asset.visualPrompt + ". Cinematic 3D animated film, preserve the exact character identity, face, clothing, proportions and scene composition. Natural body movement, clear physical action, subtle cinematic camera movement, smooth temporal consistency.",
                negativePrompt = asset.negativePrompt,
                onProgress = { message -> setStatus("Adegan #${asset.sceneNumber}: $message") }
            ) { result ->
                Log.i("AIVideoDiag", "queue result project=$projectId scene=${asset.sceneNumber} attempt=$attempt success=${result.success} message=${result.message.take(300)}")
                if (result.success && result.localUri.isNotBlank()) {
                    val nextAssets = bundle.assets.map { item ->
                        if (item.sceneNumber == asset.sceneNumber) item.copy(localVideoUri = result.localUri) else item
                    }
                    val updated = bundle.copy(assets = nextAssets, updatedAt = System.currentTimeMillis())
                    bundle = updated
                    saveVisualBundle(context, updated)
                    successCount++
                    mainHandler.postDelayed({ runNext(index + 1) }, 3000)
                } else if (attempt < 3) {
                    setStatus("Adegan #" + asset.sceneNumber + " gagal pada percobaan " + attempt + ": " + result.message + " Mencoba ulang...")
                    mainHandler.postDelayed({ attemptScene(index, attempt + 1) }, 5000)
                } else {
                    failedScenes.add(asset.sceneNumber)
                    failureDetails.add("#" + asset.sceneNumber + ": " + result.message.ifBlank { "Penyebab tidak diberikan server" }.take(180))
                    setStatus("Adegan #" + asset.sceneNumber + " gagal setelah 3 percobaan. " + result.message.take(180) + " Melanjutkan ke adegan berikutnya...")
                    mainHandler.postDelayed({ runNext(index + 1) }, 3000)
                }
            }
        }
        runNext(0)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "STORYBOARD VISUAL & ASET ADEGAN (V0.5+)",
                color = Color(0xFF7356D8),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Pilih salah satu atau gabungkan: Generate Gambar AI via Cloudflare Worker atau Impor Gambar dari penyimpanan internal perangkat.",
                color = Color(0xFF77738A),
                style = MaterialTheme.typography.bodySmall
            )

            Text("Gaya visual", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (style in VISUAL_STYLES) {
                    FilterChip(
                        selected = bundle.visualStyle == style,
                        onClick = {
                            val updated = bundle.copy(visualStyle = style, updatedAt = System.currentTimeMillis())
                            bundle = updated
                            saveVisualBundle(context, updated)
                        },
                        label = { Text(style, maxLines = 1) }
                    )
                }
            }

            Text("Rasio aspek visual", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (ratio in VISUAL_RATIOS) {
                    FilterChip(
                        selected = bundle.aspectRatio == ratio,
                        onClick = {
                            val updated = bundle.copy(aspectRatio = ratio, updatedAt = System.currentTimeMillis())
                            bundle = updated
                            saveVisualBundle(context, updated)
                        },
                        label = { Text(ratio) }
                    )
                }
            }

            Button(
                onClick = { triggerAllSceneVideoGeneration() },
                enabled = generatingVideoSceneNum == null && generatingSceneNum == null &&
                    bundle.assets.any { it.localImageUri.isNotBlank() && it.localVideoUri.isBlank() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate Semua Video Adegan Secara Berurutan")
            }

            Button(
                onClick = { generateStoryboardFromScript() },
                enabled = !isGeneratingStoryboard && generatingSceneNum == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isGeneratingStoryboard) "Menganalisis Naskah..." else "Buat 4 Adegan dari Naskah")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { rebuildPromptsFromTopic() },
                    enabled = !isGeneratingStoryboard && generatingSceneNum == null &&
                        bundle.assets.none { it.sceneSummary.isNotBlank() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Prompt Umum (tanpa naskah)")
                }
                OutlinedButton(
                    onClick = {
                        saveVisualBundle(context, bundle)
                        statusText = "Aset & prompt visual disimpan ke proyek #$projectId."
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Simpan Aset")
                }
            }

            Text(statusText, color = Color(0xFF543BB2), style = MaterialTheme.typography.bodySmall)

            for ((idx, asset) in bundle.assets.withIndex()) {
                val isThisGenerating = generatingSceneNum == asset.sceneNumber
                val isThisVideoGenerating = generatingVideoSceneNum == asset.sceneNumber
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1EDFF).copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Adegan ${asset.sceneNumber} • ${asset.assetSourceLabel}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        OutlinedTextField(
                            value = asset.sceneTitle,
                            onValueChange = { newTitle ->
                                val next = bundle.assets.mapIndexed { i, it ->
                                    if (i == idx) it.copy(sceneTitle = newTitle) else it
                                }
                                val updated = bundle.copy(assets = next, updatedAt = System.currentTimeMillis())
                                bundle = updated
                                saveVisualBundle(context, updated)
                            },
                            label = { Text("Judul adegan") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (asset.sceneSummary.isNotBlank()) {
                            Text(
                                "Bagian naskah: ${asset.sceneSummary}",
                                color = Color(0xFF543BB2),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedTextField(
                            value = asset.visualPrompt,
                            onValueChange = { newPrompt ->
                                val next = bundle.assets.mapIndexed { i, it ->
                                    if (i == idx) it.copy(visualPrompt = newPrompt) else it
                                }
                                val updated = bundle.copy(assets = next, updatedAt = System.currentTimeMillis())
                                bundle = updated
                                saveVisualBundle(context, updated)
                            },
                            label = { Text("Visual prompt") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = asset.negativePrompt,
                            onValueChange = { newNeg ->
                                val next = bundle.assets.mapIndexed { i, it ->
                                    if (i == idx) it.copy(negativePrompt = newNeg) else it
                                }
                                val updated = bundle.copy(assets = next, updatedAt = System.currentTimeMillis())
                                bundle = updated
                                saveVisualBundle(context, updated)
                            },
                            label = { Text("Negative prompt") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (asset.localImageUri.isNotBlank()) {
                            val previewBitmap = remember(asset.localImageUri) {
                                runCatching {
                                    context.contentResolver.openInputStream(Uri.parse(asset.localImageUri))?.use {
                                        BitmapFactory.decodeStream(it)
                                    }
                                }.getOrNull()
                            }
                            if (previewBitmap != null) {
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = "Gambar adegan ${asset.sceneNumber}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                )
                                Text(
                                    asset.assetSourceLabel,
                                    color = Color(0xFF247B5B),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Text(
                                    "File gambar tercatat tetapi tidak dapat dibuka. Silakan impor atau generate ulang gambar.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (asset.localVideoUri.isNotBlank()) {
                            Text("VIDEO ADEGAN TERSIMPAN", color = Color(0xFF247B5B), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            AndroidView(
                                factory = { ctx -> VideoView(ctx).apply {
                                    setVideoURI(Uri.parse(asset.localVideoUri))
                                    setOnPreparedListener { player -> player.isLooping = true; start() }
                                } },
                                update = { view -> if (view.tag != asset.localVideoUri) {
                                    view.tag = asset.localVideoUri
                                    view.setVideoURI(Uri.parse(asset.localVideoUri))
                                    view.setOnPreparedListener { player -> player.isLooping = true; view.start() }
                                } },
                                modifier = Modifier.fillMaxWidth().height(220.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { triggerAiImageGeneration(asset) },
                                enabled = generatingSceneNum == null && !isGeneratingStoryboard,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isThisGenerating) "Membuat..." else "Generate Gambar AI")
                            }
                            OutlinedButton(
                                onClick = {
                                    activePickScene = asset.sceneNumber
                                    onRequestPickImage(asset.sceneNumber)
                                },
                                enabled = generatingSceneNum == null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Impor Gambar")
                            }
                        }
                        Button(
                            onClick = { triggerSceneVideoGeneration(asset) },
                            enabled = asset.localImageUri.isNotBlank() && generatingVideoSceneNum == null && generatingSceneNum == null && !isGeneratingStoryboard,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isThisVideoGenerating) "Mengantre / Membuat Video..." else if (asset.localVideoUri.isNotBlank()) "Buat Ulang Video Adegan" else "Animasi Video Wan 2.2 (Gratis)")
                        }
                    }
                }
            }
        }
    }
}
