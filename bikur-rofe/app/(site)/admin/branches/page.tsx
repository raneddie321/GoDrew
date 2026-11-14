import { BranchesAdmin } from "@/components/admin/branches-admin";
import { createClient } from "@/lib/supabase/server";
import type { Branch, Service } from "@/lib/types";

export default async function AdminBranches() {
  const supabase = await createClient();
  const [{ data: branches }, { data: services }] = await Promise.all([
    supabase.from("branches").select("*, branch_services(service_slug)").order("sort"),
    supabase.from("services").select("*").order("sort"),
  ]);
  const list = ((branches ?? []) as (Branch & { branch_services: { service_slug: string }[] })[]).map(({ branch_services, ...b }) => ({
    ...b,
    services: branch_services.map((s) => s.service_slug),
  }));
  return <BranchesAdmin branches={list} services={(services ?? []) as Service[]} />;
}
