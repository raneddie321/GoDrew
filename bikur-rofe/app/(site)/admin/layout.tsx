import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { AdminNav } from "@/components/admin/nav";
import { getSession } from "@/lib/data";

export const metadata: Metadata = { title: "ניהול", robots: { index: false } };
export const dynamic = "force-dynamic";

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  const session = await getSession();
  if (!session.userId) redirect("/login?next=/admin");
  const role = session.profile?.role;
  if (role !== "staff" && role !== "admin") {
    return (
      <div className="mx-auto max-w-xl px-4 py-24 text-center">
        <h1 className="font-display text-3xl font-black">אין הרשאת ניהול</h1>
        <p className="mt-3 text-muted">האזור מיועד לצוות בלבד. אם אתם חלק מהצוות, בקשו ממנהל/ת המערכת להוסיף את החשבון שלכם.</p>
      </div>
    );
  }
  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      <AdminNav />
      <div className="mt-6">{children}</div>
    </div>
  );
}
