import { useCallback, useRef, useState } from "react";

/**
 * Runs an async action and refuses to start it again while it is still running,
 * so a double click or Enter-then-click can't submit a form twice.
 *   const [save, saving] = useAsyncAction(async () => { await api.save(form); });
 */
export function useAsyncAction<A extends unknown[], R>(fn: (...args: A) => Promise<R>): [(...args: A) => Promise<R | undefined>, boolean] {
  const [busy, setBusy] = useState(false);
  const running = useRef(false);
  const run = useCallback(async (...args: A) => {
    if (running.current) return undefined;
    running.current = true;
    setBusy(true);
    try { return await fn(...args); }
    finally { running.current = false; setBusy(false); }
  }, [fn]);
  return [run, busy];
}
