"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const ITEMS = [
  { href: "/admin", label: "יומן תורים" },
  { href: "/admin/branches", label: "סניפים" },
  { href: "/admin/doctors", label: "רופאים ויומנים" },
  { href: "/admin/messages", label: "פניות" },
  { href: "/admin/settings", label: "הגדרות וצוות" },
];

export function AdminNav() {
  const pathname = usePathname();
  return (
    <nav aria-label="ניהול" className="flex gap-1 overflow-x-auto border-b border-line">
      {ITEMS.map((i) => {
        const active = i.href === "/admin" ? pathname === "/admin" : pathname.startsWith(i.href);
        return (
          <Link
            key={i.href}
            href={i.href}
            aria-current={active ? "page" : undefined}
            className={`-mb-px shrink-0 border-b-[3px] px-4 py-3 font-bold ${active ? "border-brand text-navy" : "border-transparent text-muted hover:text-navy"}`}
          >
            {i.label}
          </Link>
        );
      })}
    </nav>
  );
}
