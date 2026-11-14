export const TZ = "Asia/Jerusalem";
export const WEEKDAYS = ["ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת"];
export const WEEKDAYS_SHORT = ["א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳"];

/** Today's date in Israel as YYYY-MM-DD. */
export function israelToday(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: TZ }).format(new Date());
}

export function addDays(isoDate: string, days: number): string {
  const d = new Date(`${isoDate}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

export function weekdayOf(isoDate: string): number {
  return new Date(`${isoDate}T12:00:00Z`).getUTCDay();
}

export function formatTime(iso: string): string {
  return new Intl.DateTimeFormat("he-IL", { timeZone: TZ, hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date(iso));
}

export function formatDate(iso: string, opts: Intl.DateTimeFormatOptions = { weekday: "long", day: "numeric", month: "long" }): string {
  return new Intl.DateTimeFormat("he-IL", { timeZone: TZ, ...opts }).format(new Date(iso));
}

/** "היום" / "מחר" / weekday + date, for a YYYY-MM-DD in Israel. */
export function relativeDay(isoDate: string): string {
  const today = israelToday();
  if (isoDate === today) return "היום";
  if (isoDate === addDays(today, 1)) return "מחר";
  return `יום ${WEEKDAYS[weekdayOf(isoDate)]}`;
}

export function israelDateOf(iso: string): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: TZ }).format(new Date(iso));
}

export function isValidIsraeliPhone(phone: string): boolean {
  const digits = phone.replace(/[^\d+]/g, "").replace(/^\+972/, "0");
  return /^0(5\d|[2-4]|[89]|7\d)\d{7}$/.test(digits);
}

export function distanceKm(a: { lat: number; lng: number }, b: { lat: number; lng: number }): number {
  const R = 6371;
  const dLat = ((b.lat - a.lat) * Math.PI) / 180;
  const dLng = ((b.lng - a.lng) * Math.PI) / 180;
  const s = Math.sin(dLat / 2) ** 2 + Math.cos((a.lat * Math.PI) / 180) * Math.cos((b.lat * Math.PI) / 180) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(s));
}

export function wazeLink(b: { lat: number | null; lng: number | null; address: string; city: string }): string {
  if (b.lat != null && b.lng != null) return `https://waze.com/ul?ll=${b.lat},${b.lng}&navigate=yes`;
  return `https://waze.com/ul?q=${encodeURIComponent(`${b.address} ${b.city}`)}&navigate=yes`;
}

export function mapsLink(b: { lat: number | null; lng: number | null; address: string; city: string; name: string }): string {
  const q = b.address ? `${b.address}, ${b.city}` : `${b.name}, ${b.city}`;
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(q)}`;
}

export function telLink(phone: string): string {
  return `tel:${phone.replace(/[^\d*+#]/g, "")}`;
}
