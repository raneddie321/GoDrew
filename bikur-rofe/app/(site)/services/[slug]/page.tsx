import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { CalendarPlus, MapPin } from "lucide-react";
import { ServiceIcon } from "@/components/service-icon";
import { getBranches, getDoctors, getServices } from "@/lib/data";

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  const service = (await getServices()).find((s) => s.slug === slug);
  return service ? { title: service.name, description: service.short } : { title: "שירות לא נמצא" };
}

export default async function ServicePage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const [services, branches, doctors] = await Promise.all([getServices(), getBranches(), getDoctors()]);
  const service = services.find((s) => s.slug === slug);
  if (!service) notFound();
  const where = branches.filter((b) => b.services?.includes(slug));
  const who = doctors.filter((d) => d.service_slug === slug && d.title);

  return (
    <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6">
      <nav aria-label="פירורי לחם" className="text-[15px] text-muted">
        <Link href="/services" className="hover:underline">
          שירותים
        </Link>{" "}
        / {service.name}
      </nav>
      <div className="mt-6 grid gap-10 lg:grid-cols-[1.4fr_1fr]">
        <div>
          <span className="grid h-16 w-16 place-items-center rounded-2xl bg-brand-50 text-brand">
            <ServiceIcon name={service.icon} className="h-8 w-8" />
          </span>
          <h1 className="mt-5 font-display text-4xl font-black">{service.name}</h1>
          <p className="mt-4 max-w-2xl text-lg leading-relaxed text-navy/85">{service.description}</p>

          {who.length > 0 && (
            <>
              <h2 className="mt-10 font-display text-2xl font-extrabold">הרופאים שלנו</h2>
              <ul className="mt-4 grid gap-3 sm:grid-cols-2">
                {who.map((d) => (
                  <li key={d.id} className="rounded-xl border border-line bg-white p-4">
                    <p className="font-bold">
                      {d.title} {d.name}
                    </p>
                    <p className="text-[15px] text-muted">{d.bio}</p>
                    <Link href={`/booking?service=${slug}&doctor=${d.id}`} className="mt-2 inline-block text-[15px] font-bold text-brand-700 hover:underline">
                      תור אצל {d.title} {d.name.split(" ")[0]}
                    </Link>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
        <aside className="h-fit rounded-2xl border border-line bg-white p-6 lg:sticky lg:top-24">
          <h2 className="font-display text-lg font-extrabold">איפה מקבלים את השירות</h2>
          <ul className="mt-3 divide-y divide-line">
            {where.map((b) => (
              <li key={b.id} className="flex items-center justify-between gap-3 py-2.5">
                <Link href={`/branches/${b.slug}`} className="flex items-center gap-2 font-semibold hover:text-brand-700">
                  <MapPin className="h-4 w-4 text-brand" /> {b.name}
                </Link>
                {!service.walk_in && (
                  <Link href={`/booking?service=${slug}&branch=${b.slug}`} className="text-sm font-bold text-brand-700 hover:underline">
                    לתור
                  </Link>
                )}
              </li>
            ))}
          </ul>
          <Link
            href={service.walk_in && slug === "urgent" ? "/urgent" : `/booking?service=${slug}`}
            className="mt-5 flex items-center justify-center gap-2 rounded-xl bg-brand py-3.5 text-lg font-bold text-white hover:bg-brand-600"
          >
            <CalendarPlus className="h-5 w-5" />
            {slug === "urgent" ? "שעות פתיחה ומיקום" : "קביעת תור"}
          </Link>
          {slug === "urgent" && (
            <Link href={`/booking?service=urgent`} className="mt-2 block text-center text-[15px] font-semibold text-navy/80 hover:underline">
              או קביעת שעת הגעה מראש
            </Link>
          )}
        </aside>
      </div>
    </div>
  );
}
