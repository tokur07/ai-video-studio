export default {
  async fetch(request) {
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
      response = Response.json({
        ok: true,
        service: "ai-video-studio-api",
        message: "API starter is running"
      });
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
