package com.aivideostudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VideoStudioApp()
                }
            }
        }
    }
}

private enum class Tab(val label: String, val icon: String) {
    HOME("Beranda", "⌂"),
    PROJECTS("Proyek", "▤"),
    EDITOR("Editor", "▶"),
    SETTINGS("Setelan", "⚙")
}

@Composable
private fun VideoStudioApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var topic by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Siap membuat proyek baru.") }
    var mode by remember { mutableStateOf("Otomatis") }
    var type by remember { mutableStateOf("Dokumenter") }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.values().forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Text(tab.icon) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("AI VIDEO STUDIO", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text("Studio produksi video berbasis AI",
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            when (Tab.values()[selectedTab]) {
                Tab.HOME -> {
                    Text("Mulai proyek video", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Topik atau ide video") },
                        minLines = 2
                    )
                    Text("Jenis video", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Dokumenter", "Shorts", "Promosi").forEach {
                            SelectButton(it, type == it) { type = it }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Edukasi", "Storytelling").forEach {
                            SelectButton(it, type == it) { type = it }
                        }
                    }
                    Text("Mode produksi", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Otomatis", "Manual").forEach {
                            SelectButton(it, mode == it) { mode = it }
                        }
                    }
                    Button(
                        onClick = {
                            status = if (topic.isBlank()) "Masukkan topik terlebih dahulu."
                            else "Draft '$topic' dibuat. Integrasi AI/cloud menyusul."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Buat Proyek") }
                    Text(status, color = MaterialTheme.colorScheme.primary)
                    Text("Studio produksi", style = MaterialTheme.typography.titleLarge)
                    StudioCard("Documentary Studio", "Dokumenter sejarah, sains, dan biografi")
                    StudioCard("Short Video Studio", "Shorts, Reels, dan video vertikal")
                    StudioCard("Marketing Studio", "Iklan dan promosi produk")
                    StudioCard("Educational Studio", "Pembelajaran dan tutorial")
                    StudioCard("Storytelling Studio", "Cerita pendek dan narasi sinematik")
                }
                Tab.PROJECTS -> Placeholder(
                    "Proyek Saya",
                    "Daftar proyek akan tersimpan di cloud setelah Supabase dikonfigurasi."
                )
                Tab.EDITOR -> Placeholder(
                    "Advanced Editor",
                    "Storyboard, scene manager, timeline, audio, subtitle, dan efek."
                )
                Tab.SETTINGS -> Placeholder(
                    "Pengaturan",
                    "Akun, kualitas video, penyimpanan, dan penyedia AI."
                )
            }
        }
    }
}

@Composable
private fun SelectButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) }
    else OutlinedButton(onClick = onClick) { Text(label) }
}

@Composable
private fun StudioCard(title: String, description: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Placeholder(title: String, description: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(description)
}
