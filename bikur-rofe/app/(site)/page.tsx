import Link from "next/link";
import { ArrowLeft, CalendarCheck, Clock, MapPin, Siren, Stethoscope } from "lucide-react";
import { QuickBook } from "@/components/quick-book";
import { ServiceIcon } from "@/components/service-icon";
import { Faq } from "@/components/faq";
import { getBranches, getDoctors, getServices } from "@/lib/data";
import { FAQ, STATS } from "@/lib/content";

export default async function HomePage() {
  const [services, branches, doctors] = await Promise.all([getServices(), getBranches(), getDoctors()]);
  const urgentBranches = branches.filter((b) => b.urgent_care);
  const cities = [...new Set(branches.map((b) => b.city))];

  return (
    <>
      {/* Hero */}
      <section className="border-b border-line bg-white">
        <div className="mx-auto grid max-w-7xl items-center gap-12 px-4 py-14 sm:px-6 lg:grid-cols-[1fr_440px] lg:py-20">
          <div>
            <p className="inline-flex items-center gap-2 rounded-full bg-brand-50 px-3 py-1 text-sm font-bold text-brand-700">
              <span className="h-2 w-2 rounded-full bg-brand" />
              {branches.length > 0 ? `${branches.length} סניפים בפריסה ארצית` : "סניפים בפריסה ארצית"}
            </p>
            <h1 className="mt-5 font-display text-[2.7rem] font-black leading-[1.05] tracking-tight sm:text-6xl lg:text-[4.2rem]">
              רפואה פרטית.
              <br />
              <span className="text-brand">זמינה.</span> אנושית.
            </h1>
            <p className="mt-6 max-w-xl text-xl text-muted">תור לרופא מומחה עוד היום, בלי לחכות חודשים. רפואה דחופה ללא תור, שבעה ימים בשבוע.</p>
            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
              <Link href="/booking" className="inline-flex items-center justify-center gap-2 rounded-xl bg-brand px-7 py-4 text-lg font-bold text-white transition-colors hover:bg-brand-600">
                <CalendarCheck className="h-5 w-5" />
                קבע תור עכשיו
              </Link>
              <Link href="/branches?near=1" className="inline-flex items-center justify-center gap-2 rounded-xl border-2 border-navy/15 px-7 py-4 text-lg font-bold text-navy transition-colors hover:border-navy/40">
                <MapPin className="h-5 w-5 text-brand" />
                מצא סניף קרוב אלי
              </Link>
            </div>
          </div>
          <QuickBook services={services} branches={branches} doctors={doctors} />
        </div>
      </section>

      {/* Trust bar */}
      <section className="bg-navy text-white">
        <ul className="mx-auto grid max-w-7xl gap-px px-4 sm:grid-cols-3 sm:px-6">
          {STATS.map((s, i) => (
            <li key={s.label} className="flex items-center gap-4 py-6 sm:justify-center">
              <span className="grid h-11 w-11 place-items-center rounded-full bg-white/10 text-brand-100">
                {i === 0 ? <Stethoscope className="h-5 w-5" /> : i === 1 ? <Clock className="h-5 w-5" /> : <Siren className="h-5 w-5" />}
              </span>
              <span>
                <span className="block font-display text-2xl font-extrabold">{s.value}</span>
                <span className="block text-white/70">{s.label}</span>
              </span>
            </li>
          ))}
        </ul>
      </section>

      {/* Services */}
      <section className="mx-auto max-w-7xl px-4 py-20 sm:px-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h2 className="font-display text-3xl font-black sm:text-4xl">השירותים שלנו</h2>
            <p className="mt-2 max-w-xl text-lg text-muted">רופאים מומחים, רפואה דחופה ובדיקות — במקום אחד.</p>
          </div>
          <Link href="/services" className="inline-flex items-center gap-1 font-bold text-brand-700 hover:underline">
            לכל השירותים <ArrowLeft className="h-4 w-4" />
          </Link>
        </div>
        <ul className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {services.map((s) => (
            <li key={s.slug}>
              <Link
                href={`/services/${s.slug}`}
                className="group flex h-full flex-col rounded-2xl border border-line bg-white p-6 transition-all hover:-translate-y-1 hover:border-brand/40 hover:shadow-[0_18px_40px_-24px_rgba(10,25,49,0.35)]"
              >
                <div className="flex items-start justify-between">
                  <span className="grid h-12 w-12 place-items-center rounded-xl bg-brand-50 text-brand transition-colors group-hover:bg-brand group-hover:text-white">
                    <ServiceIcon name={s.icon} className="h-6 w-6" />
                  </span>
                  {s.walk_in && <span className="rounded-full bg-card px-2.5 py-1 text-xs font-bold text-navy/70">ללא תור מראש</span>}
                </div>
                <h3 className="mt-5 font-display text-xl font-extrabold">{s.name}</h3>
                <p className="mt-1.5 flex-1 text-muted">{s.short}</p>
                <span className="mt-5 inline-flex items-center gap-1 font-bold text-navy">
                  פרטים וזימון תור <ArrowLeft className="h-4 w-4 transition-transform group-hover:-translate-x-1" />
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      {/* How it works */}
      <section className="border-y border-line bg-white">
        <div className="mx-auto max-w-7xl px-4 py-20 sm:px-6">
          <h2 className="font-display text-3xl font-black sm:text-4xl">איך זה עובד</h2>
          <ol className="mt-10 grid gap-8 md:grid-cols-3">
            {[
              { icon: MapPin, title: "מוצאים סניף", text: "בוחרים סניף קרוב לפי מיקום, עיר או תחום." },
              { icon: CalendarCheck, title: "מזמינים תור אונליין", text: "בוחרים רופא ושעה פנויה, ומקבלים אישור מיידי." },
              { icon: Stethoscope, title: "מגיעים ונבדקים", text: "מגיעים לסניף בשעה שנקבעה. את התורים מנהלים באזור האישי." },
            ].map((step, i) => (
              <li key={step.title} className="flex gap-4">
                <span className="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-navy font-display text-lg font-black text-white">{i + 1}</span>
                <div>
                  <h3 className="flex items-center gap-2 font-display text-xl font-extrabold">
                    {step.title}
                    <step.icon className="h-5 w-5 text-brand" />
                  </h3>
                  <p className="mt-1 text-muted">{step.text}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* Urgent care */}
      {urgentBranches.length > 0 && (
        <section className="mx-auto max-w-7xl px-4 py-20 sm:px-6">
          <div className="grid gap-10 rounded-3xl bg-navy p-8 text-white sm:p-12 lg:grid-cols-[1fr_1.2fr]">
            <div>
              <span className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-sm font-bold text-brand-100">
                <Siren className="h-4 w-4" /> רפואה דחופה
              </span>
              <h2 className="mt-4 font-display text-3xl font-black sm:text-4xl">צריכים רופא עכשיו?</h2>
              <p className="mt-3 text-lg text-white/75">מגיעים ללא תור לאחד מסניפי הרפואה הדחופה — לטיפול בפציעות, שברים, חתכים ומחלות חריפות.</p>
              <Link href="/urgent" className="mt-6 inline-flex items-center gap-2 rounded-xl bg-white px-6 py-3 font-bold text-navy hover:bg-brand-50">
                שעות פתיחה וסניפים <ArrowLeft className="h-4 w-4" />
              </Link>
            </div>
            <ul className="grid gap-3 sm:grid-cols-2">
              {urgentBranches.map((b) => (
                <li key={b.id} className="rounded-2xl bg-white/[0.06] p-5 ring-1 ring-white/10">
                  <Link href={`/branches/${b.slug}`} className="font-display text-lg font-bold hover:underline">
                    {b.name}
                  </Link>
                  <ul className="mt-2 space-y-0.5 text-[15px] text-white/75">
                    {b.hours.map((h) => (
                      <li key={h.days} className="flex justify-between gap-3">
                        <span>{h.days}</span>
                        <span dir="ltr">
                          {h.open}–{h.close}
                        </span>
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
          </div>
        </section>
      )}

      {/* Branches by city */}
      {cities.length > 0 && (
        <section className="mx-auto max-w-7xl px-4 pb-20 sm:px-6">
          <h2 className="font-display text-3xl font-black sm:text-4xl">הסניפים שלנו</h2>
          <ul className="mt-8 flex flex-wrap gap-3">
            {branches.map((b) => (
              <li key={b.id}>
                <Link href={`/branches/${b.slug}`} className="inline-flex items-center gap-2 rounded-full border border-line bg-white px-5 py-2.5 font-semibold hover:border-brand/50 hover:text-brand-700">
                  <MapPin className="h-4 w-4 text-brand" />
                  {b.city}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* FAQ */}
      <section className="border-t border-line bg-white">
        <div className="mx-auto grid max-w-7xl gap-10 px-4 py-20 sm:px-6 lg:grid-cols-[1fr_1.6fr]">
          <div>
            <h2 className="font-display text-3xl font-black sm:text-4xl">שאלות נפוצות</h2>
            <p className="mt-3 text-lg text-muted">
              לא מצאתם תשובה?{" "}
              <Link href="/contact" className="font-bold text-brand-700 hover:underline">
                כתבו לנו
              </Link>
            </p>
          </div>
          <Faq items={FAQ} />
        </div>
      </section>
    </>
  );
}
