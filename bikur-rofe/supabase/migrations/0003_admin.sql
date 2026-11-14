-- Admin helpers: manage staff roles by email.

create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.profiles where id = auth.uid() and role = 'admin');
$$;
grant execute on function public.is_admin() to authenticated;

-- Give a registered user a role. Admins only; an admin can't demote themselves.
create or replace function public.set_user_role(p_email text, p_role text)
returns void language plpgsql security definer set search_path = public, auth as $$
declare
  v_id uuid;
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  if p_role not in ('patient', 'staff', 'admin') then raise exception 'bad_role'; end if;
  select id into v_id from auth.users where lower(email) = lower(trim(p_email));
  if v_id is null then raise exception 'user_not_found'; end if;
  if v_id = auth.uid() and p_role <> 'admin' then raise exception 'cannot_demote_self'; end if;
  insert into public.profiles (id, role) values (v_id, p_role)
  on conflict (id) do update set role = excluded.role;
end;
$$;
revoke execute on function public.set_user_role(text, text) from public, anon;
grant execute on function public.set_user_role(text, text) to authenticated;

-- Staff list with emails, for the admin screen.
create or replace function public.staff_members()
returns table (id uuid, email text, full_name text, role text)
language sql stable security definer set search_path = public, auth as $$
  select p.id, u.email::text, p.full_name, p.role
  from public.profiles p join auth.users u on u.id = p.id
  where p.role in ('staff', 'admin') and public.is_staff()
  order by p.role, u.email;
$$;
revoke execute on function public.staff_members() from public, anon;
grant execute on function public.staff_members() to authenticated;
