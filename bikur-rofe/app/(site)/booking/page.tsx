import type { Metadata } from "next";
import { Suspense } from "react";
import { BookingWizard } from "@/components/booking-wizard";
import { getBranches, getDoctors, getServices, getSession } from "@/lib/data";

export const metadata: Metadata = { title: "זימון תור", description: "זימון תור אונליין לרופא מומחה, רפואה דחופה ובדיקות דם." };

export default async function BookingPage() {
  const [services, branches, doctors, session] = await Promise.all([getServices(), getBranches(), getDoctors(), getSession()]);
  return (
    <div className="mx-auto max-w-5xl px-4 py-12 sm:px-6">
      <h1 className="font-display text-4xl font-black">זימון תור</h1>
      <p className="mt-2 text-lg text-muted">בכמה צעדים קצרים — ואישור מיידי.</p>
      <div className="mt-8">
        <Suspense>
          <BookingWizard services={services} branches={branches} doctors={doctors} userId={session.userId} profile={session.profile} />
        </Suspense>
      </div>
    </div>
  );
}
