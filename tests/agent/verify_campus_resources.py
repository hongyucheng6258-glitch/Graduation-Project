"""Live acceptance for lost-and-found, study partners, and campus Q&A cards."""

import argparse
import json
import os
import subprocess
import uuid
from urllib.parse import urlsplit

import pymysql

from verify_live import ROOT, ask, call, cleanup_student, local_config, ok, token


def verify(base, cfg, db, browser):
    marker = "CampusLink" + uuid.uuid4().hex[:7]
    private_contact = "contact-" + marker
    users = []
    with db.cursor() as cur:
        try:
            for index in range(2):
                cur.execute("INSERT INTO user(student_no,nickname) VALUES(%s,%s)",
                            (f"{marker}{index}", f"Campus Resource QA {index}"))
                users.append(cur.lastrowid)
            reader, author = users
            fixture = []
            for audit in (1, 0):
                cur.execute("INSERT INTO lost_found(user_id,type,title,location,contact,audit_status,status) VALUES(%s,1,%s,'图书馆',%s,%s,0)",
                            (author, f"{marker} 水杯 {audit}", private_contact, audit))
                if audit == 1:
                    fixture.append({"kind": "lost", "id": cur.lastrowid, "question": f"有人捡到标题含 {marker} 的水杯吗？查校园失物招领。", "path": f"/lostfound/detail/{cur.lastrowid}"})
                cur.execute("INSERT INTO study_partner(user_id,subject,goal,contact,audit_status,status) VALUES(%s,%s,'算法练习',%s,%s,0)",
                            (author, f"{marker} Java {audit}", private_contact, audit))
                if audit == 1:
                    fixture.append({"kind": "partner", "id": cur.lastrowid, "question": f"帮我找科目包含 {marker} 的学习搭子。", "path": f"/user/{author}"})
                cur.execute("INSERT INTO campus_question(user_id,title,content,category,status) VALUES(%s,%s,'选课咨询','课程',%s)",
                            (author, f"{marker} 怎么选课 {audit}", 0 if audit == 1 else 9))
                if audit == 1:
                    fixture.append({"kind": "qa", "id": cur.lastrowid, "question": f"查校园互助问答里标题含 {marker} 的选课问题。", "path": f"/qa/detail/{cur.lastrowid}"})
            db.commit()
            bearer = token(cfg["JWT_SECRET"], reader)
            if browser:
                subprocess.run(["node", str(ROOT / "tests/agent/verify_campus_resources.cjs")],
                               env={**os.environ, "CAMPUS_RESOURCE_FIXTURE": json.dumps({"token": bearer, "rows": fixture})},
                               timeout=120, check=True)
                return
            for entry in fixture:
                result = ask(base, bearer, entry["question"], "search_campus_resources")
                assert [(row["kind"], row["id"]) for row in result["resources"]] == [(entry["kind"], entry["id"])]
                assert private_contact not in json.dumps(result)
                entry["session"] = result["sessionId"]
            cur.execute("UPDATE lost_found SET audit_status=0 WHERE id=%s", (fixture[0]["id"],))
            cur.execute("UPDATE study_partner SET status=1 WHERE id=%s", (fixture[1]["id"],))
            cur.execute("UPDATE campus_question SET status=9 WHERE id=%s", (fixture[2]["id"],))
            db.commit()
            for entry in fixture:
                history = ok(call(base, f'/api/ai/session/{entry["session"]}/messages', bearer=bearer, method="GET"))
                assert not any(row.get("attachments", {}).get("resources") for row in history["list"])
            print("PASS live lost-and-found, study partners, Q&A, visibility and restored-card revocation", flush=True)
        finally:
            for uid in users:
                cur.execute("DELETE FROM lost_found WHERE user_id=%s", (uid,))
                cur.execute("DELETE FROM study_partner WHERE user_id=%s", (uid,))
                cur.execute("DELETE FROM campus_question WHERE user_id=%s", (uid,))
                cleanup_student(cur, uid)
            db.commit()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--browser", action="store_true")
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    if urlsplit(args.base).hostname not in ("127.0.0.1", "localhost", "::1"):
        raise SystemExit("Loopback backends only")
    config = local_config()
    with pymysql.connect(host="127.0.0.1", port=int(config.get("MYSQL_PORT", 3306)),
                         user=config.get("MYSQL_USER", "root"), password=config["MYSQL_PASSWORD"],
                         database=config.get("MYSQL_DB", "ai_campus_platform"), charset="utf8mb4") as connection:
        verify(args.base.rstrip("/"), config, connection, args.browser)
