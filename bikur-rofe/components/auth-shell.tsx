export function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: React.ReactNode }) {
  return (
    <div className="mx-auto max-w-md px-4 py-14 sm:py-20">
      <div className="rounded-2xl border border-line bg-white p-7 sm:p-8">
        <h1 className="font-display text-3xl font-black">{title}</h1>
        <p className="mt-1.5 text-muted">{subtitle}</p>
        <div className="mt-7">{children}</div>
      </div>
    </div>
  );
}
