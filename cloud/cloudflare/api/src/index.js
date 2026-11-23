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
    } else if (url.pathname === "/v1/generate-video" && request.method === "POST") {
      try {
        const form = await request.formData();
        const image = form.get("image");
        const paramsRaw = form.get("params");
        if (!(image instanceof File)) {
          response = Response.json({ ok: false, error: "File gambar adegan wajib dikirim." }, { status: 400 });
        } else if (image.size > 8 * 1024 * 1024) {
          response = Response.json({ ok: false, error: "Ukuran gambar maksimal 8 MB." }, { status: 413 });
        } else {
          let params = {};
          try { params = typeof paramsRaw === "string" ? JSON.parse(paramsRaw) : {}; } catch (_) {}
          const spaceBase = (env.HF_VIDEO_SPACE_BASE || "https://cbensimon-wan2-2-fp8da-aoti-preview2.hf.space").replace(/\/$/, "");
          const fnName = env.HF_VIDEO_FN_NAME || "generate_video";
          const uploaded = await uploadWanImage(spaceBase, image);
          const eventId = await submitWanVideo(spaceBase, fnName, uploaded, image.name || "scene.png", params);
          const upstream = await fetch(`${spaceBase}/gradio_api/call/${fnName}/${eventId}`, {
            headers: { Accept: "text/event-stream" }
          });
          if (!upstream.ok || !upstream.body) {
            throw new Error(`Tidak dapat membuka antrean hasil video (HTTP ${upstream.status}).`);
          }
          response = new Response(upstream.body, {
            status: 200,
            headers: {
              "Content-Type": "text/event-stream; charset=utf-8",
              "Cache-Control": "no-cache, no-transform",
              "X-Accel-Buffering": "no"
            }
          });
        }
      } catch (err) {
        response = Response.json(
          { ok: false, error: err?.message || "Gagal mengirim adegan ke Wan 2.2." },
          { status: 502 }
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


async function uploadWanImage(spaceBase, image) {
  const fd = new FormData();
  fd.append("files", image, image.name || "scene.png");
  const res = await fetch(`${spaceBase}/gradio_api/upload`, { method: "POST", body: fd });
  if (!res.ok) throw new Error(`Unggah gambar ke GPU gagal (HTTP ${res.status}). Space mungkin sedang tidur atau antrean penuh.`);
  const paths = await res.json();
  if (!Array.isArray(paths) || !paths[0]) throw new Error("Space tidak mengembalikan lokasi gambar yang valid.");
  return paths[0];
}

async function submitWanVideo(spaceBase, fnName, imagePath, fileName, params) {
  const image = {
    path: imagePath,
    url: `${spaceBase}/gradio_api/file=${imagePath}`,
    orig_name: fileName,
    size: null,
    mime_type: null,
    meta: { _type: "gradio.FileData" }
  };
  const clamp = (n, min, max) => Math.max(min, Math.min(max, Number(n) || min));
  const data = [
    image,
    null,
    String(params.prompt || "Animate this cinematic 3D cartoon scene with natural character movement and a subtle camera push. Preserve the character identity and composition."),
    Math.round(clamp(params.steps, 1, 12)),
    String(params.negativePrompt || "static image, frozen motion, flicker, blurry, low quality, deformed face, extra fingers, extra limbs, text, subtitles, watermark"),
    clamp(params.durationSeconds, 0.5, 4.5),
    1,
    1,
    42,
    true,
    6,
    "UniPCMultistep",
    3.0,
    16,
    false,
    true
  ];
  const res = await fetch(`${spaceBase}/gradio_api/call/${fnName}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ data })
  });
  if (!res.ok) throw new Error(`Space menolak job video (HTTP ${res.status}).`);
  const body = await res.json();
  if (!body.event_id) throw new Error("Space tidak mengembalikan ID antrean video.");
  return body.event_id;
}
