"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { ArrowRight, CalendarCheck, CalendarPlus, Check, ChevronLeft, Clock, Loader2, LocateFixed, MapPin, Navigation, UserRound } from "lucide-react";
import { getSupabase } from "@/lib/supabase/client";
import { ServiceIcon } from "./service-icon";
import { addDays, distanceKm, formatDate, formatTime, israelDateOf, israelToday, isValidIsraeliPhone, relativeDay, wazeLink, WEEKDAYS_SHORT, weekdayOf } from "@/lib/format";
import { appointmentIcs, downloadIcs } from "@/lib/ics";
import type { Branch, Doctor, Profile, Schedule, Service } from "@/lib/types";

const DAYS_AHEAD = 21;

const ERRORS: Record<string, string> = {
  slot_unavailable: "התור הזה נתפס הרגע. בחרו בבקשה שעה אחרת.",
  too_many_appointments: "יש לכם כבר כמה תורים עתידיים פעילים. אפשר לנהל אותם באזור האישי.",
  missing_details: "חסרים שם או טלפון.",
  not_authenticated: "יש להתחבר כדי לקבוע תור.",
};

interface Props {
  services: Service[];
  branches: Branch[];
  doctors: Doctor[];
  userId: string | null;
  profile: Profile | null;
}

export function BookingWizard({ services, branches, doctors, userId, profile }: Props) {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const serviceSlug = params.get("service") ?? "";
  const branchSlug = params.get("branch") ?? "";
  const doctorParam = params.get("doctor") ?? "";
  const dateParam = params.get("date") ?? "";
  const slotParam = params.get("slot") ?? "";

  const service = services.find((s) => s.slug === serviceSlug) ?? null;
  const branch = branches.find((b) => b.slug === branchSlug && (!service || b.services?.includes(service.slug))) ?? null;
  const doctor = doctors.find((d) => d.id === doctorParam) ?? null;

  const step = !service ? 1 : !branch ? 2 : !slotParam || !doctor ? 3 : 4;

  const setParams = useCallback(
    (patch: Record<string, string | null>) => {
      const next = new URLSearchParams(params.toString());
      for (const [k, v] of Object.entries(patch)) {
        if (v) next.set(k, v);
        else next.delete(k);
      }
      router.replace(`${pathname}?${next.toString()}`, { scroll: false });
    },
    [params, pathname, router],
  );

  const [done, setDone] = useState<{ id: string; slot: string; rescheduled: boolean } | null>(null);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, [step]);

  if (done && service && branch && doctor) {
    return <Success id={done.id} slot={done.slot} rescheduled={done.rescheduled} service={service} branch={branch} doctor={doctor} />;
  }

  const steps = ["שירות", "סניף", "רופא ושעה", "אישור"];

  return (
    <div>
      <ol className="flex items-center gap-2 overflow-x-auto pb-1" aria-label="שלבי זימון התור">
        {steps.map((label, i) => {
          const n = i + 1;
          const state = n < step ? "done" : n === step ? "current" : "todo";
          const reset: Record<string, string | null> | undefined = (
            [
            { service: null, branch: null, doctor: null, date: null, slot: null },
            { branch: null, doctor: null, date: null, slot: null },
            { slot: null },
            ] as Record<string, string | null>[]
          )[i];
          return (
            <li key={label} className="flex shrink-0 items-center gap-2">
              <button
                type="button"
                disabled={state !== "done"}
                onClick={() => reset && setParams(reset)}
                aria-current={state === "current" ? "step" : undefined}
                className={`flex items-center gap-2 rounded-full px-3 py-1.5 text-[15px] font-bold ${
                  state === "current" ? "bg-navy text-white" : state === "done" ? "bg-brand-50 text-brand-700 hover:bg-brand-100" : "text-muted"
                }`}
              >
                <span className={`grid h-6 w-6 place-items-center rounded-full text-xs ${state === "current" ? "bg-white text-navy" : state === "done" ? "bg-brand text-white" : "bg-card"}`}>
                  {state === "done" ? <Check className="h-3.5 w-3.5" /> : n}
                </span>
                {label}
              </button>
              {n < steps.length && <ChevronLeft className="h-4 w-4 text-line" />}
            </li>
          );
        })}
      </ol>

      <div className="mt-8">
        {step === 1 && <PickService services={services} onPick={(slug) => setParams({ service: slug, branch: branch && branch.services?.includes(slug) ? branch.slug : null })} />}
        {step === 2 && service && <PickBranch branches={branches.filter((b) => b.services?.includes(service.slug))} onPick={(slug) => setParams({ branch: slug })} />}
        {step === 3 && service && branch && (
          <PickSlot
            service={service}
            branch={branch}
            doctors={doctors.filter((d) => d.service_slug === service.slug)}
            preferredDoctor={doctor?.id ?? null}
            date={dateParam}
            onDate={(d) => setParams({ date: d })}
            onPick={(doctorId, slot) => setParams({ doctor: doctorId, slot, date: israelDateOf(slot) })}
            onClearDoctor={() => setParams({ doctor: null })}
          />
        )}
        {step === 4 && service && branch && doctor && (
          <Confirm
            service={service}
            branch={branch}
            doctor={doctor}
            slot={slotParam}
            userId={userId}
            profile={profile}
            returnTo={`${pathname}?${params.toString()}`}
            onBack={() => setParams({ slot: null })}
            onDone={async (id) => {
              // Rescheduling: the new appointment is confirmed, so release the old one.
              const old = params.get("reschedule");
              if (old) await getSupabase().rpc("cancel_appointment", { p_id: old });
              setDone({ id, slot: slotParam, rescheduled: Boolean(old) });
            }}
          />
        )}
      </div>
    </div>
  );
}

/* --------------------------------------------------------------------------- */

function PickService({ services, onPick }: { services: Service[]; onPick: (slug: string) => void }) {
  return (
    <section aria-labelledby="pick-service">
      <h2 id="pick-service" className="font-display text-2xl font-extrabold">
        במה נוכל לעזור?
      </h2>
      <ul className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {services.map((s) => (
          <li key={s.slug}>
            <button onClick={() => onPick(s.slug)} className="flex w-full items-center gap-4 rounded-2xl border border-line bg-white p-5 text-start transition-colors hover:border-brand/50">
              <span className="grid h-12 w-12 shrink-0 place-items-center rounded-xl bg-brand-50 text-brand">
                <ServiceIcon name={s.icon} className="h-6 w-6" />
              </span>
              <span>
                <span className="block font-display text-lg font-extrabold">{s.name}</span>
                <span className="block text-[15px] text-muted">{s.short}</span>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

function PickBranch({ branches, onPick }: { branches: Branch[]; onPick: (slug: string) => void }) {
  const [pos, setPos] = useState<{ lat: number; lng: number } | null>(null);
  const [locating, setLocating] = useState(false);
  const sorted = useMemo(() => {
    if (!pos) return branches.map((b) => ({ b, km: null as number | null }));
    return branches
      .map((b) => ({ b, km: b.lat != null && b.lng != null ? distanceKm(pos, { lat: b.lat, lng: b.lng }) : null }))
      .sort((x, y) => (x.km ?? 1e9) - (y.km ?? 1e9));
  }, [branches, pos]);

  return (
    <section aria-labelledby="pick-branch">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="pick-branch" className="font-display text-2xl font-extrabold">
          באיזה סניף?
        </h2>
        <button
          onClick={() => {
            setLocating(true);
            navigator.geolocation?.getCurrentPosition(
              (p) => {
                setPos({ lat: p.coords.latitude, lng: p.coords.longitude });
                setLocating(false);
              },
              () => setLocating(false),
              { timeout: 10000 },
            );
          }}
          className="inline-flex items-center gap-2 rounded-lg border border-line bg-white px-4 py-2 font-semibold hover:border-navy/30"
        >
          {locating ? <Loader2 className="h-4 w-4 animate-spin" /> : <LocateFixed className="h-4 w-4" />}
          מיון לפי קרבה אליי
        </button>
      </div>
      <ul className="mt-5 grid gap-3 sm:grid-cols-2">
        {sorted.map(({ b, km }) => (
          <li key={b.id}>
            <button onClick={() => onPick(b.slug)} className="flex w-full items-center gap-4 rounded-2xl border border-line bg-white p-5 text-start transition-colors hover:border-brand/50">
              <span className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-card text-navy">
                <MapPin className="h-5 w-5" />
              </span>
              <span className="flex-1">
                <span className="block font-display text-lg font-extrabold">{b.name}</span>
                <span className="block text-[15px] text-muted">{[b.address, b.city].filter(Boolean).join(", ")}</span>
              </span>
              {km != null && <span className="shrink-0 text-[15px] font-bold text-navy">{km < 10 ? km.toFixed(1) : Math.round(km)} ק״מ</span>}
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

function PickSlot({
  service,
  branch,
  doctors,
  preferredDoctor,
  date,
  onDate,
  onPick,
  onClearDoctor,
}: {
  service: Service;
  branch: Branch;
  doctors: Doctor[];
  preferredDoctor: string | null;
  date: string;
  onDate: (d: string) => void;
  onPick: (doctorId: string, slot: string) => void;
  onClearDoctor: () => void;
}) {
  const today = israelToday();
  const days = useMemo(() => Array.from({ length: DAYS_AHEAD }, (_, i) => addDays(today, i)), [today]);
  const [schedules, setSchedules] = useState<Schedule[] | null>(null);
  const [slots, setSlots] = useState<Record<string, string[]> | null>(null);
  const [nextSlot, setNextSlot] = useState<string | null | undefined>(undefined);

  const candidates = useMemo(() => {
    const ids = new Set((schedules ?? []).map((s) => s.doctor_id));
    return doctors.filter((d) => ids.has(d.id) && (!preferredDoctor || d.id === preferredDoctor));
  }, [schedules, doctors, preferredDoctor]);

  const workingDays = useMemo(() => {
    const ids = new Set(candidates.map((d) => d.id));
    return new Set((schedules ?? []).filter((s) => ids.has(s.doctor_id)).map((s) => s.weekday));
  }, [schedules, candidates]);

  // Schedules for this branch.
  useEffect(() => {
    let cancelled = false;
    getSupabase()
      .from("doctor_schedules")
      .select("*")
      .eq("branch_id", branch.id)
      .then(({ data }) => !cancelled && setSchedules((data ?? []) as Schedule[]));
    return () => {
      cancelled = true;
    };
  }, [branch.id]);

  // First available slot, to jump straight to it.
  useEffect(() => {
    let cancelled = false;
    getSupabase()
      .rpc("next_available", { p_service: service.slug, p_branch: branch.id, p_days: 30 })
      .then(({ data }) => {
        if (cancelled) return;
        const rows = ((data ?? []) as { doctor_id: string; first_slot: string | null }[]).filter(
          (r) => r.first_slot && (!preferredDoctor || r.doctor_id === preferredDoctor),
        );
        rows.sort((a, b) => a.first_slot!.localeCompare(b.first_slot!));
        const first = rows[0]?.first_slot ?? null;
        setNextSlot(first);
        if (!date && first) onDate(israelDateOf(first));
      });
    return () => {
      cancelled = true;
    };
  }, [service.slug, branch.id, preferredDoctor]);

  const day = date || today;

  // Slots per doctor for the selected day.
  useEffect(() => {
    if (!schedules) return;
    let cancelled = false;
    setSlots(null);
    const working = candidates.filter((d) => schedules.some((s) => s.doctor_id === d.id && s.weekday === weekdayOf(day)));
    Promise.all(
      working.map(async (d) => {
        const { data } = await getSupabase().rpc("available_slots", { p_doctor: d.id, p_branch: branch.id, p_day: day });
        return [d.id, ((data ?? []) as { starts_at: string }[]).map((r) => r.starts_at)] as const;
      }),
    ).then((entries) => !cancelled && setSlots(Object.fromEntries(entries)));
    return () => {
      cancelled = true;
    };
  }, [schedules, candidates, day, branch.id]);

  const preferred = preferredDoctor ? doctors.find((d) => d.id === preferredDoctor) : null;

  return (
    <section aria-labelledby="pick-slot">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id="pick-slot" className="font-display text-2xl font-extrabold">
            בחרו מועד
          </h2>
          <p className="text-muted">
            {service.name} · {branch.name}
            {preferred && (
              <>
                {" "}
                · {preferred.title} {preferred.name}{" "}
                <button onClick={onClearDoctor} className="font-semibold text-brand-700 underline">
                  (כל הרופאים)
                </button>
              </>
            )}
          </p>
        </div>
        {nextSlot && (
          <button onClick={() => onDate(israelDateOf(nextSlot))} className="rounded-lg bg-brand-50 px-3 py-2 text-[15px] font-bold text-brand-700 hover:bg-brand-100">
            התור הפנוי הקרוב: {relativeDay(israelDateOf(nextSlot))} {formatTime(nextSlot)}
          </button>
        )}
      </div>

      <div className="mt-5 flex gap-2 overflow-x-auto pb-2" role="listbox" aria-label="בחירת יום">
        {days.map((d) => {
          const works = workingDays.has(weekdayOf(d));
          const selected = d === day;
          return (
            <button
              key={d}
              role="option"
              aria-selected={selected}
              disabled={!works}
              onClick={() => onDate(d)}
              className={`flex w-[68px] shrink-0 flex-col items-center rounded-xl border py-2.5 transition-colors ${
                selected ? "border-navy bg-navy text-white" : works ? "border-line bg-white hover:border-navy/40" : "border-transparent bg-card/60 text-muted/50"
              }`}
            >
              <span className="text-xs font-bold">{d === today ? "היום" : WEEKDAYS_SHORT[weekdayOf(d)]}</span>
              <span className="font-display text-xl font-extrabold">{Number(d.slice(8, 10))}</span>
              <span className="text-[11px] opacity-70">{formatDate(`${d}T12:00:00Z`, { month: "short" })}</span>
            </button>
          );
        })}
      </div>

      <div className="mt-6" aria-live="polite">
        {!schedules || !slots ? (
          <p className="flex items-center gap-2 text-muted">
            <Loader2 className="h-5 w-5 animate-spin" /> טוען תורים פנויים…
          </p>
        ) : Object.keys(slots).length === 0 || Object.values(slots).every((s) => s.length === 0) ? (
          <div className="rounded-2xl bg-card p-6">
            <p className="font-bold">אין תורים פנויים ב{relativeDay(day) === "היום" ? "יום הזה" : relativeDay(day)}.</p>
            {nextSlot ? (
              <button onClick={() => onDate(israelDateOf(nextSlot))} className="mt-2 font-bold text-brand-700 underline">
                לתור הפנוי הקרוב ({relativeDay(israelDateOf(nextSlot))}, {formatTime(nextSlot)})
              </button>
            ) : (
              nextSlot === null && <p className="mt-1 text-muted">לא נמצאו תורים פנויים בחודש הקרוב בסניף הזה. נסו סניף אחר.</p>
            )}
          </div>
        ) : (
          <ul className="space-y-4">
            {candidates
              .filter((d) => slots[d.id]?.length)
              .map((d) => (
                <li key={d.id} className="rounded-2xl border border-line bg-white p-5">
                  <div className="flex items-center gap-3">
                    <span className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-navy text-white">
                      {d.title ? <span className="font-bold">{d.name.slice(0, 1)}</span> : <UserRound className="h-5 w-5" />}
                    </span>
                    <div>
                      <p className="font-display text-lg font-extrabold">{[d.title, d.name].filter(Boolean).join(" ")}</p>
                      {d.bio && <p className="text-[15px] text-muted">{d.bio}</p>}
                    </div>
                  </div>
                  <div className="mt-4 grid grid-cols-4 gap-2 sm:grid-cols-6 lg:grid-cols-8" dir="ltr">
                    {slots[d.id].map((slot) => (
                      <button
                        key={slot}
                        onClick={() => onPick(d.id, slot)}
                        className="rounded-lg border border-line py-2 font-bold tabular-nums transition-colors hover:border-brand hover:bg-brand hover:text-white"
                        aria-label={`${formatTime(slot)} אצל ${[d.title, d.name].filter(Boolean).join(" ")}`}
                      >
                        {formatTime(slot)}
                      </button>
                    ))}
                  </div>
                </li>
              ))}
          </ul>
        )}
      </div>
    </section>
  );
}

function Confirm({
  service,
  branch,
  doctor,
  slot,
  userId,
  profile,
  returnTo,
  onBack,
  onDone,
}: {
  service: Service;
  branch: Branch;
  doctor: Doctor;
  slot: string;
  userId: string | null;
  profile: Profile | null;
  returnTo: string;
  onBack: () => void;
  onDone: (id: string) => void | Promise<void>;
}) {
  const [name, setName] = useState(profile?.full_name ?? "");
  const [phone, setPhone] = useState(profile?.phone ?? "");
  const [reason, setReason] = useState("");
  const [agree, setAgree] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (name.trim().length < 2) return setError("נא למלא שם מלא.");
    if (!isValidIsraeliPhone(phone)) return setError("נא למלא מספר טלפון תקין.");
    if (!agree) return setError("יש לאשר את תנאי השימוש ומדיניות הפרטיות.");
    setBusy(true);
    const { data, error: rpcError } = await getSupabase().rpc("book_appointment", {
      p_doctor: doctor.id,
      p_branch: branch.id,
      p_starts_at: slot,
      p_patient_name: name,
      p_patient_phone: phone,
      p_reason: reason,
    });
    setBusy(false);
    if (rpcError) {
      const key = Object.keys(ERRORS).find((k) => rpcError.message.includes(k));
      setError(key ? ERRORS[key] : "לא הצלחנו לקבוע את התור. נסו שוב.");
      return;
    }
    await onDone(data as string);
  }

  const summary = (
    <div className="rounded-2xl border border-line bg-white p-6">
      <p className="text-sm font-bold text-muted">פרטי התור</p>
      <p className="mt-2 font-display text-2xl font-extrabold">
        {formatDate(slot)}, {formatTime(slot)}
      </p>
      <dl className="mt-4 space-y-2 text-[16px]">
        <div className="flex gap-2">
          <dt className="w-16 shrink-0 text-muted">תחום</dt>
          <dd className="font-semibold">{service.name}</dd>
        </div>
        <div className="flex gap-2">
          <dt className="w-16 shrink-0 text-muted">רופא/ה</dt>
          <dd className="font-semibold">{[doctor.title, doctor.name].filter(Boolean).join(" ")}</dd>
        </div>
        <div className="flex gap-2">
          <dt className="w-16 shrink-0 text-muted">סניף</dt>
          <dd className="font-semibold">{[branch.name, branch.address].filter(Boolean).join(", ")}</dd>
        </div>
      </dl>
      <button onClick={onBack} className="mt-4 inline-flex items-center gap-1 font-semibold text-brand-700 hover:underline">
        <ArrowRight className="h-4 w-4" /> שינוי מועד
      </button>
    </div>
  );

  if (!userId) {
    const next = encodeURIComponent(returnTo);
    return (
      <div className="grid gap-6 lg:grid-cols-2">
        {summary}
        <div className="rounded-2xl border border-line bg-white p-6">
          <h2 className="font-display text-xl font-extrabold">כמעט סיימנו</h2>
          <p className="mt-1 text-muted">כדי לשמור את התור ולנהל אותו, התחברו לאזור האישי או צרו חשבון — זה לוקח פחות מדקה. התור שבחרתם יחכה לכם כאן.</p>
          <div className="mt-5 grid gap-2">
            <Link href={`/login?next=${next}`} className="flex items-center justify-center rounded-xl bg-navy py-3.5 font-bold text-white hover:bg-navy-800">
              כניסה לאזור האישי
            </Link>
            <Link href={`/signup?next=${next}`} className="flex items-center justify-center rounded-xl border-2 border-navy/15 py-3.5 font-bold hover:border-navy/40">
              יצירת חשבון חדש
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      {summary}
      <form onSubmit={submit} className="rounded-2xl border border-line bg-white p-6" noValidate>
        <h2 className="font-display text-xl font-extrabold">פרטי המטופל/ת</h2>
        <div className="mt-4 space-y-4">
          <label className="block">
            <span className="mb-1.5 block text-sm font-bold">שם מלא</span>
            <input value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" required className="h-12 w-full rounded-xl border border-line px-3 outline-none focus:border-brand" />
          </label>
          <label className="block">
            <span className="mb-1.5 block text-sm font-bold">טלפון נייד</span>
            <input value={phone} onChange={(e) => setPhone(e.target.value)} type="tel" inputMode="tel" autoComplete="tel" dir="ltr" required className="h-12 w-full rounded-xl border border-line px-3 text-end outline-none focus:border-brand" />
          </label>
          <label className="block">
            <span className="mb-1.5 block text-sm font-bold">
              סיבת הפנייה <span className="font-normal text-muted">(לא חובה)</span>
            </span>
            <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={3} maxLength={500} className="w-full rounded-xl border border-line p-3 outline-none focus:border-brand" />
          </label>
          <label className="flex items-start gap-3 text-[15px]">
            <input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} className="mt-1 h-5 w-5 accent-[#008c47]" />
            <span>
              קראתי ואני מסכים/ה ל
              <Link href="/terms" target="_blank" className="font-semibold text-brand-700 underline">
                תנאי השימוש
              </Link>{" "}
              ול
              <Link href="/privacy" target="_blank" className="font-semibold text-brand-700 underline">
                מדיניות הפרטיות
              </Link>
              .
            </span>
          </label>
        </div>
        {error && (
          <p role="alert" className="mt-4 rounded-xl bg-red-50 p-3 font-semibold text-danger">
            {error}
          </p>
        )}
        <button disabled={busy} className="mt-5 flex w-full items-center justify-center gap-2 rounded-xl bg-brand py-4 text-lg font-bold text-white hover:bg-brand-600 disabled:opacity-60">
          {busy ? <Loader2 className="h-5 w-5 animate-spin" /> : <CalendarCheck className="h-5 w-5" />}
          אישור וקביעת התור
        </button>
      </form>
    </div>
  );
}

function Success({ id, slot, rescheduled, service, branch, doctor }: { id: string; slot: string; rescheduled: boolean; service: Service; branch: Branch; doctor: Doctor }) {
  const doctorName = [doctor.title, doctor.name].filter(Boolean).join(" ");
  function addToCalendar() {
    const end = new Date(new Date(slot).getTime() + 15 * 60000).toISOString();
    downloadIcs(
      "bikur-rofe-appointment.ics",
      appointmentIcs({
        id,
        starts_at: slot,
        ends_at: end,
        title: `ביקור רופא — ${service.name}`,
        location: [branch.name, branch.address, branch.city].filter(Boolean).join(", "),
        description: `תור אצל ${doctorName}. ניהול התור באזור האישי.`,
      }),
    );
  }
  return (
    <div className="mx-auto max-w-xl text-center">
      <span className="mx-auto grid h-16 w-16 place-items-center rounded-full bg-brand text-white">
        <Check className="h-8 w-8" />
      </span>
      <h2 className="mt-5 font-display text-3xl font-black">{rescheduled ? "התור הועבר!" : "התור נקבע!"}</h2>
      <p className="mt-2 text-lg text-muted">
        {formatDate(slot)} בשעה {formatTime(slot)} · {doctorName} · {branch.name}
      </p>
      <div className="mt-8 grid gap-2 sm:grid-cols-3">
        <button onClick={addToCalendar} className="flex items-center justify-center gap-2 rounded-xl border border-line bg-white py-3 font-bold hover:border-navy/30">
          <CalendarPlus className="h-5 w-5" /> הוספה ליומן
        </button>
        <a href={wazeLink(branch)} target="_blank" rel="noreferrer" className="flex items-center justify-center gap-2 rounded-xl border border-line bg-white py-3 font-bold hover:border-navy/30">
          <Navigation className="h-5 w-5" /> ניווט לסניף
        </a>
        <Link href="/account" className="flex items-center justify-center gap-2 rounded-xl bg-navy py-3 font-bold text-white hover:bg-navy-800">
          <Clock className="h-5 w-5" /> התורים שלי
        </Link>
      </div>
    </div>
  );
}
