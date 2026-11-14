import type { Metadata } from "next";
import Link from "next/link";
import { AlertTriangle, MapPin, Navigation, Siren } from "lucide-react";
import { getBranches } from "@/lib/data";
import { wazeLink } from "@/lib/format";

export const metadata: Metadata = { title: "רפואה דחופה", description: "מוקדי רפואה דחופה ללא תור — שעות פתיחה ומיקומים." };

const TREATS = ["פציעות ונקעים", "חשד לשבר", "חתכים ותפרים", "כוויות", "חום גבוה וזיהומים", "כאבי בטן", "דלקות אוזניים וגרון", "תגובות אלרגיות קלות", "גוף זר בעין"];

export default async function UrgentPage() {
  const branches = (await getBranches()).filter((b) => b.urgent_care);
  return (
    <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6">
      <span className="inline-flex items-center gap-2 rounded-full bg-red-50 px-3 py-1 text-sm font-bold text-danger">
        <Siren className="h-4 w-4" /> ללא תור מראש
      </span>
      <h1 className="mt-4 font-display text-4xl font-black">רפואה דחופה</h1>
      <p className="mt-3 max-w-2xl text-lg text-muted">מגיעים ישירות לסניף בשעות הפעילות. במקום להמתין שעות בחדר מיון — טיפול מהיר אצל רופא, כולל בדיקות במקרה הצורך.</p>

      <div className="mt-8 flex items-start gap-3 rounded-2xl border border-red-200 bg-red-50 p-5 text-red-900">
        <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" />
        <p>
          <strong>במצב מסכן חיים</strong> — כאב בחזה, קוצר נשימה חמור, סימני שבץ, איבוד הכרה או דימום שאינו נעצר — יש להתקשר מיד למד״א:{" "}
          <a href="tel:101" className="font-black underline">
            101
          </a>
          .
        </p>
      </div>

      <h2 className="mt-12 font-display text-2xl font-extrabold">במה מטפלים</h2>
      <ul className="mt-4 flex flex-wrap gap-2">
        {TREATS.map((t) => (
          <li key={t} className="rounded-full border border-line bg-white px-4 py-2 font-semibold">
            {t}
          </li>
        ))}
      </ul>

      <h2 className="mt-12 font-display text-2xl font-extrabold">מוקדים ושעות פתיחה</h2>
      <ul className="mt-4 grid gap-4 md:grid-cols-2">
        {branches.map((b) => (
          <li key={b.id} className="rounded-2xl border border-line bg-white p-6">
            <Link href={`/branches/${b.slug}`} className="font-display text-xl font-extrabold hover:text-brand-700">
              {b.name}
            </Link>
            <p className="mt-0.5 flex items-center gap-1.5 text-muted">
              <MapPin className="h-4 w-4" /> {[b.address, b.city].filter(Boolean).join(", ")}
            </p>
            <ul className="mt-3 space-y-0.5 text-[15px]">
              {b.hours.map((h) => (
                <li key={h.days} className="flex justify-between gap-3">
                  <span className="text-muted">{h.days}</span>
                  <span dir="ltr" className="font-bold">
                    {h.open}–{h.close}
                  </span>
                </li>
              ))}
            </ul>
            <div className="mt-4 flex gap-2">
              <a href={wazeLink(b)} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1.5 rounded-lg bg-navy px-4 py-2 font-bold text-white hover:bg-navy-800">
                <Navigation className="h-4 w-4" /> ניווט
              </a>
              <Link href={`/booking?service=urgent&branch=${b.slug}`} className="inline-flex items-center rounded-lg border border-line px-4 py-2 font-semibold hover:border-navy/30">
                קביעת שעת הגעה
              </Link>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
