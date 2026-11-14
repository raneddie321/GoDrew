export interface Service {
  slug: string;
  name: string;
  short: string;
  description: string;
  icon: string;
  walk_in: boolean;
  sort: number;
}

export interface OpeningHours {
  days: string;
  open: string;
  close: string;
}

export interface Branch {
  id: string;
  slug: string;
  name: string;
  city: string;
  address: string;
  phone: string | null;
  lat: number | null;
  lng: number | null;
  hours: OpeningHours[];
  urgent_care: boolean;
  parking: string | null;
  accessible: boolean;
  active: boolean;
  is_sample: boolean;
  sort: number;
  services?: string[];
}

export interface Doctor {
  id: string;
  name: string;
  title: string;
  service_slug: string;
  bio: string;
  photo_url: string | null;
  languages: string[];
  active: boolean;
  is_sample: boolean;
}

export interface Schedule {
  id: number;
  doctor_id: string;
  branch_id: string;
  weekday: number;
  start_time: string;
  end_time: string;
  slot_minutes: number;
}

export type AppointmentStatus = "booked" | "cancelled" | "arrived" | "completed" | "no_show";

export interface Appointment {
  id: string;
  user_id: string;
  doctor_id: string;
  branch_id: string;
  service_slug: string;
  starts_at: string;
  ends_at: string;
  status: AppointmentStatus;
  patient_name: string;
  patient_phone: string;
  reason: string;
  created_at: string;
  doctor?: Pick<Doctor, "name" | "title"> | null;
  branch?: Pick<Branch, "name" | "city" | "address" | "lat" | "lng" | "slug" | "phone"> | null;
  service?: Pick<Service, "name"> | null;
}

export interface Profile {
  id: string;
  full_name: string | null;
  phone: string | null;
  role: "patient" | "staff" | "admin";
}

export interface Settings {
  call_center_phone: string;
  whatsapp: string;
  email: string;
}
