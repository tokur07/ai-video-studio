package com.aivideostudio.app.generator

import com.aivideostudio.app.model.GeneratedScript
import com.aivideostudio.app.model.GeneratorConfig
import com.aivideostudio.app.model.ProjectContentBundle
import com.aivideostudio.app.model.StoryboardScene

object LocalTemplateGenerator {

    const val GENERATOR_LABEL = "Generator Dasar (Template Lokal)"

    private fun structureForType(videoType: String): String = when (videoType) {
        "Dokumenter" -> "Hook Fakta -> Latar Belakang -> Penelusuran Mendalam -> Refleksi Akhir"
        "Video Shorts" -> "Hook Cepat (3 detik) -> Poin Inti -> Twist / Kesimpulan Singkat -> CTA"
        "Promosi & Iklan" -> "Masalah Audiens -> Solusi Produk/Ide -> Keunggulan Utama -> Ajakan Bertindak"
        "Edukasi & Tutorial" -> "Tujuan Belajar -> Persiapan/Konsep -> Langkah Praktis -> Ringkasan"
        "Storytelling" -> "Pengantar Tokoh/Situasi -> Konflik/Tantangan -> Titik Balik -> Resolusi"
        "Video Presentasi" -> "Agenda/Ringkasan -> Poin Utama -> Bukti/Contoh -> Penutup & Tanya Jawab"
        else -> "Pembukaan Topik -> Poin Informasi Utama -> Contoh Penerapan -> Kesimpulan"
    }

    private fun transitionForIndex(index: Int, total: Int): String = when {
        index == 0 -> "Fade In"
        index == total - 1 -> "Fade Out"
        index % 2 == 0 -> "Cut Tajam"
        else -> "Cross Dissolve"
    }

    private fun cameraForIndex(index: Int): String = when (index % 4) {
        0 -> "Medium Shot, gerakan kamera halus ke depan"
        1 -> "Close-Up / Detail Shot dengan fokus subjek"
        2 -> "Wide Shot untuk konteks ruang dan suasana"
        else -> "Tracking Shot perlahan mengikuti alur narasi"
    }

    fun generate(projectId: Long, fallbackTitle: String, rawConfig: GeneratorConfig): ProjectContentBundle {
        val cleanTopic = rawConfig.topic.trim().ifEmpty { fallbackTitle.trim().ifEmpty { "Topik Video" } }
        val safeSceneCount = rawConfig.sceneCount.coerceIn(1, 12)
        val safeDuration = rawConfig.targetDurationSec.coerceIn(15, 900)
        val perSceneDuration = (safeDuration / safeSceneCount).coerceAtLeast(3)
        val structure = structureForType(rawConfig.videoType)

        val config = rawConfig.copy(
            topic = cleanTopic,
            targetDurationSec = safeDuration,
            sceneCount = safeSceneCount
        )

        val opening = when (config.detailLevel) {
            "Ringkas" -> "Halo audiens ${config.targetAudience}, mari bahas \"$cleanTopic\" secara singkat."
            "Mendalam" -> "Selamat datang. Hari ini kita mengulas \"$cleanTopic\" secara menyeluruh dengan gaya ${config.narrationStyle.lowercase()} untuk ${config.targetAudience}."
            else -> "Pernahkah Anda memperhatikan bagaimana \"$cleanTopic\" bekerja? Mari kita bahas untuk ${config.targetAudience}."
        }

        val scenes = (0 until safeSceneCount).map { idx ->
            val num = idx + 1
            val sceneTitle = when {
                num == 1 -> "Pembukaan: $cleanTopic"
                num == safeSceneCount -> "Penutup & Ringkasan"
                else -> "Bagian $num: Poin ${config.detailLevel}"
            }
            val narration = when {
                num == 1 -> "$opening Fokus format ${config.videoType}."
                num == safeSceneCount -> "Sebagai penutup tentang \"$cleanTopic\", ingat poin utama yang telah kita bahas. Sampai jumpa di video berikutnya."
                else -> "Adegan ke-$num membahas aspek \"$cleanTopic\" (${config.detailLevel.lowercase()}) dengan pendekatan ${config.narrationStyle.lowercase()}."
            }
            val visualDesc = "Ilustrasi $sceneTitle untuk ${config.videoType}, rasio ${config.aspectRatio}, nuansa ${config.narrationStyle.lowercase()}."
            val visualPrompt = "$cleanTopic, $sceneTitle, ${config.videoType.lowercase()} style, ${config.narrationStyle.lowercase()} mood, aspect ratio ${config.aspectRatio}, clean composition, high clarity"
            val audioDir = if (num == 1) "Musik latar lembut masuk (volume rendah) + narasi jelas"
            else "Narasi jelas + ambience halus sesuai adegan"

            StoryboardScene(
                sceneNumber = num,
                title = sceneTitle,
                durationSec = perSceneDuration,
                narration = narration,
                visualDescription = visualDesc,
                cameraDirection = cameraForIndex(idx),
                visualPrompt = visualPrompt,
                transition = transitionForIndex(idx, safeSceneCount),
                audioDirection = audioDir
            )
        }

        val body = scenes.drop(1).dropLast(1).ifEmpty { scenes }
            .joinToString("\n\n") { "Adegan ${it.sceneNumber} (${it.durationSec}d): ${it.narration}" }

        val closing = scenes.last().narration

        val script = GeneratedScript(
            opening = opening,
            body = body,
            closing = closing,
            sceneNarrations = scenes.map { it.narration },
            estimatedDurationSec = scenes.sumOf { it.durationSec },
            storyStructure = structure,
            isManuallyEdited = false,
            sourceLabel = GENERATOR_LABEL
        )

        return ProjectContentBundle(
            projectId = projectId,
            config = config,
            script = script,
            scenes = scenes,
            updatedAt = System.currentTimeMillis()
        )
    }
}
