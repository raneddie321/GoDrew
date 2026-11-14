import type { Metadata } from "next";
import { Suspense } from "react";
import { AuthForm } from "@/components/auth-form";
import { AuthShell } from "@/components/auth-shell";

export const metadata: Metadata = { title: "הרשמה" };

export default function SignupPage() {
  return (
    <AuthShell title="יצירת חשבון" subtitle="חשבון אחד לזימון תורים וניהולם בכל הסניפים.">
      <Suspense>
        <AuthForm mode="signup" />
      </Suspense>
    </AuthShell>
  );
}
