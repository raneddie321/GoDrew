import { DoctorsAdmin } from "@/components/admin/doctors-admin";
import { createClient } from "@/lib/supabase/server";
import type { Branch, Doctor, Schedule, Service } from "@/lib/types";

export default async function AdminDoctors() {
  const supabase = await createClient();
  const [{ data: doctors }, { data: schedules }, { data: branches }, { data: services }] = await Promise.all([
    supabase.from("doctors").select("*").order("name"),
    supabase.from("doctor_schedules").select("*").order("weekday").order("start_time"),
    supabase.from("branches").select("id,name,city").order("sort"),
    supabase.from("services").select("*").order("sort"),
  ]);
  return (
    <DoctorsAdmin
      doctors={(doctors ?? []) as Doctor[]}
      schedules={(schedules ?? []) as Schedule[]}
      branches={(branches ?? []) as Pick<Branch, "id" | "name" | "city">[]}
      services={(services ?? []) as Service[]}
    />
  );
}
