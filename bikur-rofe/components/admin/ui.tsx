"use client";

import { Loader2 } from "lucide-react";

export const input = "h-11 w-full rounded-lg border border-line bg-white px-3 outline-none focus:border-brand";

export function Field({ label, children, className = "" }: { label: string; children: React.ReactNode; className?: string }) {
  return (
    <label className={`block ${className}`}>
      <span className="mb-1 block text-sm font-bold text-navy/80">{label}</span>
      {children}
    </label>
  );
}

export function Check({ label, checked, onChange }: { label: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="inline-flex cursor-pointer items-center gap-2 font-semibold">
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} className="h-5 w-5 accent-[#008c47]" />
      {label}
    </label>
  );
}

export function SaveBar({ busy, message, error, children }: { busy: boolean; message?: string | null; error?: string | null; children?: React.ReactNode }) {
  return (
    <div className="flex flex-wrap items-center gap-3 border-t border-line pt-4">
      <button disabled={busy} className="inline-flex items-center gap-2 rounded-lg bg-navy px-5 py-2.5 font-bold text-white hover:bg-navy-800 disabled:opacity-60">
        {busy && <Loader2 className="h-4 w-4 animate-spin" />}
        שמירה
      </button>
      {children}
      {message && (
        <span role="status" className="font-semibold text-brand-700">
          {message}
        </span>
      )}
      {error && (
        <span role="alert" className="font-semibold text-danger">
          {error}
        </span>
      )}
    </div>
  );
}

export function dbError(message: string): string {
  if (/duplicate key|unique/i.test(message)) return "כבר קיים פריט עם אותו מזהה (slug).";
  if (/foreign key|violates/i.test(message)) return "לא ניתן למחוק — קיימים תורים או נתונים מקושרים. אפשר להשבית במקום.";
  if (/row-level security|permission/i.test(message)) return "אין הרשאה לפעולה זו.";
  return "הפעולה נכשלה. נסו שוב.";
}
