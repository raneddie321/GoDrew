import type { Metadata } from "next";
import { Suspense } from "react";
import { AuthForm } from "@/components/auth-form";
import { AuthShell } from "@/components/auth-shell";

export const metadata: Metadata = { title: "כניסה לאזור האישי" };

export default function LoginPage() {
  return (
    <AuthShell title="כניסה לאזור האישי" subtitle="ניהול תורים, פרטים אישיים והיסטוריית ביקורים.">
      <Suspense>
        <AuthForm mode="login" />
      </Suspense>
    </AuthShell>
  );
}
