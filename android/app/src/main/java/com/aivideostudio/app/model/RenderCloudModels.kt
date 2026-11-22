package com.aivideostudio.app.model

data class RenderExportBundle(
    val projectId: Long,
    val resolution: String = "1080p",
    val aspectRatio: String = "9:16",
    val audioMixingEnabled: Boolean = true,
    val subtitleBurnInEnabled: Boolean = true,
    val renderStatus: String = "Siap Antre (Belum Dijalankan)",
    val progressPercent: Int = 0,
    val lastOutputLabel: String = "",
    val lastDownloadUrl: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class CloudSyncBundle(
    val projectId: Long,
    val userEmail: String = "",
    val authStatus: String = "Belum Login (Mode Lokal)",
    val syncStatus: String = "Belum Disinkronkan ke Cloud",
    val r2AssetPrefix: String = "",
    val lastSyncedAt: Long = 0L
)
