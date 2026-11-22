import { runResearchAndScriptProvider, SUPPORTED_PROVIDERS } from "./providers.js";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type,Authorization"
    };

    if (request.method === "OPTIONS") {
      return new Response(null, { headers: cors });
    }

    let response;
    if (url.pathname === "/health" && request.method === "GET") {
      const configuredProviders = SUPPORTED_PROVIDERS.filter((name) => {
        if (name === "gemini") return Boolean(env.GEMINI_API_KEY);
        if (name === "groq") return Boolean(env.GROQ_API_KEY);
        if (name === "openrouter") return Boolean(env.OPENROUTER_API_KEY);
        return false;
      });
      response = Response.json({
        ok: true,
        service: "ai-video-studio-api",
        version: "0.4.0-starter",
        configuredProviders
      });
    } else if (url.pathname === "/v1/research-script" && request.method === "POST") {
      try {
        const body = await request.json();
        const topic = (body?.topic || "").toString().trim();
        if (!topic) {
          response = Response.json(
            { ok: false, error: "Field 'topic' wajib diisi." },
            { status: 400 }
          );
        } else {
          const result = await runResearchAndScriptProvider(env, {
            topic,
            provider: body?.provider,
            videoType: body?.videoType || "Edukasi & Tutorial",
            language: body?.language || "Indonesia",
            targetDurationSec: Number(body?.targetDurationSec) || 60,
            writingStyle: body?.writingStyle || "Informatif & Jelas",
            targetAudience: body?.targetAudience || "Umum",
            revisionInstruction: body?.revisionInstruction || "",
            currentDraft: body?.currentDraft || null
          });

          response = Response.json({
            ok: true,
            providerUsed: result.providerUsed,
            hasLiveAiOutput: Boolean(result.parsed),
            data: result.parsed,
            rawText: result.rawText
          });
        }
      } catch (err) {
        response = Response.json(
          { ok: false, error: err?.message || "Gagal memproses riset dan naskah." },
          { status: 500 }
        );
      }
    } else {
      response = Response.json(
        { ok: false, error: "Route not implemented yet" },
        { status: 404 }
      );
    }

    const headers = new Headers(response.headers);
    Object.entries(cors).forEach(([key, value]) => headers.set(key, value));
    return new Response(response.body, { status: response.status, headers });
  }
};


cd ~/ai-video-studio && \
mkdir -p android/app/src/main/java/com/aivideostudio/app/network && \
cat <<'EOF' > android/app/src/main/java/com/aivideostudio/app/model/ResearchScriptModels.kt
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
