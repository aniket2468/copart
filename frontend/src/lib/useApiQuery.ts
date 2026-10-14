import {useEffect, useState} from 'react';
import {api} from './api';

export function useApiQuery<T>(path: string | null) {
  const [revision, setRevision] = useState(0);
  const key = JSON.stringify([path, revision]);
  const [result, setResult] = useState<{key: string; data?: T; error: string} | null>(null);
  useEffect(() => {
    if (path === null) return;
    const controller = new AbortController();
    api<T>(path, {signal: controller.signal})
      .then(data => {
        if (!controller.signal.aborted) setResult({key, data, error: ''});
      })
      .catch(error => {
        if (!controller.signal.aborted) setResult({key, error: error instanceof Error ? error.message : 'Unable to load data'});
      });
    return () => controller.abort();
  }, [path, key]);
  const current = result?.key === key ? result : null;
  return {data: current?.data, error: current?.error ?? '',
    loading: path !== null && current === null, reload: () => setRevision(value => value + 1)};
}
