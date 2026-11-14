-- Catalog and SAMPLE data (is_sample = true). Replace the sample branches and doctors
-- with real ones in the admin area (/admin), then use “מחיקת נתוני דוגמה” there.

insert into public.services (slug, name, short, description, icon, walk_in, sort) values ('urgent', 'רפואה דחופה', 'פציעות, שברים, חתכים ומחלות חריפות — בלי לחכות במיון.', 'מוקד לטיפול רפואי דחוף ללא צורך בתור מראש. רופאים ואחיות מטפלים בפציעות ספורט ובית, שברים, נקעים, חתכים, כוויות, זיהומים, חום גבוה ומחלות חריפות. במקרה הצורך מבוצעות במקום בדיקות ומופנים להמשך טיפול.', 'siren', true, 1) on conflict (slug) do nothing;
insert into public.services (slug, name, short, description, icon, walk_in, sort) values ('orthopedics', 'אורתופדיה', 'כאבי גב, מפרקים ופציעות ספורט.', 'אבחון וטיפול בבעיות שלד ושריר: כאבי גב וצוואר, ברכיים, כתפיים, פציעות ספורט ומעקב אחרי שברים. האורתופדים שלנו מתאימים תוכנית טיפול אישית ומפנים לפיזיותרפיה או להדמיה לפי הצורך.', 'bone', false, 2) on conflict (slug) do nothing;
insert into public.services (slug, name, short, description, icon, walk_in, sort) values ('ent', 'אף אוזן גרון', 'דלקות אוזניים, סינוסים, גרון ושמיעה.', 'בדיקה וטיפול בדלקות אוזניים, שעווה, סינוסיטיס, כאבי גרון, צרידות ובעיות שמיעה — למבוגרים ולילדים.', 'ear', false, 3) on conflict (slug) do nothing;
insert into public.services (slug, name, short, description, icon, walk_in, sort) values ('eyes', 'עיניים', 'בדיקות ראייה, דלקות וגירויים בעיניים.', 'רופאי עיניים לבדיקה מקיפה, טיפול בדלקות ובגירויים, גוף זר בעין, ומעקב שוטף. במקרה הצורך מבוצעת הפניה להמשך טיפול.', 'eye', false, 4) on conflict (slug) do nothing;
insert into public.services (slug, name, short, description, icon, walk_in, sort) values ('family', 'רפואת משפחה', 'ליווי רפואי שוטף לכל בני המשפחה.', 'רופאי משפחה לבדיקה כללית, מחלות חריפות, חידוש מרשמים, אישורי מחלה והכוונה להמשך טיפול — למבוגרים ולילדים.', 'heart-handshake', false, 5) on conflict (slug) do nothing;
insert into public.services (slug, name, short, description, icon, walk_in, sort) values ('blood-tests', 'בדיקות דם', 'בדיקות מעבדה עם תוצאות דיגיטליות.', 'לקיחת דמים ובדיקות מעבדה בהפניית רופא. התוצאות נשלחות ישירות לאזור האישי. לחלק מהבדיקות נדרשת צום של 8–12 שעות — כדאי לבדוק בהפניה.', 'droplet', true, 6) on conflict (slug) do nothing;

insert into public.branches (slug, name, city, address, lat, lng, urgent_care, hours, is_sample, sort) values ('tel-aviv', 'סניף תל אביב', 'תל אביב', '', 32.0853, 34.7818, true, '[{"days": "א׳–ה׳", "open": "08:00", "close": "23:00"}, {"days": "ו׳ וערבי חג", "open": "08:00", "close": "15:00"}, {"days": "מוצ״ש", "open": "18:00", "close": "23:00"}]'::jsonb, true, 1) on conflict (slug) do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'urgent' from public.branches where slug = 'tel-aviv' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'orthopedics' from public.branches where slug = 'tel-aviv' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'ent' from public.branches where slug = 'tel-aviv' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'eyes' from public.branches where slug = 'tel-aviv' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'family' from public.branches where slug = 'tel-aviv' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'blood-tests' from public.branches where slug = 'tel-aviv' on conflict do nothing;
insert into public.branches (slug, name, city, address, lat, lng, urgent_care, hours, is_sample, sort) values ('jerusalem', 'סניף ירושלים', 'ירושלים', '', 31.7683, 35.2137, true, '[{"days": "א׳–ה׳", "open": "08:00", "close": "23:00"}, {"days": "ו׳ וערבי חג", "open": "08:00", "close": "15:00"}, {"days": "מוצ״ש", "open": "18:00", "close": "23:00"}]'::jsonb, true, 2) on conflict (slug) do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'urgent' from public.branches where slug = 'jerusalem' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'orthopedics' from public.branches where slug = 'jerusalem' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'ent' from public.branches where slug = 'jerusalem' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'family' from public.branches where slug = 'jerusalem' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'blood-tests' from public.branches where slug = 'jerusalem' on conflict do nothing;
insert into public.branches (slug, name, city, address, lat, lng, urgent_care, hours, is_sample, sort) values ('haifa', 'סניף חיפה', 'חיפה', '', 32.794, 34.9896, true, '[{"days": "א׳–ה׳", "open": "08:00", "close": "23:00"}, {"days": "ו׳ וערבי חג", "open": "08:00", "close": "15:00"}, {"days": "מוצ״ש", "open": "18:00", "close": "23:00"}]'::jsonb, true, 3) on conflict (slug) do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'urgent' from public.branches where slug = 'haifa' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'orthopedics' from public.branches where slug = 'haifa' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'eyes' from public.branches where slug = 'haifa' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'family' from public.branches where slug = 'haifa' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'blood-tests' from public.branches where slug = 'haifa' on conflict do nothing;
insert into public.branches (slug, name, city, address, lat, lng, urgent_care, hours, is_sample, sort) values ('rishon', 'סניף ראשון לציון', 'ראשון לציון', '', 31.973, 34.7925, false, '[{"days": "א׳–ה׳", "open": "08:00", "close": "21:00"}, {"days": "ו׳", "open": "08:00", "close": "13:00"}]'::jsonb, true, 4) on conflict (slug) do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'orthopedics' from public.branches where slug = 'rishon' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'ent' from public.branches where slug = 'rishon' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'eyes' from public.branches where slug = 'rishon' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'family' from public.branches where slug = 'rishon' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'blood-tests' from public.branches where slug = 'rishon' on conflict do nothing;
insert into public.branches (slug, name, city, address, lat, lng, urgent_care, hours, is_sample, sort) values ('beer-sheva', 'סניף באר שבע', 'באר שבע', '', 31.252, 34.7915, true, '[{"days": "א׳–ה׳", "open": "08:00", "close": "23:00"}, {"days": "ו׳ וערבי חג", "open": "08:00", "close": "15:00"}, {"days": "מוצ״ש", "open": "18:00", "close": "23:00"}]'::jsonb, true, 5) on conflict (slug) do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'urgent' from public.branches where slug = 'beer-sheva' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'orthopedics' from public.branches where slug = 'beer-sheva' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'family' from public.branches where slug = 'beer-sheva' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'blood-tests' from public.branches where slug = 'beer-sheva' on conflict do nothing;
insert into public.branches (slug, name, city, address, lat, lng, urgent_care, hours, is_sample, sort) values ('netanya', 'סניף נתניה', 'נתניה', '', 32.3215, 34.8532, false, '[{"days": "א׳–ה׳", "open": "08:00", "close": "21:00"}, {"days": "ו׳", "open": "08:00", "close": "13:00"}]'::jsonb, true, 6) on conflict (slug) do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'ent' from public.branches where slug = 'netanya' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'eyes' from public.branches where slug = 'netanya' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'family' from public.branches where slug = 'netanya' on conflict do nothing;
insert into public.branch_services (branch_id, service_slug) select id, 'blood-tests' from public.branches where slug = 'netanya' on conflict do nothing;

with d as (insert into public.doctors (name, title, service_slug, bio, is_sample) values ('מיכל לוי', 'ד״ר', 'orthopedics', 'אורתופדית, מתמחה בפציעות ספורט וברכיים.', true) returning id)
insert into public.doctor_schedules (doctor_id, branch_id, weekday, start_time, end_time, slot_minutes)
select d.id, b.id, 0, '08:00'::time, '14:00'::time, 15 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 2, '08:00'::time, '14:00'::time, 15 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 4, '08:00'::time, '14:00'::time, 15 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 1, '15:00'::time, '20:00'::time, 15 from d, public.branches b where b.slug = 'rishon'
union all
select d.id, b.id, 3, '15:00'::time, '20:00'::time, 15 from d, public.branches b where b.slug = 'rishon';
with d as (insert into public.doctors (name, title, service_slug, bio, is_sample) values ('אבי כהן', 'ד״ר', 'orthopedics', 'אורתופד, מתמחה בעמוד שדרה וכתפיים.', true) returning id)
insert into public.doctor_schedules (doctor_id, branch_id, weekday, start_time, end_time, slot_minutes)
select d.id, b.id, 0, '09:00'::time, '15:00'::time, 20 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 1, '09:00'::time, '15:00'::time, 20 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 2, '09:00'::time, '15:00'::time, 20 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 3, '09:00'::time, '15:00'::time, 20 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 4, '08:00'::time, '13:00'::time, 20 from d, public.branches b where b.slug = 'beer-sheva';
with d as (insert into public.doctors (name, title, service_slug, bio, is_sample) values ('יעל שרון', 'ד״ר', 'orthopedics', 'אורתופדית ילדים ומבוגרים.', true) returning id)
insert into public.doctor_schedules (doctor_id, branch_id, weekday, start_time, end_time, slot_minutes)
select d.id, b.id, 0, '14:00'::time, '20:00'::time, 15 from d, public.branches b where b.slug = 'haifa'
union all
select d.id, b.id, 2, '14:00'::time, '20:00'::time, 15 from d, public.branches b where b.slug = 'haifa'
union all
select d.id, b.id, 3, '14:00'::time, '20:00'::time, 15 from d, public.branches b where b.slug = 'haifa';
with d as (insert into public.doctors (name, title, service_slug, bio, is_sample) values ('רון אדלר', 'ד״ר', 'ent', 'רופא אף אוזן גרון למבוגרים ולילדים.', true) returning id)
insert into public.doctor_schedules (doctor_id, branch_id, weekday, start_time, end_time, slot_minutes)
select d.id, b.id, 1, '15:00'::time, '21:00'::time, 15 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 3, '15:00'::time, '21:00'::time, 15 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 0, '08:00'::time, '13:00'::time, 15 from d, public.branches b where b.slug = 'netanya'
union all
select d.id, b.id, 2, '08:00'::time, '13:00'::time, 15 from d, public.branches b where b.slug = 'netanya';
with d as (insert into public.doctors (name, title, service_slug, bio, is_sample) values ('נועה ברק', 'ד״ר', 'ent', 'רופאת אא״ג, מתמחה בסינוסים ובשמיעה.', true) returning id)
insert into public.doctor_schedules (doctor_id, branch_id, weekday, start_time, end_time, slot_minutes)
select d.id, b.id, 0, '08:00'::time, '14:00'::time, 15 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 2, '08:00'::time, '14:00'::time, 15 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 4, '08:00'::time, '14:00'::time, 15 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 3, '09:00'::time, '14:00'::time, 15 from d, public.branches b where b.slug = 'rishon';
with d as (insert into public.doctors (name, title, service_slug, bio, is_sample) values ('דנה גולן', 'ד״ר', 'eyes', 'רופאת עיניים, מתמחה בדלקות ובבדיקות ראייה.', true) returning id)
insert into public.doctor_schedules (doctor_id, branch_id, weekday, start_time, end_time, slot_minutes)
select d.id, b.id, 0, '15:00'::time, '21:00'::time, 20 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 2, '15:00'::time, '21:00'::time, 20 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 1, '09:00'::time, '14:00'::time, 20 from d, public.branches b where b.slug = 'haifa'
union all
select d.id, b.id, 3, '09:00'::time, '14:00'::time, 20 from d, public.branches b where b.slug = 'haifa';
with d as (insert into public.doctors (name, title, service_slug, bio, is_sample) values ('עידו מזרחי', 'ד״ר', 'eyes', 'רופא עיניים למבוגרים ולילדים.', true) returning id)
insert into public.doctor_schedules (doctor_id, branch_id, weekday, start_time, end_time, slot_minutes)
select d.id, b.id, 0, '08:00'::time, '13:00'::time, 20 from d, public.branches b where b.slug = 'rishon'
union all
select d.id, b.id, 1, '08:00'::time, '13:00'::time, 20 from d, public.branches b where b.slug = 'rishon'
union all
select d.id, b.id, 4, '08:00'::time, '13:00'::time, 20 from d, public.branches b where b.slug = 'rishon'
union all
select d.id, b.id, 3, '15:00'::time, '20:00'::time, 20 from d, public.branches b where b.slug = 'netanya';
with d as (insert into public.doctors (name, title, service_slug, bio, is_sample) values ('שירה אלון', 'ד״ר', 'family', 'רופאת משפחה, ליווי שוטף לכל המשפחה.', true) returning id)
insert into public.doctor_schedules (doctor_id, branch_id, weekday, start_time, end_time, slot_minutes)
select d.id, b.id, 0, '08:00'::time, '13:00'::time, 15 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 1, '08:00'::time, '13:00'::time, 15 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 2, '08:00'::time, '13:00'::time, 15 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 3, '08:00'::time, '13:00'::time, 15 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 4, '08:00'::time, '13:00'::time, 15 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 0, '16:00'::time, '20:00'::time, 15 from d, public.branches b where b.slug = 'rishon'
union all
select d.id, b.id, 2, '16:00'::time, '20:00'::time, 15 from d, public.branches b where b.slug = 'rishon';
with d as (insert into public.doctors (name, title, service_slug, bio, is_sample) values ('יוסי פרץ', 'ד״ר', 'family', 'רופא משפחה ורפואה כללית.', true) returning id)
insert into public.doctor_schedules (doctor_id, branch_id, weekday, start_time, end_time, slot_minutes)
select d.id, b.id, 0, '14:00'::time, '20:00'::time, 15 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 1, '14:00'::time, '20:00'::time, 15 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 2, '14:00'::time, '20:00'::time, 15 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 3, '14:00'::time, '20:00'::time, 15 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 4, '14:00'::time, '20:00'::time, 15 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 5, '08:00'::time, '12:00'::time, 15 from d, public.branches b where b.slug = 'haifa';
with d as (insert into public.doctors (name, title, service_slug, bio, is_sample) values ('מאיה רוזן', 'ד״ר', 'family', 'רופאת משפחה, מתמחה ברפואה מונעת.', true) returning id)
insert into public.doctor_schedules (doctor_id, branch_id, weekday, start_time, end_time, slot_minutes)
select d.id, b.id, 0, '08:00'::time, '14:00'::time, 15 from d, public.branches b where b.slug = 'beer-sheva'
union all
select d.id, b.id, 1, '08:00'::time, '14:00'::time, 15 from d, public.branches b where b.slug = 'beer-sheva'
union all
select d.id, b.id, 2, '08:00'::time, '14:00'::time, 15 from d, public.branches b where b.slug = 'beer-sheva'
union all
select d.id, b.id, 3, '08:00'::time, '14:00'::time, 15 from d, public.branches b where b.slug = 'beer-sheva'
union all
select d.id, b.id, 1, '15:00'::time, '20:00'::time, 15 from d, public.branches b where b.slug = 'netanya'
union all
select d.id, b.id, 4, '15:00'::time, '20:00'::time, 15 from d, public.branches b where b.slug = 'netanya';
with d as (insert into public.doctors (name, title, service_slug, bio, is_sample) values ('צוות המעבדה', '', 'blood-tests', 'לקיחת דמים ובדיקות מעבדה.', true) returning id)
insert into public.doctor_schedules (doctor_id, branch_id, weekday, start_time, end_time, slot_minutes)
select d.id, b.id, 0, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 1, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 2, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 3, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 4, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 0, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 1, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 2, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 3, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 4, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 0, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'haifa'
union all
select d.id, b.id, 1, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'haifa'
union all
select d.id, b.id, 2, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'haifa'
union all
select d.id, b.id, 3, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'haifa'
union all
select d.id, b.id, 4, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'haifa'
union all
select d.id, b.id, 0, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'rishon'
union all
select d.id, b.id, 1, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'rishon'
union all
select d.id, b.id, 2, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'rishon'
union all
select d.id, b.id, 3, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'rishon'
union all
select d.id, b.id, 4, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'rishon'
union all
select d.id, b.id, 0, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'beer-sheva'
union all
select d.id, b.id, 1, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'beer-sheva'
union all
select d.id, b.id, 2, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'beer-sheva'
union all
select d.id, b.id, 3, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'beer-sheva'
union all
select d.id, b.id, 4, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'beer-sheva'
union all
select d.id, b.id, 0, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'netanya'
union all
select d.id, b.id, 1, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'netanya'
union all
select d.id, b.id, 2, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'netanya'
union all
select d.id, b.id, 3, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'netanya'
union all
select d.id, b.id, 4, '07:00'::time, '11:00'::time, 10 from d, public.branches b where b.slug = 'netanya';
with d as (insert into public.doctors (name, title, service_slug, bio, is_sample) values ('רופא/ת תורנ/ית', '', 'urgent', 'צוות הרפואה הדחופה — אפשר להגיע גם ללא תור.', true) returning id)
insert into public.doctor_schedules (doctor_id, branch_id, weekday, start_time, end_time, slot_minutes)
select d.id, b.id, 0, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 1, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 2, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 3, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 4, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'tel-aviv'
union all
select d.id, b.id, 0, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 1, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 2, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 3, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 4, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'jerusalem'
union all
select d.id, b.id, 0, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'haifa'
union all
select d.id, b.id, 1, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'haifa'
union all
select d.id, b.id, 2, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'haifa'
union all
select d.id, b.id, 3, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'haifa'
union all
select d.id, b.id, 4, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'haifa'
union all
select d.id, b.id, 0, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'beer-sheva'
union all
select d.id, b.id, 1, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'beer-sheva'
union all
select d.id, b.id, 2, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'beer-sheva'
union all
select d.id, b.id, 3, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'beer-sheva'
union all
select d.id, b.id, 4, '08:00'::time, '23:00'::time, 20 from d, public.branches b where b.slug = 'beer-sheva';

insert into public.site_settings (key, value) values ('call_center_phone', ''), ('whatsapp', ''), ('email', '') on conflict (key) do nothing;

-- Removes every sample branch and doctor (and their schedules). Staff only.
create or replace function public.delete_sample_data()
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.is_staff() then raise exception 'forbidden'; end if;
  delete from public.appointments where doctor_id in (select id from public.doctors where is_sample)
    or branch_id in (select id from public.branches where is_sample);
  delete from public.doctors where is_sample;
  delete from public.branches where is_sample;
end;
$$;
grant execute on function public.delete_sample_data() to authenticated;

