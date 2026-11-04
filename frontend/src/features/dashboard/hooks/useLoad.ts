import { useCallback, useEffect, useRef, useState } from "react";

export interface Loaded<T> { data: T | null; loading: boolean; failed: boolean; reload: () => void }

/** Loads one widget's data on its own, so a slow or failing request never blanks the whole dashboard. */
export function useLoad<T>(fn: () => Promise<T>, enabled = true): Loaded<T> {
  const [state, setState] = useState<{ data: T | null; loading: boolean; failed: boolean }>({ data: null, loading: enabled, failed: false });
  const fnRef = useRef(fn);
  useEffect(() => { fnRef.current = fn; });

  const run = useCallback(() => {
    let cancelled = false;
    setState(s => ({ ...s, loading: true, failed: false }));
    fnRef.current()
      .then(data => { if (!cancelled) setState({ data, loading: false, failed: false }); })
      .catch(() => { if (!cancelled) setState({ data: null, loading: false, failed: true }); });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!enabled) { setState({ data: null, loading: false, failed: false }); return; }
    return run();
  }, [enabled, run]);

  return { ...state, reload: () => { run(); } };
}
