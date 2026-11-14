import { ChevronDown } from "lucide-react";

export function Faq({ items }: { items: { q: string; a: string }[] }) {
  return (
    <div className="divide-y divide-line rounded-2xl border border-line bg-white">
      {items.map((item) => (
        <details key={item.q} className="group px-5 sm:px-6">
          <summary className="flex cursor-pointer items-center justify-between gap-4 py-5 font-display text-[17px] font-bold">
            {item.q}
            <ChevronDown className="h-5 w-5 shrink-0 text-muted transition-transform group-open:rotate-180" />
          </summary>
          <p className="-mt-1 pb-5 text-muted">{item.a}</p>
        </details>
      ))}
    </div>
  );
}
