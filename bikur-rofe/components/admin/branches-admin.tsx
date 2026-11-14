"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Plus, Trash2 } from "lucide-react";
import { getSupabase } from "@/lib/supabase/client";
import type { Branch, OpeningHours, Service } from "@/lib/types";
import { Check, Field, SaveBar, dbError, input } from "./ui";

const EMPTY: Branch = {
  id: "",
  slug: "",
  name: "",
  city: "",
  address: "",
  phone: "",
  lat: null,
  lng: null,
  hours: [{ days: "א׳–ה׳", open: "08:00", close: "20:00" }],
  urgent_care: false,
  parking: "",
  accessible: true,
  active: true,
  is_sample: false,
  sort: 100,
  services: [],
};

export function BranchesAdmin({ branches, services }: { branches: Branch[]; services: Service[] }) {
  const [selected, setSelected] = useState<string>(branches[0]?.id ?? "new");
  const current = branches.find((b) => b.id === selected) ?? EMPTY;
  return (
    <div className="grid gap-6 lg:grid-cols-[280px_1fr]">
      <aside className="space-y-1">
        <button onClick={() => setSelected("new")} className={`flex w-full items-center gap-2 rounded-lg px-3 py-2.5 font-bold ${selected === "new" ? "bg-brand text-white" : "text-brand-700 hover:bg-brand-50"}`}>
          <Plus className="h-4 w-4" /> סניף חדש
        </button>
        {branches.map((b) => (
          <button
            key={b.id}
            onClick={() => setSelected(b.id)}
            className={`block w-full rounded-lg px-3 py-2.5 text-start ${selected === b.id ? "bg-navy text-white" : "hover:bg-card"}`}
          >
            <span className="font-bold">{b.name}</span>
            <span className={`block text-sm ${selected === b.id ? "text-white/70" : "text-muted"}`}>
              {b.city}
              {!b.active && " · מושבת"}
              {b.is_sample && " · דוגמה"}
            </span>
          </button>
        ))}
      </aside>
      <BranchForm key={current.id || "new"} branch={current} services={services} onCreated={setSelected} />
    </div>
  );
}

function slugify(s: string) {
  return s
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}

function BranchForm({ branch, services, onCreated }: { branch: Branch; services: Service[]; onCreated: (id: string) => void }) {
  const router = useRouter();
  const [b, setB] = useState<Branch>({ ...branch, phone: branch.phone ?? "", parking: branch.parking ?? "" });
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const set = <K extends keyof Branch>(k: K, v: Branch[K]) => setB((x) => ({ ...x, [k]: v }));
  const setHours = (i: number, patch: Partial<OpeningHours>) => set("hours", b.hours.map((h, j) => (j === i ? { ...h, ...patch } : h)));
  const isNew = !branch.id;

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    setError(null);
    if (!b.name.trim() || !b.city.trim()) return setError("נא למלא שם סניף ועיר.");
    const slug = slugify(b.slug);
    if (!slug) return setError("נא למלא מזהה לכתובת (באנגלית, למשל tel-aviv).");
    setBusy(true);
    const supabase = getSupabase();
    const row = {
      slug,
      name: b.name.trim(),
      city: b.city.trim(),
      address: b.address.trim(),
      phone: b.phone?.trim() || null,
      lat: b.lat,
      lng: b.lng,
      hours: b.hours.filter((h) => h.days.trim()),
      urgent_care: b.urgent_care,
      parking: b.parking?.trim() || null,
      accessible: b.accessible,
      active: b.active,
      sort: Number(b.sort) || 100,
      // Once a person has edited and saved a branch, it is real data.
      is_sample: false,
    };
    const res = isNew ? await supabase.from("branches").insert(row).select("id").single() : await supabase.from("branches").update(row).eq("id", branch.id).select("id").single();
    if (res.error) {
      setBusy(false);
      return setError(dbError(res.error.message));
    }
    const id = res.data.id as string;
    await supabase.from("branch_services").delete().eq("branch_id", id);
    if (b.services?.length) {
      const { error: err } = await supabase.from("branch_services").insert(b.services.map((s) => ({ branch_id: id, service_slug: s })));
      if (err) {
        setBusy(false);
        return setError(dbError(err.message));
      }
    }
    setBusy(false);
    setMsg("נשמר ✓");
    router.refresh();
    if (isNew) onCreated(id);
  }

  async function remove() {
    if (!confirm(`למחוק את ${branch.name}? אם יש לסניף תורים, עדיף להשבית אותו.`)) return;
    setBusy(true);
    const { error: err } = await getSupabase().from("branches").delete().eq("id", branch.id);
    setBusy(false);
    if (err) return setError(dbError(err.message));
    router.refresh();
    onCreated("new");
  }

  return (
    <form onSubmit={save} className="space-y-5 rounded-2xl border border-line bg-white p-6">
      <h1 className="font-display text-2xl font-extrabold">{isNew ? "סניף חדש" : b.name}</h1>
      {branch.is_sample && <p className="rounded-lg bg-amber-50 p-3 text-sm font-semibold text-amber-900">סניף לדוגמה. עדכנו כתובת, טלפון ושעות אמיתיים ושמרו — הסימון “דוגמה” יוסר.</p>}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="שם הסניף">
          <input className={input} value={b.name} onChange={(e) => set("name", e.target.value)} />
        </Field>
        <Field label="עיר">
          <input className={input} value={b.city} onChange={(e) => set("city", e.target.value)} />
        </Field>
        <Field label="כתובת">
          <input className={input} value={b.address} onChange={(e) => set("address", e.target.value)} placeholder="רחוב ומספר" />
        </Field>
        <Field label="טלפון הסניף">
          <input className={`${input} text-end`} dir="ltr" value={b.phone ?? ""} onChange={(e) => set("phone", e.target.value)} />
        </Field>
        <Field label="מזהה לכתובת האתר (אנגלית)">
          <input className={`${input} text-end`} dir="ltr" value={b.slug} onChange={(e) => set("slug", e.target.value)} placeholder="tel-aviv" />
        </Field>
        <Field label="סדר הצגה">
          <input className={input} type="number" value={b.sort} onChange={(e) => set("sort", Number(e.target.value))} />
        </Field>
        <Field label="קו רוחב (lat)">
          <input className={`${input} text-end`} dir="ltr" inputMode="decimal" value={b.lat ?? ""} onChange={(e) => set("lat", e.target.value === "" ? null : Number(e.target.value))} />
        </Field>
        <Field label="קו אורך (lng)">
          <input className={`${input} text-end`} dir="ltr" inputMode="decimal" value={b.lng ?? ""} onChange={(e) => set("lng", e.target.value === "" ? null : Number(e.target.value))} />
        </Field>
        <Field label="חניה" className="sm:col-span-2">
          <input className={input} value={b.parking ?? ""} onChange={(e) => set("parking", e.target.value)} placeholder="למשל: חניון ציבורי בבניין" />
        </Field>
      </div>
      <p className="-mt-2 text-sm text-muted">קואורדינטות משמשות למיון “הסניף הקרוב אליי”. אפשר להעתיק מ-Google Maps (קליק ימני על המיקום).</p>

      <fieldset>
        <legend className="mb-2 text-sm font-bold text-navy/80">שעות פתיחה</legend>
        <div className="space-y-2">
          {b.hours.map((h, i) => (
            <div key={i} className="flex flex-wrap items-center gap-2">
              <input className={`${input} w-40`} value={h.days} onChange={(e) => setHours(i, { days: e.target.value })} aria-label="ימים" placeholder="א׳–ה׳" />
              <input className={`${input} w-32`} type="time" value={h.open} onChange={(e) => setHours(i, { open: e.target.value })} aria-label="פתיחה" />
              <span>–</span>
              <input className={`${input} w-32`} type="time" value={h.close} onChange={(e) => setHours(i, { close: e.target.value })} aria-label="סגירה" />
              <button type="button" onClick={() => set("hours", b.hours.filter((_, j) => j !== i))} className="grid h-11 w-11 place-items-center rounded-lg text-danger hover:bg-red-50" aria-label="הסרת שורה">
                <Trash2 className="h-4 w-4" />
              </button>
            </div>
          ))}
          <button type="button" onClick={() => set("hours", [...b.hours, { days: "", open: "08:00", close: "20:00" }])} className="text-sm font-bold text-brand-700 hover:underline">
            + הוספת שורה
          </button>
        </div>
      </fieldset>

      <fieldset>
        <legend className="mb-2 text-sm font-bold text-navy/80">שירותים בסניף</legend>
        <div className="flex flex-wrap gap-x-6 gap-y-2">
          {services.map((s) => (
            <Check
              key={s.slug}
              label={s.name}
              checked={b.services?.includes(s.slug) ?? false}
              onChange={(v) => set("services", v ? [...(b.services ?? []), s.slug] : (b.services ?? []).filter((x) => x !== s.slug))}
            />
          ))}
        </div>
      </fieldset>

      <div className="flex flex-wrap gap-x-6 gap-y-2">
        <Check label="מוקד רפואה דחופה" checked={b.urgent_care} onChange={(v) => set("urgent_care", v)} />
        <Check label="נגיש" checked={b.accessible} onChange={(v) => set("accessible", v)} />
        <Check label="פעיל (מוצג באתר)" checked={b.active} onChange={(v) => set("active", v)} />
      </div>

      <SaveBar busy={busy} message={msg} error={error}>
        {!isNew && (
          <button type="button" onClick={remove} disabled={busy} className="rounded-lg px-4 py-2.5 font-semibold text-danger hover:bg-red-50">
            מחיקה
          </button>
        )}
      </SaveBar>
    </form>
  );
}
