# ביקור רופא — אתר וזימון תורים

Next.js 16 + Supabase. Includes: online booking (no double booking), a personal area (cancel, reschedule, calendar, Waze), branches with "near me", urgent care, a contact form, accessibility/privacy/terms pages, and a staff admin area.

## Setup (one time)

1. **Supabase**: create a project → SQL Editor → run, in order:
   `supabase/migrations/0001_schema.sql`, `0002_seed.sql`, `0003_admin.sql`.
2. **Auth → URL Configuration**: Site URL = the site's address; add `https://<domain>/auth/callback` to Redirect URLs.
3. **Vercel → Environment Variables**:
   - `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY` (Supabase → Project Settings → API)
   - `NEXT_PUBLIC_SITE_URL` = the site's address
   - `NEXT_PUBLIC_ALLOW_INDEXING=true` only at launch (until then search engines are asked not to index)
4. **First admin**: sign up on the site, then in the SQL Editor:
   `update profiles set role = 'admin' where id = (select id from auth.users where email = 'you@example.com');`
   After that, more staff can be added from **ניהול → הגדרות וצוות**.

## Sample data
Branches and doctors from `0002_seed.sql` are marked as samples, and the site shows a notice while any remain.
Edit them in **ניהול → סניפים / רופאים ויומנים** (saving removes the sample flag) or delete them all from **הגדרות וצוות**.

## Local development
```
npx supabase start   # applies the migrations
cp .env.example .env.local   # fill with the local URL/anon key from `npx supabase status`
npm install && npm run dev
```
