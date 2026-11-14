import type { Metadata } from "next";
import { ForgotForm } from "@/components/auth-form";
import { AuthShell } from "@/components/auth-shell";

export const metadata: Metadata = { title: "איפוס סיסמה" };

export default function ForgotPasswordPage() {
  return (
    <AuthShell title="שכחתי סיסמה" subtitle="נשלח אליכם קישור לבחירת סיסמה חדשה.">
      <ForgotForm />
    </AuthShell>
  );
}
