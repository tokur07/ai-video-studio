package com.aivideostudio.app.model

data class FactCheckItem(
    val claim: String,
    val status: String,
    val note: String
)

data class ReferenceItem(
    val title: String,
    val sourceType: String,
    val note: String
)

data class ResearchScriptBundle(
    val projectId: Long,
    val topic: String = "",
    val videoType: String = "Edukasi & Tutorial",
    val writingStyle: String = "Informatif & Jelas",
    val targetAudience: String = "Pemula / Umum",
    val targetDurationSec: Int = 60,
    val summary: String = "",
    val keyPoints: List<String> = emptyList(),
    val factChecks: List<FactCheckItem> = emptyList(),
    val references: List<ReferenceItem> = emptyList(),
    val opening: String = "",
    val body: String = "",
    val closing: String = "",
    val revisionInstruction: String = "",
    val providerLabel: String = "Mode Terstruktur Lokal (Belum Terhubung Cloud)",
    val isLiveCloudResult: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
