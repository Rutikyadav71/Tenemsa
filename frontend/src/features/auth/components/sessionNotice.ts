const KEY = "tenemsa-session-expired";

/** Set by the axios layer just before it sends a signed-out user back to a sign-in page. */
export function flagSessionExpired() {
  try { sessionStorage.setItem(KEY, "1"); } catch { /* storage unavailable: the notice is optional */ }
}

/** Returns true once, then clears the flag so a refresh doesn't repeat the notice. */
export function takeSessionExpired(): boolean {
  try {
    const v = sessionStorage.getItem(KEY) === "1";
    sessionStorage.removeItem(KEY);
    return v;
  } catch { return false; }
}
