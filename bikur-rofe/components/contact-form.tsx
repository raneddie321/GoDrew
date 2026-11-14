"use client";

import { useState } from "react";
import { Check, Loader2, Send } from "lucide-react";
import { getSupabase } from "@/lib/supabase/client";
import { isValidIsraeliPhone } from "@/lib/format";

const SUBJECTS = ["זימון או ביטול תור", "שאלה על שירות", "שירות לקוחות ומשוב", "נגישות", "אחר"];
const field = "h-12 w-full rounded-xl border border-line px-3 outline-none focus:border-brand";

export function ContactForm() {
  const [form, setForm] = useState({ name: "", phone: "", email: "", subject: SUBJECTS[0], message: "", website: "" });
  const [state, setState] = useState<"idle" | "busy" | "sent">("idle");
  const [error, setError] = useState<string | null>(null);
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => setForm({ ...form, [k]: e.target.value });

  if (state === "sent") {
    return (
      <div role="status" className="rounded-2xl border border-brand/30 bg-brand-50 p-8 text-center">
        <span className="mx-auto grid h-12 w-12 place-items-center rounded-full bg-brand text-white">
          <Check className="h-6 w-6" />
        </span>
        <p className="mt-4 font-display text-xl font-extrabold">הפנייה התקבלה</p>
        <p className="mt-1 text-muted">נחזור אליכם בהקדם, בדרך כלל עד יום עסקים אחד.</p>
      </div>
    );
  }

  return (
    <form
      noValidate
      className="space-y-4 rounded-2xl border border-line bg-white p-6"
      onSubmit={async (e) => {
        e.preventDefault();
        setError(null);
        if (form.website) return setState("sent"); // honeypot
        if (form.name.trim().length < 2) return setError("נא למלא שם.");
        if (!form.phone.trim() && !form.email.trim()) return setError("נא להשאיר טלפון או אימייל לחזרה.");
        if (form.phone.trim() && !isValidIsraeliPhone(form.phone)) return setError("מספר הטלפון אינו תקין.");
        if (form.message.trim().length < 5) return setError("נא לכתוב את תוכן הפנייה.");
        setState("busy");
        const { error: err } = await getSupabase()
          .from("contact_messages")
          .insert({ name: form.name.trim(), phone: form.phone.trim() || null, email: form.email.trim() || null, subject: form.subject, message: form.message.trim() });
        if (err) {
          setState("idle");
          return setError("השליחה נכשלה. נסו שוב.");
        }
        setState("sent");
      }}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="block">
          <span className="mb-1.5 block text-sm font-bold">שם מלא</span>
          <input value={form.name} onChange={set("name")} autoComplete="name" className={field} />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-sm font-bold">טלפון</span>
          <input value={form.phone} onChange={set("phone")} type="tel" dir="ltr" autoComplete="tel" className={`${field} text-end`} />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-sm font-bold">אימייל</span>
          <input value={form.email} onChange={set("email")} type="email" dir="ltr" autoComplete="email" className={`${field} text-end`} />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-sm font-bold">נושא</span>
          <select value={form.subject} onChange={set("subject")} className={`${field} bg-white`}>
            {SUBJECTS.map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
        </label>
      </div>
      <label className="block">
        <span className="mb-1.5 block text-sm font-bold">תוכן הפנייה</span>
        <textarea value={form.message} onChange={set("message")} rows={5} maxLength={2000} className="w-full rounded-xl border border-line p-3 outline-none focus:border-brand" />
      </label>
      <input value={form.website} onChange={set("website")} tabIndex={-1} autoComplete="off" aria-hidden="true" className="hidden" />
      <p className="text-sm text-muted">אין לשלוח בטופס מידע רפואי רגיש. בשאלות רפואיות דחופות יש לפנות לסניף או למד״א (101).</p>
      {error && (
        <p role="alert" className="rounded-xl bg-red-50 p-3 font-semibold text-danger">
          {error}
        </p>
      )}
      <button disabled={state === "busy"} className="inline-flex items-center gap-2 rounded-xl bg-navy px-6 py-3 font-bold text-white hover:bg-navy-800 disabled:opacity-60">
        {state === "busy" ? <Loader2 className="h-5 w-5 animate-spin" /> : <Send className="h-5 w-5" />}
        שליחה
      </button>
    </form>
  );
}
