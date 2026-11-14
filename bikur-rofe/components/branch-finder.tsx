"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Accessibility, CalendarPlus, LocateFixed, Loader2, MapPin, Navigation, Phone, Search, Siren } from "lucide-react";
import { distanceKm, mapsLink, telLink, wazeLink } from "@/lib/format";
import type { Branch, Service } from "@/lib/types";

export function BranchFinder({ branches, services }: { branches: Branch[]; services: Service[] }) {
  const params = useSearchParams();
  const [query, setQuery] = useState("");
  const [service, setService] = useState(params.get("service") ?? "");
  const [position, setPosition] = useState<{ lat: number; lng: number } | null>(null);
  const [locating, setLocating] = useState(false);
  const [locError, setLocError] = useState<string | null>(null);

  function locate() {
    if (!navigator.geolocation) {
      setLocError("הדפדפן לא תומך באיתור מיקום.");
      return;
    }
    setLocating(true);
    setLocError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setPosition({ lat: pos.coords.latitude, lng: pos.coords.longitude });
        setLocating(false);
      },
      () => {
        setLocError("לא הצלחנו לאתר את המיקום. אפשר לחפש לפי עיר.");
        setLocating(false);
      },
      { enableHighAccuracy: false, timeout: 10000 },
    );
  }

  useEffect(() => {
    if (params.get("near") === "1") locate();
  }, []);

  const list = useMemo(() => {
    const q = query.trim();
    const filtered = branches.filter(
      (b) => (!q || b.name.includes(q) || b.city.includes(q) || b.address.includes(q)) && (!service || b.services?.includes(service)),
    );
    const withDistance = filtered.map((b) => ({
      branch: b,
      km: position && b.lat != null && b.lng != null ? distanceKm(position, { lat: b.lat, lng: b.lng }) : null,
    }));
    if (position) withDistance.sort((a, b) => (a.km ?? 1e9) - (b.km ?? 1e9));
    return withDistance;
  }, [branches, query, service, position]);

  return (
    <div>
      <div className="flex flex-col gap-3 rounded-2xl border border-line bg-white p-4 sm:flex-row sm:items-center">
        <label className="flex flex-1 items-center gap-2 rounded-xl border border-line px-3 focus-within:border-brand">
          <Search className="h-5 w-5 text-muted" />
          <span className="sr-only">חיפוש לפי עיר או שם סניף</span>
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="חיפוש לפי עיר או שם סניף" className="h-12 w-full bg-transparent outline-none" />
        </label>
        <label className="sm:w-56">
          <span className="sr-only">סינון לפי שירות</span>
          <select value={service} onChange={(e) => setService(e.target.value)} className="h-12 w-full rounded-xl border border-line bg-white px-3 outline-none focus:border-brand">
            <option value="">כל השירותים</option>
            {services.map((s) => (
              <option key={s.slug} value={s.slug}>
                {s.name}
              </option>
            ))}
          </select>
        </label>
        <button onClick={locate} className="flex h-12 items-center justify-center gap-2 rounded-xl bg-navy px-5 font-bold text-white hover:bg-navy-800">
          {locating ? <Loader2 className="h-5 w-5 animate-spin" /> : <LocateFixed className="h-5 w-5" />}
          הסניף הקרוב אליי
        </button>
      </div>
      <div aria-live="polite" className="mt-3 min-h-6 text-[15px]">
        {locError && <p className="text-danger">{locError}</p>}
        {position && !locError && <p className="text-muted">הסניפים ממוינים לפי המרחק מכם.</p>}
      </div>

      {list.length === 0 ? (
        <p className="mt-6 rounded-2xl bg-card p-6 text-muted">לא נמצאו סניפים מתאימים.</p>
      ) : (
        <ul className="mt-4 grid gap-4 md:grid-cols-2">
          {list.map(({ branch: b, km }) => (
            <li key={b.id} className="flex flex-col rounded-2xl border border-line bg-white p-6">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <Link href={`/branches/${b.slug}`} className="font-display text-xl font-extrabold hover:text-brand-700">
                    {b.name}
                  </Link>
                  <p className="mt-0.5 flex items-center gap-1.5 text-muted">
                    <MapPin className="h-4 w-4 shrink-0" />
                    {[b.address, b.city].filter(Boolean).join(", ")}
                    {km != null && <span className="font-semibold text-navy">· {km < 10 ? km.toFixed(1) : Math.round(km)} ק״מ</span>}
                  </p>
                </div>
                <div className="flex shrink-0 gap-1.5">
                  {b.urgent_care && (
                    <span className="inline-flex items-center gap-1 rounded-full bg-red-50 px-2.5 py-1 text-xs font-bold text-danger">
                      <Siren className="h-3.5 w-3.5" /> דחוף
                    </span>
                  )}
                  {b.accessible && (
                    <span className="inline-flex items-center rounded-full bg-card px-2 py-1 text-navy/70" title="נגיש">
                      <Accessibility className="h-3.5 w-3.5" />
                      <span className="sr-only">סניף נגיש</span>
                    </span>
                  )}
                </div>
              </div>
              <ul className="mt-4 space-y-0.5 text-[15px]">
                {b.hours.map((h) => (
                  <li key={h.days} className="flex justify-between gap-3 border-b border-dashed border-line py-1 last:border-0">
                    <span className="text-muted">{h.days}</span>
                    <span dir="ltr" className="font-semibold">
                      {h.open}–{h.close}
                    </span>
                  </li>
                ))}
              </ul>
              <div className="mt-5 flex flex-wrap gap-2">
                <Link href={`/booking?branch=${b.slug}${service ? `&service=${service}` : ""}`} className="inline-flex items-center gap-1.5 rounded-lg bg-brand px-4 py-2 font-bold text-white hover:bg-brand-600">
                  <CalendarPlus className="h-4 w-4" /> קביעת תור
                </Link>
                <a href={wazeLink(b)} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1.5 rounded-lg border border-line px-4 py-2 font-semibold hover:border-navy/30">
                  <Navigation className="h-4 w-4" /> Waze
                </a>
                <a href={mapsLink(b)} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1.5 rounded-lg border border-line px-4 py-2 font-semibold hover:border-navy/30">
                  <MapPin className="h-4 w-4" /> מפה
                </a>
                {b.phone && (
                  <a href={telLink(b.phone)} className="inline-flex items-center gap-1.5 rounded-lg border border-line px-4 py-2 font-semibold hover:border-navy/30">
                    <Phone className="h-4 w-4" /> חיוג
                  </a>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
