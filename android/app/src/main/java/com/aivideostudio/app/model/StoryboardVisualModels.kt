package com.aivideostudio.app.model

data class SceneVisualAsset(
    val sceneNumber: Int,
    val sceneTitle: String,
    val visualPrompt: String,
    val negativePrompt: String = "teks acak, watermark, buram, distorsi wajah, anatomi cacat",
    val cameraShot: String = "Medium Shot",
    val aspectRatio: String = "9:16",
    val localImageUri: String = "",
    val assetSourceLabel: String = "Prompt Siap Pakai (Belum Ada Gambar)"
)

data class StoryboardVisualBundle(
    val projectId: Long,
    val aspectRatio: String = "9:16",
    val visualStyle: String = "Fotorealistik & Sinematik",
    val assets: List<SceneVisualAsset> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)
