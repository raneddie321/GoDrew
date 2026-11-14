import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Accessibility, CalendarPlus, Car, MapPin, Navigation, Phone, Siren } from "lucide-react";
import { ServiceIcon } from "@/components/service-icon";
import { getBranches, getDoctors, getServices } from "@/lib/data";
import { createClient } from "@/lib/supabase/server";
import { isSupabaseConfigured } from "@/lib/supabase/config";
import { mapsLink, telLink, wazeLink } from "@/lib/format";

async function load(slug: string) {
  const branch = (await getBranches()).find((b) => b.slug === slug);
  return branch ?? null;
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const branch = await load((await params).slug);
  return branch ? { title: branch.name, description: `${branch.name} — שעות פתיחה, כתובת, שירותים וזימון תור.` } : { title: "סניף לא נמצא" };
}

export default async function BranchPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const branch = await load(slug);
  if (!branch) notFound();
  const [services, doctors] = await Promise.all([getServices(), getDoctors()]);
  let doctorIds: string[] = [];
  if (isSupabaseConfigured()) {
    const supabase = await createClient();
    const { data } = await supabase.from("doctor_schedules").select("doctor_id").eq("branch_id", branch.id);
    doctorIds = [...new Set((data ?? []).map((r: { doctor_id: string }) => r.doctor_id))];
  }
  const branchServices = services.filter((s) => branch.services?.includes(s.slug));
  const branchDoctors = doctors.filter((d) => doctorIds.includes(d.id) && d.title);

  return (
    <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6">
      <nav aria-label="פירורי לחם" className="text-[15px] text-muted">
        <Link href="/branches" className="hover:underline">
          סניפים
        </Link>{" "}
        / {branch.name}
      </nav>
      <div className="mt-4 grid gap-8 lg:grid-cols-[1.4fr_1fr]">
        <div>
          <h1 className="font-display text-4xl font-black">{branch.name}</h1>
          <p className="mt-2 flex items-center gap-2 text-lg text-muted">
            <MapPin className="h-5 w-5" /> {[branch.address, branch.city].filter(Boolean).join(", ")}
          </p>
          <div className="mt-4 flex flex-wrap gap-2">
            {branch.urgent_care && (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-red-50 px-3 py-1 text-sm font-bold text-danger">
                <Siren className="h-4 w-4" /> רפואה דחופה ללא תור
              </span>
            )}
            {branch.accessible && (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-card px-3 py-1 text-sm font-bold text-navy/80">
                <Accessibility className="h-4 w-4" /> סניף נגיש
              </span>
            )}
          </div>

          <h2 className="mt-10 font-display text-2xl font-extrabold">שירותים בסניף</h2>
          <ul className="mt-4 grid gap-3 sm:grid-cols-2">
            {branchServices.map((s) => (
              <li key={s.slug}>
                <Link href={`/booking?branch=${branch.slug}&service=${s.slug}`} className="flex items-center gap-3 rounded-xl border border-line bg-white p-4 hover:border-brand/40">
                  <span className="grid h-10 w-10 place-items-center rounded-lg bg-brand-50 text-brand">
                    <ServiceIcon name={s.icon} className="h-5 w-5" />
                  </span>
                  <span className="font-bold">{s.name}</span>
                  <span className="ms-auto text-sm font-semibold text-brand-700">{s.walk_in ? "ללא תור" : "לזימון תור"}</span>
                </Link>
              </li>
            ))}
          </ul>

          {branchDoctors.length > 0 && (
            <>
              <h2 className="mt-10 font-display text-2xl font-extrabold">רופאים בסניף</h2>
              <ul className="mt-4 grid gap-3 sm:grid-cols-2">
                {branchDoctors.map((d) => (
                  <li key={d.id} className="flex items-center gap-3 rounded-xl border border-line bg-white p-4">
                    <span className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-navy font-bold text-white">{d.name.slice(0, 1)}</span>
                    <span>
                      <span className="block font-bold">
                        {d.title} {d.name}
                      </span>
                      <span className="block text-[15px] text-muted">{services.find((s) => s.slug === d.service_slug)?.name}</span>
                    </span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>

        <aside className="h-fit space-y-4 lg:sticky lg:top-24">
          <div className="rounded-2xl border border-line bg-white p-6">
            <h2 className="font-display text-lg font-extrabold">שעות פתיחה</h2>
            <ul className="mt-3 space-y-1">
              {branch.hours.map((h) => (
                <li key={h.days} className="flex justify-between gap-3 border-b border-dashed border-line py-1.5 last:border-0">
                  <span className="text-muted">{h.days}</span>
                  <span dir="ltr" className="font-bold">
                    {h.open}–{h.close}
                  </span>
                </li>
              ))}
            </ul>
            {branch.parking && (
              <p className="mt-4 flex items-start gap-2 text-[15px] text-muted">
                <Car className="mt-1 h-4 w-4 shrink-0" /> {branch.parking}
              </p>
            )}
          </div>
          <div className="grid gap-2">
            <Link href={`/booking?branch=${branch.slug}`} className="flex items-center justify-center gap-2 rounded-xl bg-brand py-3.5 text-lg font-bold text-white hover:bg-brand-600">
              <CalendarPlus className="h-5 w-5" /> קביעת תור בסניף
            </Link>
            <div className="grid grid-cols-2 gap-2">
              <a href={wazeLink(branch)} target="_blank" rel="noreferrer" className="flex items-center justify-center gap-2 rounded-xl border border-line bg-white py-3 font-bold hover:border-navy/30">
                <Navigation className="h-4 w-4" /> Waze
              </a>
              <a href={mapsLink(branch)} target="_blank" rel="noreferrer" className="flex items-center justify-center gap-2 rounded-xl border border-line bg-white py-3 font-bold hover:border-navy/30">
                <MapPin className="h-4 w-4" /> Google Maps
              </a>
            </div>
            {branch.phone && (
              <a href={telLink(branch.phone)} className="flex items-center justify-center gap-2 rounded-xl border border-line bg-white py-3 font-bold hover:border-navy/30" dir="ltr">
                <Phone className="h-4 w-4" /> {branch.phone}
              </a>
            )}
          </div>
        </aside>
      </div>
    </div>
  );
}
