import { cache } from "react";
import { createClient } from "@/lib/supabase/server";
import { isSupabaseConfigured } from "@/lib/supabase/config";
import type { Branch, Doctor, Profile, Service, Settings } from "@/lib/types";

/** Server-side reads shared by pages. Cached per request. */

export const getServices = cache(async (): Promise<Service[]> => {
  if (!isSupabaseConfigured()) return [];
  const supabase = await createClient();
  const { data } = await supabase.from("services").select("*").order("sort");
  return (data ?? []) as Service[];
});

export const getBranches = cache(async (): Promise<Branch[]> => {
  if (!isSupabaseConfigured()) return [];
  const supabase = await createClient();
  const { data } = await supabase.from("branches").select("*, branch_services(service_slug)").eq("active", true).order("sort");
  return ((data ?? []) as (Branch & { branch_services: { service_slug: string }[] })[]).map(({ branch_services, ...b }) => ({
    ...b,
    services: branch_services.map((s) => s.service_slug),
  }));
});

export const getDoctors = cache(async (): Promise<Doctor[]> => {
  if (!isSupabaseConfigured()) return [];
  const supabase = await createClient();
  const { data } = await supabase.from("doctors").select("*").eq("active", true).order("name");
  return (data ?? []) as Doctor[];
});

export const getSettings = cache(async (): Promise<Settings> => {
  const settings: Settings = { call_center_phone: "", whatsapp: "", email: "" };
  if (!isSupabaseConfigured()) return settings;
  const supabase = await createClient();
  const { data } = await supabase.from("site_settings").select("key,value");
  for (const row of (data ?? []) as { key: keyof Settings; value: string }[]) settings[row.key] = row.value;
  return settings;
});

export const getSession = cache(async (): Promise<{ userId: string | null; email: string | null; profile: Profile | null }> => {
  if (!isSupabaseConfigured()) return { userId: null, email: null, profile: null };
  const supabase = await createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return { userId: null, email: null, profile: null };
  const { data } = await supabase.from("profiles").select("id,full_name,phone,role").eq("id", user.id).maybeSingle<Profile>();
  return { userId: user.id, email: user.email ?? null, profile: data };
});

export async function hasSampleData(): Promise<boolean> {
  const branches = await getBranches();
  const doctors = await getDoctors();
  return branches.some((b) => b.is_sample) || doctors.some((d) => d.is_sample);
}
