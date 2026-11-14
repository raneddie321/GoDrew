import { SettingsAdmin, type StaffMember } from "@/components/admin/settings-admin";
import { getSession, getSettings } from "@/lib/data";
import { createClient } from "@/lib/supabase/server";

export default async function AdminSettings() {
  const supabase = await createClient();
  const [settings, session, { data: staff }, { count: sampleBranches }, { count: sampleDoctors }] = await Promise.all([
    getSettings(),
    getSession(),
    supabase.rpc("staff_members"),
    supabase.from("branches").select("id", { count: "exact", head: true }).eq("is_sample", true),
    supabase.from("doctors").select("id", { count: "exact", head: true }).eq("is_sample", true),
  ]);
  return (
    <SettingsAdmin
      settings={settings}
      staff={(staff ?? []) as StaffMember[]}
      isAdmin={session.profile?.role === "admin"}
      myId={session.userId ?? ""}
      sampleCount={(sampleBranches ?? 0) + (sampleDoctors ?? 0)}
    />
  );
}
