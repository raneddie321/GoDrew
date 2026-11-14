import Link from "next/link";
import { Mail, Phone } from "lucide-react";
import { Logo } from "./logo";
import type { Branch, Service, Settings } from "@/lib/types";
import { telLink } from "@/lib/format";

export function Footer({ services, branches, settings }: { services: Service[]; branches: Branch[]; settings: Settings }) {
  const columns = [
    { title: "שירותים", links: services.map((s) => ({ href: `/services/${s.slug}`, label: s.name })) },
    { title: "סניפים", links: [...branches.slice(0, 5).map((b) => ({ href: `/branches/${b.slug}`, label: b.name })), { href: "/branches", label: "כל הסניפים" }] },
    {
      title: "מידע ושירות",
      links: [
        { href: "/booking", label: "זימון תור" },
        { href: "/account", label: "אזור אישי" },
        { href: "/contact", label: "צור קשר" },
        { href: "/accessibility", label: "הצהרת נגישות" },
        { href: "/privacy", label: "מדיניות פרטיות" },
        { href: "/terms", label: "תנאי שימוש" },
      ],
    },
  ];
  return (
    <footer className="bg-navy text-white">
      <div className="mx-auto max-w-7xl px-4 pb-10 pt-16 sm:px-6">
        <div className="grid gap-12 lg:grid-cols-[1.2fr_2fr]">
          <div>
            <Logo variant="white" />
            <p className="mt-5 max-w-sm text-white/70">רשת מרפאות פרטיות: רפואה דחופה, רופאים מומחים ובדיקות — בפריסה ארצית, שבעה ימים בשבוע.</p>
            <div className="mt-6 space-y-2 text-[15px]">
              {settings.call_center_phone && (
                <a href={telLink(settings.call_center_phone)} className="flex items-center gap-2 font-bold hover:text-brand-100" dir="ltr">
                  <Phone className="h-4 w-4 text-brand-100" />
                  {settings.call_center_phone}
                </a>
              )}
              {settings.email && (
                <a href={`mailto:${settings.email}`} className="flex items-center gap-2 hover:text-brand-100">
                  <Mail className="h-4 w-4 text-brand-100" />
                  {settings.email}
                </a>
              )}
            </div>
          </div>
          <div className="grid grid-cols-2 gap-8 sm:grid-cols-3">
            {columns.map((col) => (
              <div key={col.title}>
                <h2 className="font-display text-base font-bold">{col.title}</h2>
                <ul className="mt-4 space-y-2.5">
                  {col.links.map((l) => (
                    <li key={l.href + l.label}>
                      <Link href={l.href} className="text-[15px] text-white/70 transition-colors hover:text-white">
                        {l.label}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </div>
        <div className="mt-14 flex flex-col gap-2 border-t border-white/10 pt-6 text-sm text-white/55 sm:flex-row sm:justify-between">
          <p>© {new Date().getFullYear()} ביקור רופא. כל הזכויות שמורות.</p>
          <p>במקרה חירום רפואי מסכן חיים יש לפנות למד״א בטלפון 101.</p>
        </div>
      </div>
    </footer>
  );
}
