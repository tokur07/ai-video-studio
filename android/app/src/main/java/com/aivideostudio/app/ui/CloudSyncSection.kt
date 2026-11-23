package com.aivideostudio.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aivideostudio.app.model.CloudSyncBundle
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private const val PREFS_NAME = "ai_video_studio"
private const val KEY_SUPABASE_URL = "supabase_url"
private const val KEY_SUPABASE_ANON_KEY = "supabase_anon_key"
private const val KEY_R2_BUCKET_INFO = "r2_bucket_info"
private const val KEY_SUPABASE_ACCESS_TOKEN = "supabase_access_token"

fun loadSupabaseUrl(context: Context): String =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_SUPABASE_URL, "") ?: ""

fun saveSupabaseUrl(context: Context, v: String) =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_SUPABASE_URL, v.trim()).apply()

fun loadSupabaseAnonKey(context: Context): String =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_SUPABASE_ANON_KEY, "") ?: ""

fun saveSupabaseAnonKey(context: Context, v: String) =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_SUPABASE_ANON_KEY, v.trim()).apply()

fun loadR2BucketInfo(context: Context): String =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_R2_BUCKET_INFO, "") ?: ""

fun saveR2BucketInfo(context: Context, v: String) =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_R2_BUCKET_INFO, v.trim()).apply()

private fun loadCloudBundle(context: Context, projectId: Long): CloudSyncBundle {
    val fallback = CloudSyncBundle(
        projectId = projectId,
        r2AssetPrefix = "projects/$projectId/"
    )
    return try {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("v09_cloud_$projectId", null) ?: return fallback
        val o = JSONObject(raw)
        CloudSyncBundle(
            projectId = projectId,
            userEmail = o.optString("userEmail", ""),
            authStatus = o.optString("authStatus", "Belum Login (Mode Lokal)"),
            syncStatus = o.optString("syncStatus", "Belum Disinkronkan ke Cloud"),
            r2AssetPrefix = o.optString("r2AssetPrefix", "projects/$projectId/"),
            lastSyncedAt = o.optLong("lastSyncedAt", 0L)
        )
    } catch (_: Exception) {
        fallback
    }
}

private fun saveCloudBundle(context: Context, bundle: CloudSyncBundle) {
    val o = JSONObject().apply {
        put("projectId", bundle.projectId)
        put("userEmail", bundle.userEmail)
        put("authStatus", bundle.authStatus)
        put("syncStatus", bundle.syncStatus)
        put("r2AssetPrefix", bundle.r2AssetPrefix)
        put("lastSyncedAt", bundle.lastSyncedAt)
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString("v09_cloud_${bundle.projectId}", o.toString()).apply()
}

@Composable
fun CloudSyncSection(
    context: Context,
    projectId: Long,
    projectTitle: String,
    projectType: String,
    projectMode: String
) {
    val initial = remember(projectId) { loadCloudBundle(context, projectId) }
    var bundle by remember(projectId) { mutableStateOf(initial) }
    var password by remember(projectId) { mutableStateOf("") }
    var busy by remember(projectId) { mutableStateOf(false) }
    var confirmCloudDelete by remember(projectId) { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun signInSupabase() {
        val sbUrl = loadSupabaseUrl(context).trim().trimEnd('/')
        val anonKey = loadSupabaseAnonKey(context).trim()
        if (sbUrl.isEmpty() || anonKey.isEmpty()) {
            val updated = bundle.copy(
                authStatus = "URL atau anon key Supabase belum diisi di menu Setelan."
            )
            bundle = updated
            saveCloudBundle(context, updated)
            return
        }
        if (bundle.userEmail.isBlank() || password.isBlank()) {
            val updated = bundle.copy(authStatus = "Masukkan email dan password akun Supabase.")
            bundle = updated
            return
        }

        busy = true
        bundle = bundle.copy(authStatus = "Memproses autentikasi Supabase...")
        val emailInput = bundle.userEmail.trim()

        Thread {
            try {
                val conn = (URL("$sbUrl/auth/v1/token?grant_type=password").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("apikey", anonKey)
                }
                val payload = JSONObject().apply {
                    put("email", emailInput)
                    put("password", password)
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                    it.write(payload.toString())
                    it.flush()
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val raw = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                conn.disconnect()

                val root = JSONObject(raw)
                val token = root.optString("access_token", "")
                if (code in 200..299 && token.isNotBlank()) {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putString(KEY_SUPABASE_ACCESS_TOKEN, token).apply()
                    mainHandler.post {
                        busy = false
                        password = ""
                        val next = bundle.copy(
                            userEmail = emailInput,
                            authStatus = "Terautentikasi via Supabase ($emailInput)"
                        )
                        bundle = next
                        saveCloudBundle(context, next)
                    }
                } else {
                    val err = root.optString("msg", root.optString("error_description", "Login gagal ($code)"))
                    mainHandler.post {
                        busy = false
                        val next = bundle.copy(authStatus = "Auth gagal: $err")
                        bundle = next
                        saveCloudBundle(context, next)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    busy = false
                    val next = bundle.copy(authStatus = "Kesalahan koneksi Auth: ${e.message ?: "gagal"}")
                    bundle = next
                    saveCloudBundle(context, next)
                }
            }
        }.start()
    }

    fun syncProjectMetadata() {
        val sbUrl = loadSupabaseUrl(context).trim().trimEnd('/')
        val anonKey = loadSupabaseAnonKey(context).trim()
        val token = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SUPABASE_ACCESS_TOKEN, "") ?: ""
        val r2Info = loadR2BucketInfo(context)

        if (sbUrl.isEmpty() || anonKey.isEmpty()) {
            val next = bundle.copy(
                syncStatus = "Konfigurasi Supabase belum diisi; data tetap tersimpan aman di lokal.",
                r2AssetPrefix = if (r2Info.isNotBlank()) "$r2Info/projects/$projectId/" else "projects/$projectId/"
            )
            bundle = next
            saveCloudBundle(context, next)
            return
        }

        busy = true
        bundle = bundle.copy(syncStatus = "Menyinkronkan metadata proyek ke Supabase...")
        Thread {
            try {
                val conn = (URL("$sbUrl/rest/v1/studio_projects?on_conflict=user_id,project_id").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("apikey", anonKey)
                    setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
                    if (token.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                }
                if (token.isBlank()) throw IllegalStateException("Login Supabase terlebih dahulu sebelum sinkronisasi cloud.")
                val userConn = (URL("$sbUrl/auth/v1/user").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 15000
                    setRequestProperty("apikey", anonKey)
                    setRequestProperty("Authorization", "Bearer $token")
                }
                val userCode = userConn.responseCode
                val userStream = if (userCode in 200..299) userConn.inputStream else userConn.errorStream
                val userRaw = BufferedReader(InputStreamReader(userStream, Charsets.UTF_8)).use { it.readText() }
                userConn.disconnect()
                if (userCode !in 200..299) throw IllegalStateException("Sesi Supabase tidak valid (HTTP $userCode). Login ulang.")
                val userId = JSONObject(userRaw).optString("id", "")
                if (userId.isBlank()) throw IllegalStateException("Supabase tidak mengembalikan ID pengguna.")
                val bodyJson = JSONObject().apply {
                    put("project_id", projectId.toString())
                    put("user_id", userId)
                    put("title", projectTitle)
                    put("project_type", projectType)
                    put("production_mode", projectMode)
                    put("r2_prefix", if (r2Info.isNotBlank()) "$r2Info/projects/$projectId/" else "projects/$projectId/")
                    put("updated_at", java.time.Instant.now().toString())
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                    it.write(bodyJson.toString())
                    it.flush()
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val raw = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                conn.disconnect()

                mainHandler.post {
                    busy = false
                    val now = System.currentTimeMillis()
                    val statusMsg = if (code in 200..299) {
                        "Tersinkron ke database Supabase (HTTP $code)."
                    } else {
                        "Sinkronisasi Supabase HTTP $code: ${raw.take(140)}"
                    }
                    val next = bundle.copy(
                        syncStatus = statusMsg,
                        r2AssetPrefix = if (r2Info.isNotBlank()) "$r2Info/projects/$projectId/" else "projects/$projectId/",
                        lastSyncedAt = if (code in 200..299) now else bundle.lastSyncedAt
                    )
                    bundle = next
                    saveCloudBundle(context, next)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    busy = false
                    val next = bundle.copy(
                        syncStatus = "Gagal sinkronisasi Supabase: ${e.message ?: "kesalahan jaringan"}"
                    )
                    bundle = next
                    saveCloudBundle(context, next)
                }
            }
        }.start()
    }

    fun deleteCloudMetadata() {
        val sbUrl = loadSupabaseUrl(context).trim().trimEnd('/')
        val anonKey = loadSupabaseAnonKey(context).trim()
        val token = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SUPABASE_ACCESS_TOKEN, "") ?: ""
        if (sbUrl.isBlank() || anonKey.isBlank() || token.isBlank()) {
            bundle = bundle.copy(syncStatus = "Login Supabase diperlukan untuk menghapus metadata cloud.")
            return
        }
        busy = true
        bundle = bundle.copy(syncStatus = "Memeriksa kepemilikan lalu menghapus metadata cloud...")
        Thread {
            try {
                val conn = (URL("$sbUrl/rest/v1/studio_projects?project_id=eq.$projectId").openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    connectTimeout = 12000
                    readTimeout = 20000
                    setRequestProperty("apikey", anonKey)
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Prefer", "return=minimal")
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val raw = if (stream != null) BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() } else ""
                conn.disconnect()
                mainHandler.post {
                    busy = false
                    val message = if (code in 200..299) {
                        "Metadata cloud proyek berhasil dihapus. File video/gambar belum pernah diunggah ke R2 oleh versi ini."
                    } else {
                        "Penghapusan cloud gagal (HTTP $code): ${raw.take(140)}"
                    }
                    val next = bundle.copy(syncStatus = message, lastSyncedAt = if (code in 200..299) 0L else bundle.lastSyncedAt)
                    bundle = next
                    if (code in 200..299) saveCloudBundle(context, next)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    busy = false
                    bundle = bundle.copy(syncStatus = "Gagal menghapus metadata cloud: ${e.message ?: "kesalahan jaringan"}")
                }
            }
        }.start()
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "CLOUD SYNC, AUTH SUPABASE & R2 (V0.9)",
                color = Color(0xFF7356D8),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Autentikasi & database proyek menggunakan Supabase (anon key) serta referensi path aset Cloudflare R2. Jangan pernah memakai service_role key di aplikasi.",
                color = Color(0xFF77738A),
                style = MaterialTheme.typography.bodySmall
            )

            Text("Status Auth: ${bundle.authStatus}", color = Color(0xFF543BB2), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text("Status Sync: ${bundle.syncStatus}", color = Color(0xFF171329), style = MaterialTheme.typography.bodySmall)
            Text("R2 Asset Prefix: ${bundle.r2AssetPrefix.ifBlank { "projects/$projectId/" }}", color = Color(0xFF247B5B), style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(
                value = bundle.userEmail,
                onValueChange = { bundle = bundle.copy(userEmail = it) },
                label = { Text("Email akun Supabase") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password akun Supabase") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (confirmCloudDelete) {
                AlertDialog(
                    onDismissRequest = { confirmCloudDelete = false },
                    title = { Text("Hapus metadata cloud?") },
                    text = { Text("Data metadata proyek ini akan dihapus dari akun Supabase yang sedang login. Video dan gambar tidak ikut terhapus karena belum diunggah ke R2 oleh versi aplikasi saat ini.") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmCloudDelete = false
                            deleteCloudMetadata()
                        }) { Text("Hapus cloud", color = Color(0xFFB3261E)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmCloudDelete = false }) { Text("Batal") }
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { signInSupabase() },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (busy) "Proses..." else "Login Supabase")
                }
                OutlinedButton(
                    onClick = { syncProjectMetadata() },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sync Proyek")
                }
            }
            OutlinedButton(
                onClick = { confirmCloudDelete = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Hapus metadata proyek dari cloud", color = Color(0xFFB3261E))
            }
        }
    }
}
