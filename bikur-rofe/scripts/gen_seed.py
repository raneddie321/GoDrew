# Generates supabase/migrations/0002_seed.sql (catalog + clearly-marked sample data).
import json, pathlib

services = [
    ("urgent", "רפואה דחופה", "פציעות, שברים, חתכים ומחלות חריפות — בלי לחכות במיון.",
     "מוקד לטיפול רפואי דחוף ללא צורך בתור מראש. רופאים ואחיות מטפלים בפציעות ספורט ובית, שברים, נקעים, חתכים, כוויות, זיהומים, חום גבוה ומחלות חריפות. במקרה הצורך מבוצעות במקום בדיקות ומופנים להמשך טיפול.",
     "siren", True, 1),
    ("orthopedics", "אורתופדיה", "כאבי גב, מפרקים ופציעות ספורט.",
     "אבחון וטיפול בבעיות שלד ושריר: כאבי גב וצוואר, ברכיים, כתפיים, פציעות ספורט ומעקב אחרי שברים. האורתופדים שלנו מתאימים תוכנית טיפול אישית ומפנים לפיזיותרפיה או להדמיה לפי הצורך.",
     "bone", False, 2),
    ("ent", "אף אוזן גרון", "דלקות אוזניים, סינוסים, גרון ושמיעה.",
     "בדיקה וטיפול בדלקות אוזניים, שעווה, סינוסיטיס, כאבי גרון, צרידות ובעיות שמיעה — למבוגרים ולילדים.",
     "ear", False, 3),
    ("eyes", "עיניים", "בדיקות ראייה, דלקות וגירויים בעיניים.",
     "רופאי עיניים לבדיקה מקיפה, טיפול בדלקות ובגירויים, גוף זר בעין, ומעקב שוטף. במקרה הצורך מבוצעת הפניה להמשך טיפול.",
     "eye", False, 4),
    ("family", "רפואת משפחה", "ליווי רפואי שוטף לכל בני המשפחה.",
     "רופאי משפחה לבדיקה כללית, מחלות חריפות, חידוש מרשמים, אישורי מחלה והכוונה להמשך טיפול — למבוגרים ולילדים.",
     "heart-handshake", False, 5),
    ("blood-tests", "בדיקות דם", "בדיקות מעבדה עם תוצאות דיגיטליות.",
     "לקיחת דמים ובדיקות מעבדה בהפניית רופא. התוצאות נשלחות ישירות לאזור האישי. לחלק מהבדיקות נדרשת צום של 8–12 שעות — כדאי לבדוק בהפניה.",
     "droplet", True, 6),
]

urgent_hours = [{"days": "א׳–ה׳", "open": "08:00", "close": "23:00"}, {"days": "ו׳ וערבי חג", "open": "08:00", "close": "15:00"}, {"days": "מוצ״ש", "open": "18:00", "close": "23:00"}]
clinic_hours = [{"days": "א׳–ה׳", "open": "08:00", "close": "21:00"}, {"days": "ו׳", "open": "08:00", "close": "13:00"}]

branches = [
    ("tel-aviv", "סניף תל אביב", "תל אביב", "", 32.0853, 34.7818, True, urgent_hours, ["urgent", "orthopedics", "ent", "eyes", "family", "blood-tests"]),
    ("jerusalem", "סניף ירושלים", "ירושלים", "", 31.7683, 35.2137, True, urgent_hours, ["urgent", "orthopedics", "ent", "family", "blood-tests"]),
    ("haifa", "סניף חיפה", "חיפה", "", 32.7940, 34.9896, True, urgent_hours, ["urgent", "orthopedics", "eyes", "family", "blood-tests"]),
    ("rishon", "סניף ראשון לציון", "ראשון לציון", "", 31.9730, 34.7925, False, clinic_hours, ["orthopedics", "ent", "eyes", "family", "blood-tests"]),
    ("beer-sheva", "סניף באר שבע", "באר שבע", "", 31.2520, 34.7915, True, urgent_hours, ["urgent", "orthopedics", "family", "blood-tests"]),
    ("netanya", "סניף נתניה", "נתניה", "", 32.3215, 34.8532, False, clinic_hours, ["ent", "eyes", "family", "blood-tests"]),
]

# (name, title, service, bio, [(branch, [weekdays], start, end, slot)])
doctors = [
    ("מיכל לוי", "ד״ר", "orthopedics", "אורתופדית, מתמחה בפציעות ספורט וברכיים.", [("tel-aviv", [0, 2, 4], "08:00", "14:00", 15), ("rishon", [1, 3], "15:00", "20:00", 15)]),
    ("אבי כהן", "ד״ר", "orthopedics", "אורתופד, מתמחה בעמוד שדרה וכתפיים.", [("jerusalem", [0, 1, 2, 3], "09:00", "15:00", 20), ("beer-sheva", [4], "08:00", "13:00", 20)]),
    ("יעל שרון", "ד״ר", "orthopedics", "אורתופדית ילדים ומבוגרים.", [("haifa", [0, 2, 3], "14:00", "20:00", 15)]),
    ("רון אדלר", "ד״ר", "ent", "רופא אף אוזן גרון למבוגרים ולילדים.", [("tel-aviv", [1, 3], "15:00", "21:00", 15), ("netanya", [0, 2], "08:00", "13:00", 15)]),
    ("נועה ברק", "ד״ר", "ent", "רופאת אא״ג, מתמחה בסינוסים ובשמיעה.", [("jerusalem", [0, 2, 4], "08:00", "14:00", 15), ("rishon", [3], "09:00", "14:00", 15)]),
    ("דנה גולן", "ד״ר", "eyes", "רופאת עיניים, מתמחה בדלקות ובבדיקות ראייה.", [("tel-aviv", [0, 2], "15:00", "21:00", 20), ("haifa", [1, 3], "09:00", "14:00", 20)]),
    ("עידו מזרחי", "ד״ר", "eyes", "רופא עיניים למבוגרים ולילדים.", [("rishon", [0, 1, 4], "08:00", "13:00", 20), ("netanya", [3], "15:00", "20:00", 20)]),
    ("שירה אלון", "ד״ר", "family", "רופאת משפחה, ליווי שוטף לכל המשפחה.", [("tel-aviv", [0, 1, 2, 3, 4], "08:00", "13:00", 15), ("rishon", [0, 2], "16:00", "20:00", 15)]),
    ("יוסי פרץ", "ד״ר", "family", "רופא משפחה ורפואה כללית.", [("jerusalem", [0, 1, 2, 3, 4], "14:00", "20:00", 15), ("haifa", [5], "08:00", "12:00", 15)]),
    ("מאיה רוזן", "ד״ר", "family", "רופאת משפחה, מתמחה ברפואה מונעת.", [("beer-sheva", [0, 1, 2, 3], "08:00", "14:00", 15), ("netanya", [1, 4], "15:00", "20:00", 15)]),
    ("צוות המעבדה", "", "blood-tests", "לקיחת דמים ובדיקות מעבדה.", [(b, [0, 1, 2, 3, 4], "07:00", "11:00", 10) for b in ["tel-aviv", "jerusalem", "haifa", "rishon", "beer-sheva", "netanya"]]),
    ("רופא/ת תורנ/ית", "", "urgent", "צוות הרפואה הדחופה — אפשר להגיע גם ללא תור.", [(b, [0, 1, 2, 3, 4], "08:00", "23:00", 20) for b in ["tel-aviv", "jerusalem", "haifa", "beer-sheva"]]),
]

def q(s):
    return "'" + s.replace("'", "''") + "'"

out = ["-- Catalog and SAMPLE data (is_sample = true). Replace the sample branches and doctors",
       "-- with real ones in the admin area (/admin), then use “מחיקת נתוני דוגמה” there.", ""]
for slug, name, short, desc, icon, walk_in, sort in services:
    out.append(f"insert into public.services (slug, name, short, description, icon, walk_in, sort) values ({q(slug)}, {q(name)}, {q(short)}, {q(desc)}, {q(icon)}, {str(walk_in).lower()}, {sort}) on conflict (slug) do nothing;")
out.append("")
for i, (slug, name, city, address, lat, lng, urgent, hours, svc) in enumerate(branches):
    out.append(f"insert into public.branches (slug, name, city, address, lat, lng, urgent_care, hours, is_sample, sort) values ({q(slug)}, {q(name)}, {q(city)}, {q(address)}, {lat}, {lng}, {str(urgent).lower()}, {q(json.dumps(hours, ensure_ascii=False))}::jsonb, true, {i + 1}) on conflict (slug) do nothing;")
    for s in svc:
        out.append(f"insert into public.branch_services (branch_id, service_slug) select id, {q(s)} from public.branches where slug = {q(slug)} on conflict do nothing;")
out.append("")
for name, title, svc, bio, sched in doctors:
    out.append(f"with d as (insert into public.doctors (name, title, service_slug, bio, is_sample) values ({q(name)}, {q(title)}, {q(svc)}, {q(bio)}, true) returning id)")
    rows = []
    for branch, days, start, end, slot in sched:
        for wd in days:
            rows.append(f"select d.id, b.id, {wd}, {q(start)}::time, {q(end)}::time, {slot} from d, public.branches b where b.slug = {q(branch)}")
    out.append("insert into public.doctor_schedules (doctor_id, branch_id, weekday, start_time, end_time, slot_minutes)\n" + "\nunion all\n".join(rows) + ";")
out.append("")
out.append("insert into public.site_settings (key, value) values ('call_center_phone', ''), ('whatsapp', ''), ('email', '') on conflict (key) do nothing;")
out.append("""
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
""")
pathlib.Path("supabase/migrations/0002_seed.sql").write_text("\n".join(out) + "\n", encoding="utf-8")
print("ok")
