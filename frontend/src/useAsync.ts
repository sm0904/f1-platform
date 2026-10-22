import { useEffect, useState } from "react";
import { ApiError } from "./api";

export interface AsyncState<T> {
  data: T | null;
  error: string | null;
  loading: boolean;
}

/** Runs a loader whenever its dependencies change. Ignores results from requests that were superseded. */
export function useAsync<T>(load: () => Promise<T>, deps: ReadonlyArray<unknown>): AsyncState<T> {
  const [state, setState] = useState<AsyncState<T>>({ data: null, error: null, loading: true });

  useEffect(() => {
    let cancelled = false;
    setState((s) => ({ ...s, loading: true, error: null }));
    load()
      .then((data) => {
        if (!cancelled) setState({ data, error: null, loading: false });
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        const message = e instanceof ApiError ? e.message : "Something went wrong loading this data.";
        setState({ data: null, error: message, loading: false });
      });
    return () => {
      cancelled = true;
    };
    // The loader is supplied by the caller and identified by its dependencies.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return state;
}
