/**
 * Turns anything a failed request can throw into one short sentence for a person.
 * Never returns raw server text for 5xx/unknown failures, stack traces, SQL or internal paths.
 */
interface HttpLikeError {
  response?: { status?: number; data?: unknown };
  code?: string;
  message?: string;
}

const MAX_SERVER_MESSAGE = 220;

/** Server messages we wrote ourselves are safe to show; anything that looks technical is not. */
function looksTechnical(text: string): boolean {
  return /exception|stack ?trace|\bat [\w.$]+\(|sql|jdbc|hibernate|org\.|com\.[a-z]+\.|null ?pointer|<html|<!doctype|\bbean\b|constraint/i.test(text);
}

function serverMessage(data: unknown): string | null {
  let text: string | null = null;
  if (typeof data === "string") text = data;
  else if (data && typeof data === "object") {
    const d = data as { message?: unknown; error?: unknown };
    if (typeof d.message === "string") text = d.message;
    else if (typeof d.error === "string") text = d.error;
  }
  if (!text) return null;
  text = text.trim();
  if (!text || text.length > MAX_SERVER_MESSAGE || looksTechnical(text)) return null;
  return text;
}

export function errorMessage(e: unknown, fallback = "Something went wrong. Please try again."): string {
  const err = e as HttpLikeError;
  const status = err?.response?.status;
  const fromServer = serverMessage(err?.response?.data);

  if (!err?.response) {
    if (err?.code === "ECONNABORTED") return "The request took too long. Please try again.";
    if (err?.message === "Network Error" || err?.code === "ERR_NETWORK") return "Can't reach the server. Check your connection and try again.";
    return fallback;
  }
  if (status === 401) return "Your session has expired. Please sign in again.";
  if (status === 403) return fromServer ?? "You don't have permission to do that.";
  if (status === 404) return fromServer ?? "We couldn't find what you were looking for.";
  if (status === 413) return "That file is too large.";
  if (status === 429) return fromServer ?? "Too many requests. Please wait a moment and try again.";
  if (status && status >= 500) return fromServer && status !== 500 ? fromServer : fallback;
  return fromServer ?? fallback;
}

/** Field-level messages from a 400 response (`fieldErrors`), keyed by field name. */
export function fieldErrors(e: unknown): Record<string, string> {
  const data = (e as HttpLikeError)?.response?.data as { fieldErrors?: unknown } | undefined;
  const raw = data?.fieldErrors;
  if (!raw || typeof raw !== "object") return {};
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(raw as Record<string, unknown>)) if (typeof v === "string") out[k] = v;
  return out;
}
