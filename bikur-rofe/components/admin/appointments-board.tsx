"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ChevronLeft, ChevronRight, Phone } from "lucide-react";
import { getSupabase } from "@/lib/supabase/client";
import { addDays, formatDate, formatTime, israelToday, telLink } from "@/lib/format";
import type { Appointment, AppointmentStatus, Branch } from "@/lib/types";

const STATUS: Record<AppointmentStatus, { label: string; cls: string }> = {
  booked: { label: "נקבע", cls: "bg-blue-50 text-blue-800" },
  arrived: { label: "הגיע/ה", cls: "bg-brand-50 text-brand-700" },
  completed: { label: "הסתיים", cls: "bg-card text-navy/70" },
  no_show: { label: "לא הגיע/ה", cls: "bg-amber-50 text-amber-800" },
  cancelled: { label: "בוטל", cls: "bg-red-50 text-danger" },
};
const ACTIONS: Record<AppointmentStatus, AppointmentStatus[]> = {
  booked: ["arrived", "no_show", "cancelled"],
  arrived: ["completed", "booked"],
  completed: ["arrived"],
  no_show: ["booked"],
  cancelled: [],
};
const ACTION_LABEL: Record<AppointmentStatus, string> = { arrived: "סימון הגעה", completed: "סיום ביקור", no_show: "לא הגיע/ה", cancelled: "ביטול", booked: "החזרה ל״נקבע״" };

export function AppointmentsBoard({ date, branchId, branches, appointments }: { date: string; branchId: string; branches: Pick<Branch, "id" | "name" | "city">[]; appointments: Appointment[] }) {
  const router = useRouter();
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showCancelled, setShowCancelled] = useState(false);
  const href = (d: string, b = branchId) => `/admin?date=${d}${b ? `&branch=${b}` : ""}`;
  const active = appointments.filter((a) => a.status !== "cancelled");
  const list = showCancelled ? appointments : active;
  const count = (s: AppointmentStatus) => appointments.filter((a) => a.status === s).length;

  async function setStatus(a: Appointment, status: AppointmentStatus) {
    if (status === "cancelled" && !confirm(`לבטל את התור של ${a.patient_name}?`)) return;
    setBusy(a.id);
    setError(null);
    const { error: err } = await getSupabase().from("appointments").update({ status }).eq("id", a.id);
    setBusy(null);
    if (err) setError(/unique|duplicate/i.test(err.message) ? "המועד כבר תפוס על ידי תור אחר." : "העדכון נכשל.");
    else router.refresh();
  }

  return (
    <div>
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex items-center gap-1">
          <Link href={href(addDays(date, -1))} aria-label="היום הקודם" className="grid h-10 w-10 place-items-center rounded-lg border border-line hover:bg-card">
            <ChevronRight className="h-5 w-5" />
          </Link>
          <input type="date" value={date} onChange={(e) => e.target.value && router.push(href(e.target.value))} className="h-10 rounded-lg border border-line px-3" aria-label="תאריך" />
          <Link href={href(addDays(date, 1))} aria-label="היום הבא" className="grid h-10 w-10 place-items-center rounded-lg border border-line hover:bg-card">
            <ChevronLeft className="h-5 w-5" />
          </Link>
          {date !== israelToday() && (
            <Link href={href(israelToday())} className="rounded-lg px-3 py-2 font-semibold text-brand-700 hover:bg-brand-50">
              היום
            </Link>
          )}
        </div>
        <select value={branchId} onChange={(e) => router.push(href(date, e.target.value))} className="h-10 rounded-lg border border-line bg-white px-3" aria-label="סניף">
          <option value="">כל הסניפים</option>
          {branches.map((b) => (
            <option key={b.id} value={b.id}>
              {b.name}
            </option>
          ))}
        </select>
        <label className="ms-auto inline-flex items-center gap-2 text-sm font-semibold">
          <input type="checkbox" checked={showCancelled} onChange={(e) => setShowCancelled(e.target.checked)} className="accent-[#008c47]" />
          הצגת תורים שבוטלו
        </label>
      </div>

      <h1 className="mt-6 font-display text-2xl font-extrabold">{formatDate(`${date}T12:00:00Z`)}</h1>
      <p className="mt-1 text-muted">
        {active.length} תורים · הגיעו {count("arrived") + count("completed")} · לא הגיעו {count("no_show")} · בוטלו {count("cancelled")}
      </p>
      {error && (
        <p role="alert" className="mt-3 rounded-lg bg-red-50 p-3 font-semibold text-danger">
          {error}
        </p>
      )}

      {list.length === 0 ? (
        <p className="mt-6 rounded-2xl border border-dashed border-line p-10 text-center text-muted">אין תורים ביום זה.</p>
      ) : (
        <div className="mt-6 overflow-x-auto rounded-2xl border border-line bg-white">
          <table className="w-full min-w-[820px] text-start">
            <thead className="bg-card text-sm text-navy/70">
              <tr>
                {["שעה", "מטופל/ת", "שירות ורופא", "סניף", "סטטוס", ""].map((h) => (
                  <th key={h} className="px-4 py-3 text-start font-bold">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {list.map((a) => (
                <tr key={a.id} className="border-t border-line align-top">
                  <td className="px-4 py-3 font-display text-lg font-extrabold tabular-nums">{formatTime(a.starts_at)}</td>
                  <td className="px-4 py-3">
                    <div className="font-bold">{a.patient_name}</div>
                    <a href={telLink(a.patient_phone)} className="inline-flex items-center gap-1 text-sm text-brand-700 hover:underline" dir="ltr">
                      <Phone className="h-3.5 w-3.5" />
                      {a.patient_phone}
                    </a>
                    {a.reason && <div className="mt-1 max-w-xs text-sm text-muted">{a.reason}</div>}
                  </td>
                  <td className="px-4 py-3">
                    <div className="font-semibold">{a.service?.name}</div>
                    <div className="text-sm text-muted">{[a.doctor?.title, a.doctor?.name].filter(Boolean).join(" ")}</div>
                  </td>
                  <td className="px-4 py-3 text-sm">{a.branch?.name}</td>
                  <td className="px-4 py-3">
                    <span className={`rounded-full px-2.5 py-1 text-sm font-bold ${STATUS[a.status].cls}`}>{STATUS[a.status].label}</span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1.5">
                      {ACTIONS[a.status].map((s) => (
                        <button
                          key={s}
                          disabled={busy === a.id}
                          onClick={() => setStatus(a, s)}
                          className={`rounded-lg border px-2.5 py-1.5 text-sm font-semibold disabled:opacity-50 ${s === "cancelled" ? "border-red-200 text-danger hover:bg-red-50" : "border-line hover:bg-card"}`}
                        >
                          {ACTION_LABEL[s]}
                        </button>
                      ))}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
