"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { CalendarClock, CalendarPlus, CalendarX, Check, Loader2, LogOut, MapPin, Navigation, Plus, UserRound } from "lucide-react";
import { getSupabase } from "@/lib/supabase/client";
import { formatDate, formatTime, israelDateOf, isValidIsraeliPhone, relativeDay, wazeLink } from "@/lib/format";
import { appointmentIcs, downloadIcs } from "@/lib/ics";
import type { Appointment, Profile } from "@/lib/types";

const STATUS: Record<Appointment["status"], { label: string; cls: string }> = {
  booked: { label: "מאושר", cls: "bg-brand-50 text-brand-700" },
  arrived: { label: "הגעת לסניף", cls: "bg-brand-50 text-brand-700" },
  completed: { label: "הושלם", cls: "bg-card text-navy/70" },
  cancelled: { label: "בוטל", cls: "bg-red-50 text-danger" },
  no_show: { label: "לא הגיע/ה", cls: "bg-card text-navy/70" },
};

export function AccountView({ appointments, profile, email }: { appointments: Appointment[]; profile: Profile | null; email: string }) {
  const router = useRouter();
  const [tab, setTab] = useState<"upcoming" | "past" | "profile">("upcoming");
  const now = Date.now();
  const upcoming = appointments.filter((a) => a.status === "booked" && new Date(a.starts_at).getTime() > now).reverse();
  const past = appointments.filter((a) => !(a.status === "booked" && new Date(a.starts_at).getTime() > now));
  const firstName = profile?.full_name?.split(" ")[0];

  return (
    <div className="mx-auto max-w-5xl px-4 py-12 sm:px-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-4xl font-black">{firstName ? `שלום, ${firstName}` : "האזור האישי"}</h1>
          <p className="mt-1 text-lg text-muted">{upcoming.length ? `יש לך ${upcoming.length === 1 ? "תור אחד קרוב" : `${upcoming.length} תורים קרובים`}.` : "אין תורים קרובים."}</p>
        </div>
        <div className="flex gap-2">
          <Link href="/booking" className="inline-flex items-center gap-2 rounded-xl bg-brand px-5 py-3 font-bold text-white hover:bg-brand-600">
            <Plus className="h-5 w-5" /> תור חדש
          </Link>
          <form action="/auth/signout" method="post">
            <button className="inline-flex items-center gap-2 rounded-xl border border-line bg-white px-4 py-3 font-semibold hover:border-navy/30">
              <LogOut className="h-4 w-4" /> יציאה
            </button>
          </form>
        </div>
      </div>

      <div role="tablist" aria-label="אזור אישי" className="mt-8 flex gap-1 border-b border-line">
        {(
          [
            ["upcoming", `תורים קרובים (${upcoming.length})`],
            ["past", "היסטוריה"],
            ["profile", "פרטים אישיים"],
          ] as const
        ).map(([key, label]) => (
          <button
            key={key}
            role="tab"
            aria-selected={tab === key}
            onClick={() => setTab(key)}
            className={`-mb-px border-b-[3px] px-4 py-3 font-bold transition-colors ${tab === key ? "border-brand text-navy" : "border-transparent text-muted hover:text-navy"}`}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="mt-6">
        {tab === "upcoming" &&
          (upcoming.length ? (
            <ul className="space-y-4">
              {upcoming.map((a) => (
                <AppointmentCard key={a.id} a={a} onChanged={() => router.refresh()} />
              ))}
            </ul>
          ) : (
            <Empty text="אין לך תורים קרובים." />
          ))}
        {tab === "past" &&
          (past.length ? (
            <ul className="space-y-3">
              {past.map((a) => (
                <li key={a.id} className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-line bg-white p-5">
                  <div>
                    <p className="font-bold">
                      {a.service?.name} · {[a.doctor?.title, a.doctor?.name].filter(Boolean).join(" ")}
                    </p>
                    <p className="text-[15px] text-muted">
                      {formatDate(a.starts_at, { day: "numeric", month: "long", year: "numeric" })}, {formatTime(a.starts_at)} · {a.branch?.name}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className={`rounded-full px-3 py-1 text-sm font-bold ${STATUS[a.status].cls}`}>{STATUS[a.status].label}</span>
                    <Link href={`/booking?service=${a.service_slug}&branch=${a.branch?.slug ?? ""}&doctor=${a.doctor_id}`} className="text-[15px] font-bold text-brand-700 hover:underline">
                      קביעת תור חוזר
                    </Link>
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            <Empty text="עדיין אין היסטוריית ביקורים." />
          ))}
        {tab === "profile" && <ProfileForm profile={profile} email={email} />}
      </div>
    </div>
  );
}

function Empty({ text }: { text: string }) {
  return (
    <div className="rounded-2xl border border-dashed border-line bg-white p-10 text-center">
      <p className="text-lg text-muted">{text}</p>
      <Link href="/booking" className="mt-4 inline-flex items-center gap-2 rounded-xl bg-navy px-5 py-3 font-bold text-white hover:bg-navy-800">
        <CalendarPlus className="h-5 w-5" /> לזימון תור
      </Link>
    </div>
  );
}

function AppointmentCard({ a, onChanged }: { a: Appointment; onChanged: () => void }) {
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const doctorName = [a.doctor?.title, a.doctor?.name].filter(Boolean).join(" ");

  async function cancel() {
    setBusy(true);
    const { error: err } = await getSupabase().rpc("cancel_appointment", { p_id: a.id });
    setBusy(false);
    if (err) return setError("לא ניתן לבטל את התור. אם הוא כבר התחיל — פנו לסניף.");
    onChanged();
  }

  return (
    <li className="rounded-2xl border border-line bg-white p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-sm font-bold text-brand-700">{relativeDay(israelDateOf(a.starts_at))}</p>
          <p className="font-display text-2xl font-extrabold">
            {formatDate(a.starts_at)}, {formatTime(a.starts_at)}
          </p>
          <p className="mt-1 text-lg">
            {a.service?.name} · {doctorName}
          </p>
          <p className="mt-0.5 flex items-center gap-1.5 text-muted">
            <MapPin className="h-4 w-4" /> {[a.branch?.name, a.branch?.address].filter(Boolean).join(", ")}
          </p>
        </div>
        <span className={`rounded-full px-3 py-1 text-sm font-bold ${STATUS[a.status].cls}`}>{STATUS[a.status].label}</span>
      </div>
      <div className="mt-5 flex flex-wrap gap-2">
        {a.branch && (
          <a href={wazeLink(a.branch)} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1.5 rounded-lg border border-line px-4 py-2 font-semibold hover:border-navy/30">
            <Navigation className="h-4 w-4" /> ניווט
          </a>
        )}
        <button
          onClick={() =>
            downloadIcs(
              "bikur-rofe-appointment.ics",
              appointmentIcs({
                id: a.id,
                starts_at: a.starts_at,
                ends_at: a.ends_at,
                title: `ביקור רופא — ${a.service?.name ?? ""}`,
                location: [a.branch?.name, a.branch?.address, a.branch?.city].filter(Boolean).join(", "),
                description: `תור אצל ${doctorName}`,
              }),
            )
          }
          className="inline-flex items-center gap-1.5 rounded-lg border border-line px-4 py-2 font-semibold hover:border-navy/30"
        >
          <CalendarPlus className="h-4 w-4" /> הוספה ליומן
        </button>
        <Link
          href={`/booking?service=${a.service_slug}&branch=${a.branch?.slug ?? ""}&doctor=${a.doctor_id}&reschedule=${a.id}`}
          className="inline-flex items-center gap-1.5 rounded-lg border border-line px-4 py-2 font-semibold hover:border-navy/30"
        >
          <CalendarClock className="h-4 w-4" /> תור במועד אחר
        </Link>
        {!confirming ? (
          <button onClick={() => setConfirming(true)} className="inline-flex items-center gap-1.5 rounded-lg px-4 py-2 font-semibold text-danger hover:bg-red-50">
            <CalendarX className="h-4 w-4" /> ביטול התור
          </button>
        ) : (
          <span className="inline-flex items-center gap-2 rounded-lg bg-red-50 px-3 py-1.5">
            <span className="font-semibold text-danger">לבטל את התור?</span>
            <button onClick={cancel} disabled={busy} className="inline-flex items-center gap-1 rounded-md bg-danger px-3 py-1 font-bold text-white">
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : null} כן, לבטל
            </button>
            <button onClick={() => setConfirming(false)} className="rounded-md px-2 py-1 font-semibold text-navy">
              לא
            </button>
          </span>
        )}
      </div>
      {error && (
        <p role="alert" className="mt-3 font-semibold text-danger">
          {error}
        </p>
      )}
    </li>
  );
}

function ProfileForm({ profile, email }: { profile: Profile | null; email: string }) {
  const router = useRouter();
  const [name, setName] = useState(profile?.full_name ?? "");
  const [phone, setPhone] = useState(profile?.phone ?? "");
  const [state, setState] = useState<"idle" | "busy" | "saved" | "error">("idle");
  const [error, setError] = useState("");

  return (
    <form
      className="max-w-lg space-y-4 rounded-2xl border border-line bg-white p-6"
      onSubmit={async (e) => {
        e.preventDefault();
        if (name.trim().length < 2) return (setError("נא למלא שם מלא."), setState("error"));
        if (!isValidIsraeliPhone(phone)) return (setError("נא למלא מספר טלפון תקין."), setState("error"));
        setState("busy");
        const supabase = getSupabase();
        const { data: auth } = await supabase.auth.getUser();
        const { error: err } = await supabase.from("profiles").update({ full_name: name.trim(), phone: phone.trim() }).eq("id", auth.user?.id ?? "");
        if (err) return (setError("השמירה נכשלה. נסו שוב."), setState("error"));
        setState("saved");
        router.refresh();
      }}
    >
      <div className="flex items-center gap-3">
        <span className="grid h-12 w-12 place-items-center rounded-full bg-navy text-white">
          <UserRound className="h-6 w-6" />
        </span>
        <div>
          <p className="font-bold">{profile?.full_name || "החשבון שלי"}</p>
          <p className="text-[15px] text-muted" dir="ltr">
            {email}
          </p>
        </div>
      </div>
      <label className="block">
        <span className="mb-1.5 block text-sm font-bold">שם מלא</span>
        <input value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" className="h-12 w-full rounded-xl border border-line px-3 outline-none focus:border-brand" />
      </label>
      <label className="block">
        <span className="mb-1.5 block text-sm font-bold">טלפון נייד</span>
        <input value={phone} onChange={(e) => setPhone(e.target.value)} type="tel" dir="ltr" autoComplete="tel" className="h-12 w-full rounded-xl border border-line px-3 text-end outline-none focus:border-brand" />
      </label>
      {state === "error" && (
        <p role="alert" className="font-semibold text-danger">
          {error}
        </p>
      )}
      <button disabled={state === "busy"} className="inline-flex items-center gap-2 rounded-xl bg-navy px-6 py-3 font-bold text-white hover:bg-navy-800 disabled:opacity-60">
        {state === "busy" ? <Loader2 className="h-5 w-5 animate-spin" /> : state === "saved" ? <Check className="h-5 w-5" /> : null}
        {state === "saved" ? "נשמר" : "שמירה"}
      </button>
      <p className="text-sm text-muted">
        לשינוי סיסמה:{" "}
        <Link href="/forgot-password" className="font-semibold text-brand-700 underline">
          איפוס סיסמה
        </Link>
      </p>
    </form>
  );
}
