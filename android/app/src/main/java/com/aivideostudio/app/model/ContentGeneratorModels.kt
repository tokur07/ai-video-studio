package com.aivideostudio.app.model

data class GeneratorConfig(
    val topic: String = "",
    val videoType: String = "Edukasi & Tutorial",
    val language: String = "Indonesia",
    val targetDurationSec: Int = 60,
    val sceneCount: Int = 4,
    val narrationStyle: String = "Informatif & Ramah",
    val targetAudience: String = "Pemula / Umum",
    val aspectRatio: String = "9:16",
    val detailLevel: String = "Standar"
)

data class GeneratedScript(
    val opening: String = "",
    val body: String = "",
    val closing: String = "",
    val sceneNarrations: List<String> = emptyList(),
    val estimatedDurationSec: Int = 60,
    val storyStructure: String = "",
    val isManuallyEdited: Boolean = false,
    val sourceLabel: String = "Generator Dasar (Template Lokal)"
)

data class StoryboardScene(
    val sceneNumber: Int,
    val title: String,
    val durationSec: Int,
    val narration: String,
    val visualDescription: String,
    val cameraDirection: String,
    val visualPrompt: String,
    val transition: String,
    val audioDirection: String
)

data class ProjectContentBundle(
    val projectId: Long,
    val config: GeneratorConfig = GeneratorConfig(),
    val script: GeneratedScript = GeneratedScript(),
    val scenes: List<StoryboardScene> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)
