import type { Metadata } from "next";
import Link from "next/link";
import { CalendarPlus, Mail, MapPin, Phone } from "lucide-react";
import { ContactForm } from "@/components/contact-form";
import { getSettings } from "@/lib/data";
import { telLink } from "@/lib/format";

export const metadata: Metadata = { title: "צור קשר" };

export default async function ContactPage() {
  const settings = await getSettings();
  return (
    <div className="mx-auto max-w-6xl px-4 py-12 sm:px-6">
      <h1 className="font-display text-4xl font-black">צור קשר</h1>
      <p className="mt-2 text-lg text-muted">נשמח לעזור — בטופס, בטלפון או בסניף הקרוב אליכם.</p>
      <div className="mt-8 grid gap-8 lg:grid-cols-[1.5fr_1fr]">
        <ContactForm />
        <aside className="space-y-3">
          {settings.call_center_phone && (
            <a href={telLink(settings.call_center_phone)} className="flex items-center gap-3 rounded-2xl border border-line bg-white p-5 hover:border-navy/30">
              <Phone className="h-5 w-5 text-brand" />
              <span>
                <span className="block text-sm text-muted">מוקד טלפוני</span>
                <span className="block font-bold" dir="ltr">
                  {settings.call_center_phone}
                </span>
              </span>
            </a>
          )}
          {settings.email && (
            <a href={`mailto:${settings.email}`} className="flex items-center gap-3 rounded-2xl border border-line bg-white p-5 hover:border-navy/30">
              <Mail className="h-5 w-5 text-brand" />
              <span>
                <span className="block text-sm text-muted">אימייל</span>
                <span className="block font-bold">{settings.email}</span>
              </span>
            </a>
          )}
          <Link href="/booking" className="flex items-center gap-3 rounded-2xl border border-line bg-white p-5 hover:border-navy/30">
            <CalendarPlus className="h-5 w-5 text-brand" />
            <span className="font-bold">זימון תור אונליין</span>
          </Link>
          <Link href="/branches" className="flex items-center gap-3 rounded-2xl border border-line bg-white p-5 hover:border-navy/30">
            <MapPin className="h-5 w-5 text-brand" />
            <span className="font-bold">סניפים ושעות פתיחה</span>
          </Link>
        </aside>
      </div>
    </div>
  );
}
