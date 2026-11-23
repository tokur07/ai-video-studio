package com.aivideostudio.app.render

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.Effects
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import org.json.JSONObject
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import java.io.File
import java.util.Locale

object LocalVideoRenderer {
    private const val PREFS = "ai_video_studio"
    private val main = Handler(Looper.getMainLooper())

    data class RenderAsset(val sceneNumber: Int, val videoUri: String, val subtitle: String)

    @OptIn(UnstableApi::class)
    fun renderProject(
        context: Context,
        projectId: Long,
        audioEnabled: Boolean,
        subtitlesEnabled: Boolean,
        speechRate: Float,
        onStatus: (String, Int) -> Unit,
        onComplete: (Boolean, String, String) -> Unit
    ) {
        val app = context.applicationContext
        val assets = loadAssets(app, projectId, subtitlesEnabled)
        if (assets.isEmpty()) {
            onComplete(false, "", "Belum ada video adegan. Buat video Wan 2.2 untuk minimal satu adegan terlebih dahulu.")
            return
        }
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cueTexts = assets.map { it.subtitle }.filter { it.isNotBlank() }
        val narration = cueTexts.joinToString(". ").take(3500)
        val renderDir = File(app.filesDir, "renders").apply { mkdirs() }
        val output = File(renderDir, "project_${projectId}_${System.currentTimeMillis()}.mp4")
        val ttsFile = File(renderDir, "narration_${projectId}_${System.currentTimeMillis()}.wav")

        fun startExport(audioFile: File?) {
            try {
                onStatus("Menyiapkan komposisi video, subtitle, dan audio...", 25)
                val videoItems = assets.map { asset ->
                    val mediaItem = MediaItem.fromUri(Uri.parse(asset.videoUri))
                    val effects = if (subtitlesEnabled && asset.subtitle.isNotBlank()) {
                        val text = SpannableString("  ${asset.subtitle.trim()}  ")
                        text.setSpan(ForegroundColorSpan(Color.WHITE), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        text.setSpan(BackgroundColorSpan(Color.argb(190, 12, 12, 18)), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        text.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        val settings = StaticOverlaySettings.Builder()
                            .setBackgroundFrameAnchor(0f, -0.82f)
                            .setOverlayFrameAnchor(0f, -1f)
                            .setScale(0.55f, 0.55f)
                            .build()
                        val overlay = TextOverlay.createStaticTextOverlay(text, settings)
                        Effects(emptyList(), listOf(OverlayEffect(listOf(overlay))))
                    } else {
                        Effects.EMPTY
                    }
                    EditedMediaItem.Builder(mediaItem)
                        .setRemoveAudio(true)
                        .setEffects(effects)
                        .build()
                }
                val videoSequence = EditedMediaItemSequence.withVideoFrom(videoItems)
                val sequences = mutableListOf(videoSequence)
                if (audioEnabled && audioFile != null && audioFile.exists() && audioFile.length() > 1000) {
                    val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(audioFile)))
                        .setRemoveVideo(true)
                        .build()
                    sequences.add(EditedMediaItemSequence.withAudioFrom(listOf(audioItem)))
                }
                val composition = Composition.Builder(sequences).build()
                onStatus("Merender MP4 di perangkat. Jangan tutup aplikasi sampai selesai...", 35)
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        try {
                            onStatus("Menyimpan video ke folder Movies/AI Video Studio...", 90)
                            val savedUri = publishVideo(app, output)
                            onComplete(true, savedUri.toString(), "Video MP4 final berhasil dibuat dan disimpan.")
                        } catch (e: Exception) {
                            onComplete(false, "", "Video selesai dirender tetapi gagal disimpan: ${e.message}")
                        }
                    }

                    override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                        onComplete(false, "", "Render MP4 gagal: ${exception.message ?: exception.errorCodeName}")
                    }
                }
                Transformer.Builder(app).addListener(listener).build().start(composition, output.absolutePath)
            } catch (e: Exception) {
                onComplete(false, "", "Tidak dapat memulai render: ${e.message ?: "kesalahan tidak diketahui"}")
            }
        }

        if (!audioEnabled || narration.isBlank()) {
            if (audioEnabled && narration.isBlank()) {
                onComplete(false, "", "Narasi belum tersedia. Tekan Sinkronkan Subtitle atau isi naskah adegan terlebih dahulu.")
            } else {
                startExport(null)
            }
            return
        }

        onStatus("Membuat narasi bahasa Indonesia dari teks adegan...", 10)
        val utteranceId = "studio_${projectId}_${System.currentTimeMillis()}"
        val tts = TextToSpeech(app) { status ->
            if (status != TextToSpeech.SUCCESS) {
                onComplete(false, "", "Mesin Text-to-Speech Android tidak dapat dijalankan.")
                return@TextToSpeech
            }
            try {
                val locale = Locale.forLanguageTag("id-ID")
                val languageStatus = ttsLanguageStatusPlaceholder()
                @Suppress("UNUSED_VARIABLE") val ignored = languageStatus
                val result = tts.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.shutdown()
                    onComplete(false, "", "Suara bahasa Indonesia belum tersedia. Pasang paket suara Indonesia di pengaturan Text-to-Speech Android.")
                    return@TextToSpeech
                }
                val localVoice = tts.voices?.firstOrNull { it.locale.language == "id" && !it.isNetworkConnectionRequired }
                if (localVoice != null) tts.voice = localVoice
                tts.setSpeechRate(speechRate.coerceIn(0.75f, 1.35f))
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit
                    override fun onDone(id: String?) {
                        tts.shutdown()
                        main.post { startExport(ttsFile) }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        tts.shutdown()
                        main.post { onComplete(false, "", "Gagal membuat audio narasi dari mesin suara Android.") }
                    }
                    override fun onError(id: String?, errorCode: Int) {
                        tts.shutdown()
                        main.post { onComplete(false, "", "Gagal membuat audio narasi (kode $errorCode).") }
                    }
                })
                val queued = tts.synthesizeToFile(narration, Bundle(), ttsFile, utteranceId)
                if (queued != TextToSpeech.SUCCESS) {
                    tts.shutdown()
                    onComplete(false, "", "Mesin Text-to-Speech menolak permintaan pembuatan audio.")
                }
            } catch (e: Exception) {
                tts.shutdown()
                onComplete(false, "", "Kesalahan Text-to-Speech: ${e.message}")
            }
        }
    }

    private fun ttsLanguageStatusPlaceholder(): Int = 0

    private fun loadAssets(context: Context, projectId: Long, subtitlesEnabled: Boolean): List<RenderAsset> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val visualRaw = prefs.getString("v05_visual_$projectId", null) ?: return emptyList()
        val visual = JSONObject(visualRaw)
        val assetsArray = visual.optJSONArray("assets") ?: return emptyList()
        val voiceRaw = prefs.getString("v06_voice_sub_$projectId", null)
        val cueMap = mutableMapOf<Int, String>()
        if (voiceRaw != null) {
            runCatching {
                val cues = JSONObject(voiceRaw).optJSONArray("cues")
                if (cues != null) for (i in 0 until cues.length()) {
                    val cue = cues.optJSONObject(i) ?: continue
                    cueMap[cue.optInt("sceneNumber", i + 1)] = cue.optString("text", "")
                }
            }
        }
        val scriptRaw = prefs.getString("v03_bundle_$projectId", null)
        if (cueMap.isEmpty() && scriptRaw != null) {
            runCatching {
                val scenes = JSONObject(scriptRaw).optJSONArray("scenes")
                if (scenes != null) for (i in 0 until scenes.length()) {
                    val scene = scenes.optJSONObject(i) ?: continue
                    val narration = scene.optString("narration", "").ifBlank { scene.optString("title", "") }
                    cueMap[scene.optInt("sceneNumber", i + 1)] = narration
                }
            }
        }
        val result = mutableListOf<RenderAsset>()
        for (i in 0 until assetsArray.length()) {
            val asset = assetsArray.optJSONObject(i) ?: continue
            val uri = asset.optString("localVideoUri", "").trim()
            if (uri.isBlank()) continue
            val sceneNumber = asset.optInt("sceneNumber", i + 1)
            val subtitle = if (subtitlesEnabled) cueMap[sceneNumber].orEmpty() else ""
            result.add(RenderAsset(sceneNumber, uri, subtitle))
        }
        return result.sortedBy { it.sceneNumber }
    }

    private fun publishVideo(context: Context, source: File): Uri {
        require(source.exists() && source.length() > 1000) { "File hasil render kosong." }
        val name = "AI_Video_Studio_${System.currentTimeMillis()}.mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AI Video Studio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Tidak dapat membuat file di galeri.")
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("Tidak dapat menulis file video ke galeri.")
                val ready = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                context.contentResolver.update(uri, ready, null, null)
                return uri
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw e
            }
        }
        val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "AI Video Studio").apply { mkdirs() }
        val destination = File(folder, name)
        source.copyTo(destination, overwrite = true)
        return Uri.fromFile(destination)
    }
}
