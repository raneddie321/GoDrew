import type { Metadata } from "next";
import { ResetForm } from "@/components/auth-form";
import { AuthShell } from "@/components/auth-shell";

export const metadata: Metadata = { title: "סיסמה חדשה" };

export default function ResetPasswordPage() {
  return (
    <AuthShell title="בחירת סיסמה חדשה" subtitle="הזינו סיסמה חדשה לחשבון.">
      <ResetForm />
    </AuthShell>
  );
}
