import type { Metadata } from "next";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { ServiceIcon } from "@/components/service-icon";
import { getServices } from "@/lib/data";

export const metadata: Metadata = { title: "שירותים", description: "רפואה דחופה, אורתופדיה, אף אוזן גרון, עיניים, רפואת משפחה ובדיקות דם." };

export default async function ServicesPage() {
  const services = await getServices();
  return (
    <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6">
      <h1 className="font-display text-4xl font-black">שירותים</h1>
      <p className="mt-2 text-lg text-muted">בחרו תחום כדי לקרוא עליו ולקבוע תור.</p>
      <ul className="mt-10 grid gap-4 md:grid-cols-2">
        {services.map((s) => (
          <li key={s.slug}>
            <Link href={`/services/${s.slug}`} className="group flex h-full gap-5 rounded-2xl border border-line bg-white p-6 hover:border-brand/40">
              <span className="grid h-14 w-14 shrink-0 place-items-center rounded-xl bg-brand-50 text-brand">
                <ServiceIcon name={s.icon} className="h-7 w-7" />
              </span>
              <span className="flex-1">
                <span className="flex items-center gap-2 font-display text-xl font-extrabold">
                  {s.name}
                  {s.walk_in && <span className="rounded-full bg-card px-2 py-0.5 text-xs font-bold text-navy/70">ללא תור מראש</span>}
                </span>
                <span className="mt-1 block text-muted">{s.description}</span>
                <span className="mt-3 inline-flex items-center gap-1 font-bold text-brand-700">
                  לפרטים ולזימון תור <ArrowLeft className="h-4 w-4 transition-transform group-hover:-translate-x-1" />
                </span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
