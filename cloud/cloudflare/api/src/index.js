import {
  runResearchAndScriptProvider,
  runImageGeneratorProvider,
  SUPPORTED_PROVIDERS
} from "./providers.js";

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
        version: "0.5.0-visual-ready",
        configuredProviders,
        imageProviderReady: Boolean(env.AI || env.HF_TOKEN)
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
    } else if (url.pathname === "/v1/generate-storyboard" && request.method === "POST") {
      try {
        const body = await request.json();
        const topic = (body?.topic || "").toString().trim();
        const script = body?.script || {};
        const opening = (script.opening || "").toString().trim();
        const bodyText = (script.body || "").toString().trim();
        const closing = (script.closing || "").toString().trim();
        if (!topic || !(opening || bodyText || closing)) {
          response = Response.json(
            { ok: false, error: "Topik dan naskah (opening/body/closing) wajib diisi." },
            { status: 400 }
          );
        } else {
          const result = await runResearchAndScriptProvider(env, {
            task: "storyboard",
            topic,
            videoType: body?.videoType || "Video",
            language: "Indonesia",
            writingStyle: body?.visualStyle || "Fotorealistik & Sinematik",
            targetAudience: body?.targetAudience || "Umum",
            targetDurationSec: Number(body?.targetDurationSec) || 60,
            script: { opening, body: bodyText, closing },
            currentDraft: null
          });
          const scenes = Array.isArray(result.parsed?.scenes) ? result.parsed.scenes : [];
          const validScenes = scenes.filter((scene) =>
            scene && Number(scene.sceneNumber) >= 1 && Number(scene.sceneNumber) <= 4 &&
            String(scene.visualPrompt || "").trim()
          ).slice(0, 4);
          response = Response.json({
            ok: true,
            providerUsed: result.providerUsed,
            hasLiveStoryboard: validScenes.length === 4,
            scenes: validScenes
          });
        }
      } catch (err) {
        response = Response.json(
          { ok: false, error: err?.message || "Gagal menyusun storyboard dari naskah." },
          { status: 500 }
        );
      }
    } else if (url.pathname === "/v1/generate-image" && request.method === "POST") {
      try {
        const body = await request.json();
        const visualPrompt = (body?.visualPrompt || "").toString().trim();
        if (!visualPrompt) {
          response = Response.json(
            { ok: false, error: "Field 'visualPrompt' wajib diisi." },
            { status: 400 }
          );
        } else {
          const imgRes = await runImageGeneratorProvider(env, {
            visualPrompt,
            sceneTitle: body?.sceneTitle || "",
            sceneSummary: body?.sceneSummary || "",
            visualStyle: body?.visualStyle || "Fotorealistik & Sinematik",
            negativePrompt: body?.negativePrompt || "",
            aspectRatio: body?.aspectRatio || "9:16"
          });
          response = Response.json({
            ok: true,
            providerUsed: imgRes.providerUsed,
            hasLiveImage: imgRes.hasLiveImage,
            mimeType: imgRes.mimeType,
            imageBase64: imgRes.imageBase64
          });
        }
      } catch (err) {
        response = Response.json(
          { ok: false, error: err?.message || "Gagal menghasilkan gambar AI." },
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
