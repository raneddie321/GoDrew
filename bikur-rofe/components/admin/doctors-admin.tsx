"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Plus, Trash2 } from "lucide-react";
import { getSupabase } from "@/lib/supabase/client";
import { WEEKDAYS } from "@/lib/format";
import type { Branch, Doctor, Schedule, Service } from "@/lib/types";
import { Check, Field, SaveBar, dbError, input } from "./ui";

type Row = Omit<Schedule, "id" | "doctor_id">;
type BranchLite = Pick<Branch, "id" | "name" | "city">;

const EMPTY: Doctor = { id: "", name: "", title: "ד״ר", service_slug: "", bio: "", photo_url: null, languages: ["עברית"], active: true, is_sample: false };

export function DoctorsAdmin({ doctors, schedules, branches, services }: { doctors: Doctor[]; schedules: Schedule[]; branches: BranchLite[]; services: Service[] }) {
  const [selected, setSelected] = useState<string>(doctors[0]?.id ?? "new");
  const [filter, setFilter] = useState("");
  const current = doctors.find((d) => d.id === selected) ?? { ...EMPTY, service_slug: services[0]?.slug ?? "" };
  const shown = doctors.filter((d) => !filter || d.service_slug === filter);
  return (
    <div className="grid gap-6 lg:grid-cols-[300px_1fr]">
      <aside className="space-y-1">
        <button onClick={() => setSelected("new")} className={`flex w-full items-center gap-2 rounded-lg px-3 py-2.5 font-bold ${selected === "new" ? "bg-brand text-white" : "text-brand-700 hover:bg-brand-50"}`}>
          <Plus className="h-4 w-4" /> רופא/ה חדש/ה
        </button>
        <select value={filter} onChange={(e) => setFilter(e.target.value)} className={`${input} my-2`} aria-label="סינון לפי שירות">
          <option value="">כל השירותים</option>
          {services.map((s) => (
            <option key={s.slug} value={s.slug}>
              {s.name}
            </option>
          ))}
        </select>
        {shown.map((d) => (
          <button key={d.id} onClick={() => setSelected(d.id)} className={`block w-full rounded-lg px-3 py-2.5 text-start ${selected === d.id ? "bg-navy text-white" : "hover:bg-card"}`}>
            <span className="font-bold">{[d.title, d.name].filter(Boolean).join(" ")}</span>
            <span className={`block text-sm ${selected === d.id ? "text-white/70" : "text-muted"}`}>
              {services.find((s) => s.slug === d.service_slug)?.name}
              {!d.active && " · מושבת"}
              {d.is_sample && " · דוגמה"}
            </span>
          </button>
        ))}
      </aside>
      <DoctorForm
        key={current.id || "new"}
        doctor={current}
        initialRows={schedules.filter((s) => s.doctor_id === current.id).map(({ branch_id, weekday, start_time, end_time, slot_minutes }) => ({ branch_id, weekday, start_time: start_time.slice(0, 5), end_time: end_time.slice(0, 5), slot_minutes }))}
        branches={branches}
        services={services}
        onSelect={setSelected}
      />
    </div>
  );
}

function DoctorForm({ doctor, initialRows, branches, services, onSelect }: { doctor: Doctor; initialRows: Row[]; branches: BranchLite[]; services: Service[]; onSelect: (id: string) => void }) {
  const router = useRouter();
  const [d, setD] = useState(doctor);
  const [langs, setLangs] = useState(doctor.languages.join(", "));
  const [rows, setRows] = useState<Row[]>(initialRows);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const isNew = !doctor.id;
  const set = <K extends keyof Doctor>(k: K, v: Doctor[K]) => setD((x) => ({ ...x, [k]: v }));
  const setRow = (i: number, patch: Partial<Row>) => setRows((r) => r.map((x, j) => (j === i ? { ...x, ...patch } : x)));

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    setError(null);
    if (!d.name.trim()) return setError("נא למלא שם.");
    if (!d.service_slug) return setError("נא לבחור שירות.");
    for (const r of rows) {
      if (!r.branch_id) return setError("בכל שורת יומן יש לבחור סניף.");
      if (r.end_time <= r.start_time) return setError("בשורת יומן, שעת הסיום חייבת להיות אחרי שעת ההתחלה.");
    }
    setBusy(true);
    const supabase = getSupabase();
    const row = {
      name: d.name.trim(),
      title: d.title.trim(),
      service_slug: d.service_slug,
      bio: d.bio.trim(),
      languages: langs.split(/[,،]/).map((s) => s.trim()).filter(Boolean),
      active: d.active,
      is_sample: false,
    };
    const res = isNew ? await supabase.from("doctors").insert(row).select("id").single() : await supabase.from("doctors").update(row).eq("id", doctor.id).select("id").single();
    if (res.error) {
      setBusy(false);
      return setError(dbError(res.error.message));
    }
    const id = res.data.id as string;
    const del = await supabase.from("doctor_schedules").delete().eq("doctor_id", id);
    if (!del.error && rows.length) {
      const ins = await supabase.from("doctor_schedules").insert(rows.map((r) => ({ ...r, doctor_id: id, slot_minutes: Number(r.slot_minutes) || 15 })));
      if (ins.error) {
        setBusy(false);
        return setError(dbError(ins.error.message));
      }
    }
    setBusy(false);
    setMsg("נשמר ✓");
    router.refresh();
    if (isNew) onSelect(id);
  }

  async function remove() {
    if (!confirm(`למחוק את ${d.name}? אם יש תורים קיימים, עדיף להשבית.`)) return;
    setBusy(true);
    const { error: err } = await getSupabase().from("doctors").delete().eq("id", doctor.id);
    setBusy(false);
    if (err) return setError(dbError(err.message));
    router.refresh();
    onSelect("new");
  }

  return (
    <form onSubmit={save} className="space-y-5 rounded-2xl border border-line bg-white p-6">
      <h1 className="font-display text-2xl font-extrabold">{isNew ? "רופא/ה חדש/ה" : [doctor.title, doctor.name].filter(Boolean).join(" ")}</h1>
      {doctor.is_sample && <p className="rounded-lg bg-amber-50 p-3 text-sm font-semibold text-amber-900">רשומה לדוגמה. עדכנו לפרטים אמיתיים ושמרו — הסימון “דוגמה” יוסר.</p>}
      <div className="grid gap-4 sm:grid-cols-[120px_1fr_1fr]">
        <Field label="תואר">
          <input className={input} value={d.title} onChange={(e) => set("title", e.target.value)} placeholder="ד״ר" />
        </Field>
        <Field label="שם מלא">
          <input className={input} value={d.name} onChange={(e) => set("name", e.target.value)} />
        </Field>
        <Field label="שירות">
          <select className={input} value={d.service_slug} onChange={(e) => set("service_slug", e.target.value)}>
            {services.map((s) => (
              <option key={s.slug} value={s.slug}>
                {s.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label="שפות (מופרדות בפסיק)" className="sm:col-span-3">
          <input className={input} value={langs} onChange={(e) => setLangs(e.target.value)} />
        </Field>
        <Field label="אודות" className="sm:col-span-3">
          <textarea className="w-full rounded-lg border border-line p-3 outline-none focus:border-brand" rows={3} value={d.bio} onChange={(e) => set("bio", e.target.value)} />
        </Field>
      </div>

      <fieldset>
        <legend className="mb-1 text-sm font-bold text-navy/80">יומן קבלה שבועי</legend>
        <p className="mb-3 text-sm text-muted">התורים הפנויים באתר נוצרים אוטומטית מהיומן. שינוי היומן לא מבטל תורים שכבר נקבעו.</p>
        <div className="space-y-2">
          {rows.map((r, i) => (
            <div key={i} className="flex flex-wrap items-center gap-2 rounded-lg bg-card p-2">
              <select className={`${input} w-44`} value={r.branch_id} onChange={(e) => setRow(i, { branch_id: e.target.value })} aria-label="סניף">
                <option value="">סניף…</option>
                {branches.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>
              <select className={`${input} w-28`} value={r.weekday} onChange={(e) => setRow(i, { weekday: Number(e.target.value) })} aria-label="יום">
                {WEEKDAYS.map((w, j) => (
                  <option key={j} value={j}>
                    יום {w}
                  </option>
                ))}
              </select>
              <input className={`${input} w-28`} type="time" value={r.start_time} onChange={(e) => setRow(i, { start_time: e.target.value })} aria-label="משעה" />
              <span>–</span>
              <input className={`${input} w-28`} type="time" value={r.end_time} onChange={(e) => setRow(i, { end_time: e.target.value })} aria-label="עד שעה" />
              <select className={`${input} w-32`} value={r.slot_minutes} onChange={(e) => setRow(i, { slot_minutes: Number(e.target.value) })} aria-label="משך תור">
                {[5, 10, 15, 20, 30, 45, 60].map((m) => (
                  <option key={m} value={m}>
                    {m} דק׳ לתור
                  </option>
                ))}
              </select>
              <button type="button" onClick={() => setRows(rows.filter((_, j) => j !== i))} className="grid h-11 w-11 place-items-center rounded-lg text-danger hover:bg-red-50" aria-label="הסרת שורה">
                <Trash2 className="h-4 w-4" />
              </button>
            </div>
          ))}
          <button
            type="button"
            onClick={() => setRows([...rows, { branch_id: rows.at(-1)?.branch_id ?? "", weekday: Math.min(((rows.at(-1)?.weekday ?? -1) + 1) % 7, 6), start_time: "09:00", end_time: "13:00", slot_minutes: rows.at(-1)?.slot_minutes ?? 15 }])}
            className="text-sm font-bold text-brand-700 hover:underline"
          >
            + הוספת יום קבלה
          </button>
        </div>
      </fieldset>

      <Check label="פעיל/ה (זמין/ה לזימון)" checked={d.active} onChange={(v) => set("active", v)} />

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
