export const SUPPORTED_PROVIDERS = ["cloudflare-workers-ai"];

const RESEARCH_SCRIPT_SYSTEM_PROMPT = `
Anda adalah peneliti dan penulis naskah video profesional berbahasa Indonesia. Prioritas tertinggi: pahami IDENTITAS TOPIK secara benar sebelum menulis. Jangan pernah mengubah topik menjadi jenis entitas lain.

LANGKAH WAJIB SEBELUM MENULIS (lakukan secara internal):
1. Baca teks topic persis seperti diberikan. Tentukan entitas inti dan klasifikasikan sebagai salah satu: TOKOH/ORANG, TEMPAT, BENDA/PRODUK, HEWAN, PERISTIWA, SEJARAH, FENOMENA, KONSEP, atau LAINNYA.
2. Kunci identitas tersebut sepanjang seluruh output. Jika topik menyebut nama seseorang atau frasa seperti "sosok", "tokoh", "biografi", "profil", atau "perjalanan hidup", perlakukan sebagai manusia/tokoh. Naskah harus membahas identitas, latar kehidupan, perjalanan, tindakan, kontribusi, karya, atau pengaruh orang itu sesuai fakta yang dapat dipastikan. DILARANG mendeskripsikannya seperti benda, produk, alat, material, spesifikasi, fitur, cara kerja, atau proses pembuatan.
3. Jika topik adalah benda/produk, bahas fungsi, bentuk, bahan, sejarah, atau cara kerja hanya jika relevan. Jangan mengubah orang menjadi benda atau sebaliknya.
4. Jika nama topik ambigu (bisa merujuk ke orang dan benda/hal lain), jangan menebak. Nyatakan ambiguitas itu secara jelas di summary dan factChecks, lalu buat naskah netral yang tidak mengarang identitas. Jangan memilih makna yang tidak didukung konteks.
5. Pastikan setiap bagian (summary, keyPoints, factChecks, opening, body, closing) tetap membahas entitas dan kategori topik yang sama. Sebut nama/topik secara eksplisit di awal naskah; jangan menggantinya dengan kata generik seperti "barang ini", "produk tersebut", atau "objek ini" kecuali memang topiknya benda.

ATURAN KUALITAS RISET:
6. Jangan mengarang fakta biografis, tanggal, tempat lahir, jabatan, kutipan, statistik, hubungan keluarga, karya, atau peristiwa. Bedakan fakta mapan, interpretasi, dan hal yang perlu diverifikasi.
7. Jika informasi tentang topik tidak cukup atau tidak yakin, katakan secara terbuka. Jangan mengisi kekosongan dengan deskripsi generik atau fakta rekaan.
8. Referensi harus relevan dengan entitas dan kategori topik. Jangan membuat URL palsu atau menyebut sumber seolah sudah diperiksa jika belum.
9. Naskah harus benar-benar substantif dan spesifik terhadap topik, bukan template yang hanya mengulang judul. Isi harus sesuai format video, gaya penulisan, audiens, dan durasi yang diminta.
10. Lakukan pemeriksaan akhir: apakah naskah ini benar-benar tentang entitas yang diminta? Apakah ada kalimat yang salah menganggap tokoh sebagai barang atau barang sebagai tokoh? Jika ya, perbaiki sebelum mengembalikan hasil.


PENGUATAN KEDALAMAN NASKAH (WAJIB, BUKAN OPSIONAL):
11. Jangan menulis naskah seperti pengumuman, kata sambutan, esai formal, atau rangkaian kalimat aman yang bisa dipakai untuk topik apa pun. Gunakan bahasa lisan Indonesia yang hidup, mengalir, spesifik, dan enak didengar saat dinarasikan.
12. Sebelum menulis, susun peta isi khusus topik: (a) siapa/apa dan konteksnya, (b) fakta atau detail paling penting, (c) kronologi atau mekanisme/penjelasan yang relevan, (d) contoh/peristiwa/karya/bukti konkret, (e) dampak atau arti penting, dan (f) satu hal menarik yang membuat penonton ingin terus menyimak. Pilih hanya unsur yang benar-benar relevan dengan kategori topik.
13. BODY harus memuat sekurang-kurangnya 4 sampai 7 informasi konkret yang berbeda untuk video 60–90 detik, bukan mengulang satu gagasan dengan sinonim. Untuk durasi lain, sesuaikan kepadatan dan panjang naskah secara proporsional. Sebut nama, tempat, periode, angka, karya, kejadian, ciri, atau mekanisme spesifik hanya jika dapat dipertanggungjawabkan.
14. Untuk TOKOH/ORANG: prioritaskan identitas, asal-usul bila diketahui, perjalanan hidup/kronologi, keputusan atau tindakan penting, karya/kontribusi, tantangan, serta pengaruhnya. Jangan membuat biografi generik. Untuk PERISTIWA/SEJARAH: jelaskan kapan dan di mana, latar penyebab, urutan kejadian, pihak terkait, akibat, dan konteks sejarah. Untuk TEMPAT: jelaskan lokasi, ciri khas, asal-usul, fungsi, dan fakta yang membedakannya. Untuk BENDA/PRODUK/HEWAN/FENOMENA/KONSEP: jelaskan ciri atau mekanisme yang benar, contoh nyata, konteks, kegunaan/dampak, serta batasan atau hal yang sering disalahpahami.
15. OPENING harus berupa hook yang spesifik: fakta mengejutkan yang terverifikasi, pertanyaan tajam, konflik, atau situasi khas topik. Jangan membuka dengan "Pada kesempatan kali ini", "Mari kita mengenal", "Dalam video ini kita akan membahas", atau basa-basi sejenis.
16. BODY harus terasa seperti penceritaan dokumenter: bangun konteks, lanjutkan detail/fakta, jelaskan hubungan sebab-akibat, lalu tunjukkan arti pentingnya. Gunakan transisi alami. Hindari daftar poin yang dibacakan kaku, repetisi, kalimat kosong, dan klaim bombastis tanpa dasar.
17. CLOSING harus memberi kesimpulan atau perspektif yang lahir dari fakta yang dibahas, bukan penutup generik seperti "Demikian pembahasan kali ini". Jangan mengulang opening.
18. Perkiraan panjang: gunakan target durasi sebagai batas nyata. Untuk narasi bahasa Indonesia, sasarkan sekitar 125–150 kata per menit, kecuali gaya/durasi format mengharuskan lebih lambat. Jangan mengisi durasi dengan pengulangan.
19. Fakta dan referensi: konteks riset dari Wikipedia bahasa Indonesia dan/atau Inggris dapat disertakan pada input sebagai retrievedResearchSources. Gunakan hanya sumber yang benar-benar disertakan di sana; cantumkan judul dan URL sumber tersebut pada references. Jangan menganggap Wikipedia sebagai bukti final untuk klaim kontroversial atau sensitif. Jika pencarian tidak menemukan sumber yang cocok, jangan mengaku telah memverifikasi fakta dan tandai klaim penting sebagai "Perlu Verifikasi". Jangan pernah membuat sumber atau URL palsu.
20. Jika topik terlalu luas, pilih sudut bahasan paling kuat dan nyatakan fokusnya dalam summary. Jika topik berupa sosok bernama yang ambigu, jangan mengarang. Jika fakta spesifik belum cukup, hasilkan naskah yang jujur dan terfokus, bukan paragraf formal yang hambar.
21. Lakukan tes anti-template: hapus nama topik dari naskah. Jika naskah masih bisa cocok untuk puluhan topik lain, tulis ulang dengan detail yang benar-benar khas topik ini.
22. Buat alur kalimat yang nyaman diucapkan: variasikan panjang kalimat, gunakan tanda baca untuk jeda alami, hindari kalimat bertingkat terlalu panjang, singkatan yang membingungkan, dan angka/tanggal yang sulit dilafalkan. Jangan menambahkan label "Opening/Body/Closing" ke dalam isi narasi.

KELUARAN:
Kembalikan HANYA JSON valid dengan struktur:
{
  "topicType": "TOKOH/ORANG | TEMPAT | BENDA/PRODUK | HEWAN | PERISTIWA | SEJARAH | FENOMENA | KONSEP | LAINNYA",
  "summary": "ringkasan yang menyebut entitas/topik secara spesifik",
  "keyPoints": ["poin spesifik dan relevan"],
  "factChecks": [{"claim": "klaim", "status": "Perlu Verifikasi | Umum Dikenal | Catatan", "note": "catatan"}],
  "references": [{"title": "judul sumber", "url": "URL sumber yang benar-benar disertakan", "sourceType": "jenis sumber", "note": "relevansi sumber"}],
  "script": {
    "opening": "hook yang langsung menyebut dan memperkenalkan topik sesuai kategori yang benar",
    "body": "isi substantif, runtut, spesifik, dan konsisten dengan identitas topik",
    "closing": "penutup yang tetap membahas topik yang sama",
    "writingStyle": "string",
    "revisionNote": "string",
    "estimatedDurationSec": 60
  }
}
`.trim();

const STORYBOARD_SYSTEM_PROMPT = `
Anda adalah sutradara visual dan storyboard artist profesional untuk video.
Ubah seluruh naskah menjadi tepat 4 frame storyboard yang berbeda, berurutan, dan setia pada isi naskah.
ATURAN KETEPATAN CERITA:
1. Baca opening, body, dan closing sebagai satu cerita utuh. Pilih empat momen paling penting secara kronologis.
2. Setiap sceneSummary harus menyebut tindakan konkret yang benar-benar dinyatakan atau tersirat langsung dalam naskah. Jangan mengganti tindakan dengan gambaran umum tentang topik.
3. Jangan mengarang tokoh, jumlah orang, usia, pakaian, lokasi, periode, benda, kejadian, emosi, atau hasil yang tidak didukung naskah. Jika detail tidak disebut, gunakan deskripsi netral dan jangan mengunci detail palsu.
4. Jangan menggabungkan beberapa momen dalam satu gambar. Satu adegan = satu momen, satu lokasi, satu aksi utama, satu frame.
5. Keempat adegan wajib berbeda dan menunjukkan perkembangan cerita, bukan empat variasi sudut kamera dari objek yang sama.
ATURAN PROMPT GAMBAR:
6. visualPrompt wajib bahasa Inggris, konkret, dan langsung menyebut subjek utama serta kata kerja/aksi yang sedang berlangsung pada kalimat pertama.
7. Setiap visualPrompt harus menjelaskan: siapa/apa yang terlihat, aksi persisnya, lokasi dan objek penting yang memang ada dalam naskah, latar waktu bila disebut, komposisi kamera, jarak kamera, pencahayaan, dan detail visual yang relevan. Jangan memakai kalimat generik seperti "cinematic scene about the topic".
8. KUNCI KONSISTENSI KARAKTER: jika tokoh yang sama muncul di lebih dari satu adegan, tetapkan satu deskripsi identitas visual yang stabil berdasarkan detail naskah (wajah, rambut, warna kulit, bentuk tubuh, pakaian, dan aksesori yang disebut). Ulangi deskripsi identitas yang sama secara konsisten di setiap visualPrompt tokoh tersebut; jangan mengubah wajah, usia tampak, pakaian, proporsi, atau aksesori antaradegan.
9. Jika naskah tidak menyebut detail penampilan tokoh fiksi, pilih desain kartun netral yang sederhana satu kali lalu gunakan persis sama di semua adegan. Jangan lakukan ini untuk tokoh sejarah atau orang nyata: detail mereka harus tetap berbasis informasi yang dapat dipertanggungjawabkan.
10. Jika tidak ada tokoh, jaga konsistensi desain benda, kendaraan, bangunan, dan lingkungan yang berulang.
11. Terapkan gaya visual yang diminta pengguna secara konsisten, termasuk cinematic 3D cartoon bila diminta.
12. Buat satu frame tunggal, bukan kolase, panel komik, poster, montase, atau beberapa kejadian sekaligus. Tanpa teks, subtitle, logo, watermark, atau tulisan terbaca.
13. negativePrompt berisi artefak visual yang harus dihindari, bukan instruksi cerita baru.
14. Kembalikan HANYA JSON valid, tepat empat objek, dengan bentuk:
{"scenes":[{"sceneNumber":1,"sceneTitle":"...","sceneSummary":"...","visualPrompt":"...","negativePrompt":"..."}]}
`.trim();

async function fetchWikipediaResearch(topic) {
  const cleanTopic = String(topic || "").trim();
  if (!cleanTopic) return [];
  const languages = ["id", "en"];
  const results = await Promise.all(languages.map(async (lang) => {
    try {
      const searchUrl = new URL(`https://${lang}.wikipedia.org/w/api.php`);
      searchUrl.search = new URLSearchParams({
        action: "query",
        list: "search",
        srsearch: cleanTopic,
        srlimit: "4",
        format: "json",
        utf8: "1"
      }).toString();
      const searchRes = await fetch(searchUrl.toString(), {
        headers: { "User-Agent": "AI-Video-Studio/0.4 (open educational video research)" }
      });
      if (!searchRes.ok) return [];
      const searchData = await searchRes.json();
      const hits = searchData?.query?.search || [];
      const pageIds = hits.map((hit) => hit.pageid).filter(Boolean);
      if (!pageIds.length) return [];
      const detailUrl = new URL(`https://${lang}.wikipedia.org/w/api.php`);
      detailUrl.search = new URLSearchParams({
        action: "query",
        prop: "extracts",
        explaintext: "1",
        exchars: "5000",
        pageids: pageIds.join("|"),
        format: "json"
      }).toString();
      const detailRes = await fetch(detailUrl.toString(), {
        headers: { "User-Agent": "AI-Video-Studio/0.4 (open educational video research)" }
      });
      if (!detailRes.ok) return [];
      const detailData = await detailRes.json();
      const pages = detailData?.query?.pages || {};
      return hits.map((hit) => {
        const page = pages[String(hit.pageid)] || {};
        const extract = String(page.extract || "").replace(/\s+/g, " ").trim();
        return {
          language: lang,
          title: hit.title,
          url: `https://${lang}.wikipedia.org/wiki/${encodeURIComponent(String(hit.title).replace(/ /g, "_"))}`,
          extract: extract.slice(0, 2600)
        };
      }).filter((item) => item.extract.length > 180);
    } catch (_) {
      return [];
    }
  }));
  const seen = new Set();
  return results.flat().filter((item) => {
    const key = item.title.toLowerCase();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  }).slice(0, 8);
}

function buildUserPrompt(payload, researchContext = []) {
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
    currentDraft: payload.currentDraft || null,
    retrievedResearchSources: researchContext
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

function validateResearchOutput(parsed, payload, researchContext) {
  const issues = [];
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    return { ok: false, issues: ["Jawaban AI bukan objek JSON yang valid."] };
  }
  const topic = String(payload.topic || "").trim();
  const script = parsed.script && typeof parsed.script === "object" ? parsed.script : {};
  const opening = String(script.opening || "").trim();
  const body = String(script.body || "").trim();
  const closing = String(script.closing || "").trim();
  const summary = String(parsed.summary || "").trim();
  const keyPoints = Array.isArray(parsed.keyPoints) ? parsed.keyPoints.filter(v => typeof v === "string" && v.trim()) : [];
  const factChecks = Array.isArray(parsed.factChecks) ? parsed.factChecks.filter(v => v && typeof v.claim === "string" && v.claim.trim()) : [];
  const references = Array.isArray(parsed.references) ? parsed.references : [];
  const wordCount = body.split(/\\s+/).filter(Boolean).length;
  const targetWords = Math.max(45, Math.round((Number(payload.targetDurationSec) || 60) / 60 * 95));

  if (!String(parsed.topicType || "").trim()) issues.push("Jenis topik tidak ditentukan.");
  if (summary.length < 45) issues.push("Ringkasan riset terlalu pendek atau kosong.");
  if (keyPoints.length < 4) issues.push("Poin riset kurang dari empat fakta/poin khusus.");
  if (factChecks.length < 2) issues.push("Pemeriksaan fakta kurang dari dua klaim.");
  if (opening.length < 25) issues.push("Pembukaan naskah kosong atau terlalu pendek.");
  if (wordCount < targetWords) issues.push(`Isi naskah terlalu pendek: ${wordCount} kata, minimal sekitar ${targetWords} kata untuk durasi yang diminta.`);
  if (closing.length < 20) issues.push("Penutup naskah kosong atau terlalu pendek.");

  const topicTokens = topic.toLowerCase().normalize("NFKD")
    .replace(/[^a-z0-9\\s]/g, " ").split(/\\s+/)
    .filter(token => token.length >= 3 && !["tentang", "sejarah", "sosok", "tokoh", "profil", "biografi", "video"].includes(token));
  const combined = [summary, ...keyPoints, opening, body, closing].join(" ").toLowerCase();
  if (topicTokens.length && !topicTokens.some(token => combined.includes(token))) {
    issues.push("Isi jawaban tidak menunjukkan keterkaitan yang jelas dengan nama/topik yang diminta.");
  }

  const sourceUrls = new Set((researchContext || []).map(source => String(source.url || "").trim()).filter(Boolean));
  for (const ref of references) {
    const url = String(ref?.url || "").trim();
    if (url && !sourceUrls.has(url)) {
      issues.push("AI mencantumkan referensi yang tidak berasal dari sumber riset yang diberikan.");
      break;
    }
  }
  const genericPhrases = [
    "mari kita telusuri bagian intinya",
    "punya satu titik penting",
    "dalam konteks video",
    "demikian pembahasan kali ini",
    "topik ini sangat menarik"
  ];
  if (genericPhrases.some(phrase => combined.includes(phrase))) {
    issues.push("Naskah masih mengandung kalimat template/generik.");
  }
  return { ok: issues.length === 0, issues };
}

export async function runResearchAndScriptProvider(env, payload) {
  if (!env.AI || typeof env.AI.run !== "function") {
    throw new Error("Cloudflare Workers AI belum tersedia. Periksa binding AI pada Worker.");
  }

  const systemPrompt = payload.task === "storyboard"
    ? STORYBOARD_SYSTEM_PROMPT
    : RESEARCH_SCRIPT_SYSTEM_PROMPT;
  const researchContext = payload.task === "storyboard" ? [] : await fetchWikipediaResearch(payload.topic);
  const userPrompt = buildUserPrompt(payload, researchContext);
  const model = env.CLOUDFLARE_TEXT_MODEL || "@cf/ibm-granite/granite-4.0-h-micro";

  let result;
  try {
    result = await env.AI.run(model, {
      messages: [
        { role: "system", content: systemPrompt },
        { role: "user", content: userPrompt }
      ],
      response_format: { type: "json_object" },
      max_tokens: 3000,
      temperature: 0.35
    });
  } catch (error) {
    throw new Error(`Cloudflare Workers AI error (${model}): ${error?.message || String(error)}`);
  }

  const rawText = typeof result?.response === "string"
    ? result.response
    : typeof result?.result?.response === "string"
      ? result.result.response
      : typeof result?.choices?.[0]?.message?.content === "string"
        ? result.choices[0].message.content
        : typeof result?.result?.choices?.[0]?.message?.content === "string"
          ? result.result.choices[0].message.content
          : typeof result?.output_text === "string"
            ? result.output_text
            : "";
  const parsed = extractJsonObject(rawText);
  if (!parsed) {
    throw new Error("Cloudflare Workers AI tidak mengembalikan JSON yang dapat dibaca.");
  }

  const quality = payload.task === "storyboard"
    ? { ok: true, issues: [] }
    : validateResearchOutput(parsed, payload, researchContext);
  if (!quality.ok) {
    throw new Error(`Hasil Cloudflare Workers AI ditolak pemeriksaan kualitas: ${quality.issues.join(" ")}`);
  }

  return {
    providerUsed: `cloudflare-workers-ai (${model})`,
    rawText,
    parsed
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
  const sceneTitle = (payload.sceneTitle || "").trim();
  const sceneSummary = (payload.sceneSummary || "").trim();
  const visualStyle = (payload.visualStyle || "Animasi 3D Kartun Sinematik").trim();
  const aspectRatio = (payload.aspectRatio || "9:16").trim();
  const negativePrompt = (payload.negativePrompt || "").trim();

  // Put the narrative action first: this is the highest-priority instruction for the image model.
  const promptParts = [
    "Create ONE single storyboard frame. Depict only one exact moment, one main action, and one location. Do not create a collage, montage, comic panels, poster, or a generic illustration about the topic.",
    sceneSummary ? `Exact moment from the script: ${sceneSummary}.` : "",
    sceneTitle ? `Scene: ${sceneTitle}.` : "",
    `Visual details and composition: ${prompt}.`,
    `Visual style: ${visualStyle}.`,
    `Compose for a ${aspectRatio} frame.`,
    "The exact action and objects described in the script take priority over decorative details. Do not invent additional people, events, locations, or important objects.",
    negativePrompt ? `Avoid these visual defects: ${negativePrompt}.` : ""
  ].filter(Boolean);
  const combinedPrompt = promptParts.join(" ").slice(0, 2048);

  if (!prompt) throw new Error("Visual prompt kosong untuk adegan ini.");

  if (env.AI && typeof env.AI.run === "function") {
    const model = env.CLOUDFLARE_IMAGE_MODEL || "@cf/black-forest-labs/flux-1-schnell";
    const result = await env.AI.run(model, {
      prompt: combinedPrompt,
      steps: 8
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
