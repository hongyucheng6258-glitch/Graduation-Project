"""Add a self-contained campus demo batch using the running Compose project's MySQL."""
import argparse
import base64
import json
import re
import secrets
import subprocess
from datetime import datetime, timedelta, timezone
from pathlib import Path

import bcrypt

from campus_demo_content import add_content, backfill_legacy
from campus_demo_learning import add_learning

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "docker" / "demo" / "campus"
PREFIX = "CAMPUSDEMO"
NAMES = (
    "林予安", "陈知夏", "周明澈", "许书禾", "何沐阳", "叶听澜", "梁嘉言", "唐星野",
    "顾清和", "沈南乔", "陆承远", "宋晚晴", "方亦舟", "程若棠", "苏景行", "徐念初",
)
BIOS = (
    "计算机专业，喜欢整理读书笔记，常去图书馆自习。",
    "外语专业，平时练口语，也参加校园读书会。",
    "数学专业，喜欢羽毛球，周末经常在体育馆。",
    "设计专业，喜欢摄影和速写，偶尔交换画材。",
    "电子信息专业，参加过校内机器人训练。",
    "教育专业，常参加图书整理和校园志愿活动。",
    "管理专业，和同学组队准备案例比赛。",
    "软件工程专业，喜欢做课程项目和校园跑步。",
)


class Ref:
    def __init__(self, sql):
        self.sql = sql


def ident(name):
    if not re.fullmatch(r"[a-zA-Z0-9_]+", name):
        raise ValueError(f"Invalid SQL identifier: {name}")
    return f"`{name}`"


def literal(value):
    if isinstance(value, Ref):
        return value.sql
    if value is None:
        return "NULL"
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, datetime):
        value = value.isoformat(" ", timespec="seconds")
    if value == "":
        return "''"
    return "CONVERT(0x" + str(value).encode("utf-8").hex() + " USING utf8mb4)"


class Seed:
    def __init__(self):
        self.sql = ["SET time_zone = '+08:00'", "START TRANSACTION"]
        self.serial = 0
        self.covers = {}

    def insert(self, table, **fields):
        self.serial += 1
        columns = ", ".join(map(ident, fields))
        values = ", ".join(literal(value) for value in fields.values())
        self.sql.append(f"INSERT INTO {ident(table)} ({columns}) VALUES ({values})")
        ref = Ref(f"@campus_demo_{self.serial}")
        self.sql.append(f"SET {ref.sql} = LAST_INSERT_ID()")
        return ref

    def update(self, table, key_ref, **fields):
        fields_sql = ", ".join(f"{ident(k)} = {literal(v)}" for k, v in fields.items())
        self.sql.append(f"UPDATE {ident(table)} SET {fields_sql} WHERE id = {literal(key_ref)}")

    def backfill(self, table, row_id, title, photo):
        images = literal(self.images(photo))
        self.sql.append(f"""UPDATE {ident(table)} AS i JOIN `user` AS u ON u.id=i.user_id
          SET i.images={images}, i.description=CONCAT(COALESCE(i.description,''), {literal('（演示配图）')})
          WHERE i.id={literal(row_id)} AND i.title={literal(title)}
            AND u.student_no IN ('2021001','2021002','2021003','2021004','2021005','2021006')
            AND (i.images IS NULL OR TRIM(i.images) IN ('','[]'))""")

    def cover(self, key):
        if key not in self.covers:
            image = (ASSETS / f"{key}.webp").read_bytes()
            if not (image.startswith(b"RIFF") and image[8:12] == b"WEBP"):
                raise ValueError(f"Invalid image {key}")
            self.covers[key] = "data:image/webp;base64," + base64.b64encode(image).decode("ascii")
        return self.covers[key]

    def image_resource(self, owner, key, created):
        url = self.cover(key)
        return self.insert("upload_resource", owner_user_id=owner, resource_url=url, resource_type="image",
                           content_type="image/webp", file_size=(ASSETS / f"{key}.webp").stat().st_size,
                           create_time=created)

    def pdf(self, owner, key, created):
        pdf = (ASSETS / f"{key}.pdf").read_bytes()
        text = (ASSETS / f"{key}.txt").read_text(encoding="utf-8")
        if not pdf.startswith(b"%PDF-") or not text.strip():
            raise ValueError(f"Invalid PDF example {key}")
        return self.insert("pdf_document", user_id=owner, file_name=text.splitlines()[0] + ".pdf",
                           file_url="data:application/pdf;base64," + base64.b64encode(pdf).decode("ascii"),
                           page_count=1, text_content=text, status=1, create_time=created)

    def images(self, key):
        return json.dumps([self.cover(key)])


def mysql(project, sql):
    command = ["docker", "compose", "-p", project, "-f", "docker-compose.yml", "-f", "docker-compose.agent.yml",
               "exec", "-T", "mysql", "sh", "-ec",
               'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --skip-column-names --default-character-set=utf8mb4 "$MYSQL_DATABASE"']
    result = subprocess.run(command, cwd=ROOT, input=sql.encode("utf-8"), capture_output=True, timeout=120)
    if result.returncode:
        errors = result.stderr.decode("utf-8", errors="replace").splitlines()
        raise RuntimeError(next((line for line in reversed(errors) if line.startswith("ERROR ")),
                                "MySQL demo import failed."))
    return result.stdout.decode("utf-8").strip()


def verify(project):
    user_ids = f"SELECT id FROM `user` WHERE student_no LIKE '{PREFIX}%'"
    expected = {
        "idle_item": 24, "activity": 20, "lost_found": 16, "post": 20,
        "study_partner": 12, "campus_question": 12, "wrong_question": 16,
        "ai_session": 6, "pdf_document": 2, "draw_game_room": 3,
    }
    for table, minimum in expected.items():
        owner = "owner_user_id" if table == "draw_game_room" else "user_id"
        count = int(mysql(project, f"SELECT COUNT(*) FROM {ident(table)} WHERE {owner} IN ({user_ids});"))
        if count < minimum:
            raise ValueError(f"{table}: expected at least {minimum}, got {count}")
        print(f"{table}: {count}")
    for table in ("idle_item", "activity", "lost_found", "post"):
        bad = mysql(project, f"SELECT COUNT(*) FROM {ident(table)} WHERE user_id IN ({user_ids}) "
                             "AND (images IS NULL OR NOT JSON_VALID(images) OR JSON_LENGTH(images) = 0);")
        if int(bad):
            raise ValueError(f"{table}: {bad} missing image sets")
    bad = mysql(project, f"""SELECT COUNT(*) FROM activity a WHERE a.user_id IN ({user_ids})
        AND (a.create_time >= a.signup_deadline OR a.signup_deadline >= a.start_time OR a.start_time >= a.end_time
        OR a.max_members < (SELECT COUNT(*) FROM activity_member m WHERE m.activity_id=a.id AND m.status=1));""")
    if int(bad):
        raise ValueError("Activity date or capacity mismatch")
    bad = mysql(project, f"""SELECT COUNT(*) FROM idle_item i WHERE i.user_id IN ({user_ids})
        AND i.status IN (1,2) AND NOT EXISTS (SELECT 1 FROM idle_appointment a
          WHERE a.item_id=i.id AND a.seller_id=i.user_id AND a.status=IF(i.status=1,1,3));""")
    if int(bad):
        raise ValueError("Idle item and reservation state mismatch")
    bad = mysql(project, f"""SELECT COUNT(*) FROM lost_found f WHERE f.user_id IN ({user_ids}) AND f.status=1
        AND NOT EXISTS (SELECT 1 FROM lost_found_claim c WHERE c.lost_found_id=f.id AND c.status=3);""")
    if int(bad):
        raise ValueError("Lost item return state mismatch")
    bad = mysql(project, f"""SELECT COUNT(*) FROM draw_game_round g JOIN draw_game_room r ON r.id=g.room_id
         WHERE r.owner_user_id IN ({user_ids}) AND g.snapshot_resource_id IS NOT NULL;""")
    if int(bad) != 9:
        raise ValueError("Expected nine archived game drawings")
    print("PASS corresponding image sets, activity chronology and capacity, exchange and lost-property states, archived artwork")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", required=True)
    parser.add_argument("--password-file", type=Path)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"ai-campus-(?:docker|demo-qa)", args.project):
        parser.error("Use ai-campus-demo-qa or ai-campus-docker")
    if args.verify_only:
        verify(args.project)
        return
    if not args.password_file:
        parser.error("--password-file is required for a new demo account")
    seed = Seed()
    existing = int(mysql(args.project, f"SELECT COUNT(*) FROM `user` WHERE student_no='{PREFIX}01';"))
    if not existing:
        admin_id = mysql(args.project, "SELECT id FROM admin WHERE status=0 AND role='super' ORDER BY id LIMIT 1;")
        if not admin_id.isdigit():
            raise ValueError("No enabled super administrator for publishing demo notices")
        now = datetime.now(timezone(timedelta(hours=8))).replace(tzinfo=None, microsecond=0)
        if args.password_file.exists():
            password = args.password_file.read_text(encoding="utf-8").strip()
        else:
            password = secrets.token_urlsafe(12)
            with args.password_file.open("x", encoding="utf-8") as handle:
                handle.write(password + "\n")
        password_hash = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()
        users = [
            seed.insert("user", student_no=f"{PREFIX}{index + 1:02}", nickname=f"{name}·演示",
                        password=password_hash if index == 0 else None, bio=BIOS[index % len(BIOS)],
                        create_time=now - timedelta(days=55 + index))
            for index, name in enumerate(NAMES)
        ]
        add_content(seed, users, now, int(admin_id))
        add_learning(seed, users, now)
        for key, title, question, answer in (
            ("library-guide", "图书馆使用演示指南", "小组讨论前需要做什么？",
             "文档建议先确认研讨室的可预约时段，预约后按时到场，取消使用时释放时段。"),
            ("lab-notes", "实验记录与复盘演示讲义", "遇到异常读数应该直接删除吗？",
             "不要直接删除异常读数。需要改正时应保留原读数，并注明改正的原因。"),
        ):
            created = now - timedelta(days=2, hours=1)
            doc = seed.pdf(users[0], key, created)
            session = seed.insert("ai_session", user_id=users[0], scene="pdf", title=title, doc_id=doc,
                                  create_time=created, update_time=created + timedelta(minutes=1))
            seed.insert("ai_message", session_id=session, role="user", content=question, create_time=created)
            seed.insert("ai_message", session_id=session, role="assistant", content=answer,
                        create_time=created + timedelta(minutes=1))
        print(f"Prepared {seed.serial} new rows and {len(seed.covers)} distinct matching image files.")
    else:
        print("Demo batch already exists. Existing demo records are retained.")
    backfill_legacy(seed)
    seed.sql.append("COMMIT")
    mysql(args.project, ";\n".join(seed.sql) + ";\n")
    verify(args.project)


if __name__ == "__main__":
    main()
