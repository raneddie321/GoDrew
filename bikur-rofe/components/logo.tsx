import Image from "next/image";
import Link from "next/link";

export function Logo({ variant = "navy", className = "" }: { variant?: "navy" | "white"; className?: string }) {
  return (
    <Link href="/" className={`inline-flex shrink-0 items-center ${className}`} aria-label="ביקור רופא — לדף הבית">
      <Image src={variant === "navy" ? "/logo-navy.png" : "/logo-white.png"} alt="ביקור רופא" width={183} height={50} priority className="h-9 w-auto sm:h-10" />
    </Link>
  );
}
