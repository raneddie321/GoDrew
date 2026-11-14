"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowLeft, CalendarCheck, Loader2, MapPin, Stethoscope } from "lucide-react";
import { getSupabase } from "@/lib/supabase/client";
import { formatTime, israelDateOf, relativeDay } from "@/lib/format";
import type { Branch, Doctor, Service } from "@/lib/types";

interface Result {
  doctor: Doctor;
  branch: Branch;
  slot: string;
}

/** Hero widget: pick a service (and optionally a branch) and get the next free appointment. */
export function QuickBook({ services, branches, doctors }: { services: Service[]; branches: Branch[]; doctors: Doctor[] }) {
  const bookable = services.filter((s) => s.slug !== "urgent");
  const [service, setService] = useState(bookable[0]?.slug ?? "");
  const [branchId, setBranchId] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<Result | null | "none">(null);

  const branchOptions = branches.filter((b) => !service || b.services?.includes(service));

  async function search(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setResult(null);
    const { data } = await getSupabase().rpc("next_available", { p_service: service, p_branch: branchId || null, p_days: 21 });
    const rows = ((data ?? []) as { doctor_id: string; branch_id: string; first_slot: string | null }[])
      .filter((r) => r.first_slot)
      .sort((a, b) => a.first_slot!.localeCompare(b.first_slot!));
    const first = rows[0];
    const doctor = first && doctors.find((d) => d.id === first.doctor_id);
    const branch = first && branches.find((b) => b.id === first.branch_id);
    setResult(first && doctor && branch ? { doctor, branch, slot: first.first_slot! } : "none");
    setLoading(false);
  }

  const bookHref =
    result && result !== "none"
      ? `/booking?service=${service}&branch=${result.branch.slug}&doctor=${result.doctor.id}&date=${israelDateOf(result.slot)}&slot=${encodeURIComponent(result.slot)}`
      : `/booking?service=${service}`;

  return (
    <div className="rounded-2xl border border-line bg-white p-6 shadow-[0_24px_60px_-28px_rgba(10,25,49,0.35)] sm:p-7">
      <h2 className="font-display text-xl font-extrabold">מצאו את התור הקרוב ביותר</h2>
      <p className="mt-1 text-[15px] text-muted">בחרו תחום, ואנחנו נמצא לכם את התור הפנוי הראשון.</p>
      <form onSubmit={search} className="mt-5 space-y-3">
        <label className="block">
          <span className="mb-1.5 flex items-center gap-1.5 text-sm font-bold">
            <Stethoscope className="h-4 w-4 text-brand" /> תחום
          </span>
          <select
            value={service}
            onChange={(e) => {
              setService(e.target.value);
              setBranchId("");
              setResult(null);
            }}
            className="h-12 w-full rounded-xl border border-line bg-white px-3 text-[16px] outline-none focus:border-brand"
          >
            {bookable.map((s) => (
              <option key={s.slug} value={s.slug}>
                {s.name}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="mb-1.5 flex items-center gap-1.5 text-sm font-bold">
            <MapPin className="h-4 w-4 text-brand" /> סניף
          </span>
          <select
            value={branchId}
            onChange={(e) => {
              setBranchId(e.target.value);
              setResult(null);
            }}
            className="h-12 w-full rounded-xl border border-line bg-white px-3 text-[16px] outline-none focus:border-brand"
          >
            <option value="">כל הסניפים</option>
            {branchOptions.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </select>
        </label>
        <button disabled={loading || !service} className="flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-navy font-bold text-white transition-colors hover:bg-navy-800 disabled:opacity-60">
          {loading ? <Loader2 className="h-5 w-5 animate-spin" /> : <CalendarCheck className="h-5 w-5" />}
          חיפוש תור פנוי
        </button>
      </form>

      <div aria-live="polite">
        {result === "none" && <p className="mt-4 rounded-xl bg-card p-4 text-[15px] text-muted">לא נמצאו תורים פנויים בשלושת השבועות הקרובים. נסו סניף או תחום אחר.</p>}
        {result && result !== "none" && (
          <div className="mt-4 rounded-xl border border-brand/30 bg-brand-50 p-4">
            <p className="text-sm font-bold text-brand-700">התור הפנוי הקרוב</p>
            <p className="mt-1 font-display text-lg font-extrabold">
              {relativeDay(israelDateOf(result.slot))}, {formatTime(result.slot)}
            </p>
            <p className="text-[15px] text-navy/80">
              {[result.doctor.title, result.doctor.name].filter(Boolean).join(" ")} · {result.branch.name}
            </p>
            <Link href={bookHref} className="mt-3 inline-flex items-center gap-1.5 font-bold text-brand-700 hover:underline">
              להזמנת התור <ArrowLeft className="h-4 w-4" />
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}
