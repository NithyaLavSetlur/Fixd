import { GoogleGenAI } from "@google/genai";
import { createRemoteJWKSet, jwtVerify } from "jose";

const FIREBASE_ISSUER_PREFIX = "https://securetoken.google.com/";

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Headers": "Content-Type, Authorization",
      "Access-Control-Allow-Methods": "POST, OPTIONS"
    }
  });
}

async function verifyFirebaseToken(idToken, projectId) {
  const jwks = createRemoteJWKSet(
    new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com")
  );

  const { payload } = await jwtVerify(idToken, jwks, {
    issuer: `${FIREBASE_ISSUER_PREFIX}${projectId}`,
    audience: projectId
  });

  return payload;
}

function buildPrompt({ text, hasImage }) {
  return `
You validate whether a wake-up alarm dismissal response is satisfactory.
You may receive text, an image, or both.
Accept only if the submission looks like genuine user effort consistent with waking up and starting the day.

For text:
- Accept only if the user provides a clear, specific, plausible affirmation, plan, goal, or to-do list.
- Reject if the text is blank, vague, meaningless, generic, repetitive, obviously unrelated, spammy, or looks copied/generated without personal specifics.

For images:
- Accept only if the image appears to be a genuine camera photo that plausibly shows the user is awake or engaged in a real wake-up task.
- Examples that can pass: a fresh selfie, the room/desk/bathroom/kitchen in a natural live photo, getting dressed, brushing teeth, breakfast setup, an authentic handwritten to-do list.
- Reject screenshots, black frames, heavily blurred/obscured images, obvious stock/generated images, memes, random unrelated objects, or content that does not plausibly indicate the user is awake and responding now.

If both text and image are present, accept if the overall submission is credible.
Be reasonably strict. Prefer rejecting weak, generic, or suspicious submissions.
Respond ONLY as minified JSON with keys passed(boolean) and feedback(string).

User text: ${text || "(none)"}
Image attached: ${hasImage ? "yes" : "no"}
`;
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return jsonResponse({}, 204);
    }

    if (request.method !== "POST") {
      return jsonResponse({ passed: false, feedback: "Method not allowed." }, 405);
    }

    const authHeader = request.headers.get("Authorization") || "";
    if (!authHeader.startsWith("Bearer ")) {
      return jsonResponse({ passed: false, feedback: "Missing auth token." }, 401);
    }

    const projectId = env.FIREBASE_PROJECT_ID;
    if (!projectId) {
      return jsonResponse({ passed: false, feedback: "Firebase project ID is not configured." }, 500);
    }

    try {
      await verifyFirebaseToken(authHeader.replace("Bearer ", ""), projectId);
    } catch {
      return jsonResponse({ passed: false, feedback: "Invalid auth token." }, 401);
    }

    if (!env.GEMINI_API_KEY) {
      return jsonResponse({ passed: false, feedback: "Gemini API key is not configured." }, 500);
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return jsonResponse({ passed: false, feedback: "Invalid JSON payload." }, 400);
    }

    const text = String(body?.text || "").trim();
    const imageBase64 = String(body?.imageBase64 || "").trim();

    if (!text && !imageBase64) {
      return jsonResponse({ passed: false, feedback: "Provide text or an image." }, 400);
    }

    const ai = new GoogleGenAI({ apiKey: env.GEMINI_API_KEY });
    const parts = [{ text: buildPrompt({ text, hasImage: Boolean(imageBase64) }) }];
    if (imageBase64) {
      parts.push({
        inlineData: {
          mimeType: "image/jpeg",
          data: imageBase64
        }
      });
    }

    try {
      const response = await ai.models.generateContent({
        model: "gemini-2.5-flash",
        contents: [{ role: "user", parts }]
      });

      const raw = response.text?.trim() || "{}";
      const cleaned = raw
        .replace(/^```json/, "")
        .replace(/^```/, "")
        .replace(/```$/, "")
        .trim();
      const parsed = JSON.parse(cleaned);
      const feedback = String(parsed.feedback || "").trim();

      return jsonResponse({
        passed: Boolean(parsed.passed),
        feedback: feedback || (parsed.passed ? "Accepted." : "Not accepted.")
      });
    } catch (error) {
      return jsonResponse(
        {
          passed: false,
          feedback: error instanceof Error ? error.message : "Validation failed."
        },
        500
      );
    }
  }
};
