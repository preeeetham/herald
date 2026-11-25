const relative = new Intl.RelativeTimeFormat("en", { numeric: "auto" });
const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ["day", 86_400_000],
  ["hour", 3_600_000],
  ["minute", 60_000],
  ["second", 1_000],
];

export function timeAgo(iso: string | null | undefined): string {
  if (!iso) return "—";
  const diff = new Date(iso).getTime() - Date.now();
  for (const [unit, ms] of UNITS) {
    if (Math.abs(diff) >= ms || unit === "second") {
      return relative.format(Math.round(diff / ms), unit);
    }
  }
  return "now";
}

export function dateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "medium" });
}

export const percent = (value: number) => `${(value * 100).toFixed(value >= 0.999 || value === 0 ? 0 : 1)}%`;

export const ms = (value: number | null | undefined) =>
  value == null ? "—" : value >= 1000 ? `${(value / 1000).toFixed(2)} s` : `${Math.round(value)} ms`;

export const compact = (value: number) => new Intl.NumberFormat("en", { notation: "compact" }).format(value);

export const shortId = (id: string) => id.slice(-8);
