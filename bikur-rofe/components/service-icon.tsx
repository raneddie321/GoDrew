import { Bone, Droplet, Ear, Eye, HeartHandshake, Siren, Stethoscope, type LucideProps } from "lucide-react";

const ICONS = { siren: Siren, bone: Bone, ear: Ear, eye: Eye, "heart-handshake": HeartHandshake, droplet: Droplet, stethoscope: Stethoscope };

export function ServiceIcon({ name, ...props }: { name: string } & LucideProps) {
  const Icon = ICONS[name as keyof typeof ICONS] ?? Stethoscope;
  return <Icon {...props} />;
}
