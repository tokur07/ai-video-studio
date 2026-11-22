package com.aivideostudio.app.network

import android.os.Handler
import android.os.Looper
import com.aivideostudio.app.model.FactCheckItem
import com.aivideostudio.app.model.ReferenceItem
import com.aivideostudio.app.model.ResearchScriptBundle
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object WorkerResearchClient {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun generateResearchAndScriptAsync(
        workerBaseUrl: String,
        current: ResearchScriptBundle,
        onResult: (ResearchScriptBundle) -> Unit
    ) {
        Thread {
            val result = generateResearchAndScriptBlocking(workerBaseUrl, current)
            mainHandler.post { onResult(result) }
        }.start()
    }

    private fun generateResearchAndScriptBlocking(
        workerBaseUrl: String,
        current: ResearchScriptBundle
    ): ResearchScriptBundle {
        val cleanUrl = workerBaseUrl.trim().trimEnd('/')
        if (cleanUrl.isNotEmpty()) {
            try {
                val endpoint = URL("$cleanUrl/v1/research-script")
                val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12000
                    readTimeout = 45000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }

                val reqJson = JSONObject().apply {
                    put("topic", current.topic)
                    put("videoType", current.videoType)
                    put("language", "Indonesia")
                    put("targetDurationSec", current.targetDurationSec)
                    put("writingStyle", current.writingStyle)
                    put("targetAudience", current.targetAudience)
                    put("revisionInstruction", current.revisionInstruction)
                    put("currentDraft", JSONObject().apply {
                        put("opening", current.opening)
                        put("body", current.body)
                        put("closing", current.closing)
                    })
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(reqJson.toString())
                    writer.flush()
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val rawResponse = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                conn.disconnect()

                val root = JSONObject(rawResponse)
                if (code in 200..299 && root.optBoolean("ok", false)) {
                    val hasLive = root.optBoolean("hasLiveAiOutput", false)
                    val providerUsed = root.optString("providerUsed", "Cloudflare Worker")
                    val dataObj = root.optJSONObject("data")
                    if (hasLive && dataObj != null) {
                        return parseCloudData(current, dataObj, providerUsed)
                    }
                    val localDraft = buildLocalDraft(current)
                    return localDraft.copy(
                        providerLabel = "Worker Terjangkau ($providerUsed) — Kerangka Lokal",
                        isLiveCloudResult = false,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            } catch (_: Exception) {
                // Fallback transparan ke penyusunan kerangka lokal bila jaringan/worker belum siap
            }
        }

        return buildLocalDraft(current)
    }

    private fun parseCloudData(
        base: ResearchScriptBundle,
        data: JSONObject,
        providerUsed: String
    ): ResearchScriptBundle {
        val pointsArr = data.optJSONArray("keyPoints") ?: JSONArray()
        val keyPoints = (0 until pointsArr.length()).mapNotNull {
            pointsArr.optString(it, "").trim().takeIf { s -> s.isNotEmpty() }
        }

        val checksArr = data.optJSONArray("factChecks") ?: JSONArray()
        val factChecks = (0 until checksArr.length()).mapNotNull { idx ->
            val obj = checksArr.optJSONObject(idx) ?: return@mapNotNull null
            val claim = obj.optString("claim", "").trim()
            if (claim.isEmpty()) null
            else FactCheckItem(
                claim = claim,
                status = obj.optString("status", "Perlu Verifikasi").trim(),
                note = obj.optString("note", "").trim()
            )
        }

        val refsArr = data.optJSONArray("references") ?: JSONArray()
        val references = (0 until refsArr.length()).mapNotNull { idx ->
            val obj = refsArr.optJSONObject(idx) ?: return@mapNotNull null
            val title = obj.optString("title", "").trim()
            if (title.isEmpty()) null
            else ReferenceItem(
                title = title,
                sourceType = obj.optString("sourceType", "Rujukan").trim(),
                note = obj.optString("note", "").trim()
            )
        }

        val scriptObj = data.optJSONObject("script") ?: JSONObject()
        val opening = scriptObj.optString("opening", base.opening).trim()
        val body = scriptObj.optString("body", base.body).trim()
        val closing = scriptObj.optString("closing", base.closing).trim()
        val duration = scriptObj.optInt("estimatedDurationSec", base.targetDurationSec)

        return base.copy(
            summary = data.optString("summary", base.summary).trim(),
            keyPoints = if (keyPoints.isNotEmpty()) keyPoints else base.keyPoints,
            factChecks = if (factChecks.isNotEmpty()) factChecks else base.factChecks,
            references = if (references.isNotEmpty()) references else base.references,
            opening = opening,
            body = body,
            closing = closing,
            targetDurationSec = duration.coerceIn(15, 900),
            providerLabel = "AI Cloud Aktif ($providerUsed)",
            isLiveCloudResult = true,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun buildLocalDraft(current: ResearchScriptBundle): ResearchScriptBundle {
        val topic = current.topic.trim().ifEmpty { "Topik Video" }
        val style = current.writingStyle.trim().ifEmpty { "Informatif & Jelas" }
        val audience = current.targetAudience.trim().ifEmpty { "Pemula / Umum" }
        val revisionNote = current.revisionInstruction.trim()

        val summary = "Ringkasan riset awal untuk topik \"$topic\" pada format ${current.videoType} dengan pendekatan gaya \"$style\" bagi audiens $audience."
        val keyPoints = listOf(
            "Definisi inti & konteks utama dari \"$topic\" yang relevan bagi $audience.",
            "Perbedaan antara fakta yang sudah mapan, sudut pandang interpretatif, dan detail yang perlu dicek ulang.",
            "Penerapan praktis atau alur cerita yang selaras dengan durasi target ${current.targetDurationSec} detik."
        )
        val factChecks = listOf(
            FactCheckItem(
                claim = "Klaim tanggal, angka statistik, atau kutipan spesifik terkait \"$topic\"",
                status = "Perlu Verifikasi",
                note = "Cek kembali ke sumber primer, dokumen resmi, atau literatur tepercaya sebelum publikasi."
            ),
            FactCheckItem(
                claim = "Kesesuaian istilah teknis untuk audiens $audience",
                status = "Catatan",
                note = "Sederhanakan istilah khusus agar tetap mudah dipahami."
            )
        )
        val references = listOf(
            ReferenceItem(
                title = "Arsip, Dokumen Resmi & Publikasi Lembaga Terkait \"$topic\"",
                sourceType = "Sumber Primer / Lembaga",
                note = "Gunakan untuk memastikan keakuratan data historis atau teknis."
            ),
            ReferenceItem(
                title = "Jurnal, Buku Teks & Artikel Ulasan Terverifikasi",
                sourceType = "Referensi Sekunder",
                note = "Bantu membandingkan interpretasi dan menyusun sudut pandang berimbang."
            )
        )

        val stylePrefix = when {
            style.contains("Sinematik", ignoreCase = true) -> "[Gaya Sinematik] "
            style.contains("Tegas", ignoreCase = true) -> "[Gaya Tegas & Ringkas] "
            style.contains("Santai", ignoreCase = true) -> "[Gaya Santai] "
            else -> "[Gaya Informatif] "
        }

        val revSuffix = if (revisionNote.isNotEmpty()) "\nCatatan revisi diterapkan: $revisionNote" else ""

        val opening = "${stylePrefix}Topik \"$topic\" punya satu titik penting untuk $audience. Mari kita telusuri bagian intinya."
        val body = buildString {
            append("1. Gambaran utama: $topic dalam konteks ${current.videoType}.\n")
            append("2. Poin kunci: Pisahkan fakta terverifikasi dari opini agar narasi tetap kuat dan kredibel.\n")
            append("3. Alur pembahasan: Susun transisi antarbagian sesuai target durasi ${current.targetDurationSec} detik.")
            if (revSuffix.isNotEmpty()) append(revSuffix)
        }
        val closing = "Demikian ringkasan \"$topic\" (${current.targetDurationSec} detik). Pastikan setiap data rujukan sudah diverifikasi sebelum tahap produksi berikutnya."

        return current.copy(
            topic = topic,
            summary = summary,
            keyPoints = keyPoints,
            factChecks = factChecks,
            references = references,
            opening = opening,
            body = body,
            closing = closing,
            providerLabel = "Kerangka Terstruktur Lokal (Belum Terhubung Cloud)",
            isLiveCloudResult = false,
            updatedAt = System.currentTimeMillis()
        )
    }
}
