"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Loader2 } from "lucide-react";
import { getSupabase } from "@/lib/supabase/client";
import { isValidIsraeliPhone } from "@/lib/format";

function safeNext(value: string | null): string {
  return value && value.startsWith("/") && !value.startsWith("//") ? value : "/account";
}

const MESSAGES: Record<string, string> = {
  "Invalid login credentials": "אימייל או סיסמה שגויים.",
  "User already registered": "כבר קיים חשבון עם האימייל הזה. נסו להתחבר.",
  "Email not confirmed": "יש לאשר את כתובת האימייל דרך הקישור שנשלח אליכם.",
};

function translate(message: string): string {
  return MESSAGES[message] ?? (message.toLowerCase().includes("password") ? "הסיסמה צריכה להכיל לפחות 8 תווים." : "משהו השתבש. נסו שוב.");
}

const field = "h-12 w-full rounded-xl border border-line px-3 outline-none focus:border-brand";

export function AuthForm({ mode }: { mode: "login" | "signup" }) {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [phone, setPhone] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setInfo(null);
    const supabase = getSupabase();
    if (mode === "signup") {
      if (fullName.trim().length < 2) return setError("נא למלא שם מלא.");
      if (!isValidIsraeliPhone(phone)) return setError("נא למלא מספר טלפון תקין.");
      if (password.length < 8) return setError("הסיסמה צריכה להכיל לפחות 8 תווים.");
    }
    setBusy(true);
    if (mode === "login") {
      const { error: err } = await supabase.auth.signInWithPassword({ email: email.trim(), password });
      setBusy(false);
      if (err) return setError(translate(err.message));
    } else {
      const { data, error: err } = await supabase.auth.signUp({
        email: email.trim(),
        password,
        options: {
          data: { full_name: fullName.trim(), phone: phone.trim() },
          emailRedirectTo: `${window.location.origin}/auth/callback?next=${encodeURIComponent(next)}`,
        },
      });
      setBusy(false);
      if (err) return setError(translate(err.message));
      if (!data.session) {
        setInfo("שלחנו אליכם אימייל לאישור החשבון. אחרי האישור תחזרו ישירות לכאן.");
        return;
      }
    }
    router.replace(next);
    router.refresh();
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      {mode === "signup" && (
        <>
          <label className="block">
            <span className="mb-1.5 block text-sm font-bold">שם מלא</span>
            <input value={fullName} onChange={(e) => setFullName(e.target.value)} autoComplete="name" required className={field} />
          </label>
          <label className="block">
            <span className="mb-1.5 block text-sm font-bold">טלפון נייד</span>
            <input value={phone} onChange={(e) => setPhone(e.target.value)} type="tel" inputMode="tel" autoComplete="tel" dir="ltr" required className={`${field} text-end`} />
          </label>
        </>
      )}
      <label className="block">
        <span className="mb-1.5 block text-sm font-bold">אימייל</span>
        <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" autoComplete="email" dir="ltr" required className={`${field} text-end`} />
      </label>
      <label className="block">
        <span className="mb-1.5 flex items-center justify-between text-sm font-bold">
          סיסמה
          {mode === "login" && (
            <Link href="/forgot-password" className="font-semibold text-brand-700 hover:underline">
              שכחתי סיסמה
            </Link>
          )}
        </span>
        <input
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          type="password"
          autoComplete={mode === "login" ? "current-password" : "new-password"}
          dir="ltr"
          required
          minLength={8}
          className={`${field} text-end`}
        />
        {mode === "signup" && <span className="mt-1 block text-sm text-muted">לפחות 8 תווים</span>}
      </label>
      {error && (
        <p role="alert" className="rounded-xl bg-red-50 p-3 font-semibold text-danger">
          {error}
        </p>
      )}
      {info && (
        <p role="status" className="rounded-xl bg-brand-50 p-3 font-semibold text-brand-700">
          {info}
        </p>
      )}
      <button disabled={busy} className="flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-navy font-bold text-white hover:bg-navy-800 disabled:opacity-60">
        {busy && <Loader2 className="h-5 w-5 animate-spin" />}
        {mode === "login" ? "כניסה" : "יצירת חשבון"}
      </button>
      <p className="text-center text-[15px] text-muted">
        {mode === "login" ? "עדיין אין לכם חשבון? " : "כבר יש לכם חשבון? "}
        <Link href={`/${mode === "login" ? "signup" : "login"}?next=${encodeURIComponent(next)}`} className="font-bold text-brand-700 hover:underline">
          {mode === "login" ? "הרשמה" : "כניסה"}
        </Link>
      </p>
    </form>
  );
}

export function ForgotForm() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  return sent ? (
    <p role="status" className="rounded-xl bg-brand-50 p-4 font-semibold text-brand-700">
      אם קיים חשבון עם האימייל הזה, שלחנו אליו קישור לאיפוס הסיסמה.
    </p>
  ) : (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        setBusy(true);
        await getSupabase().auth.resetPasswordForEmail(email.trim(), { redirectTo: `${window.location.origin}/auth/callback?next=/reset-password` });
        setBusy(false);
        setSent(true);
      }}
      className="space-y-4"
    >
      <label className="block">
        <span className="mb-1.5 block text-sm font-bold">אימייל</span>
        <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" autoComplete="email" dir="ltr" required className={`${field} text-end`} />
      </label>
      <button disabled={busy} className="flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-navy font-bold text-white hover:bg-navy-800 disabled:opacity-60">
        {busy && <Loader2 className="h-5 w-5 animate-spin" />}
        שליחת קישור לאיפוס
      </button>
    </form>
  );
}

export function ResetForm() {
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        if (password.length < 8) return setError("הסיסמה צריכה להכיל לפחות 8 תווים.");
        setBusy(true);
        const { error: err } = await getSupabase().auth.updateUser({ password });
        setBusy(false);
        if (err) return setError("הקישור פג תוקף. בקשו קישור חדש.");
        router.replace("/account");
        router.refresh();
      }}
      className="space-y-4"
    >
      <label className="block">
        <span className="mb-1.5 block text-sm font-bold">סיסמה חדשה</span>
        <input value={password} onChange={(e) => setPassword(e.target.value)} type="password" autoComplete="new-password" dir="ltr" minLength={8} required className={`${field} text-end`} />
      </label>
      {error && (
        <p role="alert" className="rounded-xl bg-red-50 p-3 font-semibold text-danger">
          {error}
        </p>
      )}
      <button disabled={busy} className="flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-navy font-bold text-white hover:bg-navy-800 disabled:opacity-60">
        {busy && <Loader2 className="h-5 w-5 animate-spin" />}
        שמירת סיסמה
      </button>
    </form>
  );
}
