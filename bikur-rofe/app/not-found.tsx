import Link from "next/link";
import { Logo } from "@/components/logo";

export default function NotFound() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 px-4 text-center">
      <Logo />
      <h1 className="font-display text-4xl font-black">העמוד לא נמצא</h1>
      <p className="text-lg text-muted">ייתכן שהקישור שגוי או שהעמוד הועבר.</p>
      <div className="flex gap-2">
        <Link href="/" className="rounded-xl bg-navy px-6 py-3 font-bold text-white">
          לדף הבית
        </Link>
        <Link href="/booking" className="rounded-xl border border-line px-6 py-3 font-bold">
          זימון תור
        </Link>
      </div>
    </div>
  );
}
