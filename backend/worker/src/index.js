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

function normalizeRecentSubmissions(value) {
  if (!Array.isArray(value)) return [];
  return value
    .slice(0, 10)
    .map((entry) => ({
      type: String(entry?.type || "").trim() || "unknown",
      text: String(entry?.text || "").trim(),
      verdict: String(entry?.verdict || "").trim() || "unknown",
      wakeStatus: String(entry?.wakeStatus || "").trim() || "unknown",
      createdAt: Number(entry?.createdAt || 0)
    }));
}

function normalizeTextForComparison(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function tokenize(text) {
  return normalizeTextForComparison(text)
    .split(" ")
    .map((token) => token.trim())
    .filter((token) => token.length > 1);
}

function uniqueTokens(tokens) {
  return [...new Set(tokens)];
}

function jaccardSimilarity(leftTokens, rightTokens) {
  if (!leftTokens.length || !rightTokens.length) return 0;
  const left = new Set(leftTokens);
  const right = new Set(rightTokens);
  let intersection = 0;
  for (const token of left) {
    if (right.has(token)) intersection += 1;
  }
  const union = new Set([...left, ...right]).size;
  return union === 0 ? 0 : intersection / union;
}

function isMinorVariant(currentNormalized, previousNormalized) {
  if (!currentNormalized || !previousNormalized) return false;
  if (currentNormalized === previousNormalized) return true;
  if (currentNormalized.includes(previousNormalized) || previousNormalized.includes(currentNormalized)) {
    return true;
  }
  const lengthGap = Math.abs(currentNormalized.length - previousNormalized.length);
  return lengthGap <= 10;
}

function detectRepetitiveResponse(text, recentSubmissions) {
  const normalizedCurrent = normalizeTextForComparison(text);
  if (!normalizedCurrent) return null;

  const currentTokens = uniqueTokens(tokenize(normalizedCurrent));
  const textHistory = recentSubmissions
    .filter((submission) => submission.type === "text" || submission.text)
    .map((submission) => ({
      normalized: normalizeTextForComparison(submission.text),
      tokens: uniqueTokens(tokenize(submission.text))
    }))
    .filter((submission) => submission.normalized);

  if (!textHistory.length) return null;

  let identicalCount = 0;
  let highSimilarityCount = 0;
  let minorVariantCount = 0;

  for (const previous of textHistory) {
    if (previous.normalized === normalizedCurrent) {
      identicalCount += 1;
      continue;
    }

    const similarity = jaccardSimilarity(currentTokens, previous.tokens);
    if (similarity >= 0.82) {
      highSimilarityCount += 1;
    }
    if (similarity >= 0.66 && isMinorVariant(normalizedCurrent, previous.normalized)) {
      minorVariantCount += 1;
    }
  }

  const wordCount = currentTokens.length;
  const looksThin = wordCount < 6 || normalizedCurrent.length < 32;

  if (identicalCount >= 1) {
    return "This response is too similar to one of your recent answers. Write a more detailed response with new specifics about what you are doing right now.";
  }
  if (highSimilarityCount >= 2) {
    return "Your recent answers are too repetitive. Write a more in-depth response with concrete new details instead of reusing the same template.";
  }
  if (minorVariantCount >= 2 && looksThin) {
    return "This answer still looks like a repeated short template. Give a more detailed, specific response about your plan or what you are doing right now.";
  }

  return null;
}

function buildPrompt({ text, hasImage, recentSubmissions }) {
  const recentSubmissionSummary = recentSubmissions.length
    ? recentSubmissions
        .map((submission, index) => {
          const textSummary = submission.text || "(no stored text)";
          return `${index + 1}. type=${submission.type}; verdict=${submission.verdict}; wakeStatus=${submission.wakeStatus}; text=${textSummary}`;
        })
        .join("\n")
    : "None.";

  return `
You validate whether a wake-up alarm dismissal response is satisfactory.
You may receive text, an image, or both.
Accept only if the submission looks like genuine user effort consistent with waking up and starting the day.
You also receive the user's 10 most recent submissions. Use that history to detect repetition, near-duplicates, stale boilerplate, and patterns suggesting the user is repeatedly typing the same thing just to dismiss the alarm.

For text:
- Accept only if the user provides a clear, specific, plausible affirmation, plan, goal, or to-do list.
- Reject if the text is blank, vague, meaningless, generic, repetitive, obviously unrelated, spammy, or looks copied/generated without personal specifics.
- Compare the current text against the recent submission history.
- Reject if the current response is identical or nearly identical to recent submissions, unless the new response still contains clearly new specific details that meaningfully distinguish it.
- Reject if the user appears to be rotating through the same short template with only tiny wording changes.
- Reject if the text is extremely vague even if it is not an exact duplicate.

For images:
- First check whether the image contains readable handwritten or printed text.
- If the image shows a note, whiteboard, notebook, or paper, read the text from the image and judge that text using the same standard as typed text.
- Accept note photos if the visible text is a genuine plan for the day, affirmation, motivation, or to-do list that suggests the user is up and engaging with the day.
- Accept only if the image appears to be a genuine camera photo that plausibly shows the user is awake or engaged in a real wake-up task.
- Examples that can pass: a fresh selfie, the room/desk/bathroom/kitchen in a natural live photo, getting dressed, brushing teeth, breakfast setup, or an authentic handwritten note with a plan/affirmation for today.
- Reject screenshots, black frames, heavily blurred/obscured images, obvious stock/generated images, memes, random unrelated objects, or note photos whose text is unreadable, empty, generic, or unrelated to starting the day.

If both text and image are present, accept if the overall submission is credible.
Be reasonably strict. Prefer rejecting weak, generic, or suspicious submissions.
If you reject because of repetition, the feedback should explicitly ask for a more detailed response with new specifics.
Respond ONLY as minified JSON with keys passed(boolean) and feedback(string).

User text: ${text || "(none)"}
Image attached: ${hasImage ? "yes" : "no"}
Recent submissions:
${recentSubmissionSummary}
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
    const recentSubmissions = normalizeRecentSubmissions(body?.recentSubmissions);

    if (!text && !imageBase64) {
      return jsonResponse({ passed: false, feedback: "Provide text or an image." }, 400);
    }

    const repetitiveFeedback = detectRepetitiveResponse(text, recentSubmissions);
    if (repetitiveFeedback) {
      return jsonResponse({ passed: false, feedback: repetitiveFeedback }, 200);
    }

    const ai = new GoogleGenAI({ apiKey: env.GEMINI_API_KEY });
    const parts = [{ text: buildPrompt({ text, hasImage: Boolean(imageBase64), recentSubmissions }) }];
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
