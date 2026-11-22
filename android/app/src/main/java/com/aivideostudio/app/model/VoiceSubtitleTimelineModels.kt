package com.aivideostudio.app.model

data class SubtitleCue(
    val sceneNumber: Int,
    val startSec: Int,
    val endSec: Int,
    val text: String
)

data class VoiceSubtitleBundle(
    val projectId: Long,
    val voiceCharacter: String = "Narator Jelas & Hangat",
    val languageAccent: String = "Indonesia (Netral)",
    val tempoPace: String = "Normal (1.0x)",
    val pauseNote: String = "Jeda 0.4 detik antar kalimat utama",
    val autoSyncEnabled: Boolean = true,
    val cues: List<SubtitleCue> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class TimelineClipItem(
    val sceneNumber: Int,
    val title: String,
    val durationSec: Int,
    val transition: String = "Cut Tajam",
    val visualSourceLabel: String = "Prompt / Aset Adegan",
    val audioTrackLabel: String = "Narasi Utama + Ambience"
)

data class TimelineProjectBundle(
    val projectId: Long,
    val clips: List<TimelineClipItem> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)
