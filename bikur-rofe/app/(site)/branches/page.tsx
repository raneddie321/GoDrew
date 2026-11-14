import type { Metadata } from "next";
import { Suspense } from "react";
import { BranchFinder } from "@/components/branch-finder";
import { getBranches, getServices } from "@/lib/data";

export const metadata: Metadata = { title: "סניפים", description: "סניפי ביקור רופא בפריסה ארצית — כתובות, שעות פתיחה וניווט." };

export default async function BranchesPage() {
  const [branches, services] = await Promise.all([getBranches(), getServices()]);
  return (
    <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6">
      <h1 className="font-display text-4xl font-black">סניפים</h1>
      <p className="mt-2 text-lg text-muted">מצאו את הסניף הקרוב אליכם, בדקו שעות פתיחה וקבעו תור.</p>
      <div className="mt-8">
        <Suspense>
          <BranchFinder branches={branches} services={services} />
        </Suspense>
      </div>
    </div>
  );
}
