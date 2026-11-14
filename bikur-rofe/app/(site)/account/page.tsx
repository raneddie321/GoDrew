import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { AccountView } from "@/components/account-view";
import { getSession } from "@/lib/data";
import { createClient } from "@/lib/supabase/server";
import type { Appointment } from "@/lib/types";

export const metadata: Metadata = { title: "אזור אישי" };
export const dynamic = "force-dynamic";

export default async function AccountPage() {
  const session = await getSession();
  if (!session.userId) redirect("/login?next=/account");
  const supabase = await createClient();
  const { data } = await supabase
    .from("appointments")
    .select("*, doctor:doctors(name,title), branch:branches(name,city,address,lat,lng,slug,phone), service:services(name)")
    .eq("user_id", session.userId)
    .order("starts_at", { ascending: false })
    .limit(100);
  return <AccountView appointments={(data ?? []) as Appointment[]} profile={session.profile} email={session.email ?? ""} />;
}
