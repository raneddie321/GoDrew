import { AppointmentsBoard } from "@/components/admin/appointments-board";
import { createClient } from "@/lib/supabase/server";
import { addDays, israelDateOf, israelToday } from "@/lib/format";
import type { Appointment, Branch } from "@/lib/types";

export default async function AdminAppointments({ searchParams }: { searchParams: Promise<{ date?: string; branch?: string }> }) {
  const sp = await searchParams;
  const date = /^\d{4}-\d{2}-\d{2}$/.test(sp.date ?? "") ? sp.date! : israelToday();
  const supabase = await createClient();
  const { data: branches } = await supabase.from("branches").select("id,name,city").order("sort");
  let q = supabase
    .from("appointments")
    .select("*, doctor:doctors(name,title), service:services(name), branch:branches(name,city,address,lat,lng,slug,phone)")
    .gte("starts_at", `${addDays(date, -1)}T00:00:00Z`)
    .lt("starts_at", `${addDays(date, 1)}T23:59:59Z`)
    .order("starts_at");
  if (sp.branch) q = q.eq("branch_id", sp.branch);
  const { data } = await q;
  const appointments = ((data ?? []) as Appointment[]).filter((a) => israelDateOf(a.starts_at) === date);
  return <AppointmentsBoard date={date} branchId={sp.branch ?? ""} branches={(branches ?? []) as Pick<Branch, "id" | "name" | "city">[]} appointments={appointments} />;
}
