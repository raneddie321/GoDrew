"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { CalendarPlus, ChevronLeft, Menu, Phone, UserRound, X } from "lucide-react";
import { Logo } from "./logo";
import { telLink } from "@/lib/format";

const NAV = [
  { href: "/branches", label: "סניפים" },
  { href: "/services", label: "שירותים" },
  { href: "/urgent", label: "רפואה דחופה" },
  { href: "/booking", label: "זימון תור" },
];

export function Header({ userName, isStaff, phone }: { userName: string | null; isStaff: boolean; phone: string }) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => setOpen(false), [pathname]);
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const accountLabel = userName ? `שלום, ${userName.split(" ")[0]}` : "אזור אישי";

  return (
    <header className={`sticky top-0 z-40 border-b bg-white/95 backdrop-blur transition-shadow ${scrolled ? "border-line shadow-[0_2px_16px_rgba(10,25,49,0.06)]" : "border-transparent"}`}>
      <div className="mx-auto flex h-[72px] max-w-7xl items-center gap-6 px-4 sm:px-6">
        <Logo />
        <nav aria-label="ניווט ראשי" className="hidden flex-1 lg:block">
          <ul className="flex items-center gap-1">
            {NAV.map((item) => {
              const active = pathname.startsWith(item.href);
              return (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    aria-current={active ? "page" : undefined}
                    className={`rounded-lg px-3.5 py-2 text-[16px] font-semibold transition-colors ${active ? "text-brand" : "text-navy/80 hover:bg-card hover:text-navy"}`}
                  >
                    {item.label}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>
        <div className="ms-auto flex items-center gap-2 lg:ms-0">
          {phone && (
            <a href={telLink(phone)} className="hidden items-center gap-2 rounded-lg px-3 py-2 text-[15px] font-bold text-navy hover:bg-card xl:flex" dir="ltr">
              <Phone className="h-4 w-4 text-brand" />
              {phone}
            </a>
          )}
          {isStaff && (
            <Link href="/admin" className="hidden rounded-lg px-3 py-2 text-[15px] font-semibold text-navy/80 hover:bg-card md:block">
              ניהול
            </Link>
          )}
          <Link href="/account" className="hidden items-center gap-2 rounded-lg border border-line px-3.5 py-2 text-[15px] font-semibold text-navy hover:border-navy/30 sm:flex">
            <UserRound className="h-4 w-4" />
            {accountLabel}
          </Link>
          <Link href="/booking" className="hidden items-center gap-2 rounded-lg bg-brand px-4 py-2.5 text-[15px] font-bold text-white transition-colors hover:bg-brand-600 sm:flex">
            <CalendarPlus className="h-4 w-4" />
            קביעת תור
          </Link>
          <button
            onClick={() => setOpen((v) => !v)}
            className="grid h-11 w-11 place-items-center rounded-lg border border-line text-navy lg:hidden"
            aria-label={open ? "סגירת תפריט" : "פתיחת תפריט"}
            aria-expanded={open}
            aria-controls="mobile-nav"
          >
            {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
        </div>
      </div>

      {open && (
        <nav id="mobile-nav" aria-label="תפריט" className="border-t border-line bg-white px-4 pb-5 pt-2 lg:hidden">
          {[...NAV, { href: "/account", label: accountLabel }, ...(isStaff ? [{ href: "/admin", label: "ניהול" }] : [])].map((item) => (
            <Link key={item.href} href={item.href} className="flex items-center justify-between border-b border-line py-4 text-lg font-semibold text-navy">
              {item.label}
              <ChevronLeft className="h-5 w-5 text-muted" />
            </Link>
          ))}
          <Link href="/booking" className="mt-4 flex items-center justify-center gap-2 rounded-xl bg-brand py-4 text-lg font-bold text-white">
            <CalendarPlus className="h-5 w-5" />
            קביעת תור
          </Link>
          {phone && (
            <a href={telLink(phone)} className="mt-2 flex items-center justify-center gap-2 rounded-xl border border-line py-4 text-lg font-bold text-navy" dir="ltr">
              <Phone className="h-5 w-5 text-brand" />
              {phone}
            </a>
          )}
        </nav>
      )}
    </header>
  );
}
