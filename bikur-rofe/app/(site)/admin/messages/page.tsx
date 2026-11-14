import { MessagesAdmin, type Message } from "@/components/admin/messages-admin";
import { createClient } from "@/lib/supabase/server";

export default async function AdminMessages() {
  const supabase = await createClient();
  const { data } = await supabase.from("contact_messages").select("*").order("handled").order("created_at", { ascending: false }).limit(200);
  return <MessagesAdmin messages={(data ?? []) as Message[]} />;
}
