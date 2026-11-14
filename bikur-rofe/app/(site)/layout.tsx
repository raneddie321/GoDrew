import { Header } from "@/components/header";
import { Footer } from "@/components/footer";
import { getBranches, getServices, getSession, getSettings, hasSampleData } from "@/lib/data";
import { isSupabaseConfigured } from "@/lib/supabase/config";

export default async function SiteLayout({ children }: { children: React.ReactNode }) {
  const [session, settings, services, branches, sample] = await Promise.all([getSession(), getSettings(), getServices(), getBranches(), hasSampleData()]);
  return (
    <>
      {!isSupabaseConfigured() && (
        <div className="bg-amber-100 px-4 py-2 text-center text-sm font-semibold text-amber-900">האתר עדיין לא מחובר למסד הנתונים (Supabase). ראו README להגדרה.</div>
      )}
      {sample && (
        <div className="bg-navy-900 px-4 py-2 text-center text-[13px] text-white/80">האתר בהרצה — פרטי הסניפים והרופאים המוצגים כרגע הם לדוגמה.</div>
      )}
      <Header userName={session.profile?.full_name ?? (session.userId ? (session.email ?? "") : null)} isStaff={session.profile?.role === "staff" || session.profile?.role === "admin"} phone={settings.call_center_phone} />
      <main id="main">{children}</main>
      <Footer services={services} branches={branches} settings={settings} />
    </>
  );
}
