package com.aivideostudio.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aivideostudio.app.ui.AiGeneratorSection
import com.aivideostudio.app.ui.CloudSyncSection
import com.aivideostudio.app.ui.RenderExportSection
import com.aivideostudio.app.ui.ResearchScriptSection
import com.aivideostudio.app.ui.StoryboardVisualSection
import com.aivideostudio.app.ui.TimelineEditorSection
import com.aivideostudio.app.ui.VoiceSubtitleSection
import com.aivideostudio.app.ui.loadR2BucketInfo
import com.aivideostudio.app.ui.loadRenderRunnerUrl
import com.aivideostudio.app.ui.loadSupabaseAnonKey
import com.aivideostudio.app.ui.loadSupabaseUrl
import com.aivideostudio.app.ui.loadWorkerUrl
import com.aivideostudio.app.ui.saveR2BucketInfo
import com.aivideostudio.app.ui.saveRenderRunnerUrl
import com.aivideostudio.app.ui.saveSupabaseAnonKey
import com.aivideostudio.app.ui.saveSupabaseUrl
import com.aivideostudio.app.ui.saveWorkerUrl
import org.json.JSONArray
import org.json.JSONObject

private val Ink = Color(0xFF171329)
private val Muted = Color(0xFF77738A)
private val Violet = Color(0xFF7356D8)
private val VioletDark = Color(0xFF543BB2)
private val Lavender = Color(0xFFF1EDFF)
private val Canvas = Color(0xFFF8F7FC)
private val SurfaceWhite = Color.White
private val Mint = Color(0xFFDCF7ED)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Violet, onPrimary = Color.White, secondary = VioletDark,
                background = Canvas, surface = SurfaceWhite, onSurface = Ink,
                onSurfaceVariant = Muted, outline = Color(0xFFE5E1EF)
            )) { VideoStudioApp(applicationContext) }
        }
    }
}

private enum class Tab(val label: String, val glyph: String) {
    HOME("Beranda", "⌂"), PROJECTS("Proyek", "▤"), EDITOR("Editor", "▶"), SETTINGS("Setelan", "⚙")
}
private data class VideoProject(val id: Long, val title: String, val type: String, val mode: String, val createdAt: Long)

@Composable
private fun VideoStudioApp(context: Context) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var topic by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Dokumenter") }
    var mode by remember { mutableStateOf("Otomatis") }
    var status by remember { mutableStateOf("Ide Anda siap diubah menjadi sebuah video.") }
    var projects by remember { mutableStateOf(loadProjects(context)) }
    var selectedProjectId by remember { mutableStateOf<Long?>(null) }
    var pickedImageUri by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pickedImageUri = uri.toString()
        }
    }

    fun createProject() {
        if (topic.isBlank()) {
            status = "Tuliskan topik atau ide video terlebih dahulu."
            return
        }
        val now = System.currentTimeMillis()
        val project = VideoProject(now, topic.trim().replace(Regex("\\s+"), " "), type, mode, now)
        projects = listOf(project) + projects
        saveProjects(context, projects)
        selectedProjectId = project.id
        topic = ""
        status = "Proyek berhasil dibuat dan disimpan di perangkat."
        selectedTab = 1
    }

    Scaffold(
        containerColor = Canvas,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                Tab.values().forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Box(Modifier.clip(RoundedCornerShape(14.dp))
                                .background(if (selectedTab == index) Lavender else Color.Transparent)
                                .padding(horizontal = 18.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
                                Text(tab.glyph, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        },
                        label = { Text(tab.label, fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Header()
            when (Tab.values()[selectedTab]) {
                Tab.HOME -> HomeScreen(topic, { topic = it }, type, { type = it }, mode, { mode = it },
                    status, projects.size, { createProject() }, { selectedTab = 1 })
                Tab.PROJECTS -> ProjectsScreen(projects, selectedProjectId, {
                    selectedProjectId = it.id; selectedTab = 2
                }, { selectedTab = 0 })
                Tab.EDITOR -> EditorScreen(
                    projects.firstOrNull { it.id == selectedProjectId } ?: projects.firstOrNull(),
                    { selectedTab = 1 },
                    pickedImageUri = pickedImageUri,
                    onClearPickedImage = { pickedImageUri = null },
                    onRequestPickImage = { imagePickerLauncher.launch("image/*") }
                )
                Tab.SETTINGS -> SettingsScreen(context, projects.size)
            }
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Violet), contentAlignment = Alignment.Center) {
            Text("✦", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
        Column {
            Text("AI VIDEO STUDIO", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Ink)
            Text("CREATE • EDIT • TELL YOUR STORY", style = MaterialTheme.typography.labelSmall, color = Muted, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(40.dp).clip(CircleShape).background(Mint), contentAlignment = Alignment.Center) {
            Text("AI", color = Color(0xFF247B5B), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HomeScreen(
    topic: String, onTopicChange: (String) -> Unit, type: String, onTypeChange: (String) -> Unit,
    mode: String, onModeChange: (String) -> Unit, status: String, projectCount: Int,
    onCreate: () -> Unit, onOpenProjects: () -> Unit
) {
    val types = listOf("Dokumenter", "Video Shorts", "Promosi & Iklan", "Edukasi & Tutorial", "Storytelling", "Video Informatif", "Video Presentasi")
    val modes = listOf("Otomatis", "Manual", "Hybrid")

    SectionHeading("Mulai proyek baru", "Masukkan ide atau topik lalu pilih format dan mode produksi")
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = topic, onValueChange = onTopicChange,
                label = { Text("Topik atau ide video") },
                placeholder = { Text("Contoh: Kisah kereta api pertama di Jawa") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Jenis produksi", fontWeight = FontWeight.SemiBold, color = Ink)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.take(3).forEach { item ->
                    FilterChip(selected = type == item, onClick = { onTypeChange(item) }, label = { Text(item, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.drop(3).take(2).forEach { item ->
                    FilterChip(selected = type == item, onClick = { onTypeChange(item) }, label = { Text(item, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.drop(5).forEach { item ->
                    FilterChip(selected = type == item, onClick = { onTypeChange(item) }, label = { Text(item, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
            Text("Mode produksi", fontWeight = FontWeight.SemiBold, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { onModeChange(m) }, label = { Text(m) })
                }
            }
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text("Buat proyek")
            }
            Text(status, color = VioletDark, style = MaterialTheme.typography.bodySmall)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeading("Studio cepat", "Akses alur kerja utama")
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onOpenProjects) { Text("Lihat ($projectCount)") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StudioTile("▶", "Shorts & Reels Studio", "Konten vertikal cepat dan hook tajam", Lavender)
        StudioTile("✎", "Edukasi & Tutorial", "Panduan langkah demi langkah yang terstruktur", Mint)
        StudioTile("♡", "Storytelling Studio", "Cerita pendek dan narasi sinematik", Color(0xFFFFF2D9))
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun StudioTile(icon: String, title: String, description: String, tint: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(tint), contentAlignment = Alignment.Center) {
                Text(icon, color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold, color = Ink)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Text("›", color = Violet, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun ProjectsScreen(
    projects: List<VideoProject>, selectedProjectId: Long?, onSelect: (VideoProject) -> Unit, onCreate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading("Proyek saya", "Semua ide video yang pernah Anda buat")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${projects.size} proyek", color = Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = onCreate) { Text("+ Proyek baru") }
        }
        if (projects.isEmpty()) {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
                Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(68.dp).clip(CircleShape).background(Lavender), contentAlignment = Alignment.Center) {
                        Text("▤", color = Violet, style = MaterialTheme.typography.headlineLarge)
                    }
                    Text("Belum ada proyek", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Mulai dari sebuah ide. Proyek Anda akan muncul di sini.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onCreate) { Text("Buat proyek pertama") }
                }
            }
        } else {
            projects.forEach { project ->
                Card(
                    onClick = { onSelect(project) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = if (selectedProjectId == project.id) Lavender else SurfaceWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Lavender), contentAlignment = Alignment.Center) {
                            Text("▶", color = Violet, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(project.title, color = Ink, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${project.type}  •  ${project.mode}", color = Muted, style = MaterialTheme.typography.bodySmall)
                            Text("Draft lokal", color = VioletDark, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("›", color = Violet, style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
            Text("Proyek tersimpan di perangkat ini dan dapat disinkronkan ke Supabase bila konfigurasi cloud diisi.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EditorScreen(
    project: VideoProject?,
    onChooseProject: () -> Unit,
    pickedImageUri: String?,
    onClearPickedImage: () -> Unit,
    onRequestPickImage: (Int) -> Unit
) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading("Studio editor", "Ruang kerja untuk menyusun video Anda")
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Ink), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("WORKSPACE", color = Color(0xFFC9BDFD), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(project?.title ?: "Pilih proyek untuk mulai mengedit", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (project == null) "Buat proyek baru atau pilih salah satu dari daftar proyek." else "${project.type}  •  Mode ${project.mode}", color = Color(0xFFD7D3E6))
                OutlinedButton(onClick = onChooseProject, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                    Text(if (project == null) "Pilih proyek" else "Ganti proyek")
                }
            }
        }

        if (project != null) {
            ResearchScriptSection(
                context = ctx,
                projectId = project.id,
                projectTitle = project.title,
                projectType = project.type
            )
            AiGeneratorSection(
                context = ctx,
                projectId = project.id,
                projectTitle = project.title,
                projectType = project.type,
                projectMode = project.mode
            )
            StoryboardVisualSection(
                context = ctx,
                projectId = project.id,
                projectTitle = project.title,
                projectType = project.type,
                pickedImageUri = pickedImageUri,
                onClearPickedImage = onClearPickedImage,
                onRequestPickImage = onRequestPickImage
            )
            VoiceSubtitleSection(
                context = ctx,
                projectId = project.id,
                projectTitle = project.title
            )
            TimelineEditorSection(
                context = ctx,
                projectId = project.id,
                projectTitle = project.title
            )
            RenderExportSection(
                context = ctx,
                projectId = project.id,
                projectTitle = project.title
            )
            CloudSyncSection(
                context = ctx,
                projectId = project.id,
                projectTitle = project.title,
                projectType = project.type,
                projectMode = project.mode
            )
        }

        SectionHeading("Alur penyuntingan", "Modul produksi & integrasi cloud")
        EditorFeature("01", "Storyboard", "Susun urutan adegan dan alur cerita", true)
        EditorFeature("02", "Scene manager", "Atur visual, prompt, impor gambar & generate AI", true)
        EditorFeature("03", "Audio & subtitle", "Kelola arahan suara dan sinkronisasi subtitle", true)
        EditorFeature("04", "Timeline, render MP4 & cloud", "Timeline (v0.7), render FFmpeg (v0.8) & Supabase/R2 (v0.9) terhubung", true)
    }
}

@Composable
private fun EditorFeature(number: String, title: String, description: String, available: Boolean) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(if (available) Mint else Lavender), contentAlignment = Alignment.Center) {
                Text(number, color = if (available) Color(0xFF247B5B) else VioletDark, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Text(if (available) "Siap" else "Segera", color = if (available) Color(0xFF247B5B) else Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SettingsScreen(context: Context, projectCount: Int) {
    var workerUrl by remember { mutableStateOf(loadWorkerUrl(context)) }
    var supabaseUrl by remember { mutableStateOf(loadSupabaseUrl(context)) }
    var supabaseAnonKey by remember { mutableStateOf(loadSupabaseAnonKey(context)) }
    var r2BucketInfo by remember { mutableStateOf(loadR2BucketInfo(context)) }
    var savedMsg by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading("Pengaturan", "Atur preferensi studio produksi & koneksi cloud Anda")
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("STATUS APLIKASI", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                SettingRow("Versi aplikasi", "0.3.0 • Animasi Wan 2.2 & render lokal")
                HorizontalDivider(color = Color(0xFFEDEAF4))
                SettingRow("Penyimpanan proyek", "Lokal + Sinkronisasi Cloud")
                HorizontalDivider(color = Color(0xFFEDEAF4))
                SettingRow("Jumlah proyek", "$projectCount")
                HorizontalDivider(color = Color(0xFFEDEAF4))
                SettingRow("Konfigurasi Supabase", if (supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()) "Terkonfigurasi" else "Belum diisi")
            }
        }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("KONEKSI CLOUD WORKER", fontWeight = FontWeight.Bold, color = VioletDark)
                Text("Cloudflare Worker digunakan untuk riset, naskah, gambar AI, dan antrean animasi Wan 2.2. Ekspor MP4 dilakukan langsung di perangkat. Jangan menaruh secret/API key provider di APK.", color = Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = workerUrl,
                    onValueChange = { workerUrl = it },
                    label = { Text("URL Cloudflare Worker") },
                    placeholder = { Text("https://ai-video-studio-api.<subdomain>.workers.dev") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    saveWorkerUrl(context, workerUrl)
                    savedMsg = "URL Cloudflare Worker disimpan."
                }) {
                    Text("Simpan Endpoint")
                }
            }
        }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SUPABASE & CLOUDFLARE R2 (V0.9)", fontWeight = FontWeight.Bold, color = VioletDark)
                Text("Gunakan Supabase anon key (bukan service_role key) untuk autentikasi dan sinkronisasi database proyek.", color = Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = supabaseUrl,
                    onValueChange = { supabaseUrl = it },
                    label = { Text("Supabase Project URL") },
                    placeholder = { Text("https://<project-ref>.supabase.co") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = supabaseAnonKey,
                    onValueChange = { supabaseAnonKey = it },
                    label = { Text("Supabase anon key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = r2BucketInfo,
                    onValueChange = { r2BucketInfo = it },
                    label = { Text("Cloudflare R2 Bucket / Base Label") },
                    placeholder = { Text("r2://ai-video-studio-assets") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    saveSupabaseUrl(context, supabaseUrl)
                    saveSupabaseAnonKey(context, supabaseAnonKey)
                    saveR2BucketInfo(context, r2BucketInfo)
                    savedMsg = "Konfigurasi Supabase & R2 disimpan di perangkat."
                }) {
                    Text("Simpan Cloud Config")
                }
                if (savedMsg.isNotBlank()) {
                    Text(savedMsg, color = VioletDark, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Lavender)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("KEAMANAN KREDENSIAL", fontWeight = FontWeight.Bold, color = VioletDark)
                Text("Jangan pernah memasukkan Supabase service_role key atau API key provider AI ke dalam kode sumber maupun APK.", color = Ink, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("AI Video Studio • Dibangun untuk produksi video dari ide hingga hasil akhir.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, modifier = Modifier.weight(1f))
        Text(value, color = Ink, fontWeight = FontWeight.SemiBold)
    }
}

private fun loadProjects(context: Context): List<VideoProject> {
    return try {
        val raw = context.getSharedPreferences("ai_video_studio", Context.MODE_PRIVATE).getString("projects", "[]") ?: "[]"
        val array = JSONArray(raw)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            VideoProject(item.optLong("id"), item.optString("title"), item.optString("type"),
                item.optString("mode"), item.optLong("createdAt"))
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveProjects(context: Context, projects: List<VideoProject>) {
    val array = JSONArray()
    projects.forEach { project ->
        array.put(JSONObject().apply {
            put("id", project.id); put("title", project.title); put("type", project.type)
            put("mode", project.mode); put("createdAt", project.createdAt)
        })
    }
    context.getSharedPreferences("ai_video_studio", Context.MODE_PRIVATE)
        .edit().putString("projects", array.toString()).apply()
}
