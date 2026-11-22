export const SUPPORTED_PROVIDERS = ["gemini", "groq", "openrouter"];

const RESEARCH_SCRIPT_SYSTEM_PROMPT = `
Anda adalah asisten riset dan penulis naskah video berbahasa Indonesia.
Ikuti aturan berikut:
1. Pisahkan fakta yang mapan, interpretasi, dan catatan verifikasi/ketidakpastian. Jangan mengarang kutipan atau tanggal palsu.
2. Sertakan daftar sumber referensi yang relevan atau rujukan jenis sumber (arsip, jurnal, lembaga resmi, dokumentasi) secara jujur tanpa mengada-ada URL palsu.
3. Susun naskah video dengan pembukaan (hook), isi per bagian, penutup, dan estimasi durasi sesuai gaya penulisan yang diminta.
4. Kembalikan HANYA JSON valid dengan struktur:
{
  "summary": "string",
  "keyPoints": ["string"],
  "factChecks": [{"claim": "string", "status": "Perlu Verifikasi | Umum Dikenal | Catatan", "note": "string"}],
  "references": [{"title": "string", "sourceType": "string", "note": "string"}],
  "script": {
    "opening": "string",
    "body": "string",
    "closing": "string",
    "writingStyle": "string",
    "revisionNote": "string",
    "estimatedDurationSec": 60
  }
}
`.trim();

const STORYBOARD_SYSTEM_PROMPT = `
Anda adalah sutradara visual dan storyboard artist profesional untuk video.
Tugas: ubah naskah lengkap menjadi tepat 4 adegan visual yang berurutan dan benar-benar menggambarkan kejadian dalam naskah.
Aturan wajib:
1. Baca opening, body, dan closing sebagai satu cerita; jangan hanya mengulang topik.
2. Setiap adegan harus menangkap momen/aksi spesifik yang memang disebut atau secara langsung tersirat dalam naskah.
3. Keempat adegan harus berbeda, tidak boleh empat variasi gambar generik tentang topik yang sama.
4. Jangan menambahkan tokoh, lokasi, peristiwa, benda penting, atau periode sejarah yang tidak didukung naskah.
5. Untuk tiap adegan berikan sceneNumber 1-4, sceneTitle ringkas, sceneSummary yang menjelaskan bagian naskah yang divisualkan, visualPrompt sangat konkret dalam bahasa Inggris agar model gambar memahami subjek, aksi, latar, periode, komposisi dan pencahayaan, serta negativePrompt.
6. Jaga konsistensi tokoh utama, pakaian, lokasi, periode waktu, dan gaya visual di semua adegan bila relevan.
7. Jangan memasukkan tulisan, subtitle, logo, watermark, atau teks dalam gambar.
8. Kembalikan HANYA JSON valid dengan bentuk:
{"scenes":[{"sceneNumber":1,"sceneTitle":"...","sceneSummary":"...","visualPrompt":"...","negativePrompt":"..."}]}
`.trim();

function buildUserPrompt(payload) {
  return JSON.stringify({
    task: payload.task || "research_script",
    script: payload.script || null,
    topic: payload.topic,
    videoType: payload.videoType,
    language: payload.language || "Indonesia",
    targetDurationSec: payload.targetDurationSec || 60,
    writingStyle: payload.writingStyle || "Informatif & Jelas",
    targetAudience: payload.targetAudience || "Umum",
    revisionInstruction: payload.revisionInstruction || "",
    currentDraft: payload.currentDraft || null
  });
}

function extractJsonObject(rawText) {
  if (!rawText || typeof rawText !== "string") return null;
  const trimmed = rawText.trim();
  try {
    return JSON.parse(trimmed);
  } catch (_) {
    const start = trimmed.indexOf("{");
    const end = trimmed.lastIndexOf("}");
    if (start !== -1 && end !== -1 && end > start) {
      try {
        return JSON.parse(trimmed.slice(start, end + 1));
      } catch (_) {
        return null;
      }
    }
    return null;
  }
}

export async function runResearchAndScriptProvider(env, payload) {
  const systemPrompt = payload.task === "storyboard"
    ? STORYBOARD_SYSTEM_PROMPT
    : RESEARCH_SCRIPT_SYSTEM_PROMPT;
  const requested = (payload.provider || env.DEFAULT_AI_PROVIDER || "gemini").toLowerCase();

  if (requested === "gemini" && env.GEMINI_API_KEY) {
    const model = env.GEMINI_MODEL || "gemini-3.8-flash";
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(env.GEMINI_API_KEY)}`;
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: systemPrompt }] },
        contents: [{ role: "user", parts: [{ text: buildUserPrompt(payload) }] }],
        generationConfig: { responseMimeType: "application/json", temperature: 0.4 }
      })
    });
    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Gemini API error (${res.status}): ${errText.slice(0, 240)}`);
    }
    const data = await res.json();
    const text = data?.candidates?.[0]?.content?.parts?.[0]?.text || "";
    const parsed = extractJsonObject(text);
    return { providerUsed: `gemini (${model})`, rawText: text, parsed };
  }

  if (requested === "groq" && env.GROQ_API_KEY) {
    const model = env.GROQ_MODEL || "llama-3.3-70b-versatile";
    const res = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${env.GROQ_API_KEY}`
      },
      body: JSON.stringify({
        model,
        temperature: 0.4,
        response_format: { type: "json_object" },
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: buildUserPrompt(payload) }
        ]
      })
    });
    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Groq API error (${res.status}): ${errText.slice(0, 240)}`);
    }
    const data = await res.json();
    const text = data?.choices?.[0]?.message?.content || "";
    const parsed = extractJsonObject(text);
    return { providerUsed: `groq (${model})`, rawText: text, parsed };
  }

  if (requested === "openrouter" && env.OPENROUTER_API_KEY) {
    const model = env.OPENROUTER_MODEL || "meta-llama/llama-3.3-70b-instruct:free";
    const res = await fetch("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${env.OPENROUTER_API_KEY}`,
        "HTTP-Referer": "https://github.com/tokur07/ai-video-studio",
        "X-Title": "AI Video Studio"
      },
      body: JSON.stringify({
        model,
        temperature: 0.4,
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: buildUserPrompt(payload) }
        ]
      })
    });
    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`OpenRouter API error (${res.status}): ${errText.slice(0, 240)}`);
    }
    const data = await res.json();
    const text = data?.choices?.[0]?.message?.content || "";
    const parsed = extractJsonObject(text);
    return { providerUsed: `openrouter (${model})`, rawText: text, parsed };
  }

  return {
    providerUsed: "none (server secret not configured)",
    rawText: "",
    parsed: null
  };
}

function arrayBufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  const chunkSize = 0x8000;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunkSize));
  }
  return btoa(binary);
}

export async function runImageGeneratorProvider(env, payload) {
  const prompt = (payload.visualPrompt || "").trim();
  const negativePrompt = (payload.negativePrompt || "").trim();
  const combinedPrompt = negativePrompt
    ? `${prompt}. Avoid: ${negativePrompt}`
    : prompt;

  if (env.AI && typeof env.AI.run === "function") {
    const model = env.CLOUDFLARE_IMAGE_MODEL || "@cf/black-forest-labs/flux-1-schnell";
    const result = await env.AI.run(model, {
      prompt: combinedPrompt,
      steps: 4
    });

    if (result && typeof result.image === "string" && result.image.length > 0) {
      return {
        providerUsed: `cloudflare-ai (${model})`,
        hasLiveImage: true,
        mimeType: "image/jpeg",
        imageBase64: result.image
      };
    }

    if (result instanceof ReadableStream || result instanceof ArrayBuffer) {
      const buf = result instanceof ArrayBuffer ? result : await new Response(result).arrayBuffer();
      return {
        providerUsed: `cloudflare-ai (${model})`,
        hasLiveImage: true,
        mimeType: "image/png",
        imageBase64: arrayBufferToBase64(buf)
      };
    }
  }

  if (env.HF_TOKEN) {
    const model = env.HF_IMAGE_MODEL || "black-forest-labs/FLUX.1-schnell";
    const res = await fetch(`https://api-inference.huggingface.co/models/${encodeURIComponent(model)}`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${env.HF_TOKEN}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ inputs: combinedPrompt })
    });
    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`HuggingFace image error (${res.status}): ${errText.slice(0, 200)}`);
    }
    const buf = await res.arrayBuffer();
    return {
      providerUsed: `huggingface (${model})`,
      hasLiveImage: true,
      mimeType: "image/png",
      imageBase64: arrayBufferToBase64(buf)
    };
  }

  return {
    providerUsed: "none (image provider binding/secret not configured)",
    hasLiveImage: false,
    mimeType: "",
    imageBase64: ""
  };
}
