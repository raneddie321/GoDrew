export function Prose({ title, updated, children }: { title: string; updated?: string; children: React.ReactNode }) {
  return (
    <article className="mx-auto max-w-3xl px-4 py-12 sm:px-6">
      <h1 className="font-display text-4xl font-black">{title}</h1>
      {updated && <p className="mt-2 text-muted">עודכן לאחרונה: {updated}</p>}
      <div className="mt-8 space-y-4 text-[17px] leading-8 [&_h2]:mt-10 [&_h2]:font-display [&_h2]:text-2xl [&_h2]:font-extrabold [&_li]:ms-5 [&_li]:list-disc [&_ul]:space-y-1">{children}</div>
    </article>
  );
}
