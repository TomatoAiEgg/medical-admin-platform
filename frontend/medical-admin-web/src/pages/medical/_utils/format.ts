export function pickRows<T>(res: unknown): T[] {
  if (Array.isArray(res))
    return res as T[];
  const data = res as { rows?: T[]; data?: T[] };
  return data?.rows || data?.data || [];
}

export function pickData<T>(res: unknown, fallback: T): T {
  const data = res as { data?: T };
  return data?.data || fallback;
}

export function toPercent(value: number, total: number) {
  if (!total)
    return 0;
  return Math.round((value / total) * 100);
}

export function displayValue(value: unknown, fallback = '-') {
  if (value === undefined || value === null || value === '')
    return fallback;
  return String(value);
}
