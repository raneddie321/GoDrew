import type { Metadata, Viewport } from "next";
import { Assistant, Heebo } from "next/font/google";
import "./globals.css";

const heebo = Heebo({ subsets: ["hebrew", "latin"], variable: "--font-heebo", weight: ["400", "500", "700", "800", "900"], display: "swap" });
const assistant = Assistant({ subsets: ["hebrew", "latin"], variable: "--font-assistant", weight: ["400", "500", "600", "700"], display: "swap" });

const allowIndexing = process.env.NEXT_PUBLIC_ALLOW_INDEXING === "true";

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL || "http://localhost:3000"),
  title: { default: "ביקור רופא — רשת מרפאות פרטיות", template: "%s | ביקור רופא" },
  description: "רפואה פרטית זמינה: רפואה דחופה, רופאים מומחים ובדיקות דם. זימון תור אונליין, סניפים בפריסה ארצית.",
  robots: allowIndexing ? { index: true, follow: true } : { index: false, follow: false },
  openGraph: { type: "website", locale: "he_IL", siteName: "ביקור רופא" },
};

export const viewport: Viewport = {
  themeColor: "#0A1931",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="he" dir="rtl" className={`${heebo.variable} ${assistant.variable}`}>
      <body className="min-h-dvh font-sans text-[17px] leading-relaxed">
        <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:start-4 focus:top-4 focus:z-[100] focus:rounded-lg focus:bg-white focus:px-4 focus:py-2 focus:shadow-lg">
          דילוג לתוכן הראשי
        </a>
        {children}
      </body>
    </html>
  );
}
