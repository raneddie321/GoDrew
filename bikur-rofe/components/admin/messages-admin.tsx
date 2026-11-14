"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Mail, Phone } from "lucide-react";
import { getSupabase } from "@/lib/supabase/client";
import { formatDate, formatTime, telLink } from "@/lib/format";

export interface Message {
  id: number;
  name: string;
  phone: string | null;
  email: string | null;
  subject: string;
  message: string;
  handled: boolean;
  created_at: string;
}

export function MessagesAdmin({ messages }: { messages: Message[] }) {
  const router = useRouter();
  const [showHandled, setShowHandled] = useState(false);
  const [busy, setBusy] = useState<number | null>(null);
  const open = messages.filter((m) => !m.handled);
  const list = showHandled ? messages : open;

  async function toggle(m: Message) {
    setBusy(m.id);
    await getSupabase().from("contact_messages").update({ handled: !m.handled }).eq("id", m.id);
    setBusy(null);
    router.refresh();
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="font-display text-2xl font-extrabold">פניות מהאתר {open.length > 0 && <span className="text-brand">({open.length} פתוחות)</span>}</h1>
        <label className="inline-flex items-center gap-2 text-sm font-semibold">
          <input type="checkbox" checked={showHandled} onChange={(e) => setShowHandled(e.target.checked)} className="accent-[#008c47]" />
          הצגת פניות שטופלו
        </label>
      </div>
      {list.length === 0 ? (
        <p className="mt-6 rounded-2xl border border-dashed border-line p-10 text-center text-muted">אין פניות פתוחות.</p>
      ) : (
        <ul className="mt-6 space-y-3">
          {list.map((m) => (
            <li key={m.id} className={`rounded-2xl border p-5 ${m.handled ? "border-line bg-card/50" : "border-line bg-white"}`}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="font-bold">
                    {m.name} <span className="font-normal text-muted">· {m.subject}</span>
                  </p>
                  <p className="text-sm text-muted">
                    {formatDate(m.created_at, { day: "numeric", month: "long" })} {formatTime(m.created_at)}
                  </p>
                </div>
                <button
                  disabled={busy === m.id}
                  onClick={() => toggle(m)}
                  className={`rounded-lg px-3 py-1.5 text-sm font-bold disabled:opacity-50 ${m.handled ? "border border-line hover:bg-card" : "bg-brand text-white hover:bg-brand-600"}`}
                >
                  {m.handled ? "החזרה לפתוחות" : "סימון כטופלה"}
                </button>
              </div>
              <p className="mt-3 whitespace-pre-wrap leading-7">{m.message}</p>
              <div className="mt-3 flex flex-wrap gap-4 text-sm">
                {m.phone && (
                  <a href={telLink(m.phone)} className="inline-flex items-center gap-1 font-semibold text-brand-700 hover:underline" dir="ltr">
                    <Phone className="h-4 w-4" /> {m.phone}
                  </a>
                )}
                {m.email && (
                  <a href={`mailto:${m.email}`} className="inline-flex items-center gap-1 font-semibold text-brand-700 hover:underline">
                    <Mail className="h-4 w-4" /> {m.email}
                  </a>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
