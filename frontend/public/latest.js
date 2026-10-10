// Conserva apenas o último resultado e compartilha pedidos simultâneos.
export function createLatestLoader(fetchDetail) {
  let entry = null, generation = 0;
  return {
    clear() { generation++; entry = null; },
    load(summary) {
      const key = `${summary.id}:${summary.atualizadoEm}`;
      if (entry?.key === key) return entry.promise;
      const current = generation;
      const next = { key, promise: null };
      next.promise = Promise.resolve().then(() => fetchDetail(summary.id)).then(job => {
        if (current !== generation) return null;
        return job;
      }).catch(error => { if (entry === next) entry = null; throw error; });
      entry = next;
      return next.promise;
    }
  };
}
