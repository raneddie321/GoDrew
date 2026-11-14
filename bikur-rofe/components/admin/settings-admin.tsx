"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { getSupabase } from "@/lib/supabase/client";
import type { Settings } from "@/lib/types";
import { Field, SaveBar, input } from "./ui";

export interface StaffMember {
  id: string;
  email: string;
  full_name: string | null;
  role: "staff" | "admin";
}

const ROLE_ERRORS: Record<string, string> = {
  user_not_found: "לא נמצא משתמש עם האימייל הזה. יש להירשם קודם לאתר.",
  cannot_demote_self: "אי אפשר להסיר את ההרשאה של עצמך.",
  forbidden: "רק מנהל/ת מערכת יכול/ה לשנות הרשאות.",
};

export function SettingsAdmin({ settings, staff, isAdmin, myId, sampleCount }: { settings: Settings; staff: StaffMember[]; isAdmin: boolean; myId: string; sampleCount: number }) {
  const router = useRouter();
  const [s, setS] = useState(settings);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<"staff" | "admin">("staff");
  const [roleMsg, setRoleMsg] = useState<{ ok: boolean; text: string } | null>(null);

  async function saveSettings(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    setError(null);
    const { error: err } = await getSupabase()
      .from("site_settings")
      .upsert(Object.entries(s).map(([key, value]) => ({ key, value: String(value).trim() })));
    setBusy(false);
    if (err) setError("השמירה נכשלה.");
    else {
      setMsg("נשמר ✓");
      router.refresh();
    }
  }

  async function setUserRole(targetEmail: string, newRole: "patient" | "staff" | "admin") {
    setRoleMsg(null);
    const { error: err } = await getSupabase().rpc("set_user_role", { p_email: targetEmail, p_role: newRole });
    if (err) {
      const code = Object.keys(ROLE_ERRORS).find((k) => err.message.includes(k));
      return setRoleMsg({ ok: false, text: code ? ROLE_ERRORS[code] : "הפעולה נכשלה." });
    }
    setRoleMsg({ ok: true, text: newRole === "patient" ? "ההרשאה הוסרה." : "ההרשאה עודכנה." });
    setEmail("");
    router.refresh();
  }

  async function deleteSamples() {
    if (!confirm("למחוק את כל הסניפים והרופאים שמסומנים כדוגמה (וכל תור שנקבע אליהם)? לא ניתן לבטל.")) return;
    const { error: err } = await getSupabase().rpc("delete_sample_data");
    if (err) alert("המחיקה נכשלה.");
    router.refresh();
  }

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <form onSubmit={saveSettings} className="space-y-4 rounded-2xl border border-line bg-white p-6">
        <h2 className="font-display text-xl font-extrabold">פרטי התקשרות באתר</h2>
        <p className="text-sm text-muted">מוצגים בכותרת, בתחתית, בעמוד יצירת הקשר ובהצהרת הנגישות. שדה ריק לא יוצג.</p>
        <Field label="טלפון מוקד">
          <input className={`${input} text-end`} dir="ltr" value={s.call_center_phone} onChange={(e) => setS({ ...s, call_center_phone: e.target.value })} placeholder="*1234" />
        </Field>
        <Field label="וואטסאפ (מספר בינלאומי, למשל 972501234567)">
          <input className={`${input} text-end`} dir="ltr" value={s.whatsapp} onChange={(e) => setS({ ...s, whatsapp: e.target.value })} />
        </Field>
        <Field label="אימייל">
          <input className={`${input} text-end`} dir="ltr" type="email" value={s.email} onChange={(e) => setS({ ...s, email: e.target.value })} />
        </Field>
        <SaveBar busy={busy} message={msg} error={error} />
      </form>

      <section className="space-y-4 rounded-2xl border border-line bg-white p-6">
        <h2 className="font-display text-xl font-extrabold">צוות והרשאות</h2>
        <ul className="divide-y divide-line">
          {staff.map((m) => (
            <li key={m.id} className="flex items-center justify-between gap-3 py-2.5">
              <span>
                <span className="block font-semibold">{m.full_name || m.email}</span>
                <span className="block text-sm text-muted" dir="ltr">
                  {m.email}
                </span>
              </span>
              <span className="flex items-center gap-2">
                <span className="rounded-full bg-card px-2.5 py-1 text-sm font-bold">{m.role === "admin" ? "מנהל/ת מערכת" : "צוות"}</span>
                {isAdmin && m.id !== myId && (
                  <button onClick={() => confirm(`להסיר את ההרשאה של ${m.email}?`) && setUserRole(m.email, "patient")} className="text-sm font-semibold text-danger hover:underline">
                    הסרה
                  </button>
                )}
              </span>
            </li>
          ))}
        </ul>
        {isAdmin ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (email.trim()) setUserRole(email, role);
            }}
            className="flex flex-wrap items-end gap-2 border-t border-line pt-4"
          >
            <Field label="הוספת איש/אשת צוות לפי אימייל" className="min-w-56 flex-1">
              <input className={`${input} text-end`} dir="ltr" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
            </Field>
            <select className={`${input} w-40`} value={role} onChange={(e) => setRole(e.target.value as "staff" | "admin")} aria-label="תפקיד">
              <option value="staff">צוות</option>
              <option value="admin">מנהל/ת מערכת</option>
            </select>
            <button className="h-11 rounded-lg bg-navy px-4 font-bold text-white hover:bg-navy-800">הוספה</button>
          </form>
        ) : (
          <p className="text-sm text-muted">רק מנהל/ת מערכת יכול/ה להוסיף צוות.</p>
        )}
        {roleMsg && (
          <p role="status" className={`font-semibold ${roleMsg.ok ? "text-brand-700" : "text-danger"}`}>
            {roleMsg.text}
          </p>
        )}
      </section>

      {sampleCount > 0 && (
        <section className="space-y-3 rounded-2xl border border-amber-200 bg-amber-50 p-6 lg:col-span-2">
          <h2 className="font-display text-xl font-extrabold text-amber-900">נתוני דוגמה ({sampleCount})</h2>
          <p className="text-amber-900">
            האתר הוקם עם סניפים ורופאים לדוגמה כדי שאפשר יהיה לנסות אותו. עדכנו אותם לפרטים אמיתיים בעמודי “סניפים” ו“רופאים” (שמירה מסירה את סימון הדוגמה), או מחקו את כל מה שנשאר מסומן. כל עוד יש נתוני דוגמה, מוצגת באתר הודעה על כך.
          </p>
          <button onClick={deleteSamples} className="rounded-lg bg-danger px-4 py-2.5 font-bold text-white hover:opacity-90">
            מחיקת כל נתוני הדוגמה
          </button>
        </section>
      )}
    </div>
  );
}
